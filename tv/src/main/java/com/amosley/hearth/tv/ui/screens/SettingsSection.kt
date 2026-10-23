package com.amosley.hearth.tv.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amosley.hearth.shared.theme.Hearth
import com.amosley.hearth.tv.BuildConfig
import com.amosley.hearth.tv.HearthApp
import com.amosley.hearth.tv.data.HaCall
import com.amosley.hearth.tv.data.ModeAction
import com.amosley.hearth.tv.data.ModeConfig
import com.amosley.hearth.tv.ui.AppArt
import com.amosley.hearth.tv.ui.Glyph
import com.amosley.hearth.tv.ui.MenuItem
import com.amosley.hearth.tv.ui.Paper
import com.amosley.hearth.tv.ui.Pill
import com.amosley.hearth.tv.ui.PinRequest
import com.amosley.hearth.tv.ui.Pivot
import com.amosley.hearth.tv.ui.StatusDot
import com.amosley.hearth.tv.ui.T
import com.amosley.hearth.tv.ui.TileMenu
import com.amosley.hearth.tv.ui.Toggle
import com.amosley.hearth.tv.ui.TvUi
import com.amosley.hearth.tv.ui.glass
import com.amosley.hearth.tv.ui.tvFocus
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

object Network {
    fun ipAddress(): String = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress ?: "tv-ip"
    } catch (_: Exception) { "tv-ip" }
}

@Composable
fun SettingsSection(ui: TvUi) {
    Box(Modifier.fillMaxSize().padding(top = 120.dp, end = 96.dp)) {
        when (ui.settingsPage) {
            "kids" -> ParentControls(ui)
            "teams" -> Column { Header("Settings · Sports", "My Teams"); Spacer(Modifier.height(24.dp)); MyTeamsSetup(ui) }
            "modes" -> ModesPage(ui)
            "setup" -> SetupPage(ui)
            "consoles" -> ConsolesPage(ui)
            "home" -> HomeAssistantPage(ui)
            "updates" -> UpdatesPage(ui)
            "pair" -> PairPage()
            else -> SettingsMain(ui)
        }
    }
}

@Composable
private fun Header(eyebrow: String, title: String, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            T(eyebrow.uppercase(), 15.sp, color = Paper.copy(alpha = .5f), letterSpacing = 0.2.em)
            Spacer(Modifier.height(6.dp))
            T(title, 52.sp, FontWeight.Medium, letterSpacing = (-0.01).em)
        }
        trailing()
    }
}

@Composable
private fun SettingsMain(ui: TvUi) {
    val app = HearthApp.get()
    val cfg by app.config.collectAsStateWithLifecycle()
    val perms by app.state.permissions.collectAsStateWithLifecycle()
    val upd by app.updater.status.collectAsStateWithLifecycle()
    val ha by app.ha.status.collectAsStateWithLifecycle()
    val ip = remember { Network.ipAddress() }
    Pivot(120f) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 60.dp)) {
            Header("Hearth ${BuildConfig.VERSION_NAME}", "Settings")
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f).glass(22.dp).padding(18.dp)) {
                    SettingRow("Pair a phone or iPad", "${cfg.remotes.size} paired", "Open the Hearth app, or http://$ip:8765 in Safari") { ui.settingsPage = "pair" }
                    SettingRow("Remote control & permissions", if (perms.accessibility || perms.adb == "connected") "Ready" else "Set up",
                        "Lets the remotes press keys in other apps, see what's playing") { ui.settingsPage = "setup" }
                    SettingRow("Profiles & parental controls", cfg.kids.name, "Approved apps, daily limit, bedtime, PIN") {
                        ui.pin = PinRequest(if (app.profiles.hasPin()) "Enter parent PIN" else "Create a parent PIN", create = !app.profiles.hasPin()) { ui.settingsPage = "kids" }
                    }
                    SettingRow("My Teams", "${cfg.sports.teams.size} teams", "Leagues, favourite teams, which app plays them") { ui.settingsPage = "teams" }
                    SettingRow("Modes", "${cfg.modes.size}", "Movie Night, Game Night… one press runs several actions") { ui.settingsPage = "modes" }
                }
                Column(Modifier.weight(1f).glass(22.dp).padding(18.dp)) {
                    SettingRow("Consoles & inputs", "${cfg.consoles.size}", "Which HDMI input each console uses") { ui.settingsPage = "consoles" }
                    SettingRow("Home Assistant", when (ha) { "connected" -> "Connected"; "not_configured" -> "Not set"; "auth_failed" -> "Bad token"; else -> "Offline" },
                        "Lights, cameras, locks, consoles") { ui.settingsPage = "home" }
                    SettingRow("Updates", when (upd.status) { "available" -> "${upd.latest} available"; "up_to_date" -> "Up to date"; else -> BuildConfig.VERSION_NAME },
                        "From GitHub releases · installs over this version") { ui.settingsPage = "updates" }
                    ToggleRow("Replace Google TV home", "Pressing Home always comes back to Hearth", cfg.replaceStockHome) {
                        app.configStore.update { it.copy(replaceStockHome = !it.replaceStockHome) }
                    }
                    ToggleRow("Ask who's watching on wake", null, cfg.askProfileOnWake) {
                        app.configStore.update { it.copy(askProfileOnWake = !it.askProfileOnWake) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            T("Everything else (Home Assistant token, entities, games, TV name) is easiest from a browser: http://$ip:8765/#settings",
                20.sp, color = Paper.copy(alpha = .55f), maxLines = 2)
        }
    }
}

@Composable
private fun PairPage() {
    val ip = remember { Network.ipAddress() }
    val app = HearthApp.get()
    val cfg by app.config.collectAsStateWithLifecycle()
    Column {
        Header("Settings", "Pair a remote")
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).glass(22.dp).padding(30.dp)) {
                T("Phone (Hearth Remote app)", 28.sp, FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                T("Open the app on the same Wi-Fi. It finds ${cfg.tvName} automatically; tap Connect and type the code that appears here.",
                    22.sp, color = Paper.copy(alpha = .7f), maxLines = 4)
            }
            Column(Modifier.weight(1f).glass(22.dp).padding(30.dp)) {
                T("iPad / any browser", 28.sp, FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                T("Open Safari and go to", 22.sp, color = Paper.copy(alpha = .7f))
                T("http://$ip:8765", 40.sp, FontWeight.Medium, family = Hearth.Newsreader)
                T("Then Share › Add to Home Screen for the full-screen remote.", 22.sp, color = Paper.copy(alpha = .7f), maxLines = 2)
            }
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth().glass(22.dp).padding(18.dp)) {
            T("Paired remotes", 24.sp, FontWeight.Medium, modifier = Modifier.padding(16.dp))
            if (cfg.remotes.isEmpty()) T("None yet", 20.sp, color = Paper.copy(alpha = .5f), modifier = Modifier.padding(16.dp))
            for (r in cfg.remotes) SettingRow(r.name, "Forget") {
                app.configStore.update { c -> c.copy(remotes = c.remotes.filterNot { it.token == r.token }) }
            }
        }
    }
}

@Composable
private fun SetupPage(ui: TvUi) {
    val app = HearthApp.get()
    val perms by app.state.permissions.collectAsStateWithLifecycle()
    val adb by app.adb.status.collectAsStateWithLifecycle()
    var results by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun open(action: String, pkgUri: Boolean = false) {
        try {
            val i = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (pkgUri) i.data = Uri.parse("package:${app.packageName}")
            app.startActivity(i)
        } catch (_: Exception) { ui.toast("This TV doesn't have that settings page. Use the ADB option.") }
    }
    Pivot(120f) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 60.dp)) {
            Header("Settings", "Remote control & permissions")
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f).glass(22.dp).padding(28.dp)) {
                    T("One-step setup (recommended)", 28.sp, FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    T("1. Settings › System › About › tap \"Android TV OS build\" 7 times.\n2. Settings › System › Developer options › turn on USB debugging.\n3. Press the button below and choose \"Always allow\" on the prompt.",
                        20.sp, color = Paper.copy(alpha = .75f), maxLines = 6)
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(if (busy) "Waiting for \"Allow\"…" else if (adb == "connected") "Grant everything again" else "Connect & grant everything", selected = true, onClick = {
                            if (busy) return@Pill
                            busy = true
                            scope.launch {
                                if (app.adb.connect(waitForApproval = true)) results = app.adb.grantHearthPermissions()
                                else results = listOf("✗ Couldn't reach ADB on this TV. Is USB debugging on?")
                                busy = false
                                app.state.refreshPermissions()
                                app.media.attach()
                            }
                        })
                        Spacer(Modifier.width(16.dp))
                        StatusDot(if (adb == "connected") Hearth.Live else Hearth.Amber); Spacer(Modifier.width(8.dp))
                        T("ADB: $adb", 18.sp, color = Paper.copy(alpha = .6f))
                    }
                    Spacer(Modifier.height(14.dp))
                    for (r in results) T(r, 20.sp, color = Paper.copy(alpha = .85f))
                }
                Column(Modifier.weight(1f).glass(22.dp).padding(18.dp)) {
                    PermRow("Accessibility", "Remote keys in other apps, Home button, typing", perms.accessibility) { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
                    PermRow("Now playing", "Remote's Now tab, Continue Watching", perms.notificationAccess) { open("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS") }
                    PermRow("Pop-ups over apps", "Doorbell card over whatever's playing", perms.overlay) { open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true) }
                    PermRow("Install updates", "Hearth updates itself from GitHub", perms.installUpdates) { open(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, true) }
                }
            }
        }
    }
}

@Composable
private fun PermRow(title: String, sub: String, ok: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().height(84.dp).tvFocus(shape, onClick, scale = 1.02f, shadow = false).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).background(if (ok) Hearth.Live else Color.White.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
            if (ok) Glyph("check", 18.dp, Hearth.Ink)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) { T(title, 22.sp); T(sub, 17.sp, color = Paper.copy(alpha = .55f)) }
        T(if (ok) "On" else "Open settings", 18.sp, color = Paper.copy(alpha = .6f))
    }
}

/** 4f · Parent controls. */
@Composable
private fun ParentControls(ui: TvUi) {
    val app = HearthApp.get()
    val cfg by app.config.collectAsStateWithLifecycle()
    val apps by app.apps.apps.collectAsStateWithLifecycle()
    val k = cfg.kids
    Column {
        Header("Settings · Profiles", "${k.name} profile")
        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxSize().padding(bottom = 54.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).glass(22.dp).padding(18.dp)) {
                T("Approved apps", 26.sp, FontWeight.Medium, modifier = Modifier.padding(12.dp))
                Pivot(80f) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        for (a in apps) {
                            val on = a.pkg in k.approvedApps
                            val shape = RoundedCornerShape(14.dp)
                            Row(Modifier.fillMaxWidth().height(76.dp).tvFocus(shape, { Favorites.kids(a.pkg, !on) }, scale = 1.02f, shadow = false)
                                .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AppArt(a.pkg, a.label, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)), wordmarkSize = 10.sp, padding = 4.dp)
                                Spacer(Modifier.width(16.dp))
                                T(a.label, 24.sp, modifier = Modifier.weight(1f))
                                Toggle(on, Hearth.Live, 64.dp, 36.dp)
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).glass(22.dp).padding(28.dp)) {
                T("Daily limit", 26.sp, FontWeight.Medium)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((min, label) in listOf(0 to "Off", 60 to "1h", 90 to "1.5h", 120 to "2h", 150 to "2.5h", 180 to "3h")) {
                        val sel = k.dailyLimitMin == min
                        val shape = RoundedCornerShape(14.dp)
                        Box(Modifier.height(56.dp).width(84.dp).tvFocus(shape, {
                            app.configStore.update { it.copy(kids = it.kids.copy(dailyLimitMin = min)) }
                        }, shadow = false).background(if (sel) Paper else Color.White.copy(alpha = .08f), shape), contentAlignment = Alignment.Center) {
                            T(label, 22.sp, FontWeight.Medium, color = if (sel) Hearth.Ink else Paper)
                        }
                    }
                }
                Spacer(Modifier.height(30.dp))
                T("Bedtime", 26.sp, FontWeight.Medium)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Pill(pretty(k.bedtime), size = 30.sp, onClick = { ui.picker = timePicker("Bedtime", 17, 23) { t -> app.configStore.update { it.copy(kids = it.kids.copy(bedtime = t)) } } })
                    T("→", 30.sp, color = Paper.copy(alpha = .5f))
                    Pill(pretty(k.wakeTime), size = 30.sp, onClick = { ui.picker = timePicker("TV wakes up", 5, 10) { t -> app.configStore.update { it.copy(kids = it.kids.copy(wakeTime = t)) } } })
                }
                Spacer(Modifier.height(30.dp))
                SettingRow("Change PIN", "", null) {
                    ui.pin = PinRequest("Enter current PIN") { ui.pin = PinRequest("New PIN", create = true) { ui.toast("PIN changed") } }
                }
                ToggleRow("Show ${k.name} profile", "Turn off to hide it from Who's watching", k.enabled) {
                    app.configStore.update { it.copy(kids = it.kids.copy(enabled = !it.kids.enabled)) }
                }
                SettingRow("Switch to ${k.name} now", "", null) { app.profiles.switchTo("kids") }
            }
        }
    }
}

private fun timePicker(title: String, fromHour: Int, toHour: Int, onPick: (String) -> Unit): TileMenu =
    TileMenu(title, (fromHour..toHour).flatMap { h -> listOf("%02d:00".format(h), "%02d:30".format(h)) }.map { t -> MenuItem(pretty(t)) { onPick(t) } })

/** Modes list + 4k mode editor. */
@Composable
private fun ModesPage(ui: TvUi) {
    val app = HearthApp.get()
    val cfg by app.config.collectAsStateWithLifecycle()
    val editing = cfg.modes.firstOrNull { it.id == ui.editingModeId }
    if (editing != null) { ModeEditor(ui, editing); return }
    Column {
        Header("Settings", "Modes") {
            Pill("New mode", onClick = {
                val id = "mode_${System.currentTimeMillis()}"
                app.configStore.update { it.copy(modes = it.modes + ModeConfig(id, "New mode", "spark")) }
                ui.editingModeId = id
            })
        }
        Spacer(Modifier.height(30.dp))
        Column(Modifier.fillMaxWidth().glass(22.dp).padding(18.dp)) {
            for (m in cfg.modes) {
                val shape = RoundedCornerShape(14.dp)
                Row(Modifier.fillMaxWidth().height(84.dp).tvFocus(shape, { ui.editingModeId = m.id }, scale = 1.02f, shadow = false).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Glyph(m.icon, 30.dp, Paper); Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) { T(m.name, 24.sp); T(m.actions.joinToString(" · ") { it.label.ifBlank { it.type } }, 17.sp, color = Paper.copy(alpha = .55f)) }
                    Glyph("chevron", 18.dp, Paper.copy(alpha = .5f))
                }
            }
        }
    }
}

@Composable
private fun ModeEditor(ui: TvUi, mode: ModeConfig) {
    val app = HearthApp.get()
    fun save(f: (ModeConfig) -> ModeConfig) = app.configStore.update { c -> c.copy(modes = c.modes.map { if (it.id == mode.id) f(it) else it }) }
    Column {
        Header("Modes · Edit", mode.name) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill("Test run", onClick = { app.actions.testRun(mode.actions); ui.toast("Testing ${mode.name}…") })
                Pill("Done", selected = true, onClick = { ui.editingModeId = null })
            }
        }
        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxSize().padding(bottom = 54.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).glass(22.dp).padding(28.dp)) {
                T("Name", 18.sp, color = Paper.copy(alpha = .55f))
                Spacer(Modifier.height(8.dp))
                SettingRow(mode.name, "Rename", null) {
                    ui.picker = TileMenu("Name", listOf("Movie Night", "Game Night", "Game Day", "Kid Mode", "Good Night", "Party", "Focus").map { n -> MenuItem(n) { save { it.copy(name = n) } } })
                }
                Spacer(Modifier.height(18.dp))
                T("Icon", 18.sp, color = Paper.copy(alpha = .55f))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (icon in listOf("film", "gamepad", "trophy", "star", "moon", "spark")) {
                        val shape = RoundedCornerShape(18.dp)
                        val sel = mode.icon == icon
                        Box(Modifier.size(72.dp).tvFocus(shape, { save { it.copy(icon = icon) } }, shadow = false)
                            .background(if (sel) Paper else Color.White.copy(alpha = .08f), shape), contentAlignment = Alignment.Center) {
                            Glyph(icon, 30.dp, if (sel) Hearth.Ink else Paper)
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                T("Show as", 18.sp, color = Paper.copy(alpha = .55f))
                ToggleRow("Tile on Home", null, mode.showOnHome) { save { it.copy(showOnHome = !it.showOnHome) } }
                ToggleRow("Button on phone remote", null, mode.showOnRemote) { save { it.copy(showOnRemote = !it.showOnRemote) } }
                Spacer(Modifier.height(10.dp))
                SettingRow("Delete mode", "", null) {
                    ui.picker = TileMenu("Delete ${mode.name}?", listOf(MenuItem("Delete", destructive = true) {
                        ui.editingModeId = null
                        app.configStore.update { c -> c.copy(modes = c.modes.filterNot { it.id == mode.id }) }
                    }, MenuItem("Cancel") {}))
                }
            }
            Column(Modifier.weight(1.3f).glass(22.dp).padding(18.dp).verticalScroll(rememberScrollState())) {
                mode.actions.forEachIndexed { i, a ->
                    val shape = RoundedCornerShape(14.dp)
                    Row(Modifier.fillMaxWidth().height(84.dp).tvFocus(shape, {
                        ui.picker = TileMenu(a.label.ifBlank { a.type }, listOf(
                            MenuItem("Move up") { save { it.copy(actions = it.actions.toMutableList().apply { if (i > 0) add(i - 1, removeAt(i)) }) } },
                            MenuItem("Move down") { save { it.copy(actions = it.actions.toMutableList().apply { if (i < size - 1) add(i + 1, removeAt(i)) }) } },
                            MenuItem("Remove", destructive = true) { save { it.copy(actions = it.actions.filterIndexed { j, _ -> j != i }) } },
                        ))
                    }, scale = 1.02f, shadow = false).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).border(2.dp, Paper.copy(alpha = .4f), CircleShape), contentAlignment = Alignment.Center) { T("${i + 1}", 18.sp, FontWeight.SemiBold) }
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            T(a.label.ifBlank { a.type }, 24.sp)
                            T(viaLine(a), 17.sp, color = Paper.copy(alpha = .55f))
                        }
                        T("≡", 26.sp, color = Paper.copy(alpha = .4f))
                    }
                }
                val shape = RoundedCornerShape(14.dp)
                Box(Modifier.fillMaxWidth().height(84.dp).tvFocus(shape, { ui.picker = addActionMenu { a -> save { it.copy(actions = it.actions + a) } } }, scale = 1.02f, shadow = false)
                    .border(2.dp, Paper.copy(alpha = .25f), shape).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                    T("+  Add action · lights, input, app, console, volume, profile, wait", 22.sp, color = Paper.copy(alpha = .7f))
                }
            }
        }
    }
}

private fun viaLine(a: ModeAction): String {
    val app = HearthApp.get()
    return when (a.type) {
        "ha" -> "via Home Assistant · ${a.ha?.service ?: ""} ${a.ha?.entityId?.ifBlank { "(room lights)" } ?: ""}"
        "app" -> "via TV · ${app.apps.labelFor(a.value)}"
        "input" -> "via TV · HDMI input"
        "console" -> "via Home Assistant + CEC · ${app.actions.console(a.value)?.name ?: a.value}"
        "volume" -> "via TV · level ${a.value}"
        "profile" -> "via Hearth · ${a.value}"
        "wait" -> "${a.value} seconds"
        "silence" -> "via Hearth · pop-ups ${if (a.value == "off") "back on" else "silenced"}"
        "section" -> "via Hearth · open ${a.value}"
        else -> a.type
    }
}

private fun addActionMenu(add: (ModeAction) -> Unit): TileMenu {
    val app = HearthApp.get()
    val cfg = app.config.value
    val items = mutableListOf(
        MenuItem("Dim lights to 20%") { add(ModeAction("ha", "Dim lights", HaCall("light.turn_on", "", kotlinx.serialization.json.buildJsonObject { put("brightness_pct", kotlinx.serialization.json.JsonPrimitive(20)) }))) },
        MenuItem("Lights to 60%") { add(ModeAction("ha", "Lights to 60%", HaCall("light.turn_on", "", kotlinx.serialization.json.buildJsonObject { put("brightness_pct", kotlinx.serialization.json.JsonPrimitive(60)) }))) },
        MenuItem("Lights off") { add(ModeAction("ha", "Lights off", HaCall("light.turn_off", ""))) },
    )
    cfg.home.scenes.forEach { s -> items += MenuItem("Scene: ${app.ha[s]?.name ?: s}") { add(ModeAction("ha", app.ha[s]?.name ?: s, HaCall(if (s.startsWith("script.")) "script.turn_on" else "scene.turn_on", s))) } }
    cfg.consoles.forEach { c -> items += MenuItem("Wake ${c.name} + switch input") { add(ModeAction("console", "Wake ${c.name}", value = c.id)) } }
    app.inputs.list().forEach { i -> items += MenuItem("Switch to ${i.label}") { add(ModeAction("input", "Switch to ${i.label}", value = i.id)) } }
    listOf(10, 20, 30, 40).forEach { v -> items += MenuItem("Volume $v") { add(ModeAction("volume", "Volume $v", value = "$v")) } }
    items += MenuItem("Switch to Kids profile") { add(ModeAction("profile", "Kid profile", value = "kids")) }
    items += MenuItem("Silence pop-ups except doorbell") { add(ModeAction("silence", "Silence pop-ups", value = "on")) }
    items += MenuItem("Open Sports") { add(ModeAction("section", "Open My Teams", value = "sports")) }
    listOf(2, 5, 10).forEach { s -> items += MenuItem("Wait $s seconds") { add(ModeAction("wait", "Wait", value = "$s")) } }
    app.apps.apps.value.forEach { a -> items += MenuItem("Open ${a.label}") { add(ModeAction("app", "Open ${a.label}", value = a.pkg)) } }
    return TileMenu("Add action", items)
}

@Composable
private fun ConsolesPage(ui: TvUi) {
    val app = HearthApp.get()
    val cfg by app.config.collectAsStateWithLifecycle()
    val inputs = remember { app.inputs.list() }
    Column {
        Header("Settings", "Consoles & inputs")
        Spacer(Modifier.height(30.dp))
        Column(Modifier.fillMaxWidth().glass(22.dp).padding(18.dp)) {
            for (c in cfg.consoles) {
                SettingRow(c.name, c.inputLabel.ifBlank { "Choose input" },
                    "Wake: ${c.wake.service.ifBlank { "not set" }} · Status: ${c.stateEntity.ifBlank { "not set" }}") {
                    ui.picker = TileMenu("${c.name} is on", listOf(MenuItem("No input") { setInput(c.id, "", "") }) +
                        inputs.map { i -> MenuItem(i.label) { setInput(c.id, i.id, i.label) } })
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        T("Wake actions, status entities and game tiles are set from a browser (http://${Network.ipAddress()}:8765/#settings), since they're Home Assistant entity IDs.",
            20.sp, color = Paper.copy(alpha = .55f), maxLines = 2)
    }
}

private fun setInput(id: String, inputId: String, label: String) = HearthApp.get().configStore.update { c ->
    c.copy(consoles = c.consoles.map { if (it.id == id) it.copy(inputId = inputId, inputLabel = label) else it })
}

@Composable
private fun HomeAssistantPage(ui: TvUi) {
    val app = HearthApp.get()
    val cfg by app.config.collectAsStateWithLifecycle()
    val status by app.ha.status.collectAsStateWithLifecycle()
    val ents by app.ha.entities.collectAsStateWithLifecycle()
    Column {
        Header("Settings", "Home Assistant")
        Spacer(Modifier.height(30.dp))
        Column(Modifier.fillMaxWidth().glass(22.dp).padding(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (status == "connected") Hearth.Live else Hearth.Amber, 12.dp); Spacer(Modifier.width(12.dp))
                T(status.replace('_', ' ').replaceFirstChar { it.uppercase() } + if (status == "connected") " · ${ents.size} entities" else "", 26.sp, FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))
            T(cfg.home.url.ifBlank { "No URL set" }, 22.sp, color = Paper.copy(alpha = .7f))
            Spacer(Modifier.height(18.dp))
            T("Set the URL, long-lived access token (HA › Profile › Security › Long-lived tokens) and which lights, thermostat, camera, lock, speaker, doorbell and scenes to show from a browser:",
                20.sp, color = Paper.copy(alpha = .6f), maxLines = 3)
            T("http://${Network.ipAddress()}:8765/#settings", 32.sp, family = Hearth.Newsreader)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill("Test doorbell pop-up", onClick = { app.doorbell.ring() })
            }
        }
    }
}

@Composable
private fun UpdatesPage(ui: TvUi) {
    val app = HearthApp.get()
    val s by app.updater.status.collectAsStateWithLifecycle()
    val cfg by app.config.collectAsStateWithLifecycle()
    Column {
        Header("Settings", "Updates")
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).glass(22.dp).padding(28.dp)) {
                T("This TV: Hearth ${BuildConfig.VERSION_NAME}", 28.sp, FontWeight.Medium)
                T(when (s.status) {
                    "checking" -> "Checking GitHub…"
                    "available" -> "Hearth ${s.latest} is available"
                    "up_to_date" -> "Up to date"
                    "downloading" -> "Downloading… ${s.progress}%"
                    "installing" -> "Installing…"
                    "waiting_for_user" -> "Confirm the update on screen"
                    "error" -> "Couldn't update: ${s.error}"
                    else -> "Not checked yet"
                }, 22.sp, color = if (s.status == "error") Hearth.Destructive else Paper.copy(alpha = .7f), maxLines = 2)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pill("Check now", onClick = { app.updater.check() })
                    if (s.status == "available") Pill("Install ${s.latest}", selected = true, onClick = { app.updater.install() })
                }
                Spacer(Modifier.height(20.dp))
                ToggleRow("Install automatically overnight", "Needs the one-step ADB setup; otherwise you'll be asked", cfg.autoUpdate) {
                    app.configStore.update { it.copy(autoUpdate = !it.autoUpdate) }
                }
                T("Updates install over this version: your settings, pairing and teams are kept. You can also start an update from the phone app.",
                    18.sp, color = Paper.copy(alpha = .5f), maxLines = 3)
            }
            Column(Modifier.weight(1f).glass(22.dp).padding(28.dp).verticalScroll(rememberScrollState())) {
                T("Release notes", 26.sp, FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                T(s.notes.ifBlank { "—" }.replace("**", "").replace("`", ""), 20.sp, color = Paper.copy(alpha = .8f), maxLines = 40)
            }
        }
    }
}
