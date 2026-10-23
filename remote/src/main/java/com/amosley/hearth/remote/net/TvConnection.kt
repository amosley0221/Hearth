package com.amosley.hearth.remote.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.view.KeyEvent
import com.amosley.hearth.remote.atv.AtvRemote
import com.amosley.hearth.shared.protocol.Keys
import com.amosley.hearth.shared.protocol.Protocol
import com.amosley.hearth.shared.protocol.Protocol.str
import com.amosley.hearth.shared.protocol.TvState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

data class FoundTv(val name: String, val host: String)

/**
 * Connection to the Hearth launcher on the TV. Falls back to the Android TV Remote protocol
 * ("Basic remote") for keys when the launcher can't be reached.
 */
class TvConnection(private val context: Context, private val scope: CoroutineScope, val atv: AtvRemote) {
    private val prefs = context.getSharedPreferences("tv", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).connectTimeout(4, TimeUnit.SECONDS).build()

    /** none | connecting | needs_pairing | code_requested | connected | offline */
    private val _status = MutableStateFlow(if (host == null) "none" else "connecting")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _state = MutableStateFlow<TvState?>(null)
    val state: StateFlow<TvState?> = _state.asStateFlow()
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts
    private val _pairError = MutableStateFlow("")
    val pairError: StateFlow<String> = _pairError.asStateFlow()
    private val _found = MutableStateFlow<List<FoundTv>>(emptyList())
    val found: StateFlow<List<FoundTv>> = _found.asStateFlow()

    val host: String? get() = prefs.getString("host", null)
    val tvName: String get() = prefs.getString("name", null) ?: "TV"
    private val token: String? get() = prefs.getString("token", null)

    private var ws: WebSocket? = null
    private var loop: Job? = null

    fun start() {
        // Don't interrupt a live connection or a pairing in progress.
        if (_status.value !in setOf("connected", "needs_pairing", "code_requested") || loop == null) host?.let { connect(it) }
        atv.pairedHost?.let { atv.connect(it) }
    }

    fun choose(tv: FoundTv) {
        if (tv.host != host) prefs.edit().putString("host", tv.host).putString("name", tv.name).remove("token").apply()
        connect(tv.host)
    }

    fun connect(h: String) {
        loop?.cancel()
        ws?.cancel()
        _status.value = "connecting"
        loop = scope.launch {
            var backoff = 1000L
            while (true) {
                if (_status.value == "connecting" || _status.value == "offline") open(h)
                delay(backoff)
                backoff = if (_status.value == "connected") 1000L else (backoff * 2).coerceAtMost(15_000L)
            }
        }
    }

    private fun open(h: String) {
        ws?.cancel()
        val req = Request.Builder().url("ws://$h:${Protocol.PORT}/ws").build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val t = token
                if (t != null) webSocket.send(Protocol.msg("hello", "token" to t, "name" to deviceName()))
                else _status.value = "needs_pairing"
            }
            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { if (webSocket === ws) offline() }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (webSocket === ws) offline() }
        })
    }

    private fun offline() {
        if (_status.value != "needs_pairing" && _status.value != "code_requested") _status.value = "offline"
    }

    private fun handle(text: String) {
        val m = try { Protocol.json.parseToJsonElement(text).jsonObject } catch (_: Exception) { return }
        when (m.str("t")) {
            "welcome" -> { _status.value = "connected"; m.str("tvName")?.let { prefs.edit().putString("name", it).apply() } }
            "auth_required" -> { prefs.edit().remove("token").apply(); _status.value = "needs_pairing" }
            "pair_pending" -> { _status.value = "code_requested"; _pairError.value = "" }
            "paired" -> {
                prefs.edit().putString("token", m.str("token")).putString("name", m.str("tvName") ?: tvName).apply()
                _status.value = "connected"
            }
            "pair_failed" -> _pairError.value = m.str("reason") ?: "Try again"
            "state" -> {
                val s = m["state"] ?: return
                _state.value = try { Protocol.json.decodeFromJsonElement(TvState.serializer(), s) } catch (_: Exception) { _state.value }
                if (_status.value != "connected") _status.value = "connected"
            }
            "toast" -> m.str("text")?.let { _toasts.tryEmit(it) }
        }
    }

    fun requestPairing() { ws?.send(Protocol.msg("pair_request", "name" to deviceName())) }
    fun confirmPairing(code: String) { ws?.send(Protocol.msg("pair_confirm", "code" to code, "name" to deviceName())) }

    val launcherConnected get() = _status.value == "connected"

    /** True when keys can reach the TV some way. */
    val anyControl get() = launcherConnected || atv.connected

    fun send(type: String, vararg fields: Pair<String, Any?>) { if (launcherConnected) ws?.send(Protocol.msg(type, *fields)) }

    fun key(k: String) {
        // Power works best through the TV's own remote protocol (it can also wake the TV).
        if (k == Keys.POWER && atv.connected) { atv.key(KeyEvent.KEYCODE_POWER); return }
        if (launcherConnected && (_state.value?.inputControl != false || k in HEARTH_ONLY_OK)) { send("key", "key" to k); return }
        val code = ATV_CODES[k]
        if (atv.connected && code != null) atv.key(code) else send("key", "key" to k)
    }

    fun text(t: String, mode: String) = send("text", "text" to t, "mode" to mode)
    fun voice(t: String) = send("voice", "text" to t)
    fun launch(pkg: String) = send("launch", "pkg" to pkg)
    fun mode(id: String) = send("mode", "id" to id)
    fun console(id: String) = send("console", "id" to id)
    fun profile(id: String) = send("profile", "id" to id)
    fun section(id: String) = send("section", "id" to id)
    fun haCall(service: String, entity: String? = null, data: JsonObject? = null) {
        if (!launcherConnected) return
        ws?.send(buildJsonObject {
            put("t", "ha_call"); put("service", service)
            if (entity != null) put("entity_id", entity)
            if (data != null) put("data", data)
        }.toString())
    }
    fun haToggle(entity: String) = haCall("toggle", entity)
    fun setVolume(level: Int) = send("volume_set", "level" to level)
    fun updateTv(install: Boolean) = send("update", "action" to if (install) "install" else "check")
    fun cameraUrl(entity: String): String? {
        val h = host ?: return null; val t = token ?: return null
        return "http://$h:${Protocol.PORT}/api/camera?token=$t&entity=$entity&t=${System.currentTimeMillis()}"
    }
    fun assetUrl(path: String): String? = host?.let { "http://$it:${Protocol.PORT}$path" }
    fun settingsUrl(): String? = host?.let { "http://$it:${Protocol.PORT}/#settings" }

    fun forget() {
        loop?.cancel(); ws?.cancel()
        prefs.edit().clear().apply()
        _state.value = null; _status.value = "none"
    }

    // ---------- discovery ----------
    private var discovery: NsdManager.DiscoveryListener? = null

    fun discover() {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        stopDiscovery()
        _found.value = emptyList()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(i: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(i: NsdServiceInfo) {
                        val addr = if (Build.VERSION.SDK_INT >= 34) i.hostAddresses.firstOrNull { it is java.net.Inet4Address } ?: i.hostAddresses.firstOrNull()
                            else @Suppress("DEPRECATION") i.host
                        val h = addr?.hostAddress ?: return
                        _found.value = (_found.value.filterNot { it.host == h } + FoundTv(i.serviceName, h))
                    }
                })
            }
        }
        discovery = l
        try { nsd.discoverServices(Protocol.NSD_TYPE, NsdManager.PROTOCOL_DNS_SD, l) } catch (_: Exception) {}
    }

    fun stopDiscovery() {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        discovery?.let { try { nsd.stopServiceDiscovery(it) } catch (_: Exception) {} }
        discovery = null
    }

    private fun deviceName(): String = Build.MODEL ?: "Phone"

    companion object {
        private val HEARTH_ONLY_OK = setOf(Keys.HOME, Keys.VOL_UP, Keys.VOL_DOWN, Keys.MUTE, Keys.PLAY_PAUSE,
            Keys.SKIP_BACK, Keys.SKIP_FORWARD, Keys.INPUTS)
        val ATV_CODES = mapOf(
            Keys.UP to KeyEvent.KEYCODE_DPAD_UP, Keys.DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
            Keys.LEFT to KeyEvent.KEYCODE_DPAD_LEFT, Keys.RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
            Keys.OK to KeyEvent.KEYCODE_DPAD_CENTER, Keys.BACK to KeyEvent.KEYCODE_BACK, Keys.HOME to KeyEvent.KEYCODE_HOME,
            Keys.MENU to KeyEvent.KEYCODE_MENU, Keys.PLAY_PAUSE to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            Keys.REWIND to KeyEvent.KEYCODE_MEDIA_REWIND, Keys.FAST_FORWARD to KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            Keys.SKIP_BACK to KeyEvent.KEYCODE_MEDIA_REWIND, Keys.SKIP_FORWARD to KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            Keys.VOL_UP to KeyEvent.KEYCODE_VOLUME_UP, Keys.VOL_DOWN to KeyEvent.KEYCODE_VOLUME_DOWN,
            Keys.MUTE to KeyEvent.KEYCODE_VOLUME_MUTE, Keys.POWER to KeyEvent.KEYCODE_POWER,
            Keys.SEARCH to KeyEvent.KEYCODE_SEARCH, Keys.INPUTS to KeyEvent.KEYCODE_TV_INPUT,
        )
    }
}

@Suppress("unused") private fun unused() = JsonPrimitive(0)
