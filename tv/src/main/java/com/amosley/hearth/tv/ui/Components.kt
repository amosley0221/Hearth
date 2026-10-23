package com.amosley.hearth.tv.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.amosley.hearth.shared.theme.Hearth
import com.amosley.hearth.tv.HearthApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val Paper = Hearth.Paper
val Ink = Hearth.Ink

/**
 * Focus behaviour for every interactive TV element: scale 1.06 + 3px Paper ring + deeper shadow,
 * 180ms. OK/Enter clicks on release; holding OK (or pressing Menu) is a long-press.
 */
fun Modifier.tvFocus(
    shape: Shape,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    scale: Float = 1.06f,
    ring: Boolean = true,
    shadow: Boolean = true,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) scale else 1f, tween(180), label = "focusScale")
    val interaction = remember { MutableInteractionSource() }
    this
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged {
            if (it.isFocused != focused) {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
        }
        .onPreviewKeyEvent { ev ->
            val k = ev.key
            if (k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter) {
                when (ev.type) {
                    KeyEventType.KeyDown -> {
                        if (ev.nativeKeyEvent.repeatCount == 1 && onLongClick != null) { longFired = true; onLongClick() }
                        true
                    }
                    KeyEventType.KeyUp -> { if (!longFired) onClick(); longFired = false; true }
                    else -> false
                }
            } else if (k == Key.Menu && onLongClick != null) {
                if (ev.type == KeyEventType.KeyUp) onLongClick()
                true
            } else false
        }
        .graphicsLayer { scaleX = s; scaleY = s }
        .then(if (shadow) Modifier.shadow(if (focused) 28.dp else 8.dp, shape, clip = false) else Modifier)
        .then(if (focused && ring) Modifier.border(3.dp, Paper, shape) else Modifier)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

@Composable
fun T(
    text: String,
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Paper,
    family: FontFamily = Hearth.Figtree,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    align: TextAlign? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    shadow: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = family, fontWeight = weight, fontSize = size, color = color, letterSpacing = letterSpacing,
            shadow = if (shadow) androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = .5f), Offset(0f, 4f), 40f) else null,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
}

fun Modifier.glass(radius: Dp = 22.dp, alpha: Float = .65f, border: Boolean = true): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .background(Color(14, 16, 22).copy(alpha = (alpha + .12f).coerceAtMost(.92f)), shape)
        .then(if (border) Modifier.border(1.dp, Hearth.GlassBorder, shape) else Modifier)
}

/** Hatched placeholder art (until real art loads). */
@Composable
fun Hatch(hue: Float, modifier: Modifier = Modifier, label: String? = null) {
    Box(modifier.background(Hearth.placeholder(hue))) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 26f
            var x = -size.height
            while (x < size.width) {
                drawLine(Color.White.copy(alpha = .045f), Offset(x, size.height), Offset(x + size.height, 0f), 1.5f)
                x += step
            }
        }
        if (label != null) T(label, 13.sp, color = Paper.copy(alpha = .45f), family = FontFamily.Monospace,
            modifier = Modifier.padding(14.dp))
    }
}

@Composable
fun Pill(text: String, selected: Boolean = false, size: TextUnit = 22.sp, hPad: Dp = 22.dp, vPad: Dp = 10.dp,
         onClick: () -> Unit = {}, modifier: Modifier = Modifier, focusRequester: FocusRequester? = null, onFocus: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .tvFocus(shape, onClick, focusRequester = focusRequester, onFocus = onFocus, shadow = false)
            .background(if (selected) Paper.copy(alpha = .95f) else Color.White.copy(alpha = .08f), shape)
            .padding(horizontal = hPad, vertical = vPad)
    ) {
        T(text, size, FontWeight.Medium, color = if (selected) Hearth.Ink2 else Paper)
    }
}

@Composable
fun Toggle(on: Boolean, onColor: Color = Hearth.Amber, width: Dp = 68.dp, height: Dp = 38.dp) {
    val bg by animateColorAsState(if (on) onColor else Color.White.copy(alpha = .2f), tween(200), label = "toggle")
    val knob = height - 8.dp
    val x by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "knob")
    Box(Modifier.size(width, height).background(bg, RoundedCornerShape(999.dp)).padding(4.dp)) {
        Box(Modifier.offset(x = (width - knob - 8.dp) * x).size(knob).background(Paper, CircleShape))
    }
}

@Composable
fun Roundel(abbr: String, size: Dp = 34.dp) {
    Box(Modifier.size(size).border(2.dp, Paper.copy(alpha = .6f), CircleShape), contentAlignment = Alignment.Center) {
        T(abbr.take(4), (size.value * 0.29f).sp, FontWeight.Bold)
    }
}

fun parseHex(hex: String, fallback: Color = Color(0xFF555555)): Color = try {
    Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
} catch (_: Exception) { fallback }

@Composable
fun TeamDot(abbr: String, colorHex: String, size: Dp, showText: Boolean = true) {
    Box(Modifier.size(size).background(parseHex(colorHex), CircleShape), contentAlignment = Alignment.Center) {
        if (showText) T(abbr, (size.value * 0.3f).sp, FontWeight.Bold)
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 10.dp) = Box(Modifier.size(size).background(color, CircleShape))

/** App banner/icon from PackageManager; the tile background is sampled from the art's edge. */
@Composable
fun AppArt(pkg: String, label: String, modifier: Modifier = Modifier, wordmarkSize: TextUnit = 24.sp, padding: Dp = 0.dp) {
    val app = HearthApp.get()
    val bmp by produceState<Bitmap?>(null, pkg) { value = withContext(Dispatchers.IO) { app.apps.banner(pkg) } }
    val brand = Hearth.brandFor(label)
    val b = bmp
    val bg = brand?.first ?: b?.let { edgeColor(it) } ?: Hearth.placeholder(Hearth.hueFor(pkg))
    Box(modifier.background(bg), contentAlignment = Alignment.Center) {
        if (b != null) {
            val isBanner = b.width > b.height * 1.3f
            Image(
                b.asImageBitmap(), contentDescription = label,
                contentScale = if (isBanner) ContentScale.Crop else ContentScale.Fit,
                modifier = if (isBanner) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(padding.coerceAtLeast(18.dp)),
            )
        } else {
            T(label, wordmarkSize, FontWeight.SemiBold, color = brand?.second ?: Paper, maxLines = 2, align = TextAlign.Center,
                modifier = Modifier.padding(8.dp))
        }
    }
}

private fun edgeColor(b: Bitmap): Color = try {
    val px = b.getPixel(2.coerceAtMost(b.width - 1), (b.height / 2).coerceAtMost(b.height - 1))
    Color(px).copy(alpha = 1f)
} catch (_: Exception) { Color(0xFF1C1C1E) }

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color = Paper, height: Dp = 3.dp) {
    Box(modifier.height(height).background(Color.White.copy(alpha = .25f), RoundedCornerShape(99.dp))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(height).background(color, RoundedCornerShape(99.dp)))
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, size: TextUnit = 60.sp) {
    Column {
        T(title, size, FontWeight.Medium, letterSpacing = (-0.01).em)
        if (subtitle != null) { Spacer(Modifier.height(6.dp)); T(subtitle, 26.sp, FontWeight.Light, color = Paper.copy(alpha = .6f)) }
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, radius: Dp = 22.dp, padding: Dp = 0.dp, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.glass(radius).clip(RoundedCornerShape(radius)).padding(padding), content = content)
}

@Composable
fun RowTitle(text: String, size: TextUnit = 26.sp, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        T(text, size, FontWeight.Medium)
        trailing?.invoke()
    }
}

@Composable
fun LiveBadge() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(Hearth.Alert, 9.dp); Spacer(Modifier.width(8.dp))
        T("LIVE", 18.sp, FontWeight.SemiBold, color = Hearth.Alert, letterSpacing = 0.12.em)
    }
}

/** Full-bleed backdrop: hue gradient (or art), hatch texture and legibility vignettes. */
@Composable
fun Backdrop(hue: Float, art: Any? = null, kids: Boolean = false) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        val brush = if (kids) Brush.linearGradient(listOf(Color(0xFF5B3FA8), Color(0xFF2E2F7A), Color(0xFF1B2450)), Offset.Zero, Offset(900f, 1600f))
        else Brush.linearGradient(listOf(Hearth.backdrop(hue), Hearth.backdropMid(hue), Ink), Offset(0f, 0f), Offset(1100f, 1900f))
        Box(Modifier.fillMaxSize().background(brush))
        if (art != null) {
            coil.compose.AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = .38f })
        }
        Canvas(Modifier.fillMaxSize()) {
            var x = -size.height
            while (x < size.width) {
                drawLine(Color.White.copy(alpha = .018f), Offset(x, size.height), Offset(x + size.height, 0f), 1.5f)
                x += 34f
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .55f), Ink.copy(alpha = .9f)), 300f, 1080f)))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Ink.copy(alpha = .5f), Color.Transparent), 0f, 700f)))
    }
}

fun Color.argb() = toArgb()
