package com.amosley.hearth.remote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amosley.hearth.remote.RemoteApp
import com.amosley.hearth.shared.theme.Hearth
import kotlinx.coroutines.delay

@Composable
fun RemoteRoot() {
    val app = RemoteApp.get()
    val tv = app.tv
    val status by tv.status.collectAsStateWithLifecycle()
    val st by tv.state.collectAsStateWithLifecycle()
    val atvStatus by tv.atv.status.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var basicPairing by rememberSaveable { mutableStateOf(false) }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    var tab by rememberSaveable { mutableStateOf(if (wide) "now" else "remote") }
    var kbSerial by remember { mutableIntStateOf(0) }
    // The pad is always visible when unfolded, so "Remote" maps to the Now pane there.
    if (wide && tab == "remote") tab = "now"

    BackHandler(showSettings || basicPairing) { showSettings = false; basicPairing = false }

    // Voice: Android's speech recogniser, then the TV works out what to do with the words.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val voiceLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { res ->
        val said = res.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!said.isNullOrBlank()) tv.voice(said)
    }
    val startVoice: () -> Unit = {
        val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Try \"play Breaking Bad\" or \"lights off\"")
        try { voiceLauncher.launch(i) } catch (_: Exception) {
            android.widget.Toast.makeText(ctx, "Voice typing isn't available on this phone", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val voiceReq by app.voiceRequests.collectAsStateWithLifecycle()
    LaunchedEffect(voiceReq) { if (voiceReq > 0) startVoice() }

    Box(Modifier.fillMaxSize().background(Ink)) {
        when {
            basicPairing -> BasicRemotePairing(tv) { basicPairing = false }
            showSettings -> Box(Modifier.windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
                SettingsScreen(st, onBasicRemote = { basicPairing = true }) { showSettings = false }
            }
            status == "none" && atvStatus != "connected" -> ConnectScreen(tv) { basicPairing = true }
            status == "needs_pairing" || status == "code_requested" -> PairingScreen(tv, status)
            else -> {
                PhoneBackdrop(st?.backdropHue ?: 35f)
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                    TopBar(status, atvStatus, st?.inputControl ?: true) { showSettings = true }
                    Box(Modifier.weight(1f)) {
                        if (wide) {
                            Row(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f).fillMaxHeight()) { Pane(tab, st, status, wide, kbSerial, startVoice) { tab = "apps"; kbSerial++ } }
                                Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = .06f)))
                                Box(Modifier.weight(1.15f).fillMaxHeight()) { UnfoldedPad(tv, st, status, startVoice) { tab = "apps"; kbSerial++ } }
                            }
                        } else Pane(tab, st, status, wide, kbSerial, startVoice) { tab = "apps"; kbSerial++ }
                    }
                    TabBar(tab, wide) { tab = it }
                }
            }
        }
        ToastHost()
    }
}

@Composable
private fun Pane(tab: String, st: com.amosley.hearth.shared.protocol.TvState?, status: String, wide: Boolean, kbSerial: Int, onVoice: () -> Unit, onKeyboard: () -> Unit) {
    val tv = RemoteApp.get().tv
    when (tab) {
        "now" -> NowScreen(tv, st, wide)
        "apps" -> AppsScreen(tv, st, kbSerial > 0)
        "home" -> HomeScreen(tv, st)
        else -> if (wide) NowScreen(tv, st, wide) else RemoteScreen(tv, st, status, onVoice, onKeyboard)
    }
}

@Composable
private fun TopBar(status: String, atv: String, inputControl: Boolean, onSettings: () -> Unit) {
    val banner = when {
        status == "connected" && !inputControl && atv != "connected" -> "Keys only reach Hearth · finish setup on the TV (Settings › Remote control)"
        status != "connected" && atv == "connected" -> "Basic remote · Hearth isn't reachable"
        status == "offline" -> "Can't reach the TV · retrying"
        status == "connecting" -> "Connecting…"
        else -> null
    }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (banner != null) Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Hearth.Amber.copy(alpha = .15f))
            .border(1.dp, Hearth.Amber.copy(alpha = .4f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Dot(Hearth.Amber); Spacer(Modifier.width(8.dp)); P(banner, 12.sp, color = Hearth.Amber, maxLines = 2)
        } else Spacer(Modifier.weight(1f))
        Box(Modifier.size(44.dp).press(onSettings), contentAlignment = Alignment.Center) { P("⋯", 22.sp, color = Paper.copy(alpha = .8f)) }
    }
}

@Composable
private fun TabBar(tab: String, wide: Boolean, onTab: (String) -> Unit) {
    val tabs = if (wide) listOf("now" to "Now", "apps" to "Apps", "home" to "Home") else listOf("remote" to "Remote", "now" to "Now", "apps" to "Apps", "home" to "Home")
    Row(Modifier.fillMaxWidth().background(Ink.copy(alpha = .6f)).border(1.dp, Color.White.copy(alpha = .06f))
        .windowInsetsPadding(WindowInsets.navigationBars).height(76.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        for ((id, label) in tabs) {
            val sel = tab == id
            Column(Modifier.weight(1f).press({ onTab(id) }), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).then(if (sel) Modifier.background(Paper) else Modifier.border(2.dp, Paper.copy(alpha = .5f), RoundedCornerShape(8.dp))))
                Spacer(Modifier.height(5.dp))
                P(label, 11.sp, FontWeight.Medium, color = Paper.copy(alpha = if (sel) 1f else .5f))
            }
        }
    }
}

@Composable
private fun ToastHost() {
    val tv = RemoteApp.get().tv
    var text by remember { mutableStateOf<String?>(null) }
    var serial by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { tv.toasts.collect { text = it; serial++ } }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(serial) { if (text != null) { visible = true; delay(1800); visible = false } }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
            Box(Modifier.padding(top = 10.dp).shadow(20.dp, RoundedCornerShape(99.dp)).clip(RoundedCornerShape(99.dp)).background(Paper.copy(alpha = .95f))
                .padding(horizontal = 20.dp, vertical = 10.dp)) { P(text ?: "", 14.sp, FontWeight.Medium, color = Hearth.Ink2) }
        }
    }
}
