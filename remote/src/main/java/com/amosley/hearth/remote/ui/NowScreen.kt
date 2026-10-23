package com.amosley.hearth.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amosley.hearth.remote.net.TvConnection
import com.amosley.hearth.shared.protocol.Game
import com.amosley.hearth.shared.protocol.Keys
import com.amosley.hearth.shared.protocol.NowPlaying
import com.amosley.hearth.shared.protocol.TvState
import com.amosley.hearth.shared.theme.Hearth
import kotlinx.coroutines.delay

/** Context-aware second screen: live game (5b), now playing, console, or pick a live game. */
@Composable
fun NowScreen(tv: TvConnection, st: TvState?, wide: Boolean) {
    val g = st?.liveGame
    val np = st?.nowPlaying
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(6.dp))
        when {
            st == null -> P("Not connected", 22.sp, FontWeight.Medium)
            st.secondScreen == "sports" && g != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(Hearth.Alert); Spacer(Modifier.width(8.dp))
                    P("LIVE · ${g.leagueName}${if (g.network.isNotBlank()) " / ${g.network}" else ""}".uppercase(), 13.sp, color = Paper.copy(alpha = .7f), letterSpacing = 0.14.em)
                }
                ScoreCard(g)
                GameTabs(st, g, Modifier.weight(1f))
            }
            np != null && np.title.isNotBlank() -> NowPlayingView(tv, np, Modifier.weight(1f))
            else -> PickGame(tv, st, Modifier.weight(1f))
        }
        if (!wide) CompactStrip(tv)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun ScoreCard(g: Game) {
    Column(Modifier.fillMaxWidth().glass(26.dp).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                TeamCircle(g.away.abbr, g.away.color, g.away.logo, 56.dp); Spacer(Modifier.height(6.dp)); P(g.away.abbr, 14.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    P(g.away.score, 48.sp, FontWeight.SemiBold); P("  –  ", 20.sp, color = Paper.copy(alpha = .4f)); P(g.home.score, 48.sp, FontWeight.SemiBold)
                }
                P(g.detail, 14.sp, color = Paper.copy(alpha = .7f))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                TeamCircle(g.home.abbr, g.home.color, g.home.logo, 56.dp); Spacer(Modifier.height(6.dp)); P(g.home.abbr, 14.sp)
            }
        }
        if (g.situation.isNotBlank() || g.highlight.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .1f)))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                P(g.situation, 14.sp, color = Paper.copy(alpha = .8f))
                if (g.highlight.isNotBlank()) { P("  •  ", 14.sp, color = Paper.copy(alpha = .4f)); P(g.highlight, 14.sp, color = Hearth.Amber) }
            }
        }
    }
}

@Composable
private fun GameTabs(st: TvState, g: Game, modifier: Modifier) {
    var tab by remember { mutableStateOf("stats") }
    Column(modifier.fillMaxWidth().glass(22.dp).padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, label) in listOf("stats" to "Stats", "plays" to "Plays", "other" to "Other games")) {
                val sel = tab == id
                Box(Modifier.clip(RoundedCornerShape(99.dp)).background(if (sel) Paper else Color.White.copy(alpha = .08f))
                    .press({ tab = id }).padding(horizontal = 14.dp, vertical = 7.dp)) {
                    P(label, 14.sp, FontWeight.Medium, color = if (sel) Hearth.Ink2 else Paper)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            when (tab) {
                "plays" -> P(g.lastPlay.ifBlank { "No play-by-play yet." }, 15.sp, maxLines = 8)
                "other" -> st.liveGames.filter { it.id != g.id }.ifEmpty { null }?.forEach { o ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        P("${o.away.abbr} ${o.away.score} · ${o.home.abbr} ${o.home.score}", 15.sp, FontWeight.Medium, modifier = Modifier.weight(1f))
                        P(o.detail, 13.sp, color = Paper.copy(alpha = .6f))
                    }
                } ?: P("No other live games.", 15.sp, color = Paper.copy(alpha = .6f))
                else -> {
                    if (g.stats.isEmpty()) P("Stats appear once the game is underway.", 15.sp, color = Paper.copy(alpha = .6f))
                    for (s in g.stats) {
                        val a = s.away.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: 0f
                        val b = s.home.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: 0f
                        val tot = (a + b).takeIf { it > 0 } ?: 1f
                        Column(Modifier.padding(vertical = 7.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                P(s.away, 14.sp, FontWeight.SemiBold); P(s.label, 14.sp, color = Paper.copy(alpha = .6f), modifier = Modifier.weight(1f), align = TextAlign.Center)
                                P(s.home, 14.sp, FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(5.dp))
                            Row(Modifier.fillMaxWidth().height(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.weight((a / tot).coerceAtLeast(.02f)).fillMaxHeight().background(Paper, RoundedCornerShape(4.dp)))
                                Box(Modifier.weight((b / tot).coerceAtLeast(.02f)).fillMaxHeight().background(Color.White.copy(alpha = .22f), RoundedCornerShape(4.dp)))
                            }
                        }
                    }
                    if (g.lastPlay.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .05f), RoundedCornerShape(14.dp)).padding(12.dp)) {
                            P("Last play · ${g.lastPlay}", 15.sp, maxLines = 4)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingView(tv: TvConnection, np: NowPlaying, modifier: Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(np) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val pos = if (np.playing) (np.positionMs + (now - np.sampledAt)).coerceAtMost(np.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE) else np.positionMs
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        P("NOW PLAYING · ${np.appLabel.uppercase()}", 13.sp, color = Paper.copy(alpha = .6f), letterSpacing = 0.14.em)
        val art = np.artUrl?.let { if (it.startsWith("/")) tv.assetUrl(it) else it }
        Box(Modifier.fillMaxWidth(.8f).aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(Hearth.placeholder(Hearth.hueFor(np.title)))) {
            if (art != null) AsyncImage(art, np.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        P(np.title, 26.sp, FontWeight.Medium, family = Hearth.Newsreader, maxLines = 2, align = TextAlign.Center)
        if (np.subtitle.isNotBlank()) P(np.subtitle, 15.sp, color = Paper.copy(alpha = .65f))
        if (np.durationMs > 0) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = .15f), RoundedCornerShape(6.dp))) {
                Box(Modifier.fillMaxWidth((pos.toFloat() / np.durationMs).coerceIn(0f, 1f)).height(6.dp).background(Paper, RoundedCornerShape(6.dp)))
            }
            Row(Modifier.fillMaxWidth()) { P(fmt(pos), 13.sp, color = Paper.copy(alpha = .6f), modifier = Modifier.weight(1f)); P("−" + fmt(np.durationMs - pos), 13.sp, color = Paper.copy(alpha = .6f)) }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("−10s", Modifier.weight(1f).fillMaxHeight(), 18.dp) { tv.key(Keys.SKIP_BACK) }
            GlassButton(if (np.playing) "Pause" else "Play", Modifier.weight(1.4f).fillMaxHeight(), 18.dp, paper = true) { tv.key(Keys.PLAY_PAUSE) }
            GlassButton("+30s", Modifier.weight(1f).fillMaxHeight(), 18.dp) { tv.key(Keys.SKIP_FORWARD) }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0); val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
private fun PickGame(tv: TvConnection, st: TvState, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        P(if (st.liveGames.isEmpty()) "Nothing playing" else "Live now", 26.sp, FontWeight.Medium)
        P(if (st.liveGames.isEmpty()) "Start something on the TV and it shows up here." else "Tap a game to open its app on the TV.", 15.sp, color = Paper.copy(alpha = .6f), maxLines = 2)
        for (g in st.liveGames) {
            Row(Modifier.fillMaxWidth().glass(18.dp).press({ tv.section("sports") }).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamCircle(g.away.abbr, g.away.color, g.away.logo, 32.dp); Spacer(Modifier.width(8.dp))
                TeamCircle(g.home.abbr, g.home.color, g.home.logo, 32.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    P("${g.away.abbr} ${g.away.score} · ${g.home.abbr} ${g.home.score}", 16.sp, FontWeight.Medium)
                    P("${g.leagueName} · ${g.detail}", 13.sp, color = Paper.copy(alpha = .6f))
                }
                if (g.favorite) Dot(Hearth.Amber)
            }
        }
        val consoles = st.consoles.filter { it.status == "on" || it.status == "standby" }
        if (consoles.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            P("Consoles", 14.sp, color = Paper.copy(alpha = .6f))
            for (c in consoles) Row(Modifier.fillMaxWidth().glass(18.dp).press({ tv.console(c.id) }).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(if (c.status == "on") Hearth.Live else Hearth.Amber); Spacer(Modifier.width(10.dp))
                Column { P(c.name, 16.sp, FontWeight.Medium); P(listOf(c.status, c.inputLabel, c.activity).filter { it.isNotBlank() }.joinToString(" · "), 13.sp, color = Paper.copy(alpha = .6f)) }
            }
        }
    }
}

/** Back / ‹ OK › / Vol strip that keeps the pad one tap away. */
@Composable
fun CompactStrip(tv: TvConnection) {
    Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassButton("Back", Modifier.weight(1.2f).fillMaxHeight(), 18.dp) { tv.key(Keys.BACK) }
        GlassButton("‹", Modifier.weight(1f).fillMaxHeight(), 18.dp, size = 22.sp, repeat = true) { tv.key(Keys.LEFT) }
        GlassButton("OK", Modifier.weight(1f).fillMaxHeight(), 18.dp, paper = true) { tv.key(Keys.OK) }
        GlassButton("›", Modifier.weight(1f).fillMaxHeight(), 18.dp, size = 22.sp, repeat = true) { tv.key(Keys.RIGHT) }
        GlassButton("Vol−", Modifier.weight(1f).fillMaxHeight(), 18.dp, repeat = true) { tv.key(Keys.VOL_DOWN) }
        GlassButton("Vol+", Modifier.weight(1f).fillMaxHeight(), 18.dp, repeat = true) { tv.key(Keys.VOL_UP) }
    }
}

@Suppress("unused") private val keep = Modifier.size(1.dp)
