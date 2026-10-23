package com.amosley.hearth.tv.ha

import android.util.Log
import com.amosley.hearth.shared.protocol.Protocol
import com.amosley.hearth.tv.data.ConfigStore
import com.amosley.hearth.tv.data.HaCall
import com.amosley.hearth.tv.data.HomeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class HaEntity(val id: String, val state: String, val attributes: JsonObject) {
    val domain get() = id.substringBefore('.')
    val name get() = attr("friendly_name") ?: id.substringAfter('.').replace('_', ' ').replaceFirstChar { it.uppercase() }
    fun attr(key: String): String? = (attributes[key] as? JsonPrimitive)?.contentOrNull
    fun attrDouble(key: String): Double? = (attributes[key] as? JsonPrimitive)?.doubleOrNull
    fun attrInt(key: String): Int? = (attributes[key] as? JsonPrimitive)?.intOrNull
    val isOn get() = state in setOf("on", "playing", "open", "unlocked", "home", "heat", "cool", "auto", "heat_cool")
}

/**
 * Home Assistant over its WebSocket API with a long-lived access token.
 * Keeps a live map of every entity's state (get_states + state_changed subscription).
 */
class HomeAssistant(private val configStore: ConfigStore, private val scope: CoroutineScope) {
    private val http = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _entities = MutableStateFlow<Map<String, HaEntity>>(emptyMap())
    val entities: StateFlow<Map<String, HaEntity>> = _entities.asStateFlow()

    /** disconnected | connecting | connected | auth_failed | not_configured */
    private val _status = MutableStateFlow("not_configured")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _changes = MutableSharedFlow<HaEntity>(extraBufferCapacity = 64)
    /** Every state_changed event, e.g. for the doorbell pop-up. */
    val changes: SharedFlow<HaEntity> = _changes

    private var ws: WebSocket? = null
    private val ids = AtomicInteger(1)
    private var loop: Job? = null
    private var currentCfg: HomeConfig? = null

    fun start() {
        scope.launch {
            configStore.config.map { it.home.url to it.home.token }.distinctUntilChanged().collect { reconnect() }
        }
    }

    private fun reconnect() {
        loop?.cancel()
        ws?.close(1000, null)
        ws = null
        val cfg = configStore.value.home
        currentCfg = cfg
        if (!cfg.configured) { _status.value = "not_configured"; _entities.value = emptyMap(); return }
        loop = scope.launch {
            var backoff = 2000L
            while (true) {
                if (_status.value != "connected" && _status.value != "connecting") {
                    connect(cfg)
                }
                delay(backoff)
                backoff = if (_status.value == "connected") 2000L else (backoff * 2).coerceAtMost(60_000L)
            }
        }
    }

    private fun wsUrl(base: String): String =
        base.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/api/websocket"

    private fun connect(cfg: HomeConfig) {
        _status.value = "connecting"
        val req = try { Request.Builder().url(wsUrl(cfg.url)).build() } catch (e: Exception) {
            _status.value = "disconnected"; return
        }
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) = handle(webSocket, cfg, text)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "HA socket failure: ${t.message}")
                if (_status.value != "auth_failed") _status.value = "disconnected"
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (_status.value != "auth_failed") _status.value = "disconnected"
            }
        })
    }

    private var statesId = -1

    private fun handle(socket: WebSocket, cfg: HomeConfig, text: String) {
        val msg = try { Protocol.json.parseToJsonElement(text).jsonObject } catch (_: Exception) { return }
        when (msg["type"]?.jsonPrimitive?.contentOrNull) {
            "auth_required" -> socket.send(buildJsonObject { put("type", "auth"); put("access_token", cfg.token) }.toString())
            "auth_invalid" -> { _status.value = "auth_failed"; socket.close(1000, null) }
            "auth_ok" -> {
                _status.value = "connected"
                statesId = ids.getAndIncrement()
                socket.send(buildJsonObject { put("id", statesId); put("type", "get_states") }.toString())
                socket.send(buildJsonObject {
                    put("id", ids.getAndIncrement()); put("type", "subscribe_events"); put("event_type", "state_changed")
                }.toString())
            }
            "result" -> {
                val id = msg["id"]?.jsonPrimitive?.intOrNull
                if (id == statesId) {
                    val list = msg["result"] as? JsonArray ?: return
                    _entities.value = list.mapNotNull { parseEntity(it) }.associateBy { it.id }
                }
            }
            "event" -> {
                val data = msg["event"]?.jsonObject?.get("data")?.jsonObject ?: return
                val newState = data["new_state"]
                val id = data["entity_id"]?.jsonPrimitive?.contentOrNull ?: return
                val e = newState?.let { parseEntity(it) }
                _entities.value = if (e == null) _entities.value - id else _entities.value + (id to e)
                if (e != null) _changes.tryEmit(e)
            }
        }
    }

    private fun parseEntity(el: JsonElement): HaEntity? {
        val o = el as? JsonObject ?: return null
        val id = o["entity_id"]?.jsonPrimitive?.contentOrNull ?: return null
        return HaEntity(id, o["state"]?.jsonPrimitive?.contentOrNull ?: "unknown", o["attributes"] as? JsonObject ?: JsonObject(emptyMap()))
    }

    operator fun get(id: String): HaEntity? = _entities.value[id]

    val connected get() = _status.value == "connected"

    /** Calls a service, e.g. call("light.turn_on", listOf("light.lamp"), {"brightness_pct": 30}). */
    fun call(service: String, entityIds: List<String> = emptyList(), data: JsonObject? = null): Boolean {
        val socket = ws ?: return false
        if (!connected || !service.contains('.')) return false
        val (domain, svc) = service.split('.', limit = 2)
        val payload = buildJsonObject {
            put("id", ids.getAndIncrement())
            put("type", "call_service")
            put("domain", domain)
            put("service", svc)
            if (data != null) put("service_data", data)
            if (entityIds.isNotEmpty()) put("target", buildJsonObject {
                put("entity_id", JsonArray(entityIds.map { JsonPrimitive(it) }))
            })
        }
        return socket.send(payload.toString())
    }

    fun call(c: HaCall, fallbackEntities: List<String> = emptyList()): Boolean {
        if (!c.valid) return false
        val targets = c.entityId.split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { fallbackEntities }
        return call(c.service, targets, c.data)
    }

    /** Toggle any entity in its own domain (light, switch, lock, script, scene...). */
    fun toggle(entityId: String) {
        val e = this[entityId]
        when (entityId.substringBefore('.')) {
            "lock" -> call(if (e?.state == "locked") "lock.unlock" else "lock.lock", listOf(entityId))
            "scene" -> call("scene.turn_on", listOf(entityId))
            "script" -> call("script.turn_on", listOf(entityId))
            "button" -> call("button.press", listOf(entityId))
            "media_player" -> call("media_player.media_play_pause", listOf(entityId))
            else -> call("homeassistant.toggle", listOf(entityId))
        }
    }

    /** Current JPEG of a camera via HA's camera proxy. */
    suspend fun cameraSnapshot(entityId: String): ByteArray? = withContext(Dispatchers.IO) {
        val cfg = currentCfg ?: return@withContext null
        if (!cfg.configured || entityId.isBlank()) return@withContext null
        try {
            val req = Request.Builder()
                .url(cfg.url.trimEnd('/') + "/api/camera_proxy/$entityId")
                .header("Authorization", "Bearer ${cfg.token}")
                .build()
            http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
        } catch (_: Exception) { null }
    }

    /** Fetches an HA-relative image (entity_picture) with auth. */
    suspend fun fetch(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val cfg = currentCfg ?: return@withContext null
        if (!cfg.configured) return@withContext null
        try {
            val url = if (path.startsWith("http")) path else cfg.url.trimEnd('/') + path
            http.newCall(Request.Builder().url(url).header("Authorization", "Bearer ${cfg.token}").build())
                .execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
        } catch (_: Exception) { null }
    }

    companion object { private const val TAG = "HearthHA" }
}
