package com.amosley.hearth.tv.server

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.amosley.hearth.shared.protocol.Protocol
import com.amosley.hearth.shared.protocol.Protocol.str
import com.amosley.hearth.shared.protocol.TvState
import com.amosley.hearth.shared.protocol.UpdateStatus
import com.amosley.hearth.tv.HearthApp
import com.amosley.hearth.tv.UiEvent
import com.amosley.hearth.tv.data.ConfigStore
import com.amosley.hearth.tv.data.PairedRemote
import com.amosley.hearth.tv.sports.SportsRepository
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList

/**
 * LAN server on port 8765:
 *  - WebSocket /ws: the remote protocol (see shared Protocol.kt)
 *  - HTTP /: the iPad web remote (assets/web), plus small JSON/image APIs it and the phone use.
 * Advertised over mDNS as _hearth._tcp so the phone app finds the TV automatically.
 */
class RemoteServer(private val app: HearthApp) : NanoWSD(Protocol.PORT) {

    private val clients = CopyOnWriteArrayList<Client>()
    private var pairCode: String? = null
    private var pairCodeAt = 0L
    private var pairAttempts = 0
    private val random = SecureRandom()

    fun startServer() {
        try {
            start(0, true)
        } catch (e: IOException) {
            Log.e(TAG, "Server failed to start", e)
            return
        }
        advertise()
        app.scope.launch {
            app.state.tv.drop(1).collect { s -> broadcast(stateMessage(s)) }
        }
        app.scope.launch {
            app.events.collect { e -> if (e is UiEvent.Toast) broadcast(Protocol.msg("toast", "text" to e.text)) }
        }
        app.scope.launch {
            while (true) {
                delay(20_000)
                for (c in clients) try { c.ping(byteArrayOf(1)) } catch (_: Exception) {}
            }
        }
    }

    private fun advertise() {
        try {
            val nsd = app.getSystemService(Context.NSD_SERVICE) as NsdManager
            val info = NsdServiceInfo().apply {
                serviceName = app.config.value.tvName
                serviceType = Protocol.NSD_TYPE
                port = Protocol.PORT
            }
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(i: NsdServiceInfo) {}
                override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) { Log.w(TAG, "NSD failed $code") }
                override fun onServiceUnregistered(i: NsdServiceInfo) {}
                override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
            })
        } catch (e: Exception) { Log.w(TAG, "NSD unavailable", e) }
    }

    private fun stateMessage(s: TvState): String = buildJsonObject {
        put("t", "state")
        put("state", Protocol.json.encodeToJsonElement(TvState.serializer(), s))
    }.toString()

    private fun broadcast(text: String) {
        for (c in clients) if (c.authed && c.isOpen) try { c.send(text) } catch (_: Exception) {}
    }

    private fun validToken(t: String?): Boolean = !t.isNullOrBlank() && app.config.value.remotes.any { it.token == t }

    // ---------------------------------------------------------------- WebSocket

    override fun openWebSocket(handshake: IHTTPSession): WebSocket = Client(handshake)

    inner class Client(handshake: IHTTPSession) : WebSocket(handshake) {
        @Volatile var authed = false
        var name = "Remote"

        override fun onOpen() { clients += this }
        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) { clients -= this }
        override fun onPong(pong: WebSocketFrame?) {}
        override fun onException(exception: IOException?) { clients -= this }

        override fun onMessage(frame: WebSocketFrame) {
            val msg = try { Protocol.json.parseToJsonElement(frame.textPayload).jsonObject } catch (_: Exception) { return }
            app.scope.launch { try { handle(this@Client, msg) } catch (e: Exception) { Log.w(TAG, "message failed", e) } }
        }

        fun reply(text: String) { try { send(text) } catch (_: Exception) {} }
    }

    private fun handle(c: Client, m: JsonObject) {
        val t = m.str("t") ?: return
        when (t) {
            "hello" -> {
                c.name = m.str("name") ?: c.name
                if (validToken(m.str("token"))) {
                    c.authed = true
                    c.reply(Protocol.msg("welcome", "tvName" to app.config.value.tvName, "version" to app.state.tv.value.version))
                    c.reply(stateMessage(app.state.tv.value))
                } else c.reply(Protocol.msg("auth_required"))
                return
            }
            "pair_request" -> {
                c.name = m.str("name") ?: c.name
                val code = (0 until 6).joinToString("") { random.nextInt(10).toString() }
                pairCode = code; pairCodeAt = System.currentTimeMillis(); pairAttempts = 0
                app.emit(UiEvent.PairCode(code, c.name))
                if (com.amosley.hearth.tv.BuildConfig.DEBUG) Log.i("HearthPair", "code=$code") // used by the CI smoke test
                c.reply(Protocol.msg("pair_pending", "tvName" to app.config.value.tvName))
                return
            }
            "pair_confirm" -> {
                val code = pairCode
                val fresh = System.currentTimeMillis() - pairCodeAt < 5 * 60_000
                if (code != null && fresh && pairAttempts < 5 && m.str("code") == code) {
                    val token = ConfigStore.newToken()
                    val name = m.str("name") ?: c.name
                    app.configStore.update { it.copy(remotes = (it.remotes + PairedRemote(token, name, System.currentTimeMillis())).takeLast(20)) }
                    pairCode = null
                    c.authed = true
                    app.emit(UiEvent.PairDone)
                    app.toast("$name paired")
                    c.reply(Protocol.msg("paired", "token" to token, "tvName" to app.config.value.tvName))
                    c.reply(stateMessage(app.state.tv.value))
                } else {
                    pairAttempts++
                    c.reply(Protocol.msg("pair_failed", "reason" to if (!fresh || code == null) "Code expired. Ask for a new one." else "That code didn't match."))
                }
                return
            }
        }
        if (!c.authed) { c.reply(Protocol.msg("auth_required")); return }
        when (t) {
            "key" -> m.str("key")?.let { app.input.key(it) }
            "text" -> app.input.text(m.str("text") ?: "", m.str("mode") ?: "append")
            "launch" -> m.str("pkg")?.let {
                if (!app.profiles.isKids || it in app.config.value.kids.approvedApps) app.apps.launch(it)
            }
            "mode" -> m.str("id")?.let { app.actions.runMode(it) }
            "ha_call" -> {
                val service = m.str("service") ?: listOfNotNull(m.str("domain"), m.str("svc")).joinToString(".")
                val ent = m.str("entity_id")
                if (service == "toggle" && ent != null) { app.ha.toggle(ent); return }
                // No entity: target the configured device for that domain. Never send an untargeted call
                // (light.turn_on with no target would switch on every light in the house).
                val h = app.config.value.home
                val targets = if (!ent.isNullOrBlank()) listOf(ent) else when (service.substringBefore('.')) {
                    "light" -> h.lights
                    "climate" -> listOf(h.climate)
                    "lock" -> listOf(h.lock)
                    "media_player" -> listOf(h.speaker)
                    "camera" -> listOf(h.camera)
                    else -> emptyList()
                }.filter { it.isNotBlank() }
                if (targets.isNotEmpty() || service.startsWith("scene.") || service.startsWith("script.")) {
                    app.ha.call(service, targets, m["data"] as? JsonObject)
                }
            }
            "console" -> m.str("id")?.let { app.actions.wakeConsole(it, null, waitForIt = false) }
            "input" -> m.str("id")?.let { app.inputs.switchTo(it) }
            "profile" -> m.str("id")?.let { app.profiles.switchTo(it) }
            "extra_time" -> app.profiles.grantExtra(m["minutes"]?.jsonPrimitive?.intOrNull ?: 30)
            "volume_set" -> m["level"]?.jsonPrimitive?.intOrNull?.let { app.input.setVolume(it) }
            "section" -> m.str("id")?.let { app.input.goHome(); app.emit(UiEvent.OpenSection(it)) }
            "doorbell_dismiss" -> app.emit(UiEvent.DismissDoorbell)
            "voice" -> m.str("text")?.let { text ->
                app.scope.launch {
                    val reply = app.voice.handle(text)
                    c.reply(Protocol.msg("voice_result", "text" to text, "reply" to reply))
                }
            }
            "update" -> when (m.str("action")) {
                "install" -> app.updater.install()
                else -> app.updater.check()
            }
        }
    }

    // ---------------------------------------------------------------- HTTP

    override fun serveHttp(session: IHTTPSession): Response {
        val uri = session.uri.trimEnd('/').ifEmpty { "/" }
        val token = session.parameters["token"]?.firstOrNull() ?: session.headers["x-hearth-token"]
        val r = try {
            when {
                uri == "/" -> asset("web/index.html")
                uri.startsWith("/api/icon/") -> png(app.apps.iconPng(uri.removePrefix("/api/icon/")))
                uri.startsWith("/api/banner/") -> png(app.apps.bannerPng(uri.removePrefix("/api/banner/")))
                uri == "/api/nowplaying/art" -> bytes(app.media.artJpeg(), "image/jpeg")
                uri.startsWith("/api/cw/") -> app.media.artFile(uri.removePrefix("/api/cw/").substringAfterLast('/'))
                    .takeIf { it.exists() }?.let { bytes(it.readBytes(), "image/jpeg") } ?: notFound()
                uri == "/api/info" -> json(buildJsonObject {
                    put("name", app.config.value.tvName); put("version", app.state.tv.value.version); put("app", "hearth")
                }.toString())
                uri.startsWith("/api/") && !validToken(token) -> newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", "{\"error\":\"pair first\"}")
                uri == "/api/camera" -> runBlocking {
                    bytes(app.ha.cameraSnapshot(session.parameters["entity"]?.firstOrNull() ?: app.config.value.home.camera), "image/jpeg")
                }
                uri == "/api/config" && session.method == Method.POST -> {
                    val files = HashMap<String, String>()
                    session.parseBody(files)
                    val body = files["postData"] ?: ""
                    app.configStore.replaceFromJson(body)
                    app.toast("Settings saved")
                    json("{\"ok\":true}")
                }
                uri == "/api/config" -> json(app.configStore.toJson())
                uri == "/api/inputs" -> json(Protocol.json.encodeToString(
                    ListSerializer(com.amosley.hearth.tv.system.TvInput.serializer()), app.inputs.list()))
                uri == "/api/apps" -> json(buildJsonArray {
                    app.apps.apps.value.forEach { a -> add(buildJsonObject { put("pkg", a.pkg); put("label", a.label) }) }
                }.toString())
                uri == "/api/ha/entities" -> json(buildJsonArray {
                    app.ha.entities.value.values.sortedBy { it.id }.forEach { e ->
                        add(buildJsonObject { put("id", e.id); put("name", e.name); put("state", e.state) })
                    }
                }.toString())
                uri == "/api/ha/status" -> json("{\"status\":\"${app.ha.status.value}\"}")
                uri == "/api/teams" -> runBlocking {
                    val league = session.parameters["league"]?.firstOrNull() ?: "nfl"
                    json(Protocol.json.encodeToString(ListSerializer(com.amosley.hearth.tv.data.TeamRef.serializer()), app.sports.teams(league)))
                }
                uri == "/api/leagues" -> json(buildJsonArray {
                    SportsRepository.LEAGUES.values.forEach { l -> add(buildJsonObject { put("key", l.key); put("name", l.name) }) }
                }.toString())
                // For Siri Shortcuts / automations: GET /api/voice?token=…&q=play%20breaking%20bad
                uri == "/api/voice" -> {
                    if (session.method == Method.POST) session.parseBody(HashMap())
                    val q = session.parameters["q"]?.firstOrNull() ?: session.parameters["text"]?.firstOrNull() ?: ""
                    val reply = runBlocking { app.voice.handle(q) }
                    json(buildJsonObject { put("query", q); put("reply", reply) }.toString())
                }
                uri == "/api/update" -> json(Protocol.json.encodeToString(UpdateStatus.serializer(), app.updater.status.value))
                else -> asset("web" + uri)
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP $uri failed", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "error")
        }
        r.addHeader("Cache-Control", if (uri.startsWith("/api/")) "no-store" else "no-cache")
        return r
    }

    private fun asset(path: String): Response {
        if (path.contains("..")) return notFound()
        return try {
            val bytes = app.assets.open(path).use { it.readBytes() }
            newFixedLengthResponse(Response.Status.OK, mime(path), ByteArrayInputStream(bytes), bytes.size.toLong())
        } catch (_: IOException) {
            // Single-page app: unknown paths fall back to index.html
            if (!path.substringAfterLast('/').contains('.')) asset("web/index.html") else notFound()
        }
    }

    private fun mime(p: String) = when (p.substringAfterLast('.').lowercase()) {
        "html" -> "text/html; charset=utf-8"; "js" -> "application/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"; "json" -> "application/json"; "webmanifest" -> "application/manifest+json"
        "png" -> "image/png"; "svg" -> "image/svg+xml"; "ttf" -> "font/ttf"; "ico" -> "image/x-icon"
        else -> "application/octet-stream"
    }

    private fun png(b: ByteArray?) = bytes(b, "image/png")
    private fun bytes(b: ByteArray?, mime: String): Response =
        if (b == null) notFound() else newFixedLengthResponse(Response.Status.OK, mime, ByteArrayInputStream(b), b.size.toLong())
    private fun json(s: String) = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", s)
    private fun notFound() = newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")

    companion object { private const val TAG = "HearthServer" }
}

private val NanoHTTPD.IHTTPSession.methodName get() = method.name
