package com.amosley.hearth.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amosley.hearth.shared.theme.Hearth
import com.amosley.hearth.tv.HearthApp
import com.amosley.hearth.tv.ui.MenuItem
import com.amosley.hearth.tv.ui.Paper
import com.amosley.hearth.tv.ui.Pivot
import com.amosley.hearth.tv.ui.RowTitle
import com.amosley.hearth.tv.ui.SectionHeader
import com.amosley.hearth.tv.ui.StatusDot
import com.amosley.hearth.tv.ui.T
import com.amosley.hearth.tv.ui.TileMenu
import com.amosley.hearth.tv.ui.TvUi
import com.amosley.hearth.tv.ui.glass
import com.amosley.hearth.tv.ui.tvFocus

@Composable
fun GamesSection(ui: TvUi) {
    val app = HearthApp.get()
    val cfg by app.config.collectAsStateWithLifecycle()
    @Suppress("UNUSED_VARIABLE") val ents by app.ha.entities.collectAsStateWithLifecycle()
    Pivot(120f) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 130.dp, end = 96.dp, bottom = 80.dp)) {
            SectionHeader("Games", "Consoles wake and switch input automatically")
            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                for (c in cfg.consoles) {
                    val status = app.actions.consoleStatus(c)
                    val activity = app.actions.consoleActivity(c)
                    val shape = RoundedCornerShape(22.dp)
                    Column(
                        Modifier.size(528.dp, 200.dp)
                            .tvFocus(shape, onClick = { app.actions.wakeConsole(c.id, null, false) },
                                onLongClick = { ui.section = "settings"; ui.settingsPage = "consoles" },
                                onFocus = { ui.hue = Hearth.hueFor(c.name); ui.art = null })
                            .glass(22.dp, .65f).padding(28.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(when (status) { "on" -> Hearth.Live; "standby" -> Hearth.Amber; else -> Paper.copy(alpha = .3f) }, 12.dp)
                            Spacer(Modifier.width(14.dp))
                            T(c.name, 30.sp, FontWeight.Medium)
                        }
                        Spacer(Modifier.height(10.dp))
                        val parts = listOfNotNull(
                            status.replaceFirstChar { it.uppercase() }.takeIf { status != "unknown" },
                            c.inputLabel.ifBlank { null },
                            activity.ifBlank { null }?.let { "Playing $it" },
                        )
                        T(parts.joinToString(" · ").ifBlank { "Set up in Settings › Consoles" }, 20.sp, color = Paper.copy(alpha = .65f))
                        Spacer(Modifier.weight(1f))
                        T(if (c.kind == "switch" || !c.wake.valid) "Switch input only" else "Wake and switch input", 18.sp, color = Paper.copy(alpha = .45f))
                    }
                }
            }
            Spacer(Modifier.height(44.dp))
            RowTitle("Pinned games", 30.sp)
            Spacer(Modifier.height(18.dp))
            if (cfg.games.isEmpty()) {
                T("Pin games from the phone or iPad: Settings › Games. PS5 games open the PS5 home screen; Xbox games launch directly.",
                    22.sp, color = Paper.copy(alpha = .55f), maxLines = 3)
            }
            for (row in cfg.games.chunked(4)) {
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    for (g in row) {
                        val c = app.actions.console(g.consoleId)
                        GameTile(g.name, consoleHint(c?.kind ?: "", c?.name ?: "", g), g.hue, g.artUrl.ifBlank { null }, 392.dp, 220.dp,
                            onClick = { app.actions.playGame(g) }, ui = ui, titleSize = 26,
                            onLongClick = {
                                ui.tileMenu = TileMenu(g.name, listOf(
                                    MenuItem("Play") { app.actions.playGame(g) },
                                    MenuItem("Remove from Games", destructive = true) {
                                        app.configStore.update { it.copy(games = it.games.filterNot { x -> x.id == g.id }) }
                                    },
                                ))
                            })
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }
}
