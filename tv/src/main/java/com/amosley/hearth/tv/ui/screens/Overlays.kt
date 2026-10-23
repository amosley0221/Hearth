package com.amosley.hearth.tv.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amosley.hearth.shared.theme.Hearth
import com.amosley.hearth.tv.HearthApp
import com.amosley.hearth.tv.control.WakeState
import com.amosley.hearth.tv.ui.Glyph
import com.amosley.hearth.tv.ui.Hatch
import com.amosley.hearth.tv.ui.Paper
import com.amosley.hearth.tv.ui.PinRequest
import com.amosley.hearth.tv.ui.StatusDot
import com.amosley.hearth.tv.ui.T
import com.amosley.hearth.tv.ui.TileMenu
import com.amosley.hearth.tv.ui.TvUi
import com.amosley.hearth.tv.ui.glass
import com.amosley.hearth.tv.ui.tvFocus
import kotlinx.coroutines.delay

/** 4j · Tile options / generic picker: dims the page, 400px glass menu. */
@Composable
fun MenuOverlay(menu: TileMenu, dimPage: Boolean, onClose: () -> Unit) {
    val first = remember(menu) { FocusRequester() }
    LaunchedEffect(menu) { try { first.requestFocus() } catch (_: Exception) {} }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (dimPage) .55f else 0f)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(480.dp).heightIn(max = 860.dp).shadow(40.dp, RoundedCornerShape(22.dp))
            .background(Color(14, 16, 22).copy(alpha = .94f), RoundedCornerShape(22.dp))
            .border(1.dp, Hearth.GlassBorder, RoundedCornerShape(22.dp)).padding(16.dp)) {
            T(menu.header, 18.sp, color = Paper.copy(alpha = .5f), modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                menu.items.forEachIndexed { i, item ->
                    var focused by remember { mutableStateOf(false) }
                    val shape = RoundedCornerShape(14.dp)
                    Box(Modifier.fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                        .tvFocus(shape, onClick = { onClose(); item.action() }, focusRequester = if (i == 0) first else null,
                            scale = 1f, ring = false, shadow = false)
                        .background(if (focused) Paper else Color.Transparent, shape)
                        .padding(horizontal = 18.dp, vertical = 16.dp)) {
                        T(item.label, 24.sp, color = when {
                            focused -> Hearth.Ink
                            item.destructive -> Hearth.Destructive
                            else -> Paper
                        })
                    }
                }
            }
        }
    }
}

/** 4b · Who's watching? */
@Composable
fun ProfilePicker(ui: TvUi) {
    val app = HearthApp.get()
    val cfg by app.config.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) {} }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF151826), Hearth.Ink))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T("Who's watching?", 56.sp)
            Spacer(Modifier.height(70.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(60.dp)) {
                ProfileAvatar(cfg.adultName, Color(0xFF55657F), false, first) {
                    ui.profilePicker = false
                    if (app.profiles.isKids) ui.pin = PinRequest("Enter parent PIN") { app.profiles.switchTo("adult") }
                }
                if (cfg.kids.enabled) ProfileAvatar(cfg.kids.name, Color(0xFF7A5BD6), true, null) {
                    ui.profilePicker = false
                    if (!app.profiles.hasPin()) ui.pin = PinRequest("Create a parent PIN first", create = true) { app.profiles.switchTo("kids") }
                    else app.profiles.switchTo("kids")
                }
            }
            Spacer(Modifier.height(70.dp))
            T("The Kids profile needs the parent PIN to exit", 20.sp, color = Paper.copy(alpha = .45f))
        }
    }
}

@Composable
private fun ProfileAvatar(name: String, color: Color, kid: Boolean, fr: FocusRequester?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(60.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(260.dp).tvFocus(shape, onClick, focusRequester = fr).background(color, shape), contentAlignment = Alignment.Center) {
            T(name.take(1).uppercase(), 110.sp, FontWeight.Medium)
            if (kid) Box(Modifier.align(Alignment.BottomEnd).padding(14.dp).size(52.dp).background(Hearth.Ink, CircleShape), contentAlignment = Alignment.Center) {
                Glyph("lock", 26.dp, Paper)
            }
        }
        Spacer(Modifier.height(20.dp))
        T(name, 30.sp)
    }
}

/** 4c · PIN pad (check or create). */
@Composable
fun PinPad(ui: TvUi, req: PinRequest) {
    val app = HearthApp.get()
    var digits by remember(req) { mutableStateOf("") }
    var firstEntry by remember(req) { mutableStateOf<String?>(null) }
    var error by remember(req) { mutableStateOf<String?>(null) }
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(req) { try { firstKey.requestFocus() } catch (_: Exception) {} }
    fun submit() {
        if (digits.length < 4) return
        if (req.create) {
            if (firstEntry == null) { firstEntry = digits; digits = ""; return }
            if (firstEntry == digits) { app.profiles.setPin(digits); ui.pin = null; req.onSuccess() }
            else { error = "PINs didn't match"; firstEntry = null; digits = "" }
        } else if (app.profiles.checkPin(digits)) { ui.pin = null; req.onSuccess() }
        else { error = "Wrong PIN"; digits = "" }
    }
    fun press(k: String) {
        error = null
        when (k) {
            "⌫" -> digits = digits.dropLast(1)
            "OK" -> submit()
            else -> if (digits.length < 4) { digits += k; if (digits.length == 4) submit() }
        }
    }
    Box(Modifier.fillMaxSize().background(Color(7, 8, 12).copy(alpha = .8f)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(620.dp).background(Color(14, 16, 22).copy(alpha = .92f), RoundedCornerShape(30.dp))
            .border(1.dp, Hearth.GlassBorder, RoundedCornerShape(30.dp)).padding(horizontal = 56.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            T(if (req.create && firstEntry != null) "Enter it again" else req.title, 36.sp, FontWeight.Medium)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                repeat(4) { i -> Box(Modifier.size(22.dp).then(if (i < digits.length) Modifier.background(Paper, CircleShape) else Modifier.border(2.dp, Paper.copy(alpha = .4f), CircleShape))) }
            }
            Spacer(Modifier.height(16.dp))
            T(error ?: " ", 20.sp, color = Hearth.Destructive)
            Spacer(Modifier.height(16.dp))
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (r in rows) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (k in r) {
                        val shape = RoundedCornerShape(20.dp)
                        val ok = k == "OK"
                        Box(Modifier.size(130.dp, 92.dp).tvFocus(shape, { press(k) }, focusRequester = if (k == "1") firstKey else null)
                            .background(if (ok) Paper else Color.White.copy(alpha = .1f), shape), contentAlignment = Alignment.Center) {
                            T(k, if (ok) 26.sp else 36.sp, if (ok) FontWeight.SemiBold else FontWeight.Normal, color = if (ok) Hearth.Ink else Paper)
                        }
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            T("Back to cancel · Forgot PIN? Use the phone remote", 18.sp, color = Paper.copy(alpha = .45f))
        }
    }
}

/** Pairing code shown on the TV while a phone/iPad pairs. */
@Composable
fun PairingOverlay(code: String, name: String, onClose: () -> Unit) {
    LaunchedEffect(code) { delay(5 * 60_000); onClose() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(860.dp).glass(30.dp, .85f).padding(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T("Pair $name", 40.sp, FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            T("Enter this code on your phone or iPad", 24.sp, color = Paper.copy(alpha = .65f))
            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (c in code) Box(Modifier.size(96.dp, 124.dp).background(Color.White.copy(alpha = .08f), RoundedCornerShape(18.dp))
                    .border(2.dp, Paper.copy(alpha = .5f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    T(c.toString(), 64.sp, family = Hearth.Newsreader)
                }
            }
            Spacer(Modifier.height(36.dp))
            T("Pairs once · reconnects automatically · Back to cancel", 20.sp, color = Paper.copy(alpha = .45f))
        }
    }
}

/** 4h · Console waking. */
@Composable
fun ConsoleWaking(w: WakeState) {
    val p by animateFloatAsState(w.progress, tween(600), label = "wake")
    Box(Modifier.fillMaxSize()) {
        Hatch(w.hue, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color(7, 8, 12).copy(alpha = .7f)))
        Column(Modifier.align(Alignment.Center).width(760.dp).glass(30.dp, .85f).padding(44.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Hatch(w.hue, Modifier.size(88.dp).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.width(24.dp))
                Column {
                    T(w.title, 36.sp, FontWeight.Medium)
                    T(w.subtitle, 20.sp, color = Paper.copy(alpha = .65f), maxLines = 2)
                }
            }
            Spacer(Modifier.height(32.dp))
            for (s in w.steps) {
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).background(when (s.status) {
                        "done" -> Hearth.Live; "running" -> Hearth.Amber; "failed" -> Hearth.Alert; else -> Color.White.copy(alpha = .12f)
                    }, CircleShape), contentAlignment = Alignment.Center) {
                        when (s.status) {
                            "done" -> Glyph("check", 20.dp, Hearth.Ink)
                            "running" -> Glyph("update", 20.dp, Hearth.Ink)
                            "failed" -> T("!", 20.sp, FontWeight.Bold, color = Hearth.Ink)
                            else -> {}
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    T(s.label + if (s.status == "running") "…" else if (s.status == "skipped") " · skipped" else "", 24.sp,
                        color = if (s.status == "pending" || s.status == "skipped") Paper.copy(alpha = .45f) else Paper)
                }
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = .12f), RoundedCornerShape(99.dp))) {
                Box(Modifier.fillMaxWidth(p).height(6.dp).background(Hearth.Amber, RoundedCornerShape(99.dp)))
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(if (w.done) "Ready" else "Usually takes 8–12 seconds", 18.sp, color = Paper.copy(alpha = .55f), modifier = Modifier.weight(1f))
                T("Back to cancel", 18.sp, color = Paper.copy(alpha = .55f))
            }
        }
    }
}

/** 4i · Doorbell pop-up, top-right. */
@Composable
fun DoorbellCard(camera: String, overlay: Boolean, onClose: () -> Unit) {
    val app = HearthApp.get()
    val talk = remember { FocusRequester() }
    LaunchedEffect(camera) {
        try { talk.requestFocus() } catch (_: Exception) {}
        delay(20_000); onClose()
    }
    val lock = app.config.value.home.lock
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopEnd).padding(top = 54.dp, end = 96.dp).width(560.dp)
            .shadow(40.dp, RoundedCornerShape(26.dp)).glass(26.dp, .85f).padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(Hearth.Alert, 10.dp); Spacer(Modifier.width(10.dp))
                T(app.ha[camera]?.name ?: "Front Door", 22.sp, FontWeight.Medium, modifier = Modifier.weight(1f))
                T("Just now", 18.sp, color = Paper.copy(alpha = .55f))
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(290.dp).clip(RoundedCornerShape(16.dp))) {
                CameraImage(camera, Modifier.fillMaxSize(), intervalMs = 800)
                Box(Modifier.padding(12.dp).background(Hearth.Alert, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    T("LIVE", 14.sp, FontWeight.Bold, letterSpacing = 0.1.em)
                }
            }
            Spacer(Modifier.height(14.dp))
            T("Someone's at the door", 22.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DoorButton("Talk", talk, Modifier.weight(1f)) { app.toast("Talk from the phone app or Home Assistant"); onClose() }
                if (lock.isNotBlank()) DoorButton("Unlock", null, Modifier.weight(1f)) { app.ha.call("lock.unlock", listOf(lock)); onClose() }
                DoorButton("Dismiss", null, Modifier.weight(1f)) { onClose() }
            }
        }
    }
}

@Composable
private fun DoorButton(label: String, fr: FocusRequester?, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.height(60.dp).onFocusChanged { focused = it.isFocused }.tvFocus(shape, onClick, focusRequester = fr, ring = false, shadow = false)
        .background(if (focused) Paper else Color.White.copy(alpha = .1f), shape), contentAlignment = Alignment.Center) {
        T(label, 22.sp, FontWeight.Medium, color = if (focused) Hearth.Ink else Paper)
    }
}

/** Inputs picker (remote "Inputs" button). */
@Composable
fun InputsPicker(ui: TvUi) {
    val app = HearthApp.get()
    val inputs = remember { app.inputs.list() }
    MenuOverlay(TileMenu("Switch input", inputs.map { i ->
        com.amosley.hearth.tv.ui.MenuItem(i.label) { app.inputs.switchTo(i.id) }
    }.ifEmpty { listOf(com.amosley.hearth.tv.ui.MenuItem("No HDMI inputs found") {}) }), dimPage = true) { ui.inputsPicker = false }
}

/** 4e · Bedtime / time's up lock. Parents hold OK for 3 seconds to enter the PIN. */
@Composable
fun BedtimeLock(ui: TvUi, reason: String) {
    val app = HearthApp.get()
    val cfg by app.config.collectAsStateWithLifecycle()
    var held by remember { mutableIntStateOf(0) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) {} }
    Box(Modifier.fillMaxSize()
        .background(Brush.radialGradient(listOf(Color(0xFF2B2A6B), Color(0xFF12132E), Hearth.Ink), radius = 1400f))
        .onPreviewKeyEvent { e ->
            if (e.key == Key.DirectionCenter || e.key == Key.Enter) {
                if (e.type == KeyEventType.KeyDown) {
                    held = e.nativeKeyEvent.repeatCount
                    if (e.nativeKeyEvent.repeatCount >= 30) {
                        held = 0
                        ui.pin = com.amosley.hearth.tv.ui.PinRequest("Enter parent PIN") {
                            ui.tileMenu = TileMenu("Parent options", listOf(
                                com.amosley.hearth.tv.ui.MenuItem("Add 30 minutes") { app.profiles.grantExtra(30) },
                                com.amosley.hearth.tv.ui.MenuItem("Add 1 hour") { app.profiles.grantExtra(60) },
                                com.amosley.hearth.tv.ui.MenuItem("Switch to ${cfg.adultName}") { app.profiles.switchTo("adult") },
                            ))
                        }
                    }
                } else held = 0
                true
            } else true
        }
        .tvFocus(RoundedCornerShape(0.dp), onClick = {}, focusRequester = fr, scale = 1f, ring = false, shadow = false),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph("moon", 160.dp, Color(0xFFFFE7A8))
            Spacer(Modifier.height(30.dp))
            T(if (reason == "bedtime") "Time for bed" else "That's all for today", 96.sp, FontWeight.Light, family = Hearth.Newsreader)
            Spacer(Modifier.height(12.dp))
            T(if (reason == "bedtime") "Sweet dreams, ${cfg.kids.name}. See you in the morning." else "You've watched all your TV for today.",
                30.sp, FontWeight.Light, color = Paper.copy(alpha = .8f), align = TextAlign.Center)
            Spacer(Modifier.height(30.dp))
            Box(Modifier.background(Color.White.copy(alpha = .1f), RoundedCornerShape(999.dp)).padding(horizontal = 28.dp, vertical = 14.dp)) {
                T("TV wakes up again at ${pretty(cfg.kids.wakeTime)}", 24.sp)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T("Parents: hold OK for 3 seconds to enter PIN", 20.sp, color = Paper.copy(alpha = .5f))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.width(240.dp).height(4.dp).background(Color.White.copy(alpha = .15f), RoundedCornerShape(99.dp))) {
                Box(Modifier.fillMaxWidth((held / 30f).coerceIn(0f, 1f)).height(4.dp).background(Paper, RoundedCornerShape(99.dp)))
            }
        }
    }
}

fun pretty(hhmm: String): String = try {
    java.time.LocalTime.parse(hhmm).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
} catch (_: Exception) { hhmm }
