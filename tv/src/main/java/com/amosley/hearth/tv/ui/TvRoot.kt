package com.amosley.hearth.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amosley.hearth.shared.theme.Hearth
import com.amosley.hearth.tv.HearthApp
import com.amosley.hearth.tv.UiEvent
import com.amosley.hearth.tv.ui.screens.BedtimeLock
import com.amosley.hearth.tv.ui.screens.CameraFull
import com.amosley.hearth.tv.ui.screens.ConsoleWaking
import com.amosley.hearth.tv.ui.screens.DoorbellCard
import com.amosley.hearth.tv.ui.screens.GamesSection
import com.amosley.hearth.tv.ui.screens.HomeSection
import com.amosley.hearth.tv.ui.screens.InputsPicker
import com.amosley.hearth.tv.ui.screens.KidHome
import com.amosley.hearth.tv.ui.screens.LibrarySection
import com.amosley.hearth.tv.ui.screens.MenuOverlay
import com.amosley.hearth.tv.ui.screens.PairingOverlay
import com.amosley.hearth.tv.ui.screens.PinPad
import com.amosley.hearth.tv.ui.screens.ProfilePicker
import com.amosley.hearth.tv.ui.screens.SettingsSection
import com.amosley.hearth.tv.ui.screens.SmartSection
import com.amosley.hearth.tv.ui.screens.SportsSection
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class RailEntry(val id: String, val label: String, val glyph: String)

val RAIL = listOf(
    RailEntry("home", "Home", "home"),
    RailEntry("library", "Library", "grid"),
    RailEntry("sports", "Sports", "sports"),
    RailEntry("smart", "Home control", "diamond"),
    RailEntry("games", "Games", "gamepad"),
)

@Composable
fun TvRoot(ui: TvUi) {
    val app = HearthApp.get()
    val profile by app.profiles.current.collectAsStateWithLifecycle()
    val locked by app.profiles.locked.collectAsStateWithLifecycle()
    val wake by app.actions.wake.collectAsStateWithLifecycle()
    val silenced by app.actions.silenced.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        app.events.collect { e ->
            when (e) {
                is UiEvent.Toast -> if (!silenced) ui.toast(e.text)
                is UiEvent.PairCode -> ui.pairCode = e.code to e.name
                UiEvent.PairDone -> ui.pairCode = null
                is UiEvent.OpenSection -> {
                    val s = e.section
                    if (s.startsWith("settings")) { ui.section = "settings"; ui.settingsPage = s.substringAfter(':', "main") }
                    else ui.section = s
                    if (s == "sports") ui.sportsTab = "live"
                }
                is UiEvent.Text -> ui.textTarget?.invoke(e.text, e.mode)
                is UiEvent.Doorbell -> ui.doorbell = e.camera
                UiEvent.DismissDoorbell -> ui.doorbell = null
                UiEvent.ShowInputs -> ui.inputsPicker = true
                UiEvent.RequestPin -> {}
                UiEvent.ShowProfiles -> ui.profilePicker = true
            }
        }
    }

    BackHandler(enabled = true) {
        when {
            ui.closeTopOverlay() -> {}
            wake != null -> app.actions.cancelWake()
            profile == "kids" -> {}
            ui.section == "settings" && ui.settingsPage != "main" -> ui.settingsPage = "main"
            ui.section == "sports" && ui.sportsTab != "live" -> ui.sportsTab = "live"
            else -> {
                // Back from content goes to the rail; from the rail, back to Home.
                val rail = ui.railRequesters[ui.section]
                if (railFocused.value || rail == null) ui.backToHome() else try { rail.requestFocus() } catch (_: Exception) {}
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Hearth.Ink)) {
        if (profile == "kids") {
            KidHome(ui)
        } else {
            Crossfade(targetState = ui.hue to ui.art, animationSpec = tween(500), label = "backdrop") { (h, a) ->
                Backdrop(h, a)
            }
            Box(Modifier.fillMaxSize().padding(start = 190.dp)) {
                when (ui.section) {
                    "home" -> HomeSection(ui)
                    "library" -> LibrarySection(ui)
                    "sports" -> SportsSection(ui)
                    "smart" -> SmartSection(ui)
                    "games" -> GamesSection(ui)
                    "settings" -> SettingsSection(ui)
                }
            }
            if (ui.section != "home") CompactClock(Modifier.align(Alignment.TopEnd).padding(top = 54.dp, end = 96.dp))
            Rail(ui)
        }

        // Overlays, top-most last.
        wake?.let { ConsoleWaking(it) }
        ui.cameraFull?.let { CameraFull(it) { ui.cameraFull = null } }
        if (ui.inputsPicker) InputsPicker(ui)
        ui.tileMenu?.let { MenuOverlay(it, dimPage = true) { ui.tileMenu = null } }
        ui.picker?.let { MenuOverlay(it, dimPage = true) { ui.picker = null } }
        if (ui.profilePicker) ProfilePicker(ui)
        ui.pin?.let { PinPad(ui, it) }
        locked?.let { if (profile == "kids") BedtimeLock(ui, it) }
        ui.doorbell?.let { cam -> DoorbellCard(cam, overlay = false) { ui.doorbell = null } }
        ui.pairCode?.let { (code, name) -> PairingOverlay(code, name) { ui.pairCode = null } }
        ToastHost(ui, Modifier.align(Alignment.TopCenter))
    }
}

/** True while focus is inside the rail. */
val railFocused = mutableStateOf(false)

@Composable
private fun Rail(ui: TvUi) {
    val app = HearthApp.get()
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) 320.dp else 130.dp, tween(250), label = "rail")
    val cfg by app.config.collectAsStateWithLifecycle()
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(if (expanded) Color(10, 11, 16).copy(alpha = .94f) else Color(10, 11, 16).copy(alpha = .62f))
            .border(1.dp, Color.White.copy(alpha = .06f))
            .onFocusChanged { expanded = it.hasFocus; railFocused.value = it.hasFocus }
            .padding(vertical = 54.dp, horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        for (e in RAIL) RailItem(ui, e.id, e.label, expanded) { Glyph(e.glyph, 26.dp, it) }
        Spacer(Modifier.weight(1f))
        RailItem(ui, "profile", cfg.adultName, expanded, onClick = { ui.profilePicker = true }) { c ->
            Box(Modifier.size(38.dp).background(Color(0xFF55657F), CircleShape), contentAlignment = Alignment.Center) {
                T(cfg.adultName.take(1).uppercase(), 18.sp, FontWeight.SemiBold, color = Paper)
            }
        }
        RailItem(ui, "settings", "Settings", expanded) { Glyph("gear", 26.dp, it) }
    }
}

@Composable
private fun RailItem(ui: TvUi, id: String, label: String, expanded: Boolean, onClick: (() -> Unit)? = null, icon: @Composable (Color) -> Unit) {
    val fr = remember(id) { FocusRequester() }
    ui.railRequesters[id] = fr
    var focused by remember { mutableStateOf(false) }
    val selected = ui.section == id
    val shape = RoundedCornerShape(18.dp)
    val bg = when {
        focused && expanded -> Paper
        selected -> Color.White.copy(alpha = .12f)
        else -> Color.Transparent
    }
    val fg = if (focused && expanded) Hearth.Ink else Paper.copy(alpha = if (selected) 1f else .8f)
    Row(
        Modifier
            .height(66.dp)
            .then(if (expanded) Modifier.width(256.dp) else Modifier.width(66.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused && onClick == null && ui.section != id) { ui.section = id; ui.settingsPage = "main" }
            }
            .tvFocus(shape, onClick = { onClick?.invoke() ?: run { ui.section = id } }, focusRequester = fr, scale = 1.04f, ring = false, shadow = false)
            .then(if (selected && !expanded) Modifier.shadow(12.dp, shape, ambientColor = Color.White, spotColor = Color.White) else Modifier)
            .background(bg, shape)
            .then(if (selected && !(focused && expanded)) Modifier.border(1.dp, Color.White.copy(alpha = .25f), shape) else Modifier)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) { icon(fg) }
        if (expanded) { Spacer(Modifier.width(20.dp)); T(label, 26.sp, FontWeight.Medium, color = fg) }
    }
}

@Composable
fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(1000L * (60 - now.second).coerceAtLeast(1)) } }
    return now
}

@Composable
fun CompactClock(modifier: Modifier = Modifier) {
    val now = rememberNow()
    val app = HearthApp.get()
    val ents by app.ha.entities.collectAsStateWithLifecycle()
    val weather = remember(ents) { app.state.weatherEntity()?.attrDouble("temperature")?.toInt() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        T(now.format(DateTimeFormatter.ofPattern("h:mm a")), 24.sp, FontWeight.Light, color = Paper.copy(alpha = .85f))
        if (weather != null) {
            T("|", 24.sp, FontWeight.Light, color = Paper.copy(alpha = .3f))
            Glyph("moon", 22.dp, Paper.copy(alpha = .85f))
            T("$weather°", 24.sp, FontWeight.Light, color = Paper.copy(alpha = .85f))
        }
        T("|", 24.sp, FontWeight.Light, color = Paper.copy(alpha = .3f))
        T(now.format(DateTimeFormatter.ofPattern("EEE MMM d")), 24.sp, FontWeight.Light, color = Paper.copy(alpha = .85f))
    }
}

@Composable
private fun ToastHost(ui: TvUi, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(ui.toastSerial) {
        if (ui.toast != null) { visible = true; delay(2200); visible = false }
    }
    AnimatedVisibility(visible, modifier.padding(top = 54.dp), enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
        Box(Modifier.shadow(30.dp, RoundedCornerShape(999.dp)).clip(RoundedCornerShape(999.dp)).background(Paper.copy(alpha = .95f))
            .padding(horizontal = 28.dp, vertical = 14.dp)) {
            T(ui.toast ?: "", 22.sp, FontWeight.Medium, color = Hearth.Ink2)
        }
    }
}
