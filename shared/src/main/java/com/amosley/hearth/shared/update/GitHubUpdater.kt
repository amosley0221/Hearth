package com.amosley.hearth.shared.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import com.amosley.hearth.shared.protocol.Protocol
import com.amosley.hearth.shared.protocol.UpdateStatus
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub Releases for a newer APK and installs it over the running app.
 *
 * Releases carry one APK per app, named "<assetPrefix>-<version>.apk"
 * (hearth-tv-1.2.0.apk, hearth-remote-1.2.0.apk). Because every release is signed with
 * the same key and has a higher versionCode, Android installs it as an in-place update:
 * no uninstall, settings and pairing are kept.
 */
class GitHubUpdater(
    private val context: Context,
    private val repo: String,
    private val assetPrefix: String,
    private val currentVersionName: String,
    private val currentVersionCode: Long,
) {
    data class Release(val tag: String, val version: String, val code: Long, val notes: String, val apkUrl: String, val size: Long)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _status = MutableStateFlow(UpdateStatus(current = currentVersionName))
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()
    private var latest: Release? = null

    private fun set(s: UpdateStatus) { _status.value = s.copy(current = currentVersionName) }

    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        set(_status.value.copy(status = "checking", error = ""))
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$repo/releases?per_page=10")
                .header("Accept", "application/vnd.github+json")
                .build()
            val body = http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) error("GitHub returned ${r.code}")
                r.body!!.string()
            }
            val releases = Protocol.json.parseToJsonElement(body) as JsonArray
            val best = releases.mapNotNull { parse(it.jsonObject) }.maxByOrNull { it.code }
            latest = best
            if (best != null && best.code > currentVersionCode) {
                set(UpdateStatus(latest = best.version, status = "available", notes = best.notes))
            } else {
                set(UpdateStatus(latest = best?.version ?: currentVersionName, status = "up_to_date", notes = best?.notes ?: ""))
            }
            best
        } catch (e: Exception) {
            set(_status.value.copy(status = "error", error = e.message ?: e.javaClass.simpleName))
            null
        }
    }

    private fun parse(o: JsonObject): Release? {
        if (o["draft"]?.jsonPrimitive?.contentOrNull == "true") return null
        if (o["prerelease"]?.jsonPrimitive?.contentOrNull == "true") return null
        val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val asset = o["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull {
            val n = it["name"]?.jsonPrimitive?.contentOrNull ?: ""
            n.startsWith("$assetPrefix-") && n.endsWith(".apk")
        } ?: return null
        val version = tag.removePrefix("v")
        return Release(
            tag = tag,
            version = version,
            code = versionCode(version),
            notes = o["body"]?.jsonPrimitive?.contentOrNull ?: "",
            apkUrl = asset["browser_download_url"]!!.jsonPrimitive.content,
            size = asset["size"]?.jsonPrimitive?.longOrNull ?: 0,
        )
    }

    /** True when Android will let this app open the system installer. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" setting for this app. */
    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(i) } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    /** Downloads the newest release and hands it to PackageInstaller as an in-place update. */
    suspend fun downloadAndInstall(): Boolean = withContext(Dispatchers.IO) {
        val rel = latest ?: check() ?: return@withContext false
        if (rel.code <= currentVersionCode) return@withContext false
        if (!canInstall()) {
            set(_status.value.copy(status = "error", error = "Allow \"Install unknown apps\" for Hearth, then try again"))
            withContext(Dispatchers.Main) { openInstallPermissionSettings() }
            return@withContext false
        }
        try {
            set(_status.value.copy(status = "downloading", progress = 0, error = ""))
            val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val apk = File(dir, "$assetPrefix-${rel.version}.apk")
            http.newCall(Request.Builder().url(rel.apkUrl).build()).execute().use { r ->
                if (!r.isSuccessful) error("Download failed (${r.code})")
                val total = r.body!!.contentLength().takeIf { it > 0 } ?: rel.size
                r.body!!.byteStream().use { input ->
                    apk.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            val pct = if (total > 0) (read * 100 / total).toInt() else 0
                            if (pct != lastPct) { lastPct = pct; set(_status.value.copy(progress = pct)) }
                        }
                    }
                }
            }
            set(_status.value.copy(status = "installing", progress = 100))
            install(apk)
            true
        } catch (e: Exception) {
            set(_status.value.copy(status = "error", error = e.message ?: e.javaClass.simpleName))
            false
        }
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Android 12+: once Hearth installed itself the first time, later self-updates
            // can complete without the confirmation dialog.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val action = "${context.packageName}.UPDATE_RESULT"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    when (val code = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            set(_status.value.copy(status = "waiting_for_user"))
                            @Suppress("DEPRECATION")
                            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            if (confirm != null) c.startActivity(confirm)
                        }
                        PackageInstaller.STATUS_SUCCESS -> set(_status.value.copy(status = "up_to_date"))
                        else -> {
                            val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed ($code)"
                            set(_status.value.copy(status = "error", error = msg))
                            try { c.unregisterReceiver(this) } catch (_: Exception) {}
                        }
                    }
                }
            }
            val filter = IntentFilter(action)
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, sessionId, Intent(action).setPackage(context.packageName), flags)
            session.commit(pi.intentSender)
        }
    }

    companion object {
        /** Must match gradle/versioning.gradle.kts. */
        fun versionCode(version: String): Long {
            val p = version.removePrefix("v").split("-")[0].split(".").map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
            return p[0] * 1_000_000L + p[1] * 1_000L + p[2]
        }
    }
}
