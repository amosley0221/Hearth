package com.amosley.hearth.tv.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amosley.hearth.shared.protocol.HomeSnapshot
import com.amosley.hearth.shared.theme.Hearth
import com.amosley.hearth.tv.HearthApp
import com.amosley.hearth.tv.ui.Glyph
import com.amosley.hearth.tv.ui.Hatch
import com.amosley.hearth.tv.ui.Paper
import com.amosley.hearth.tv.ui.Pill
import com.amosley.hearth.tv.ui.Pivot
import com.amosley.hearth.tv.ui.SectionHeader
import com.amosley.hearth.tv.ui.StatusDot
import com.amosley.hearth.tv.ui.T
import com.amosley.hearth.tv.ui.Toggle
import com.amosley.hearth.tv.ui.TvUi
import com.amosley.hearth.tv.ui.glass
import com.amosley.hearth.tv.ui.tvFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Composable
fun SmartSection(ui: TvUi) {
    val app = HearthApp.get()
    val cfg by app.config.collectAsStateWithLifecycle()
    val ents by app.ha.entities.collectAsStateWithLifecycle()
    val status by app.ha.status.collectAsStateWithLifecycle()
    val h = remember(ents, cfg) { app.state.homeSnapshot() }
    LaunchedEffect(Unit) { ui.hue = 35f; ui.art = null }

    Pivot(120f) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 130.dp, end = 96.dp, bottom = 80.dp)) {
            SectionHeader("Home", listOf(h.room, h.presence).filter { it.isNotBlank() }.joinToString(" · "))
            Spacer(Modifier.height(36.dp))
            if (!h.configured || status != "connected") {
                HaStatusCard(ui, h.configured, status)
                if (!h.configured) return@Column
                Spacer(Modifier.height(24.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LightsCard(h, Modifier.size(470.dp, 300.dp), enabled = h.connected)
                ThermostatCard(h, Modifier.size(470.dp, 300.dp), enabled = h.connected)
                CameraCard(ui, h, Modifier.size(598.dp, 300.dp))
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LockCard(h, Modifier.size(716.dp, 240.dp), enabled = h.connected)
                SpeakerCard(h, Modifier.size(846.dp, 240.dp), enabled = h.connected)
            }
            if (h.scenes.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                T("Scenes", 26.sp, FontWeight.Medium)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (s in h.scenes) Pill(s.name, onClick = { app.ha.toggle(s.id); ui.toast("${s.name}") }, size = 24.sp, hPad = 28.dp, vPad = 16.dp)
                }
            }
        }
    }
}

@Composable
private fun HaStatusCard(ui: TvUi, configured: Boolean, status: String) {
    val ip = remember { com.amosley.hearth.tv.ui.screens.Network.ipAddress() }
    Row(Modifier.fillMaxWidth().glass(22.dp).padding(28.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(if (configured) Hearth.Amber else Paper.copy(alpha = .4f), 14.dp)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            T(if (!configured) "Connect Home Assistant" else when (status) {
                "auth_failed" -> "Home Assistant rejected the token"
                "connecting" -> "Connecting to Home Assistant…"
                else -> "Home Assistant offline"
            }, 28.sp, FontWeight.Medium)
            T(if (!configured) "On your phone, iPad or computer open http://$ip:8765/#settings and add your Home Assistant URL and long-lived token."
              else "Controls are disabled until it's back. Check the URL and token in Settings.", 20.sp, color = Paper.copy(alpha = .6f), maxLines = 2)
        }
        Pill("Settings", onClick = { ui.section = "settings"; ui.settingsPage = "home" })
    }
}

private val cardShape = RoundedCornerShape(22.dp)

@Composable
private fun CardTitle(glyph: String, title: String, sub: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Glyph(glyph, 22.dp, Paper); Spacer(Modifier.width(10.dp)); T(title, 26.sp, FontWeight.Medium)
    }
    if (sub != null) { Spacer(Modifier.height(6.dp)); T(sub, 20.sp, color = Paper.copy(alpha = .6f)) }
}

@Composable
private fun LightsCard(h: HomeSnapshot, modifier: Modifier, enabled: Boolean) {
    val app = HearthApp.get()
    val on = h.lightsOn > 0
    Box(modifier.tvFocus(cardShape, onClick = {
        if (!enabled) return@tvFocus
        val lights = app.config.value.home.lights
        app.ha.call(if (on) "light.turn_off" else "light.turn_on", lights)
    }).glass(22.dp).padding(horizontal = 28.dp, vertical = 24.dp)) {
        Column {
            CardTitle("bulb", "Lights", if (h.lightsTotal == 0) "Add lights in Settings" else "${h.lightsOn} on • ${h.lightsTotal - h.lightsOn} off")
        }
        Orb(on, 150.dp, Modifier.align(Alignment.CenterEnd).padding(end = 10.dp))
        Box(Modifier.align(Alignment.BottomStart)) { Toggle(on) }
    }
}

@Composable
fun Orb(on: Boolean, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size)
        .then(if (on) Modifier.shadow(40.dp, CircleShape, ambientColor = Hearth.Amber, spotColor = Hearth.Amber) else Modifier)
        .background(
            if (on) Brush.radialGradient(listOf(Color(0xFFFFF1C9), Hearth.Amber, Color(0xFF8A6420)), center = Offset(size.value * .35f, size.value * .3f), radius = size.value * .8f)
            else Brush.radialGradient(listOf(Color(0xFF3A3C44), Color(0xFF1A1B20))),
            CircleShape))
}

@Composable
private fun ThermostatCard(h: HomeSnapshot, modifier: Modifier, enabled: Boolean) {
    val app = HearthApp.get()
    var adjusting by remember { mutableStateOf(false) }
    val entity = app.config.value.home.climate
    fun nudge(d: Int) {
        val t = (h.climateTarget ?: h.climateCurrent ?: 70.0) + d
        app.ha.call("climate.set_temperature", listOf(entity), buildJsonObject { put("temperature", JsonPrimitive(t)) })
    }
    Box(modifier
        .onPreviewKeyEvent { e ->
            if (!adjusting || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionLeft, Key.DirectionDown -> { nudge(-1); true }
                Key.DirectionRight, Key.DirectionUp -> { nudge(1); true }
                Key.Back -> { adjusting = false; true }
                else -> false
            }
        }
        .tvFocus(cardShape, onClick = { if (enabled && entity.isNotBlank()) adjusting = !adjusting })
        .glass(22.dp).then(if (adjusting) Modifier.border(3.dp, Hearth.Amber, cardShape) else Modifier)
        .padding(horizontal = 28.dp, vertical = 24.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) { Glyph("thermo", 22.dp, Paper); Spacer(Modifier.width(10.dp)); T("Thermostat", 26.sp, FontWeight.Medium) }
            Spacer(Modifier.height(34.dp))
            T(h.climateCurrent?.let { "${it.toInt()}°F" } ?: "--", 60.sp, FontWeight.Medium)
            T(listOfNotNull(h.climateName.ifBlank { null }, h.climateMode.replaceFirstChar { it.uppercase() }.ifBlank { null },
                h.climateTarget?.let { "set ${it.toInt()}°" }).joinToString(" · "), 20.sp, color = Paper.copy(alpha = .6f))
        }
        Dial(((h.climateTarget ?: 70.0) - 55) / 30.0, Modifier.align(Alignment.CenterEnd).size(150.dp))
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            T(if (adjusting) "◀ −" else "−", 34.sp, FontWeight.Light)
            if (adjusting) T("OK when done", 18.sp, color = Hearth.Amber)
            T(if (adjusting) "+ ▶" else "+", 34.sp, FontWeight.Light)
        }
    }
}

@Composable
private fun Dial(fraction: Double, modifier: Modifier) {
    Canvas(modifier) {
        val sw = 16f
        val arcSize = Size(size.width - sw, size.height - sw)
        drawArc(Color.White.copy(alpha = .12f), 135f, 270f, false, Offset(sw / 2, sw / 2), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        drawArc(Hearth.Amber, 135f, (270f * fraction.toFloat().coerceIn(0.02f, 1f)), false, Offset(sw / 2, sw / 2), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        drawCircle(Color(0xFF15171E), size.minDimension / 2 - sw * 1.6f)
        drawCircle(Paper.copy(alpha = .6f), 5f)
    }
}

@Composable
private fun CameraCard(ui: TvUi, h: HomeSnapshot, modifier: Modifier) {
    Column(modifier.tvFocus(cardShape, onClick = { if (h.cameraEntity.isNotBlank()) ui.cameraFull = h.cameraEntity })
        .glass(22.dp).padding(horizontal = 28.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Glyph("camera", 22.dp, Paper); Spacer(Modifier.width(10.dp)); T("Cameras", 26.sp, FontWeight.Medium) }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(h.cameraName.ifBlank { "No camera set" }, 20.sp, color = Paper.copy(alpha = .6f))
            if (h.cameraEntity.isNotBlank()) { Spacer(Modifier.width(10.dp)); StatusDot(Hearth.Live, 9.dp); Spacer(Modifier.width(6.dp)); T("Live", 20.sp) }
        }
        Spacer(Modifier.height(12.dp))
        CameraImage(h.cameraEntity, Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)), intervalMs = 2000)
    }
}

/** HA camera via snapshot polling (camera_proxy). */
@Composable
fun CameraImage(entity: String, modifier: Modifier, intervalMs: Long = 1000) {
    val app = HearthApp.get()
    var img by remember(entity) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(entity) {
        if (entity.isBlank()) return@LaunchedEffect
        while (true) {
            val bytes = app.ha.cameraSnapshot(entity)
            if (bytes != null) withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }?.let { img = it }
            delay(intervalMs)
        }
    }
    Box(modifier) {
        val i = img
        if (i != null) Image(i, "camera", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Hatch(210f, Modifier.fillMaxSize(), label = "camera feed")
    }
}

@Composable
private fun LockCard(h: HomeSnapshot, modifier: Modifier, enabled: Boolean) {
    val app = HearthApp.get()
    val locked = h.locked
    Box(modifier.tvFocus(cardShape, onClick = { if (enabled && h.lockEntity.isNotBlank()) app.ha.toggle(h.lockEntity) })
        .glass(22.dp).padding(horizontal = 28.dp, vertical = 24.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) { Glyph("lock", 22.dp, Paper); Spacer(Modifier.width(10.dp)); T("Locks", 26.sp, FontWeight.Medium) }
            Spacer(Modifier.height(14.dp))
            T(h.lockName.ifBlank { "No lock set" }, 22.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (locked == true) Hearth.Live else Hearth.Alert, 9.dp); Spacer(Modifier.width(8.dp))
                T(when (locked) { true -> "Locked"; false -> "Unlocked"; null -> "Unknown" }, 20.sp, color = Paper.copy(alpha = .75f))
            }
        }
        Box(Modifier.align(Alignment.BottomStart)) { Toggle(locked == true, Hearth.Live) }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(110.dp, 160.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF2A2C34), Color(0xFF15161B))), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center) {
            Box(Modifier.size(60.dp).border(2.dp, Color.White.copy(alpha = .3f), CircleShape))
        }
    }
}

@Composable
private fun SpeakerCard(h: HomeSnapshot, modifier: Modifier, enabled: Boolean) {
    val app = HearthApp.get()
    var adjusting by remember { mutableStateOf(false) }
    fun setVol(d: Int) {
        val v = ((h.speakerVolume + d).coerceIn(0, 100)) / 100.0
        app.ha.call("media_player.volume_set", listOf(h.speakerEntity), buildJsonObject { put("volume_level", JsonPrimitive(v)) })
    }
    Box(modifier
        .onPreviewKeyEvent { e ->
            if (!adjusting || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionLeft -> { setVol(-5); true }
                Key.DirectionRight -> { setVol(5); true }
                else -> false
            }
        }
        .tvFocus(cardShape, onClick = { if (enabled && h.speakerEntity.isNotBlank()) adjusting = !adjusting },
            onLongClick = { if (enabled) app.ha.toggle(h.speakerEntity) })
        .glass(22.dp).then(if (adjusting) Modifier.border(3.dp, Hearth.Amber, cardShape) else Modifier)
        .padding(horizontal = 28.dp, vertical = 24.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) { Glyph("speaker", 22.dp, Paper); Spacer(Modifier.width(10.dp)); T("Speakers", 26.sp, FontWeight.Medium) }
            Spacer(Modifier.height(14.dp))
            T(h.speakerName.ifBlank { "No speaker set" }, 22.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (h.speakerState == "playing") Hearth.Live else Paper.copy(alpha = .4f), 9.dp); Spacer(Modifier.width(8.dp))
                T(listOf(h.speakerState.replaceFirstChar { it.uppercase() }, h.speakerArtist).filter { it.isNotBlank() }.joinToString(" · "),
                    20.sp, color = Paper.copy(alpha = .75f))
            }
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            if (adjusting) T("◀ ▶ volume · OK when done", 16.sp, color = Hearth.Amber)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(360.dp).height(24.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = .15f), RoundedCornerShape(99.dp)))
                Box(Modifier.fillMaxWidth(h.speakerVolume / 100f).height(6.dp).background(Hearth.Amber, RoundedCornerShape(99.dp)))
                Box(Modifier.padding(start = (336 * h.speakerVolume / 100f).dp).size(24.dp).background(Paper, CircleShape))
            }
        }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 20.dp).size(120.dp, 170.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF34363F), Color(0xFF1A1B21))), RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp, bottomStart = 16.dp, bottomEnd = 16.dp)))
    }
}

/** Full-screen camera (Enter on the camera card). */
@Composable
fun CameraFull(entity: String, onClose: () -> Unit) {
    val app = HearthApp.get()
    val name = app.ha[entity]?.name ?: "Camera"
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraImage(entity, Modifier.fillMaxSize(), intervalMs = 700)
        Row(Modifier.padding(54.dp).glass(999.dp).padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(Hearth.Alert, 10.dp); Spacer(Modifier.width(10.dp)); T("$name · Live", 22.sp, FontWeight.Medium)
            Spacer(Modifier.width(20.dp)); T("Back to close", 18.sp, color = Paper.copy(alpha = .6f))
        }
    }
}
