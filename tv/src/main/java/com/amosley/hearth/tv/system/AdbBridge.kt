package com.amosley.hearth.tv.system

import android.content.Context
import android.util.Log
import dadb.AdbKeyPair
import dadb.AdbShellStream
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Talks to the TV's own adbd over the network (127.0.0.1:5555) once "USB debugging" /
 * "Network debugging" is on in Developer options and the TV owner taps "Always allow".
 *
 * Used for: key presses Android doesn't let normal apps send (d-pad on Android < 13, menu,
 * power), granting Hearth's own permissions in one go, and silent self-updates.
 */
class AdbBridge(private val context: Context) {
    /** off | connecting | connected | failed */
    private val _status = MutableStateFlow("off")
    val status: StateFlow<String> = _status.asStateFlow()

    private var dadb: Dadb? = null
    private var shell: AdbShellStream? = null
    private val lock = Any()

    private fun keyPair(): AdbKeyPair {
        val dir = File(context.filesDir, "adb").apply { mkdirs() }
        val priv = File(dir, "adbkey"); val pub = File(dir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    /**
     * Connects (or reconnects). The first time, the TV shows "Allow USB debugging?" for Hearth;
     * [waitForApproval] keeps the socket open long enough for someone to accept it.
     */
    suspend fun connect(waitForApproval: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (dadb != null && _status.value == "connected") return@withContext true
            _status.value = "connecting"
            try {
                val d = Dadb.create("127.0.0.1", 5555, keyPair(), connectTimeout = 3000,
                    socketTimeout = if (waitForApproval) 60_000 else 8_000)
                val r = d.shell("echo hearth")
                if (!r.output.contains("hearth")) error("unexpected shell reply")
                dadb = d
                _status.value = "connected"
                true
            } catch (e: Exception) {
                Log.i(TAG, "ADB unavailable: ${e.message}")
                dadb = null
                _status.value = "failed"
                false
            }
        }
    }

    val connected get() = _status.value == "connected"

    suspend fun shell(cmd: String): String? = withContext(Dispatchers.IO) {
        val d = dadb ?: return@withContext null
        try { d.shell(cmd).allOutput } catch (e: Exception) {
            reset(); null
        }
    }

    /** Fire-and-forget key press through a persistent shell (lower latency than one shell per key). */
    suspend fun keyevent(code: Int, longPress: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val d = dadb ?: return@withContext false
        try {
            synchronized(lock) {
                val s = shell ?: d.openShell("").also { shell = it }
                s.write("input keyevent ${if (longPress) "--longpress " else ""}$code\n")
            }
            true
        } catch (e: Exception) {
            reset(); false
        }
    }

    suspend fun text(t: String): Boolean {
        val escaped = t.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'").replace(" ", "%s")
            .replace("&", "\\&").replace("<", "\\<").replace(">", "\\>").replace(";", "\;").replace("|", "\\|")
            .replace("(", "\\(").replace(")", "\\)").replace("$", "\\$").replace("`", "\\`")
        return shell("input text \"$escaped\"") != null
    }

    suspend fun install(apk: File): Boolean = withContext(Dispatchers.IO) {
        val d = dadb ?: return@withContext false
        try { d.install(apk, "-r"); true } catch (e: Exception) { Log.w(TAG, "adb install failed", e); false }
    }

    /** Grants everything Hearth can use; each is optional and harmless if already granted. */
    suspend fun grantHearthPermissions(): List<String> {
        val pkg = context.packageName
        val a11y = "$pkg/${InputService::class.java.name}"
        val nls = "$pkg/${MediaListener::class.java.name}"
        val cmds = listOf(
            "Accessibility (remote keys, home button)" to
                "cur=\$(settings get secure enabled_accessibility_services); case \"\$cur\" in *$a11y*) ;; null|'') settings put secure enabled_accessibility_services $a11y ;; *) settings put secure enabled_accessibility_services \"\$cur:$a11y\" ;; esac; settings put secure accessibility_enabled 1",
            "Now-playing access" to "cmd notification allow_listener $nls",
            "Pop-ups over apps" to "appops set $pkg SYSTEM_ALERT_WINDOW allow",
            "Install updates" to "appops set $pkg REQUEST_INSTALL_PACKAGES allow",
            "Continue Watching (TV provider)" to "pm grant $pkg com.android.providers.tv.permission.READ_EPG_DATA",
            "Usage access" to "appops set $pkg GET_USAGE_STATS allow",
            "Keep running" to "dumpsys deviceidle whitelist +$pkg",
        )
        return cmds.map { (label, c) ->
            val out = shell(c)
            if (out == null) "✗ $label" else if (out.contains("Exception") || out.contains("rror")) "• $label (not supported)" else "✓ $label"
        }
    }

    private fun reset() {
        synchronized(lock) {
            try { shell?.close() } catch (_: Exception) {}
            try { dadb?.close() } catch (_: Exception) {}
            shell = null; dadb = null; _status.value = "failed"
        }
    }

    companion object { private const val TAG = "HearthAdb" }
}
