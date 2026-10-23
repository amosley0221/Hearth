package com.amosley.hearth.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.amosley.hearth.shared.theme.Hearth
import com.amosley.hearth.tv.HearthApp
import com.amosley.hearth.tv.ui.AppArt
import com.amosley.hearth.tv.ui.Backdrop
import com.amosley.hearth.tv.ui.Glyph
import com.amosley.hearth.tv.ui.Hatch
import com.amosley.hearth.tv.ui.Paper
import com.amosley.hearth.tv.ui.Pivot
import com.amosley.hearth.tv.ui.PinRequest
import com.amosley.hearth.tv.ui.ProgressBar
import com.amosley.hearth.tv.ui.StatusDot
import com.amosley.hearth.tv.ui.T
import com.amosley.hearth.tv.ui.TvUi
import com.amosley.hearth.tv.ui.rememberNow
import com.amosley.hearth.tv.ui.tvFocus
import java.time.format.DateTimeFormatter

/** 4d · Kid home: same bones, playful theme, approved apps only. */
@Composable
fun KidHome(ui: TvUi) {
    val app = HearthApp.get()
    val cfg by app.config.collectAsStateWithLifecycle()
    val apps by app.apps.apps.collectAsStateWithLifecycle()
    val cw by app.continueWatching.items.collectAsStateWithLifecycle()
    val used by app.profiles.usedMinutesToday.collectAsStateWithLifecycle()
    val now = rememberNow()
    val approved = remember(cfg.kids.approvedApps, apps) { apps.filter { it.pkg in cfg.kids.approvedApps } }
    val keep = remember(cw, cfg.kids.approvedApps) { cw.filter { it.pkg in cfg.kids.approvedApps } }
    val first = remember { FocusRequester() }
    LaunchedEffect(ui.homeSerial) { try { first.requestFocus() } catch (_: Exception) {} }
    val left = remember(used, now) { app.profiles.minutesLeft() }
    val greeting = when (now.hour) { in 5..11 -> "Good morning!"; in 12..16 -> "Good afternoon!"; else -> "Good evening!" }

    Box(Modifier.fillMaxSize()) {
        Backdrop(280f, kids = true)
        // Rail: Home + avatar only.
        Column(Modifier.width(130.dp).fillMaxSize().background(Color(10, 11, 16).copy(alpha = .35f)).padding(vertical = 54.dp, horizontal = 32.dp)) {
            Box(Modifier.size(66.dp).background(Color.White.copy(alpha = .16f), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                Glyph("home", 26.dp, Paper)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(66.dp).tvFocus(RoundedCornerShape(33.dp), onClick = {
                ui.pin = PinRequest("Enter parent PIN to switch") { app.profiles.switchTo("adult") }
            }).background(Color(0xFF7A5BD6), CircleShape), contentAlignment = Alignment.Center) {
                T(cfg.kids.name.take(1), 26.sp, FontWeight.Bold)
            }
        }
        Column(Modifier.fillMaxSize().padding(start = 190.dp, top = 70.dp, end = 96.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                T(now.format(DateTimeFormatter.ofPattern("h:mm")), 110.sp, FontWeight.Bold)
                Spacer(Modifier.width(16.dp))
                T(now.format(DateTimeFormatter.ofPattern("a")), 60.sp, FontWeight.Bold, modifier = Modifier.padding(bottom = 14.dp))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    T(greeting, 44.sp, FontWeight.Bold, color = Hearth.KidsYellow)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.background(Color.White.copy(alpha = .14f), RoundedCornerShape(999.dp)).padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(Hearth.KidsMint, 12.dp); Spacer(Modifier.width(12.dp))
                        val limitText = if (cfg.kids.dailyLimitMin > 0 && left < Int.MAX_VALUE) "${left / 60}h ${left % 60}m of TV left today" else "TV time"
                        T("$limitText · bedtime ${pretty(cfg.kids.bedtime)}", 22.sp, FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(50.dp))
            if (keep.isNotEmpty()) {
                T("Keep watching", 32.sp, FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Pivot(24f) {
                    LazyRow(contentPadding = PaddingValues(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        items(keep, key = { it.key }) { item ->
                            val shape = RoundedCornerShape(30.dp)
                            Box(Modifier.size(380.dp, 214.dp).tvFocus(shape, onClick = { app.continueWatching.open(item) { app.apps.launch(it) } },
                                focusRequester = if (item == keep.first()) first else null).clip(shape)) {
                                Hatch(Hearth.hueFor(item.title), Modifier.fillMaxSize())
                                if (item.art != null) AsyncImage(item.art, item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = .7f))))
                                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                                    T(item.title, 26.sp, FontWeight.Bold)
                                    Spacer(Modifier.height(10.dp))
                                    ProgressBar(item.progress, Modifier.fillMaxWidth(), color = Hearth.KidsYellow, height = 6.dp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 54.dp)
                .background(Color.White.copy(alpha = .1f), RoundedCornerShape(40.dp)).padding(22.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                approved.forEachIndexed { i, a ->
                    val shape = RoundedCornerShape(32.dp)
                    Box(Modifier.size(120.dp).tvFocus(shape, onClick = { app.apps.launch(a.pkg) },
                        focusRequester = if (keep.isEmpty() && i == 0) first else null).clip(shape)) {
                        AppArt(a.pkg, a.label, Modifier.fillMaxSize(), wordmarkSize = 20.sp)
                    }
                }
                val shape = RoundedCornerShape(32.dp)
                Box(Modifier.size(120.dp).tvFocus(shape, onClick = {
                    ui.pin = PinRequest("Grown-ups only") { ui.section = "settings"; ui.settingsPage = "kids"; app.profiles.switchTo("adult") }
                }, focusRequester = if (keep.isEmpty() && approved.isEmpty()) first else null)
                    .border(3.dp, Paper.copy(alpha = .45f), shape), contentAlignment = Alignment.Center) {
                    T("Ask a\ngrown-up", 20.sp, FontWeight.SemiBold, maxLines = 2, color = Paper.copy(alpha = .8f))
                }
            }
        }
    }
}
