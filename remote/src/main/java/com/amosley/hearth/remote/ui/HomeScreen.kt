package com.amosley.hearth.remote.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amosley.hearth.remote.net.TvConnection
import com.amosley.hearth.shared.protocol.TvState
import com.amosley.hearth.shared.theme.Hearth
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** 5d · Home control: same Home Assistant entities as the TV, through the launcher. */
@Composable
fun HomeScreen(tv: TvConnection, st: TvState?) {
    val h = st?.home
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(6.dp))
        P("Home", 28.sp, FontWeight.Medium)
        P(listOfNotNull(h?.presence?.ifBlank { null }, h?.outside?.ifBlank { null }?.let { "$it outside" }).joinToString(" · ").ifBlank { h?.room ?: "" },
            15.sp, color = Paper.copy(alpha = .6f))
        if (h == null || !h.configured) {
            Box(Modifier.fillMaxWidth().glass(22.dp).padding(18.dp)) {
                P("Connect Home Assistant from the TV's settings page (Settings › Open TV settings).", 15.sp, maxLines = 3)
            }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.clip(RoundedCornerShape(99.dp)).background(Paper).padding(horizontal = 14.dp, vertical = 8.dp)) { P(h.room, 13.sp, FontWeight.Medium, color = Hearth.Ink2) }
            if (!h.connected) Row(Modifier.clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = .08f)).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) { Dot(Paper.copy(alpha = .4f)); Spacer(Modifier.width(6.dp)); P("Home Assistant offline", 13.sp) }
        }
        val dim = Modifier.alpha(if (h.connected) 1f else .45f)
        Row(dim.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val on = h.lightsOn > 0
            Box(Modifier.weight(1f).fillMaxHeight().glass(22.dp).press({ tv.haCall(if (on) "light.turn_off" else "light.turn_on") }).padding(16.dp)) {
                Column { P("Lights", 17.sp, FontWeight.Medium); P("${h.lightsOn} on${if (h.lightsBrightness > 0) " · ${h.lightsBrightness}%" else ""}", 13.sp, color = Paper.copy(alpha = .6f)) }
                Box(Modifier.align(Alignment.TopEnd)) { Orb(on, 44.dp) }
                Box(Modifier.align(Alignment.BottomStart)) { Toggle(on) }
            }
            Box(Modifier.weight(1f).fillMaxHeight().glass(22.dp).padding(16.dp)) {
                Column {
                    P("Thermostat", 17.sp, FontWeight.Medium)
                    P(h.climateCurrent?.let { "${it.toInt()}°" } ?: "--", 34.sp, FontWeight.Medium)
                    P(listOfNotNull(h.climateMode.ifBlank { null }?.replaceFirstChar { it.uppercase() }, h.climateTarget?.let { "set ${it.toInt()}°" }).joinToString(" · "), 12.sp, color = Paper.copy(alpha = .6f))
                }
                Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((label, d) in listOf("−" to -1, "+" to 1)) {
                        Box(Modifier.size(34.dp).background(Color.White.copy(alpha = .1f), CircleShape).press({
                            tv.haCall("climate.set_temperature", null, buildJsonObject { put("temperature", JsonPrimitive((h.climateTarget ?: 70.0) + d)) })
                        }), contentAlignment = Alignment.Center) { P(label, 18.sp) }
                    }
                }
            }
        }
        if (h.cameraEntity.isNotBlank()) {
            Column(dim.fillMaxWidth().glass(22.dp).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    P(h.cameraName, 17.sp, FontWeight.Medium, modifier = Modifier.weight(1f)); Dot(Hearth.Live); Spacer(Modifier.width(6.dp)); P("Live", 13.sp)
                }
                Spacer(Modifier.height(10.dp))
                var tick by remember { mutableStateOf(0L) }
                LaunchedEffect(Unit) { while (true) { delay(1500); tick = System.currentTimeMillis() } }
                Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(14.dp)).background(Hearth.placeholder(210f))) {
                    tv.cameraUrl(h.cameraEntity)?.let { url -> AsyncImage("$url&k=$tick", "camera", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
            }
        }
        Row(dim.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (h.lockEntity.isNotBlank()) Box(Modifier.weight(1f).fillMaxHeight().glass(22.dp).press({ tv.haCall(if (h.locked == true) "lock.unlock" else "lock.lock", h.lockEntity) }).padding(16.dp)) {
                Column {
                    P(h.lockName, 17.sp, FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) { Dot(if (h.locked == true) Hearth.Live else Hearth.Alert); Spacer(Modifier.width(6.dp)); P(if (h.locked == true) "Locked" else "Unlocked", 13.sp, color = Paper.copy(alpha = .7f)) }
                }
                Box(Modifier.align(Alignment.BottomEnd)) { Toggle(h.locked == true, Hearth.Live) }
            }
            if (h.speakerEntity.isNotBlank()) Column(Modifier.weight(1f).fillMaxHeight().glass(22.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
                P(h.speakerName, 17.sp, FontWeight.Medium)
                P(listOf(h.speakerArtist, "${h.speakerVolume}%").filter { it.isNotBlank() }.joinToString(" · "), 12.sp, color = Paper.copy(alpha = .6f))
                var v by remember(h.speakerVolume) { mutableFloatStateOf(h.speakerVolume / 100f) }
                Slider(v, { v = it }, onValueChangeFinished = {
                    tv.haCall("media_player.volume_set", h.speakerEntity, buildJsonObject { put("volume_level", JsonPrimitive(v.toDouble())) })
                }, colors = SliderDefaults.colors(thumbColor = Paper, activeTrackColor = Hearth.Amber, inactiveTrackColor = Color.White.copy(alpha = .15f)))
            }
        }
        if (h.scenes.isNotEmpty()) Row(dim.fillMaxWidth().height(50.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (s in h.scenes.take(3)) GlassButton(s.name, Modifier.weight(1f).fillMaxHeight(), 16.dp, size = 13.sp) { tv.haToggle(s.id) }
        }
        Spacer(Modifier.height(10.dp))
    }
}
