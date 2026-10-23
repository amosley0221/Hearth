package com.amosley.hearth.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.amosley.hearth.shared.protocol.Game
import com.amosley.hearth.shared.theme.Hearth
import com.amosley.hearth.tv.HearthApp
import com.amosley.hearth.tv.data.ContinueItem
import com.amosley.hearth.tv.data.GameTileConfig
import com.amosley.hearth.tv.state.StateHub
import com.amosley.hearth.tv.ui.AppArt
import com.amosley.hearth.tv.ui.Glyph
import com.amosley.hearth.tv.ui.Hatch
import com.amosley.hearth.tv.ui.MenuItem
import com.amosley.hearth.tv.ui.Paper
import com.amosley.hearth.tv.ui.Pivot
import com.amosley.hearth.tv.ui.ProgressBar
import com.amosley.hearth.tv.ui.Roundel
import com.amosley.hearth.tv.ui.RowTitle
import com.amosley.hearth.tv.ui.StatusDot
import com.amosley.hearth.tv.ui.T
import com.amosley.hearth.tv.ui.TileMenu
import com.amosley.hearth.tv.ui.TvUi
import com.amosley.hearth.tv.ui.glass
import com.amosley.hearth.tv.ui.rememberNow
import com.amosley.hearth.tv.ui.tvFocus
import java.time.format.DateTimeFormatter

@Composable
fun HomeSection(ui: TvUi) {
    val app = HearthApp.get()
    val cw by app.continueWatching.items.collectAsStateWithLifecycle()
    val sports by app.sports.data.collectAsStateWithLifecycle()
    val cfg by app.config.collectAsStateWithLifecycle()
    val first = ui.contentRequester
    val scroll = rememberScrollState()

    LaunchedEffect(ui.homeSerial) {
        scroll.scrollTo(0)
        // Tiles in lazy rows attach a frame or two after first composition.
        repeat(20) { try { first.requestFocus(); return@LaunchedEffect } catch (_: Exception) { kotlinx.coroutines.delay(100) } }
    }

    Box(Modifier.fillMaxSize()) {
        Pivot(120f) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 96.dp)) {
                val hero = if (cfg.sports.heroTakeover) sports.live.firstOrNull { it.favorite } else null
                if (hero != null) HeroGame(ui, hero) else ClockBlock()
                Spacer(Modifier.height(36.dp))

                if (cw.isNotEmpty()) {
                    RowTitle("Continue Watching")
                    Spacer(Modifier.height(18.dp))
                    ContinueRow(ui, cw, first)
                    Spacer(Modifier.height(24.dp))
                }
                if (sports.live.isNotEmpty()) {
                    SportsStrip(ui, sports.live.take(3))
                    Spacer(Modifier.height(24.dp))
                }
                GamesStrip(ui, cfg.games)
                Spacer(Modifier.height(24.dp))
                val modes = cfg.modes.filter { it.showOnHome }
                if (modes.isNotEmpty()) {
                    ModesStrip(ui, modes)
                }
                Spacer(Modifier.height(230.dp))
            }
        }
        Dock(ui, Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp), if (cw.isEmpty()) first else null)
    }
}

@Composable
private fun ClockBlock() {
    val now = rememberNow()
    val app = HearthApp.get()
    val ents by app.ha.entities.collectAsStateWithLifecycle()
    val weather = remember(ents) { app.state.weatherText() }
    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            T(now.format(DateTimeFormatter.ofPattern("h:mm")), 128.sp, FontWeight.Light, family = Hearth.Newsreader, shadow = true)
            Spacer(Modifier.width(22.dp))
            T(now.format(DateTimeFormatter.ofPattern("a")), 96.sp, FontWeight.Light, family = Hearth.Newsreader, shadow = true,
                modifier = Modifier.padding(bottom = 6.dp))
        }
        T(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), 30.sp, FontWeight.Light, color = Paper.copy(alpha = .9f), shadow = true)
        if (weather != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph("moon", 26.dp, Paper); Spacer(Modifier.width(12.dp))
                T(weather, 26.sp, color = Paper.copy(alpha = .9f), shadow = true)
            }
        }
    }
}

@Composable
fun ContinueRow(ui: TvUi, items: List<ContinueItem>, first: FocusRequester?, wide: Boolean = false) {
    val app = HearthApp.get()
    val w = if (wide) 392.dp else 340.dp
    val h = if (wide) 200.dp else 190.dp
    Pivot(24f) {
        LazyRow(
            Modifier.offset(x = (-24).dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            items(items, key = { it.key }) { item ->
                val idx = items.indexOf(item)
                val shape = RoundedCornerShape(16.dp)
                Box(
                    Modifier.size(w, h)
                        .tvFocus(
                            shape,
                            onClick = { app.continueWatching.open(item) { app.apps.launch(it) } },
                            onLongClick = { ui.tileMenu = continueMenu(ui, item) },
                            onFocus = { ui.hue = Hearth.hueFor(item.title); ui.art = item.art },
                            focusRequester = if (idx == 0) first else null,
                        )
                        .clip(shape)
                ) {
                    Hatch(Hearth.hueFor(item.title), Modifier.fillMaxSize(), label = if (item.art == null) "key art" else null)
                    if (item.art != null) AsyncImage(item.art, item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(
                        if (wide) Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .75f), Color.Transparent))
                        else Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = .75f))))
                    Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                        T(item.title.uppercase(), if (wide) 30.sp else 22.sp, FontWeight.SemiBold, letterSpacing = 0.02.em)
                        if (item.subtitle.isNotBlank()) T(item.subtitle, 15.sp, color = Paper.copy(alpha = .7f))
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(item.progress, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

private fun continueMenu(ui: TvUi, item: ContinueItem): TileMenu {
    val app = HearthApp.get()
    return TileMenu(item.title, listOf(
        MenuItem("Resume") { app.continueWatching.open(item) { app.apps.launch(it) } },
        MenuItem("Open ${app.apps.labelFor(item.pkg)}") { app.apps.launch(item.pkg) },
        MenuItem("Pin ${app.apps.labelFor(item.pkg)} to dock") { Favorites.pin(item.pkg); ui.toast("Pinned to dock") },
        MenuItem("Mark as watched") { app.media.remove(item.key); app.configStore.update { it.copy(hiddenContinue = it.hiddenContinue + item.key) } },
        MenuItem("Hide from Continue Watching", destructive = true) {
            app.configStore.update { it.copy(hiddenContinue = it.hiddenContinue + item.key) }
        },
    ))
}

@Composable
private fun SportsStrip(ui: TvUi, live: List<Game>) {
    Row(
        Modifier.fillMaxWidth().height(96.dp).glass(20.dp, .6f).padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(170.dp)) {
            T("Sports", 26.sp, FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("Live now", 18.sp, color = Paper.copy(alpha = .6f)); Spacer(Modifier.width(8.dp)); StatusDot(Hearth.Live, 9.dp)
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (g in live) {
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier.tvFocus(shape, onClick = { openGame(ui, g) }, onFocus = { ui.hue = 215f; ui.art = null }, shadow = false)
                        .background(Color.White.copy(alpha = .04f), shape).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Roundel(g.league, 34.dp); Spacer(Modifier.width(18.dp))
                    Column {
                        T("${g.away.abbr} ${g.away.score}  ·  ${g.home.abbr} ${g.home.score}", 22.sp, FontWeight.Medium)
                        T(g.detail, 16.sp, color = Hearth.Live)
                    }
                }
            }
        }
        val shape = RoundedCornerShape(14.dp)
        Row(Modifier.tvFocus(shape, onClick = { ui.section = "sports"; ui.sportsTab = "live" }, shadow = false).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            T("View all scores", 20.sp); Spacer(Modifier.width(8.dp)); Glyph("chevron", 18.dp, Paper)
        }
    }
}

fun openGame(ui: TvUi, g: Game) {
    val app = HearthApp.get()
    val cfg = app.config.value.sports
    val leagueKey = com.amosley.hearth.tv.sports.SportsRepository.LEAGUES.values.firstOrNull { it.abbr == g.league }?.key
    val installed = app.apps.apps.value.map { it.pkg }.toSet()
    val net = g.network.lowercase()
    val pkg = cfg.apps[leagueKey]?.takeIf { it in installed }
        ?: com.amosley.hearth.tv.sports.SportsRepository.NETWORK_APPS.entries.firstOrNull { net.contains(it.key) }?.value?.firstOrNull { it in installed }
        ?: app.apps.apps.value.firstOrNull { net.isNotBlank() && it.label.lowercase().contains(net.split(" ").first()) }?.pkg
        ?: "com.google.android.youtube.tvunplugged".takeIf { it in installed }
    if (pkg == null) { ui.toast("Set the app for ${g.leagueName} in My Teams"); return }
    ui.toast("Opens ${app.apps.labelFor(pkg)}")
    app.apps.launch(pkg)
}

@Composable
private fun GamesStrip(ui: TvUi, games: List<GameTileConfig>) {
    val app = HearthApp.get()
    val cfg by app.config.collectAsStateWithLifecycle()
    val ents by app.ha.entities.collectAsStateWithLifecycle()
    val status = remember(cfg, ents) {
        cfg.consoles.filter { it.stateEntity.isNotBlank() }.joinToString(" · ") {
            "${it.name} ${app.actions.consoleStatus(it).let { s -> if (s == "unknown") "" else s }}".trim()
        }.ifBlank { cfg.consoles.joinToString(" · ") { it.name } }
    }
    Row(
        Modifier.fillMaxWidth().height(170.dp).glass(20.dp, .6f).padding(horizontal = 28.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(322.dp)) {
            T("Games", 26.sp, FontWeight.Medium)
            T(status, 18.sp, color = Paper.copy(alpha = .6f))
            Spacer(Modifier.height(14.dp))
            val shape = RoundedCornerShape(999.dp)
            Row(Modifier.tvFocus(shape, onClick = { ui.section = "games" }, shadow = false)
                .background(Color.White.copy(alpha = .1f), shape).padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                T("Open Games", 20.sp); Spacer(Modifier.width(6.dp)); Glyph("chevron", 16.dp, Paper)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            val tiles = games.take(4)
            if (tiles.isEmpty()) {
                for (c in cfg.consoles.take(3)) GameTile(c.name, consoleHint(c.kind, c.name, null), Hearth.hueFor(c.name), null, 300.dp, 130.dp,
                    onClick = { app.actions.wakeConsole(c.id, null, false) }, ui = ui)
            }
            for (g in tiles) {
                val c = app.actions.console(g.consoleId)
                GameTile(g.name, consoleHint(c?.kind ?: "", c?.name ?: "", g), g.hue, g.artUrl.ifBlank { null }, 300.dp, 130.dp,
                    onClick = { app.actions.playGame(g) }, ui = ui)
            }
        }
    }
}

fun consoleHint(kind: String, name: String, g: GameTileConfig?): String = when {
    g != null && g.launch.valid -> "Launch on $name"
    kind == "ps5" -> "Opens PS5"
    kind == "switch" -> "Switch · input"
    g == null -> "Wake and switch input"
    else -> "Opens $name"
}

@Composable
fun GameTile(title: String, hint: String, hue: Float, art: String?, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp,
             onClick: () -> Unit, ui: TvUi, onLongClick: (() -> Unit)? = null, titleSize: Int = 22) {
    val shape = RoundedCornerShape(14.dp)
    Box(Modifier.size(w, h).tvFocus(shape, onClick, onLongClick = onLongClick, onFocus = { ui.hue = hue; ui.art = art }).clip(shape)) {
        Hatch(hue, Modifier.fillMaxSize())
        if (art != null) AsyncImage(art, title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = .75f))))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            T(title.uppercase(), titleSize.sp, FontWeight.SemiBold, letterSpacing = 0.02.em)
            T(hint, 15.sp, color = Paper.copy(alpha = .75f))
        }
    }
}

@Composable
private fun ModesStrip(ui: TvUi, modes: List<com.amosley.hearth.tv.data.ModeConfig>) {
    val app = HearthApp.get()
    val active by app.actions.activeMode.collectAsStateWithLifecycle()
    Column {
        RowTitle("Modes")
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            for (m in modes) {
                val on = active == m.id
                val shape = RoundedCornerShape(20.dp)
                Row(
                    Modifier.size(260.dp, 92.dp)
                        .tvFocus(shape, onClick = { app.actions.runMode(m.id) },
                            onLongClick = { ui.section = "settings"; ui.settingsPage = "modes"; ui.editingModeId = m.id })
                        .then(if (on) Modifier.background(Paper, shape) else Modifier.glass(20.dp, .6f))
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(m.icon, 28.dp, if (on) Hearth.Ink else Paper)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        T(m.name, 24.sp, FontWeight.Medium, color = if (on) Hearth.Ink else Paper)
                        T(if (on) "On · press to end" else "${m.actions.size} actions", 16.sp, color = (if (on) Hearth.Ink else Paper).copy(alpha = .6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Dock(ui: TvUi, modifier: Modifier, first: FocusRequester?) {
    val app = HearthApp.get()
    val cfg by app.config.collectAsStateWithLifecycle()
    val installed by app.apps.apps.collectAsStateWithLifecycle()
    val favs = remember(cfg.favorites, installed) {
        val list = cfg.favorites.ifEmpty { StateHub.DEFAULT_FAVORITES }
        list.mapNotNull { p -> installed.firstOrNull { it.pkg == p } }.ifEmpty { installed.take(7) }.take(9)
    }
    var focusedLabel by remember { mutableStateOf<String?>(null) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        T(focusedLabel ?: " ", 20.sp, color = Paper.copy(alpha = .9f))
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.shadow(40.dp, RoundedCornerShape(30.dp)).glass(30.dp, .7f).padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            favs.forEachIndexed { i, a ->
                val shape = RoundedCornerShape(24.dp)
                Box(
                    Modifier.size(100.dp)
                        .tvFocus(shape,
                            onClick = { app.apps.launch(a.pkg) },
                            onLongClick = { ui.tileMenu = dockMenu(ui, a.pkg, a.label) },
                            onFocus = { focusedLabel = a.label; ui.hue = Hearth.hueFor(a.label); ui.art = null },
                            focusRequester = if (i == 0) first else null)
                        .clip(shape)
                ) { AppArt(a.pkg, a.label, Modifier.fillMaxSize(), wordmarkSize = 18.sp) }
            }
        }
    }
}

fun dockMenu(ui: TvUi, pkg: String, label: String): TileMenu {
    val app = HearthApp.get()
    val pinned = pkg in app.config.value.favorites.ifEmpty { StateHub.DEFAULT_FAVORITES }
    return TileMenu(label, listOfNotNull(
        MenuItem("Open") { app.apps.launch(pkg) },
        if (pinned) MenuItem("Move ‹ left") { Favorites.move(pkg, -1) } else null,
        if (pinned) MenuItem("Move right ›") { Favorites.move(pkg, 1) } else null,
        if (pinned) MenuItem("Unpin from dock") { Favorites.unpin(pkg) } else MenuItem("Pin to dock") { Favorites.pin(pkg); ui.toast("Pinned to dock") },
        if (pkg in app.config.value.kids.approvedApps) MenuItem("Remove from Kids") { Favorites.kids(pkg, false) }
        else MenuItem("Allow for Kids") { Favorites.kids(pkg, true); ui.toast("$label approved for Kids") },
        MenuItem("Hide app", destructive = true) { app.configStore.update { it.copy(hiddenApps = it.hiddenApps + pkg, favorites = it.favorites - pkg) } },
    ))
}

/** Dock / favourites editing. An empty favourites list means "the defaults"; the first edit materialises it. */
object Favorites {
    private fun current(): MutableList<String> {
        val app = HearthApp.get()
        val installed = app.apps.apps.value.map { it.pkg }.toSet()
        return app.config.value.favorites.ifEmpty { StateHub.DEFAULT_FAVORITES.filter { it in installed } }.toMutableList()
    }
    fun pin(pkg: String) { val l = current(); if (pkg !in l) l += pkg; save(l) }
    fun unpin(pkg: String) { val l = current(); l -= pkg; save(l) }
    fun move(pkg: String, by: Int) {
        val l = current(); val i = l.indexOf(pkg); if (i < 0) return
        val j = (i + by).coerceIn(0, l.size - 1); l.removeAt(i); l.add(j, pkg); save(l)
    }
    fun kids(pkg: String, allow: Boolean) {
        HearthApp.get().configStore.update { c ->
            c.copy(kids = c.kids.copy(approvedApps = if (allow) (c.kids.approvedApps + pkg).distinct() else c.kids.approvedApps - pkg))
        }
    }
    private fun save(l: List<String>) = HearthApp.get().configStore.update { it.copy(favorites = l) }
}

/** Hero takeover: a favourite team's live game replaces the clock at the top of Home. */
@Composable
private fun HeroGame(ui: TvUi, g: Game) {
    val now = rememberNow()
    Column(Modifier.fillMaxWidth().padding(top = 60.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(now.format(DateTimeFormatter.ofPattern("h:mm a")), 26.sp, FontWeight.Light, color = Paper.copy(alpha = .8f))
            Spacer(Modifier.width(18.dp))
            com.amosley.hearth.tv.ui.LiveBadge()
            Spacer(Modifier.width(14.dp))
            T("${g.leagueName} · ${g.detail}", 24.sp, color = Paper.copy(alpha = .8f))
        }
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroTeam(g.away)
            Spacer(Modifier.width(36.dp))
            T(g.away.score, 120.sp, FontWeight.Light, family = Hearth.Newsreader, shadow = true)
            T("  –  ", 60.sp, FontWeight.Light, color = Paper.copy(alpha = .5f), family = Hearth.Newsreader)
            T(g.home.score, 120.sp, FontWeight.Light, family = Hearth.Newsreader, shadow = true)
            Spacer(Modifier.width(36.dp))
            HeroTeam(g.home)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (g.situation.isNotBlank()) T(g.situation, 26.sp, color = Paper.copy(alpha = .85f))
            if (g.highlight.isNotBlank()) { T("  •  ", 26.sp, color = Paper.copy(alpha = .5f)); T(g.highlight, 26.sp, color = Hearth.Amber) }
            Spacer(Modifier.width(28.dp))
            val shape = RoundedCornerShape(999.dp)
            Row(Modifier.tvFocus(shape, onClick = { openGame(ui, g) }, onFocus = { ui.hue = 215f; ui.art = null }, shadow = false)
                .background(Paper, shape).padding(horizontal = 26.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph("play", 20.dp, Hearth.Ink); Spacer(Modifier.width(10.dp))
                T(if (g.network.isNotBlank()) "Watch on ${g.network}" else "Watch", 22.sp, FontWeight.Medium, color = Hearth.Ink)
            }
        }
    }
}

@Composable
private fun HeroTeam(t: com.amosley.hearth.shared.protocol.TeamScore) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(150.dp)) {
        Box(Modifier.size(96.dp).background(com.amosley.hearth.tv.ui.parseHex(t.color), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
            if (t.logo != null) AsyncImage(t.logo, t.abbr, Modifier.size(70.dp)) else T(t.abbr, 28.sp, FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        T(t.name, 22.sp, maxLines = 1)
    }
}
