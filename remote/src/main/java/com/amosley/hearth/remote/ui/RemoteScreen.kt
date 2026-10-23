package com.amosley.hearth.remote.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.amosley.hearth.remote.net.TvConnection
import com.amosley.hearth.shared.protocol.Keys
import com.amosley.hearth.shared.protocol.TvState
import com.amosley.hearth.shared.theme.Hearth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/** "● LIVING ROOM TV" + title + meta. */
@Composable
fun NowHeader(tv: TvConnection, st: TvState?, status: String, center: Boolean = true) {
    val np = st?.nowPlaying
    val title = np?.title?.takeIf { it.isNotBlank() } ?: st?.foregroundLabel ?: tv.tvName
    val meta = if (np != null && np.title.isNotBlank()) listOf(np.appLabel, np.subtitle, remainingText(np)).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        else if (st != null) "On screen" else ""
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(when (status) { "connected" -> Hearth.Live; else -> Hearth.Amber })
            Spacer(Modifier.width(8.dp))
            P((st?.tvName ?: tv.tvName).uppercase(), 13.sp, color = Paper.copy(alpha = .6f), letterSpacing = 0.16.em)
        }
        Spacer(Modifier.height(6.dp))
        P(title, 22.sp, FontWeight.Medium)
        if (meta.isNotBlank()) P(meta, 15.sp, color = Paper.copy(alpha = .6f))
    }
}

fun remainingText(np: com.amosley.hearth.shared.protocol.NowPlaying): String {
    if (np.durationMs <= 0) return ""
    val pos = if (np.playing) np.positionMs + (System.currentTimeMillis() - np.sampledAt) else np.positionMs
    val m = ((np.durationMs - pos) / 60_000).coerceAtLeast(0)
    return if (m >= 60) "${m / 60}h ${m % 60}m left" else "${m}m left"
}

@Composable
fun ModesRow(tv: TvConnection, st: TvState?, height: Dp = 64.dp) {
    val modes = st?.modes.orEmpty().take(4)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (modes.isEmpty()) repeat(4) { Box(Modifier.weight(1f).height(height).glass(18.dp)) }
        for (m in modes) {
            Box(Modifier.weight(1f).height(height).press({ tv.mode(m.id) })
                .then(if (m.active) Modifier.clip(RoundedCornerShape(18.dp)).background(Paper) else Modifier.glass(18.dp)),
                contentAlignment = Alignment.Center) {
                P(m.name, 12.sp, FontWeight.Medium, color = if (m.active) Hearth.Ink2 else Paper, maxLines = 2, align = TextAlign.Center)
            }
        }
    }
}

@Composable
fun DPad(tv: TvConnection, size: Dp, okSize: Dp) {
    val scope = rememberCoroutineScope()
    var flash by remember { mutableStateOf(false) }
    val okColor by animateColorAsState(if (flash) Hearth.Amber else Paper, tween(150), label = "ok")
    Box(Modifier.size(size).background(Color.White.copy(alpha = .06f), CircleShape).border(1.dp, Color.White.copy(alpha = .08f), CircleShape)) {
        val arm = size / 3
        Box(Modifier.align(Alignment.TopCenter).size(arm).press({ tv.key(Keys.UP) }, repeat = true), contentAlignment = Alignment.Center) { Arrow("up") }
        Box(Modifier.align(Alignment.BottomCenter).size(arm).press({ tv.key(Keys.DOWN) }, repeat = true), contentAlignment = Alignment.Center) { Arrow("down") }
        Box(Modifier.align(Alignment.CenterStart).size(arm).press({ tv.key(Keys.LEFT) }, repeat = true), contentAlignment = Alignment.Center) { Arrow("left") }
        Box(Modifier.align(Alignment.CenterEnd).size(arm).press({ tv.key(Keys.RIGHT) }, repeat = true), contentAlignment = Alignment.Center) { Arrow("right") }
        Box(Modifier.align(Alignment.Center).size(okSize).shadow(14.dp, CircleShape).background(okColor, CircleShape)
            .press({ tv.key(Keys.OK); flash = true; scope.launch { delay(150); flash = false } }), contentAlignment = Alignment.Center) {
            P("OK", 17.sp, FontWeight.SemiBold, color = Hearth.Ink2)
        }
    }
}

@Composable
fun VolumeRail(tv: TvConnection, st: TvState?, modifier: Modifier, width: Dp = 64.dp) {
    val frac = if ((st?.maxVolume ?: 0) > 0) st!!.volume.toFloat() / st.maxVolume else 0f
    Column(modifier.width(width).glass(width / 2).padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).press({ tv.key(Keys.VOL_UP) }, repeat = true), contentAlignment = Alignment.Center) { P("+", 28.sp, FontWeight.Light) }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).width(6.dp).background(Color.White.copy(alpha = .12f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(frac.coerceIn(0f, 1f)).background(Paper, RoundedCornerShape(6.dp)))
        }
        Spacer(Modifier.height(10.dp))
        P(st?.volume?.toString() ?: "–", 14.sp, FontWeight.SemiBold)
        Box(Modifier.size(48.dp).press({ tv.key(Keys.VOL_DOWN) }, repeat = true), contentAlignment = Alignment.Center) { P("−", 28.sp, FontWeight.Light) }
        Box(Modifier.size(36.dp).background(if (st?.muted == true) Hearth.Alert else Color.White.copy(alpha = .1f), CircleShape)
            .press({ tv.key(Keys.MUTE) }), contentAlignment = Alignment.Center) { P("◀", 13.sp) }
    }
}

@Composable
fun TransportRow(tv: TvConnection, st: TvState?, height: Dp = 50.dp, power: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassButton("−10s", Modifier.weight(1f).fillMaxHeight(), 16.dp) { tv.key(Keys.SKIP_BACK) }
        GlassButton(if (st?.nowPlaying?.playing == true) "Pause" else "Play", Modifier.weight(1.4f).fillMaxHeight(), 16.dp, paper = true) { tv.key(Keys.PLAY_PAUSE) }
        GlassButton("+30s", Modifier.weight(1f).fillMaxHeight(), 16.dp) { tv.key(Keys.SKIP_FORWARD) }
        if (power) GlassButton("Power", Modifier.weight(1f).fillMaxHeight(), 16.dp) { tv.key(Keys.POWER) }
    }
}

/** 5a · Remote, cover screen. */
@Composable
fun RemoteScreen(tv: TvConnection, st: TvState?, status: String, onVoice: () -> Unit, onKeyboard: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(6.dp))
        NowHeader(tv, st, status)
        Spacer(Modifier.height(4.dp))
        ModesRow(tv, st)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f).glass(30.dp), contentAlignment = Alignment.Center) { DPad(tv, 250.dp, 104.dp) }
                Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Back", Modifier.weight(1f).fillMaxHeight()) { tv.key(Keys.BACK) }
                    GlassButton("⌂", Modifier.weight(1f).fillMaxHeight(), size = 24.sp) { tv.key(Keys.HOME) }
                    MicButton(Modifier.weight(1f).fillMaxHeight(), onVoice)
                }
            }
            VolumeRail(tv, st, Modifier.fillMaxHeight())
        }
        TransportRow(tv, st)
        Spacer(Modifier.height(4.dp))
    }
}

/** Right half of the unfolded layout (5e): everything from the cover remote. */
@Composable
fun UnfoldedPad(tv: TvConnection, st: TvState?, status: String, onVoice: () -> Unit, onKeyboard: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(if (status == "connected") Hearth.Live else Hearth.Amber); Spacer(Modifier.width(8.dp))
            P((st?.tvName ?: tv.tvName).uppercase(), 13.sp, color = Paper.copy(alpha = .6f), letterSpacing = 0.16.em, modifier = Modifier.weight(1f))
            P("Vol ${st?.volume ?: "–"}", 13.sp, color = Paper.copy(alpha = .6f))
        }
        ModesRow(tv, st, 58.dp)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().glass(30.dp), contentAlignment = Alignment.Center) { DPad(tv, 280.dp, 116.dp) }
            VolumeRail(tv, st, Modifier.fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Back", Modifier.weight(1f).fillMaxHeight()) { tv.key(Keys.BACK) }
            GlassButton("Home", Modifier.weight(1f).fillMaxHeight()) { tv.key(Keys.HOME) }
            GlassButton("Voice", Modifier.weight(1f).fillMaxHeight()) { onVoice() }
            GlassButton("Keyboard", Modifier.weight(1f).fillMaxHeight()) { onKeyboard() }
        }
        TransportRow(tv, st, 54.dp, power = true)
    }
}

/** Microphone glyph button (design 5a: Back / Home / Mic). */
@Composable
fun MicButton(modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.press(onClick).glass(20.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(width = 18.dp, height = 28.dp)) {
            val w = size.width; val h = size.height; val sw = 2.2.dp.toPx()
            drawRoundRect(Paper, topLeft = androidx.compose.ui.geometry.Offset(w * .22f, 0f),
                size = androidx.compose.ui.geometry.Size(w * .56f, h * .62f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .28f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
            drawArc(Paper, 0f, 180f, false, topLeft = androidx.compose.ui.geometry.Offset(0f, h * .3f),
                size = androidx.compose.ui.geometry.Size(w, h * .5f), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
            drawLine(Paper, androidx.compose.ui.geometry.Offset(w / 2, h * .8f), androidx.compose.ui.geometry.Offset(w / 2, h), sw)
        }
    }
}
