package com.amosley.hearth.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amosley.hearth.remote.net.FoundTv
import com.amosley.hearth.remote.net.TvConnection
import com.amosley.hearth.shared.theme.Hearth

@Composable
private fun IndigoBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink).background(Brush.radialGradient(listOf(Color(0x666254C8), Color.Transparent), radius = 1100f))) { content() }
}

/** Find the TV on the network (mDNS) or type its address. */
@Composable
fun ConnectScreen(tv: TvConnection, onBasicRemote: () -> Unit) {
    val found by tv.found.collectAsStateWithLifecycle()
    var manual by remember { mutableStateOf("") }
    DisposableEffect(Unit) { tv.discover(); onDispose { tv.stopDiscovery() } }
    IndigoBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(40.dp))
            TvGlyph("Hearth")
            P("Find your TV", 30.sp, FontWeight.Medium)
            P("Make sure the TV is on and Hearth is its home screen. Same Wi-Fi as this phone.", 16.sp, color = Paper.copy(alpha = .65f), maxLines = 3, align = TextAlign.Center)
            if (found.isEmpty()) P("Searching…", 15.sp, color = Paper.copy(alpha = .5f))
            for (t in found) {
                Row(Modifier.fillMaxWidth().height(64.dp).glass(20.dp).press({ tv.choose(t) }).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(Hearth.Live); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { P(t.name, 17.sp, FontWeight.Medium); P(t.host, 13.sp, color = Paper.copy(alpha = .55f)) }
                    P("Connect", 15.sp, FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(8.dp))
            P("Or enter the TV's IP address (shown on the TV in Settings › Pair a remote)", 13.sp, color = Paper.copy(alpha = .55f), maxLines = 2, align = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).height(56.dp).glass(18.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    if (manual.isEmpty()) P("192.168.1.50", 16.sp, color = Paper.copy(alpha = .35f))
                    BasicTextField(manual, { manual = it.trim() }, singleLine = true, textStyle = TextStyle(color = Paper, fontSize = 16.sp),
                        cursorBrush = SolidColor(Hearth.Amber), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                }
                GlassButton("Go", Modifier.width(80.dp).height(56.dp), 18.dp, paper = true) { if (manual.isNotBlank()) tv.choose(FoundTv("TV", manual)) }
            }
            Spacer(Modifier.height(8.dp))
            GlassButton("Use as a basic remote (no Hearth)", Modifier.fillMaxWidth().height(56.dp), 18.dp) { onBasicRemote() }
        }
    }
}

@Composable
fun TvGlyph(text: String) {
    Box(Modifier.size(180.dp, 96.dp).border(3.dp, Paper.copy(alpha = .8f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        P(text, 30.sp, family = Hearth.Newsreader, letterSpacing = 0.08.em)
    }
}

/** 5g · Pairing with the launcher: the TV shows a 6-digit code. */
@Composable
fun PairingScreen(tv: TvConnection, status: String) {
    val err by tv.pairError.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(status) { if (status == "code_requested") try { fr.requestFocus() } catch (_: Exception) {} }
    LaunchedEffect(err) { if (err.isNotBlank()) code = "" }
    IndigoBackground {
        Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Spacer(Modifier.height(60.dp))
            TvGlyph(if (status == "code_requested") "• • • • • •" else "TV")
            P("Connect to your TV", 30.sp, FontWeight.Medium)
            P(if (status == "code_requested") "Type the code showing on ${tv.tvName}." else "${tv.tvName} will show a 6-digit code. You only do this once.",
                16.sp, color = Paper.copy(alpha = .65f), maxLines = 3, align = TextAlign.Center)
            if (status == "code_requested") {
                Box {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 0 until 6) {
                            Box(Modifier.size(46.dp, 60.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .07f))
                                .border(if (i == code.length) 2.dp else 1.dp, if (i == code.length) Paper else Color.White.copy(alpha = .1f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center) { P(code.getOrNull(i)?.toString() ?: "", 26.sp, FontWeight.Medium) }
                        }
                    }
                    BasicTextField(code, { v -> code = v.filter { it.isDigit() }.take(6); if (code.length == 6) tv.confirmPairing(code) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), textStyle = TextStyle(color = Color.Transparent),
                        cursorBrush = SolidColor(Color.Transparent), modifier = Modifier.matchParentSize().focusRequester(fr))
                }
                if (err.isNotBlank()) P(err, 15.sp, color = Hearth.Destructive)
                P("Pairs once · reconnects automatically", 13.sp, color = Paper.copy(alpha = .5f))
                GlassButton("Connect", Modifier.fillMaxWidth().height(60.dp), 20.dp, paper = true, size = 17.sp) { if (code.length == 6) tv.confirmPairing(code) }
                GlassButton("Show a new code", Modifier.fillMaxWidth().height(52.dp), 20.dp) { tv.requestPairing() }
            } else {
                GlassButton(if (status == "needs_pairing") "Show code on TV" else "Connecting…", Modifier.fillMaxWidth().height(60.dp), 20.dp, paper = true, size = 17.sp) {
                    tv.requestPairing()
                }
            }
            Spacer(Modifier.weight(1f))
            GlassButton("Choose a different TV", Modifier.fillMaxWidth().height(52.dp), 20.dp) { tv.forget() }
        }
    }
}

/** Pairs the Android TV Remote protocol (the TV shows a 6-character code). */
@Composable
fun BasicRemotePairing(tv: TvConnection, onDone: () -> Unit) {
    val atv = tv.atv
    val status by atv.status.collectAsStateWithLifecycle()
    val err by atv.error.collectAsStateWithLifecycle()
    var host by remember { mutableStateOf(tv.host ?: atv.pairedHost ?: "") }
    var code by remember { mutableStateOf("") }
    LaunchedEffect(status) { if (status == "connected") onDone() }
    IndigoBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(40.dp))
            TvGlyph(if (status == "code") "A1B2C3" else "TV")
            P("Basic remote", 30.sp, FontWeight.Medium)
            P("Uses the same connection as the Google TV app, so the phone still works when Hearth isn't running. Keys, volume and power only.",
                15.sp, color = Paper.copy(alpha = .65f), maxLines = 4, align = TextAlign.Center)
            Field(host, { host = it.trim() }, "TV IP address", KeyboardType.Uri)
            if (status == "code") {
                Field(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) }, "6-character code on the TV", KeyboardType.Ascii)
                GlassButton("Pair", Modifier.fillMaxWidth().height(60.dp), 20.dp, paper = true) { atv.finishPairing(code) }
            } else {
                GlassButton(if (status == "pairing") "Asking the TV…" else "Show code on TV", Modifier.fillMaxWidth().height(60.dp), 20.dp, paper = true) {
                    if (host.isNotBlank()) atv.startPairing(host)
                }
            }
            if (err.isNotBlank() && status == "error") P(err, 14.sp, color = Hearth.Destructive, maxLines = 3, align = TextAlign.Center)
            GlassButton("Back", Modifier.fillMaxWidth().height(52.dp), 20.dp) { onDone() }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, type: KeyboardType) {
    Box(Modifier.fillMaxWidth().height(56.dp).glass(18.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) P(hint, 16.sp, color = Paper.copy(alpha = .4f))
        BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = Paper, fontSize = 16.sp),
            cursorBrush = SolidColor(Hearth.Amber), keyboardOptions = KeyboardOptions(keyboardType = type, capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth())
    }
}
