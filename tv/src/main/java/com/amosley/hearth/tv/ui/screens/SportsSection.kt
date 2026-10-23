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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.amosley.hearth.shared.protocol.Game
import com.amosley.hearth.shared.theme.Hearth
import com.amosley.hearth.tv.HearthApp
import com.amosley.hearth.tv.data.TeamRef
import com.amosley.hearth.tv.sports.SportsRepository
import com.amosley.hearth.tv.ui.Glyph
import com.amosley.hearth.tv.ui.LiveBadge
import com.amosley.hearth.tv.ui.MenuItem
import com.amosley.hearth.tv.ui.Paper
import com.amosley.hearth.tv.ui.Pill
import com.amosley.hearth.tv.ui.Pivot
import com.amosley.hearth.tv.ui.Roundel
import com.amosley.hearth.tv.ui.RowTitle
import com.amosley.hearth.tv.ui.T
import com.amosley.hearth.tv.ui.TeamDot
import com.amosley.hearth.tv.ui.TileMenu
import com.amosley.hearth.tv.ui.Toggle
import com.amosley.hearth.tv.ui.TvUi
import com.amosley.hearth.tv.ui.glass
import com.amosley.hearth.tv.ui.parseHex
import com.amosley.hearth.tv.ui.tvFocus
import kotlinx.coroutines.launch

@Composable
fun SportsSection(ui: TvUi) {
    val app = HearthApp.get()
    DisposableEffect(Unit) {
        ui.hue = 215f; ui.art = null
        app.scope.launch { app.sports.refreshNow() }
        onDispose {}
    }
    Column(Modifier.fillMaxSize().padding(top = 120.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("Sports", 52.sp, FontWeight.Medium, letterSpacing = (-0.01).em)
            Spacer(Modifier.width(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((id, label) in listOf("live" to "Live", "teams" to "My Teams", "upcoming" to "Upcoming", "scores" to "Scores")) {
                    Pill(label, selected = ui.sportsTab == id, onClick = { ui.sportsTab = id })
                }
            }
        }
        Spacer(Modifier.height(36.dp))
        when (ui.sportsTab) {
            "teams" -> MyTeamsSetup(ui)
            "upcoming" -> ScheduleList(ui, upcoming = true)
            "scores" -> ScoresGrid(ui)
            else -> LiveTab(ui)
        }
    }
}

@Composable
private fun LiveTab(ui: TvUi) {
    val app = HearthApp.get()
    val data by app.sports.data.collectAsStateWithLifecycle()
    Pivot(120f) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(end = 96.dp, bottom = 80.dp)) {
            if (data.paused) { T("Scores paused · showing last known", 20.sp, color = Hearth.Amber); Spacer(Modifier.height(12.dp)) }
            // Empty-state rule: favourites live → other live games in followed leagues → Upcoming leads.
            if (data.live.isNotEmpty()) {
                RowTitle("Live Games", 30.sp) { LiveBadge() }
                Spacer(Modifier.height(10.dp))
                Pivot(24f) {
                    LazyRow(Modifier.offset(x = (-24).dp).fillMaxWidth(), contentPadding = PaddingValues(24.dp, 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        items(data.live, key = { it.id }) { g -> LiveCard(ui, g) }
                    }
                }
                Spacer(Modifier.height(18.dp))
            } else if (data.loaded && data.upcoming.isEmpty()) {
                T("No live games right now.", 30.sp, FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                T("Follow teams in My Teams to see their schedules here.", 22.sp, color = Paper.copy(alpha = .6f))
                Spacer(Modifier.height(24.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                GamesPanel(ui, "Upcoming · My Teams", data.upcoming.take(3), Modifier.weight(1f))
                GamesPanel(ui, "Recent · My Teams", data.recent.take(3), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LiveCard(ui: TvUi, g: Game) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.size(528.dp, 320.dp)
            .tvFocus(shape, onClick = { openGame(ui, g) }, onLongClick = { ui.tileMenu = gameMenu(ui, g) })
            .background(Brush.verticalGradient(listOf(Color(20, 24, 34).copy(alpha = .85f), Color(12, 14, 20).copy(alpha = .92f))), shape)
            .border(1.dp, Hearth.GlassBorder, shape)
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Roundel(g.league, 34.dp); Spacer(Modifier.width(12.dp))
            T(g.leagueName, 20.sp, FontWeight.Medium, color = Paper.copy(alpha = .9f))
            T("  •  ${g.detail}", 20.sp, color = Paper.copy(alpha = .75f))
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TeamColumn(g.away.abbr, g.away.name, g.away.color, g.away.logo)
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(g.away.score, 64.sp, FontWeight.SemiBold, letterSpacing = (-0.02).em)
                T("  vs  ", 22.sp, color = Paper.copy(alpha = .5f))
                T(g.home.score, 64.sp, FontWeight.SemiBold, letterSpacing = (-0.02).em)
            }
            TeamColumn(g.home.abbr, g.home.name, g.home.color, g.home.logo)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .1f)))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            T(g.situation.ifBlank { g.network }, 19.sp, color = Paper.copy(alpha = .8f))
            if (g.highlight.isNotBlank()) { T("  •  ", 19.sp, color = Paper.copy(alpha = .5f)); T(g.highlight, 19.sp, color = Hearth.Amber) }
        }
    }
}

@Composable
private fun TeamColumn(abbr: String, name: String, color: String, logo: String?) {
    Column(Modifier.width(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).background(parseHex(color), CircleShape), contentAlignment = Alignment.Center) {
            if (logo != null) AsyncImage(logo, abbr, Modifier.size(54.dp)) else T(abbr, 22.sp, FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        T(name, 20.sp, align = TextAlign.Center, maxLines = 2)
    }
}

private fun gameMenu(ui: TvUi, g: Game): TileMenu = TileMenu("${g.away.abbr} vs ${g.home.abbr}", listOf(
    MenuItem("Watch") { openGame(ui, g) },
    MenuItem("Choose app for ${g.leagueName}") { ui.sportsTab = "teams" },
))

@Composable
private fun GamesPanel(ui: TvUi, title: String, games: List<Game>, modifier: Modifier) {
    Column(modifier.glass(22.dp).padding(horizontal = 26.dp, vertical = 22.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T(title, 30.sp, FontWeight.Medium, modifier = Modifier.weight(1f))
            Pill("View all", size = 20.sp, hPad = 14.dp, vPad = 6.dp, onClick = { ui.sportsTab = "upcoming" })
        }
        Spacer(Modifier.height(10.dp))
        if (games.isEmpty()) T("Nothing scheduled", 20.sp, color = Paper.copy(alpha = .5f), modifier = Modifier.padding(14.dp))
        for (g in games) GameListRow(ui, g)
    }
}

@Composable
fun GameListRow(ui: TvUi, g: Game) {
    val shape = RoundedCornerShape(14.dp)
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .06f)))
        Row(
            Modifier.fillMaxWidth().tvFocus(shape, onClick = { openGame(ui, g) }, scale = 1.02f, shadow = false)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Roundel(g.league, 38.dp); Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Row { T(g.leagueName, 18.sp, FontWeight.Medium); T("  •  ${g.detail}", 18.sp, color = Paper.copy(alpha = .65f)) }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamDot(g.away.abbr, g.away.color, 28.dp, showText = false); Spacer(Modifier.width(10.dp))
                    T(if (g.state == "post") "${g.away.abbr} ${g.away.score}" else g.away.name.split(" ").last(), 22.sp, modifier = Modifier.width(170.dp))
                    T("vs", 18.sp, color = Paper.copy(alpha = .5f), modifier = Modifier.width(48.dp))
                    TeamDot(g.home.abbr, g.home.color, 28.dp, showText = false); Spacer(Modifier.width(10.dp))
                    T(if (g.state == "post") "${g.home.abbr} ${g.home.score}" else g.home.name.split(" ").last(), 22.sp)
                }
            }
            if (g.state == "post") {
                Box(Modifier.background(if (g.highlight == "loss") Hearth.Loss else Hearth.Win, RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)) { T(g.situation, 17.sp, FontWeight.Medium) }
            } else T(g.network, 20.sp, color = Paper.copy(alpha = .65f))
        }
    }
}

@Composable
private fun ScheduleList(ui: TvUi, upcoming: Boolean) {
    val app = HearthApp.get()
    val data by app.sports.data.collectAsStateWithLifecycle()
    Pivot(120f) {
        Row(Modifier.fillMaxSize().padding(end = 96.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).glass(22.dp).padding(26.dp)) {
                T("Upcoming · My Teams", 30.sp, FontWeight.Medium)
                if (data.upcoming.isEmpty()) T("Follow teams in My Teams", 20.sp, color = Paper.copy(alpha = .5f))
                data.upcoming.forEach { GameListRow(ui, it) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).glass(22.dp).padding(26.dp)) {
                T("Recent · My Teams", 30.sp, FontWeight.Medium)
                if (data.recent.isEmpty()) T("No recent results", 20.sp, color = Paper.copy(alpha = .5f))
                data.recent.forEach { GameListRow(ui, it) }
            }
        }
    }
}

@Composable
private fun ScoresGrid(ui: TvUi) {
    val app = HearthApp.get()
    val data by app.sports.data.collectAsStateWithLifecycle()
    Pivot(120f) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(end = 96.dp, bottom = 60.dp)) {
            if (data.today.isEmpty()) T("No games today in your leagues.", 26.sp, color = Paper.copy(alpha = .6f))
            for (pair in data.today.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    for (g in pair) Box(Modifier.weight(1f).glass(18.dp).padding(horizontal = 10.dp)) { GameListRow(ui, g) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

/** 4g · My Teams setup: leagues, team grid, app per league, hero/phone toggles. */
@Composable
fun MyTeamsSetup(ui: TvUi) {
    val app = HearthApp.get()
    val cfg by app.config.collectAsStateWithLifecycle()
    val sports = cfg.sports
    var league by remember { mutableStateOf(sports.leagues.firstOrNull() ?: "nfl") }
    var teams by remember { mutableStateOf<List<TeamRef>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(league) { teams = app.sports.teams(league) }
    DisposableEffect(Unit) {
        ui.textTarget = { t, mode ->
            query = when (mode) { "set" -> t; "append" -> query + t; "backspace" -> query.dropLast(1); "clear" -> ""; else -> query }
        }
        onDispose { ui.textTarget = null }
    }
    val followed = sports.teams.map { "${it.league}:${it.id}" }.toSet()
    Row(Modifier.fillMaxSize().padding(end = 96.dp, bottom = 54.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1.6f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (l in SportsRepository.LEAGUES.values) {
                    val on = l.key in sports.leagues
                    Pill(l.abbr, selected = on, size = 20.sp, hPad = 18.dp, vPad = 8.dp,
                        onFocus = { league = l.key },
                        onClick = {
                            app.configStore.update { c ->
                                val ls = if (on) c.sports.leagues - l.key else c.sports.leagues + l.key
                                c.copy(sports = c.sports.copy(leagues = ls))
                            }
                        })
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(60.dp).border(2.dp, Color.White.copy(alpha = .2f), RoundedCornerShape(16.dp)).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart) {
                T(if (query.isBlank()) "Search ${SportsRepository.LEAGUES[league]?.name ?: ""} teams · type on the phone remote" else query,
                    22.sp, color = Paper.copy(alpha = if (query.isBlank()) .5f else 1f))
            }
            Spacer(Modifier.height(18.dp))
            val shown = teams.filter { query.isBlank() || it.name.contains(query, true) || it.abbr.contains(query, true) }
            Pivot(80f) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for (row in shown.chunked(4)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            for (t in row) {
                                val on = "${t.league}:${t.id}" in followed
                                val shape = RoundedCornerShape(16.dp)
                                Row(
                                    Modifier.weight(1f).height(84.dp)
                                        .tvFocus(shape, onClick = {
                                            app.configStore.update { c ->
                                                val list = if (on) c.sports.teams.filterNot { it.league == t.league && it.id == t.id } else c.sports.teams + t
                                                c.copy(sports = c.sports.copy(teams = list))
                                            }
                                            app.scope.launch { app.sports.refreshNow() }
                                        }, scale = 1.04f, shadow = false)
                                        .glass(16.dp, .5f).padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TeamDot(t.abbr, t.color, 36.dp, showText = false)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        T(t.name, 22.sp)
                                        T(SportsRepository.LEAGUES[t.league]?.abbr ?: "", 16.sp, color = Paper.copy(alpha = .55f))
                                    }
                                    Box(Modifier.size(28.dp).then(if (on) Modifier.background(Hearth.Live, CircleShape)
                                        else Modifier.border(2.dp, Paper.copy(alpha = .3f), CircleShape)), contentAlignment = Alignment.Center) {
                                        if (on) Glyph("check", 18.dp, Hearth.Ink)
                                    }
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
        }
        Column(Modifier.weight(1f).glass(22.dp).padding(26.dp)) {
            T("Watch games on", 26.sp, FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            for (lk in sports.leagues) {
                val l = SportsRepository.LEAGUES[lk] ?: continue
                val pkg = sports.apps[lk]
                SettingRow(l.name, pkg?.let { app.apps.labelFor(it) } ?: "Auto (by network)") {
                    ui.picker = TileMenu("Watch ${l.name} on", listOf(MenuItem("Auto (by network)") {
                        app.configStore.update { c -> c.copy(sports = c.sports.copy(apps = c.sports.apps - lk)) }
                    }) + app.apps.apps.value.map { a ->
                        MenuItem(a.label) { app.configStore.update { c -> c.copy(sports = c.sports.copy(apps = c.sports.apps + (lk to a.pkg))) } }
                    })
                }
            }
            Spacer(Modifier.height(18.dp))
            ToggleRow("Hero takeover", "A live favourite takes over the top of Home", sports.heroTakeover) {
                app.configStore.update { c -> c.copy(sports = c.sports.copy(heroTakeover = !c.sports.heroTakeover)) }
            }
            ToggleRow("Push live stats to phone", "The remote shows the box score while you watch", sports.pushStatsToPhone) {
                app.configStore.update { c -> c.copy(sports = c.sports.copy(pushStatsToPhone = !c.sports.pushStatsToPhone)) }
            }
        }
    }
}

@Composable
fun SettingRow(title: String, value: String, sub: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().height(if (sub != null) 84.dp else 64.dp).tvFocus(shape, onClick, scale = 1.02f, shadow = false)
        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(title, 22.sp)
            if (sub != null) T(sub, 17.sp, color = Paper.copy(alpha = .55f))
        }
        T(value, 20.sp, color = Paper.copy(alpha = .65f)); Spacer(Modifier.width(8.dp)); Glyph("chevron", 16.dp, Paper.copy(alpha = .5f))
    }
}

@Composable
fun ToggleRow(title: String, sub: String?, on: Boolean, color: Color = Hearth.Live, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().height(if (sub != null) 84.dp else 64.dp).tvFocus(shape, onClick, scale = 1.02f, shadow = false)
        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(title, 22.sp)
            if (sub != null) T(sub, 17.sp, color = Paper.copy(alpha = .55f), maxLines = 2)
        }
        Spacer(Modifier.width(12.dp))
        Toggle(on, color, 64.dp, 36.dp)
    }
}
