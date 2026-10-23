package com.amosley.hearth.remote.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amosley.hearth.remote.BuildConfig
import com.amosley.hearth.remote.RemoteApp
import com.amosley.hearth.shared.protocol.TvState
import com.amosley.hearth.shared.protocol.UpdateStatus
import com.amosley.hearth.shared.theme.Hearth
import kotlinx.coroutines.launch

/** Connection, profiles, and updates for both this app and the TV launcher. */
@Composable
fun SettingsScreen(st: TvState?, onBasicRemote: () -> Unit, onClose: () -> Unit) {
    val app = RemoteApp.get()
    val tv = app.tv
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val phone by app.updater.status.collectAsStateWithLifecycle()
    val atvStatus by tv.atv.status.collectAsStateWithLifecycle()
    val status by tv.status.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                P("Settings", 28.sp, FontWeight.Medium, modifier = Modifier.weight(1f))
                GlassButton("Done", Modifier.width(90.dp).height(44.dp), 14.dp) { onClose() }
            }
            Section("Updates")
            UpdateCard("This phone · Hearth Remote ${BuildConfig.VERSION_NAME}", phone,
                onCheck = { scope.launch { app.updater.check() } }, onInstall = { scope.launch { app.updater.downloadAndInstall() } })
            val tvUpd = st?.update ?: UpdateStatus()
            UpdateCard("TV · Hearth ${st?.version ?: "?"}", tvUpd, enabled = status == "connected",
                onCheck = { tv.updateTv(false) }, onInstall = { tv.updateTv(true) },
                note = "The TV downloads the new version from GitHub and installs it over the current one. If the TV asks, press OK on \"Update\".")

            Section("TV")
            Row2("Connected to", "${tv.tvName} · ${tv.host ?: "-"}")
            Action("Open TV settings in browser", "Home Assistant, games, modes, teams") {
                tv.settingsUrl()?.let { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
            }
            if (st != null) {
                Action(if (st.profile == "kids") "Switch TV to adult profile" else "Switch TV to Kids profile", "Paired phones don't need the PIN") {
                    tv.profile(if (st.profile == "kids") "adult" else "kids")
                }
                Action("Give Kids 30 more minutes", null) { tv.send("extra_time", "minutes" to 30) }
            }
            Action("Basic remote · ${when (atvStatus) { "connected" -> "connected"; "idle" -> "not set up"; else -> atvStatus }}",
                "Works even when Hearth isn't running (Android TV Remote protocol)") { onBasicRemote() }
            Action("Choose a different TV", null) { tv.forget(); onClose() }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun Section(t: String) { Spacer(Modifier.height(6.dp)); P(t.uppercase(), 12.sp, color = Paper.copy(alpha = .5f)) }

@Composable
private fun Row2(label: String, value: String) {
    Row(Modifier.fillMaxWidth().glass(16.dp).padding(16.dp)) { P(label, 15.sp, modifier = Modifier.weight(1f)); P(value, 14.sp, color = Paper.copy(alpha = .6f)) }
}

@Composable
private fun Action(title: String, sub: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(16.dp).press(onClick).padding(16.dp)) {
        P(title, 16.sp, FontWeight.Medium)
        if (sub != null) P(sub, 13.sp, color = Paper.copy(alpha = .55f), maxLines = 2)
    }
}

@Composable
private fun UpdateCard(title: String, s: UpdateStatus, enabled: Boolean = true, note: String? = null, onCheck: () -> Unit, onInstall: () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(18.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        P(title, 16.sp, FontWeight.Medium)
        P(when (s.status) {
            "checking" -> "Checking GitHub…"
            "available" -> "Version ${s.latest} is available"
            "up_to_date" -> "Up to date"
            "downloading" -> "Downloading… ${s.progress}%"
            "installing" -> "Installing…"
            "waiting_for_user" -> "Confirm the install"
            "error" -> "Error: ${s.error}"
            else -> if (enabled) "Not checked yet" else "Connect to the TV first"
        }, 14.sp, color = if (s.status == "error") Hearth.Destructive else Paper.copy(alpha = .65f), maxLines = 3)
        if (s.status == "available" && s.notes.isNotBlank()) P(s.notes.replace("**", "").replace("`", "").take(600), 13.sp, color = Paper.copy(alpha = .75f), maxLines = 10)
        if (note != null) P(note, 12.sp, color = Paper.copy(alpha = .45f), maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Check", Modifier.width(110.dp).height(44.dp), 14.dp) { if (enabled) onCheck() }
            if (s.status == "available") GlassButton("Update to ${s.latest}", Modifier.weight(1f).height(44.dp), 14.dp, paper = true) { if (enabled) onInstall() }
        }
    }
}
