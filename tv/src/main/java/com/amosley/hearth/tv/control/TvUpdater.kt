package com.amosley.hearth.tv.control

import com.amosley.hearth.shared.update.GitHubUpdater
import com.amosley.hearth.tv.BuildConfig
import com.amosley.hearth.tv.HearthApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime

/**
 * TV launcher self-update from GitHub Releases. Installs silently through ADB when Hearth
 * has ADB access; otherwise Android shows its one-tap "Update" confirmation on the TV.
 */
class TvUpdater(private val app: HearthApp) {
    val github = GitHubUpdater(app, BuildConfig.GITHUB_REPO, "hearth-tv", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong())
    val status get() = github.status

    fun start() {
        app.scope.launch {
            delay(20_000)
            while (true) {
                val rel = github.check()
                if (rel != null && rel.code > BuildConfig.VERSION_CODE) {
                    val night = LocalTime.now().hour in 3..4
                    if (app.config.value.autoUpdate && app.adb.connected && night) install()
                    else app.toast("Hearth ${rel.version} is available · Settings › Updates")
                }
                delay(6 * 3600_000L)
            }
        }
    }

    fun check() = app.scope.launch { github.check() }

    fun install() = app.scope.launch {
        val rel = github.check() ?: return@launch
        if (rel.code <= BuildConfig.VERSION_CODE) return@launch
        if (app.adb.connected || app.adb.connect()) {
            // Download through the shared updater's cache, then pm install -r via ADB (no prompt).
            val apk = download(rel.apkUrl, rel.version)
            if (apk != null && app.adb.install(apk)) return@launch
        }
        github.downloadAndInstall()
    }

    private fun download(url: String, version: String): File? = try {
        val f = File(app.cacheDir, "updates/hearth-tv-$version.apk").apply { parentFile?.mkdirs() }
        val client = okhttp3.OkHttpClient()
        client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            f.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
        }
        f
    } catch (_: Exception) { null }
}
