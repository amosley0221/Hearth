package com.amosley.hearth.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amosley.hearth.remote.net.TvConnection
import com.amosley.hearth.shared.protocol.TvState
import com.amosley.hearth.shared.theme.Hearth

/** 5c · Apps + keyboard. Text goes straight into the focused TV field. */
@Composable
fun AppsScreen(tv: TvConnection, st: TvState?, focusKeyboard: Boolean) {
    var text by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(focusKeyboard) { if (focusKeyboard) try { fr.requestFocus() } catch (_: Exception) {} }
    val apps = st?.apps.orEmpty()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(52.dp).glass(18.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) P(if (st?.keyboardActive == true) "Typing on TV · ${st.keyboardHint.ifBlank { "search" }}" else "Type to send text to the TV",
                16.sp, color = Paper.copy(alpha = .5f))
            BasicTextField(
                value = text,
                onValueChange = { text = it; tv.text(it, "set") },
                singleLine = true,
                textStyle = TextStyle(color = Paper, fontSize = 16.sp, fontFamily = Hearth.Figtree),
                cursorBrush = SolidColor(Hearth.Amber),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { tv.text("", "submit") }),
                modifier = Modifier.fillMaxWidth().focusRequester(fr),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, mode) in listOf("⌫ Delete" to "backspace", "Clear" to "clear", "Search" to "submit")) {
                Box(Modifier.clip(RoundedCornerShape(99.dp)).background(if (mode == "submit") Paper else androidx.compose.ui.graphics.Color.White.copy(alpha = .08f))
                    .press({ if (mode == "clear") { text = ""; tv.text("", "set") } else tv.text("", mode); if (mode == "backspace") text = text.dropLast(1) })
                    .padding(horizontal = 14.dp, vertical = 8.dp)) { P(label, 13.sp, FontWeight.Medium, color = if (mode == "submit") Hearth.Ink2 else Paper) }
            }
        }
        P("Favorites", 13.sp, color = Paper.copy(alpha = .6f))
        LazyVerticalGrid(GridCells.Adaptive(76.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(apps, key = { it.pkg }) { a ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.press({ tv.launch(a.pkg) })) {
                    val brand = Hearth.brandFor(a.label)
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(brand?.first ?: Hearth.placeholder(Hearth.hueFor(a.pkg))),
                        contentAlignment = Alignment.Center) {
                        P(a.label, 11.sp, FontWeight.SemiBold, color = brand?.second ?: Paper, maxLines = 2, align = TextAlign.Center)
                        tv.assetUrl("/api/icon/${a.pkg}")?.let { AsyncImage(it, a.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    }
                    Spacer(Modifier.height(6.dp))
                    P(a.label, 11.sp, FontWeight.Medium, color = Paper.copy(alpha = .8f))
                }
            }
        }
        if (!st?.consoles.isNullOrEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in st!!.consoles.take(3)) {
                    Row(Modifier.weight(1f).height(64.dp).glass(18.dp).press({ tv.console(c.id) }).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Dot(when (c.status) { "on" -> Hearth.Live; "standby" -> Hearth.Amber; else -> Paper.copy(alpha = .3f) })
                        Spacer(Modifier.width(10.dp))
                        Column { P(c.name, 15.sp, FontWeight.Medium); P(c.status.replaceFirstChar { it.uppercase() }.takeIf { c.status != "unknown" } ?: c.inputLabel, 12.sp, color = Paper.copy(alpha = .6f)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
