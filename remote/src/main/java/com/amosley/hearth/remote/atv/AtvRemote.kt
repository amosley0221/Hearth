package com.amosley.hearth.remote.atv

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Android TV Remote protocol v2 — the protocol the Google Home / Google TV app uses.
 * Works even when Hearth isn't running on the TV, so the phone is never a dead remote.
 * Pairing on port 6467 (TV shows a 6-character code), commands on port 6466.
 */
class AtvRemote(private val context: Context, private val scope: CoroutineScope) {
    /** idle | pairing | code | connecting | connected | error */
    private val _status = MutableStateFlow("idle")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error.asStateFlow()

    private val prefs = context.getSharedPreferences("atv", Context.MODE_PRIVATE)
    val pairedHost: String? get() = prefs.getString("host", null)

    private var socket: SSLSocket? = null
    private var pairSocket: SSLSocket? = null
    private var job: Job? = null
    private val ksFile = File(context.filesDir, "atv-client.p12")
    private val ksPass = "hearth".toCharArray()

    // ---------- client certificate ----------
    private fun keyStore(): KeyStore {
        val ks = KeyStore.getInstance("PKCS12")
        if (ksFile.exists()) { ksFile.inputStream().use { ks.load(it, ksPass) }; return ks }
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
        val name = X500Name("CN=atvremote/hearth")
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now), Date(now - 86_400_000L),
            Date(now + 3650L * 86_400_000L), name, kp.public)
        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(kp.private)
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        ks.load(null, null)
        ks.setKeyEntry("client", kp.private, ksPass, arrayOf(cert))
        ksFile.outputStream().use { ks.store(it, ksPass) }
        return ks
    }

    private fun clientCert(ks: KeyStore) = ks.getCertificate("client") as X509Certificate

    private fun open(host: String, port: Int): SSLSocket {
        val ks = keyStore()
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, ksPass) }
        // The TV's certificate is self-signed; it's pinned implicitly by the pairing secret.
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val ctx = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, trustAll, SecureRandom()) }
        val raw = java.net.Socket().apply { connect(InetSocketAddress(host, port), 5000); soTimeout = 0 }
        val s = ctx.socketFactory.createSocket(raw, host, port, true) as SSLSocket
        s.startHandshake()
        return s
    }

    // ---------- pairing ----------
    /** Step 1: ask the TV to show a code. */
    fun startPairing(host: String) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            _status.value = "pairing"; _error.value = ""
            try {
                val s = open(host, 6467)
                pairSocket = s
                val out = s.outputStream; val inp = s.inputStream
                fun sendPair(build: ProtoWriter.() -> Unit) =
                    out.writeDelimited(ProtoWriter().int(1, 2).int(2, 200).apply(build).toByteArray())
                fun expect() { val m = ProtoMsg.parse(inp.readDelimited()); if ((m.int(2) ?: 200L) != 200L) error("TV refused pairing (${m.int(2)})") }
                sendPair { msg(10, ProtoWriter().string(1, "com.amosley.hearth.remote").string(2, "Hearth Remote")) }
                expect()
                val enc = ProtoWriter().int(1, 3).int(2, 6) // hexadecimal, 6 symbols
                sendPair { msg(20, ProtoWriter().msg(1, enc).int(3, 1)) }
                expect()
                sendPair { msg(30, ProtoWriter().msg(1, ProtoWriter().int(1, 3).int(2, 6)).int(2, 1)) }
                expect()
                prefs.edit().putString("pending_host", host).apply()
                _status.value = "code"
            } catch (e: Exception) {
                fail("Couldn't start pairing: ${e.message}")
            }
        }
    }

    /** Step 2: the 6-character code shown on the TV. */
    fun finishPairing(code: String) {
        val s = pairSocket ?: return fail("Start pairing again")
        val c = code.trim().uppercase()
        scope.launch(Dispatchers.IO) {
            try {
                if (c.length != 6) error("The code has 6 characters")
                val ks = keyStore()
                val client = clientCert(ks).publicKey as RSAPublicKey
                val server = s.session.peerCertificates[0].publicKey as RSAPublicKey
                val md = MessageDigest.getInstance("SHA-256")
                md.update(hex(client.modulus.toString(16)))
                md.update(hex("0" + client.publicExponent.toString(16)))
                md.update(hex(server.modulus.toString(16)))
                md.update(hex("0" + server.publicExponent.toString(16)))
                md.update(hex(c.substring(2)))
                val secret = md.digest()
                if ((secret[0].toInt() and 0xFF) != c.substring(0, 2).toInt(16)) error("That code doesn't match")
                s.outputStream.writeDelimited(ProtoWriter().int(1, 2).int(2, 200).msg(40, ProtoWriter().bytes(1, secret)).toByteArray())
                val ack = ProtoMsg.parse(s.inputStream.readDelimited())
                if ((ack.int(2) ?: 0L) != 200L) error("TV rejected the code")
                val host = prefs.getString("pending_host", null) ?: error("No TV")
                prefs.edit().putString("host", host).apply()
                try { s.close() } catch (_: Exception) {}
                pairSocket = null
                connect(host)
            } catch (e: Exception) {
                fail(e.message ?: "Pairing failed")
            }
        }
    }

    private fun hex(s: String): ByteArray {
        val h = if (s.length % 2 == 1) "0$s" else s
        return ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private fun fail(msg: String) { _error.value = msg; _status.value = "error"; Log.w(TAG, msg) }

    // ---------- remote session ----------
    fun connect(host: String? = pairedHost) {
        if (host == null) return
        if (_status.value == "connected" || _status.value == "connecting") return
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            var backoff = 2000L
            while (isActive) {
                _status.value = "connecting"
                try {
                    val s = open(host, 6466)
                    socket = s
                    val inp = s.inputStream; val out = s.outputStream
                    _status.value = "connected"; backoff = 2000L
                    while (isActive) {
                        val m = ProtoMsg.parse(inp.readDelimited())
                        when {
                            m.has(1) -> out.writeDelimited(ProtoWriter().msg(1, ProtoWriter().int(1, 622).msg(2,
                                ProtoWriter().string(1, "Hearth Remote").string(2, "Hearth").int(3, 1).string(4, "1")
                                    .string(5, "atvremote").string(6, "1.0.0"))).toByteArray())
                            m.has(2) -> out.writeDelimited(ProtoWriter().msg(2, ProtoWriter().int(1, 622)).toByteArray())
                            m.has(8) -> out.writeDelimited(ProtoWriter().msg(9, ProtoWriter().int(1, m.msg(8)?.int(1) ?: 0)).toByteArray())
                            m.has(3) -> Log.w(TAG, "remote error")
                        }
                    }
                } catch (e: Exception) {
                    socket = null
                    if (_status.value == "connected" || _status.value == "connecting") _status.value = "error"
                    _error.value = e.message ?: ""
                }
                delay(backoff); backoff = (backoff * 2).coerceAtMost(30_000)
            }
        }
    }

    val connected get() = _status.value == "connected"

    /** Sends an Android KeyEvent keycode as a short press. */
    fun key(keyCode: Int, longPress: Boolean = false) {
        val s = socket ?: return
        scope.launch(Dispatchers.IO) {
            try {
                synchronized(this@AtvRemote) {
                    if (longPress) {
                        s.outputStream.writeDelimited(ProtoWriter().msg(10, ProtoWriter().int(1, keyCode.toLong()).int(2, 1)).toByteArray())
                        Thread.sleep(600)
                        s.outputStream.writeDelimited(ProtoWriter().msg(10, ProtoWriter().int(1, keyCode.toLong()).int(2, 2)).toByteArray())
                    } else {
                        s.outputStream.writeDelimited(ProtoWriter().msg(10, ProtoWriter().int(1, keyCode.toLong()).int(2, 3)).toByteArray())
                    }
                }
            } catch (e: Exception) { socket = null; _status.value = "error" }
        }
    }

    /** Opens an app by deep link (e.g. https://www.netflix.com/title.*). */
    fun launchLink(link: String) {
        val s = socket ?: return
        scope.launch(Dispatchers.IO) {
            try { synchronized(this@AtvRemote) { s.outputStream.writeDelimited(ProtoWriter().msg(90, ProtoWriter().string(1, link)).toByteArray()) } }
            catch (_: Exception) {}
        }
    }

    fun forget() {
        job?.cancel(); try { socket?.close() } catch (_: Exception) {}
        socket = null; prefs.edit().remove("host").apply(); _status.value = "idle"
    }

    @Suppress("unused") private fun unusedKey(k: PrivateKey) = k

    companion object { private const val TAG = "HearthAtv" }
}
