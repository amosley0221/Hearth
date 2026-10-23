package com.amosley.hearth.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amosley.hearth.shared.theme.Hearth
import com.amosley.hearth.tv.HearthApp
import com.amosley.hearth.tv.ui.AppArt
import com.amosley.hearth.tv.ui.Glyph
import com.amosley.hearth.tv.ui.Hatch
import com.amosley.hearth.tv.ui.Paper
import com.amosley.hearth.tv.ui.Pivot
import com.amosley.hearth.tv.ui.RowTitle
import com.amosley.hearth.tv.ui.SectionHeader
import com.amosley.hearth.tv.ui.T
import com.amosley.hearth.tv.ui.TvUi
import com.amosley.hearth.tv.ui.tvFocus

private sealed interface LibTile {
    val key: String; val label: String
    data class App(val pkg: String, override val label: String) : LibTile { override val key get() = pkg }
    data class Input(val id: String, override val label: String) : LibTile { override val key get() = "input:$id" }
}

@Composable
fun LibrarySection(ui: TvUi) {
    val app = HearthApp.get()
    val apps by app.apps.apps.collectAsStateWithLifecycle()
    val cfg by app.config.collectAsStateWithLifecycle()
    val cw by app.continueWatching.items.collectAsStateWithLifecycle()
    val inputs = remember { app.inputs.list() }
    val tiles: List<LibTile> = remember(apps, cfg.hiddenApps, cfg.consoles, inputs) {
        val consoleByInput = cfg.consoles.filter { it.inputId.isNotBlank() }.associateBy { it.inputId }
        inputs.map { i -> LibTile.Input(i.id, consoleByInput[i.id]?.let { "${i.label} · ${it.name}" } ?: i.label) } +
            apps.filter { it.pkg !in cfg.hiddenApps }.map { LibTile.App(it.pkg, it.label) }
    }
    Pivot(120f) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 130.dp, end = 96.dp, bottom = 80.dp)) {
            SectionHeader("Library", "All your apps and inputs · ${apps.size} installed")
            Spacer(Modifier.height(40.dp))
            for (row in tiles.chunked(6)) {
                Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    for (t in row) LibraryTile(ui, t)
                }
                Spacer(Modifier.height(30.dp))
            }
            if (cw.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                RowTitle("Continue Watching", 30.sp)
                Spacer(Modifier.height(10.dp))
                ContinueRow(ui, cw, null, wide = true)
            }
        }
    }
}

@Composable
private fun LibraryTile(ui: TvUi, t: LibTile) {
    val app = HearthApp.get()
    val shape = RoundedCornerShape(26.dp)
    Column(Modifier.width(180.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(180.dp, 150.dp)
                .tvFocus(shape,
                    onClick = {
                        when (t) {
                            is LibTile.App -> app.apps.launch(t.pkg)
                            is LibTile.Input -> if (!app.inputs.switchTo(t.id)) ui.toast("Couldn't switch input")
                        }
                    },
                    onLongClick = { if (t is LibTile.App) ui.tileMenu = dockMenu(ui, t.pkg, t.label) },
                    onFocus = { ui.hue = Hearth.hueFor(t.label); ui.art = null })
                .clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            when (t) {
                is LibTile.App -> AppArt(t.pkg, t.label, Modifier.fillMaxSize())
                is LibTile.Input -> {
                    Hatch(220f, Modifier.fillMaxSize())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Glyph("input", 40.dp, Paper)
                        Spacer(Modifier.height(8.dp))
                        T(t.label.substringBefore(" · "), 24.sp, FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        T(t.label, 21.sp, align = TextAlign.Center, color = Paper.copy(alpha = .85f))
    }
}
