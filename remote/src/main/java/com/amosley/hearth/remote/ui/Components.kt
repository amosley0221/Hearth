package com.amosley.hearth.remote.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amosley.hearth.shared.theme.Hearth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val Paper = Hearth.Paper
val Ink = Hearth.Ink

@Composable
fun P(text: String, size: TextUnit = 15.sp, weight: FontWeight = FontWeight.Normal, color: Color = Paper, modifier: Modifier = Modifier,
      maxLines: Int = 1, align: TextAlign? = null, family: FontFamily = Hearth.Figtree, letterSpacing: TextUnit = TextUnit.Unspecified) {
    Text(text, modifier, style = TextStyle(fontFamily = family, fontWeight = weight, fontSize = size, color = color, letterSpacing = letterSpacing),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align)
}

fun Modifier.glass(radius: Dp = 20.dp, shape: Shape = RoundedCornerShape(radius)): Modifier =
    this.clip(shape).background(Color(14, 16, 22).copy(alpha = .72f)).border(1.dp, Hearth.GlassBorderPhone, shape)

/**
 * Press feedback (scale + haptic) and optional hold-to-repeat, used by every remote button.
 */
fun Modifier.press(onPress: () -> Unit, repeat: Boolean = false, onLongPress: (() -> Unit)? = null): Modifier = this.then(
    Modifier.pressImpl(onPress, repeat, onLongPress))

@Composable
private fun Modifier.pressImplC(onPress: () -> Unit, repeat: Boolean, onLongPress: (() -> Unit)?): Modifier {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (pressed) .94f else 1f, tween(120), label = "press")
    val cb by androidx.compose.runtime.rememberUpdatedState(onPress)
    val longCb by androidx.compose.runtime.rememberUpdatedState(onLongPress)
    return this.graphicsLayer { scaleX = s; scaleY = s }.pointerInput(repeat, onLongPress != null) {
        detectTapGestures(
            onPress = {
                pressed = true
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                var job: Job? = null
                if (repeat) {
                    cb()
                    job = scope.launch { delay(420); while (true) { cb(); view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); delay(110) } }
                }
                val released = tryAwaitRelease()
                job?.cancel()
                pressed = false
                if (!repeat && released) cb()
            },
            onLongPress = if (onLongPress != null && !repeat) { _ -> longCb?.invoke() } else null,
        )
    }
}

private fun Modifier.pressImpl(onPress: () -> Unit, repeat: Boolean, onLongPress: (() -> Unit)?): Modifier =
    this.composed { pressImplC(onPress, repeat, onLongPress) }

@Composable
fun GlassButton(label: String, modifier: Modifier = Modifier, radius: Dp = 20.dp, paper: Boolean = false, size: TextUnit = 15.sp,
                repeat: Boolean = false, onClick: () -> Unit) {
    Box(modifier.press(onClick, repeat).then(if (paper) Modifier.clip(RoundedCornerShape(radius)).background(Paper) else Modifier.glass(radius)),
        contentAlignment = Alignment.Center) {
        P(label, size, FontWeight.Medium, color = if (paper) Hearth.Ink2 else Paper)
    }
}

@Composable
fun GlassBox(modifier: Modifier = Modifier, radius: Dp = 22.dp, content: @Composable BoxScope.() -> Unit) =
    Box(modifier.glass(radius), content = content)

@Composable
fun Dot(color: Color, size: Dp = 8.dp) = Box(Modifier.size(size).background(color, CircleShape))

@Composable
fun Toggle(on: Boolean, onColor: Color = Hearth.Amber, width: Dp = 52.dp, height: Dp = 30.dp) {
    val bg by animateColorAsState(if (on) onColor else Color.White.copy(alpha = .2f), tween(200), label = "t")
    val x by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "x")
    val knob = height - 6.dp
    Box(Modifier.size(width, height).background(bg, RoundedCornerShape(99.dp)).padding(3.dp)) {
        Box(Modifier.offset(x = (width - knob - 6.dp) * x).size(knob).background(Paper, CircleShape))
    }
}

@Composable
fun Orb(on: Boolean, size: Dp) {
    Box(Modifier.size(size).background(
        if (on) Brush.radialGradient(listOf(Color(0xFFFFF1C9), Hearth.Amber, Color(0xFF8A6420)), center = Offset(size.value * .9f, size.value * .8f))
        else Brush.radialGradient(listOf(Color(0xFF3A3C44), Color(0xFF1A1B20))), CircleShape))
}

fun hex(c: String): Color = try { Color(android.graphics.Color.parseColor(if (c.startsWith("#")) c else "#$c")) } catch (_: Exception) { Color.Gray }

@Composable
fun TeamCircle(abbr: String, color: String, logo: String?, size: Dp) {
    Box(Modifier.size(size).background(hex(color), CircleShape), contentAlignment = Alignment.Center) {
        if (logo != null) coil.compose.AsyncImage(logo, abbr, Modifier.size(size * .72f))
        else P(abbr, (size.value * .28f).sp, FontWeight.Bold)
    }
}

/** Phone backdrop: TV's current hue fading to ink. */
@Composable
fun PhoneBackdrop(hue: Float) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Hearth.backdrop(hue), Hearth.backdropMid(hue), Ink.copy(alpha = .96f)), 0f, 1400f)))
        Canvas(Modifier.fillMaxSize()) {
            var x = -size.height
            while (x < size.width) { drawLine(Color.White.copy(alpha = .02f), Offset(x, size.height), Offset(x + size.height, 0f), 1.5f); x += 40f }
        }
    }
}

/** Triangle arrow for the d-pad. */
@Composable
fun Arrow(dir: String, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val w = size.width; val h = size.height
        val p = androidx.compose.ui.graphics.Path().apply {
            when (dir) {
                "up" -> { moveTo(w / 2, h * .2f); lineTo(w, h * .8f); lineTo(0f, h * .8f) }
                "down" -> { moveTo(0f, h * .2f); lineTo(w, h * .2f); lineTo(w / 2, h * .8f) }
                "left" -> { moveTo(w * .2f, h / 2); lineTo(w * .8f, 0f); lineTo(w * .8f, h) }
                else -> { moveTo(w * .8f, h / 2); lineTo(w * .2f, 0f); lineTo(w * .2f, h) }
            }
            close()
        }
        drawPath(p, Paper.copy(alpha = .85f))
    }
}
