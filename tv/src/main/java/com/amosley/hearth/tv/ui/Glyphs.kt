package com.amosley.hearth.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Hearth's geometric line glyphs (2.5px stroke at ~26px), drawn on a 24-unit grid. */
@Composable
fun Glyph(name: String, size: Dp = 26.dp, color: Color = Color.White, strokeWidth: Float = 2.5f, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val st = Stroke(width = strokeWidth * u * 24f / 26f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawGlyph(name, u, color, st)
    }
}

private fun DrawScope.drawGlyph(name: String, u: Float, c: Color, st: Stroke) {
    fun p(vararg pts: Pair<Float, Float>, close: Boolean = false) = Path().apply {
        pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * u, y * u) else lineTo(x * u, y * u) }
        if (close) close()
    }
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    when (name) {
        "home" -> drawPath(p(3f to 11f, 12f to 3.5f, 21f to 11f, 21f to 21f, 3f to 21f, close = true), c, style = st)
        "grid" -> for (x in listOf(3f, 13.5f)) for (y in listOf(3f, 13.5f))
            drawRoundRect(c, o(x, y), Size(7.5f * u, 7.5f * u), CornerRadius(2f * u), style = st)
        "sports" -> { drawCircle(c, 9f * u, o(12f, 12f), style = st); drawLine(c, o(6f, 18f), o(18f, 6f), st.width, StrokeCap.Round) }
        "diamond" -> { drawPath(p(12f to 2.5f, 21.5f to 12f, 12f to 21.5f, 2.5f to 12f, close = true), c, style = st); drawCircle(c, 1.8f * u, o(12f, 12f)) }
        "gamepad" -> {
            drawRoundRect(c, o(2.5f, 7f), Size(19f * u, 10f * u), CornerRadius(4f * u), style = st)
            drawCircle(c, 1.4f * u, o(8f, 12f)); drawCircle(c, 1.4f * u, o(16f, 12f))
        }
        "gear" -> { drawCircle(c, 9f * u, o(12f, 12f), style = st); drawCircle(c, 3.5f * u, o(12f, 12f), style = st) }
        "moon" -> {
            val path = Path().apply {
                moveTo(16f * u, 3.5f * u)
                cubicTo(9f * u, 3f * u, 4f * u, 8f * u, 4f * u, 13f * u)
                cubicTo(4f * u, 18f * u, 8.5f * u, 21f * u, 13f * u, 21f * u)
                cubicTo(17f * u, 21f * u, 19.5f * u, 19f * u, 21f * u, 16f * u)
                cubicTo(14f * u, 18f * u, 9.5f * u, 11f * u, 16f * u, 3.5f * u)
                close()
            }
            drawPath(path, c, style = Fill)
        }
        "bulb" -> { drawRoundRect(c, o(7f, 3f), Size(10f * u, 14f * u), CornerRadius(5f * u), style = st); drawLine(c, o(9f, 20.5f), o(15f, 20.5f), st.width, StrokeCap.Round) }
        "thermo" -> { drawRoundRect(c, o(9f, 2.5f), Size(6f * u, 19f * u), CornerRadius(3f * u), style = st) }
        "camera" -> drawRoundRect(c, o(3f, 6f), Size(18f * u, 13f * u), CornerRadius(2.5f * u), style = st)
        "lock" -> { drawRoundRect(c, o(4f, 10f), Size(16f * u, 11f * u), CornerRadius(2.5f * u), style = st); drawPath(p(8f to 10f, 8f to 7f, 16f to 7f, 16f to 10f), c, style = st) }
        "speaker" -> drawPath(p(4f to 9f, 9f to 9f, 15f to 4f, 15f to 20f, 9f to 15f, 4f to 15f, close = true), c, style = Fill)
        "check" -> drawPath(p(5f to 12.5f, 10f to 17.5f, 19f to 7f), c, style = st)
        "plus" -> { drawLine(c, o(12f, 5f), o(12f, 19f), st.width, StrokeCap.Round); drawLine(c, o(5f, 12f), o(19f, 12f), st.width, StrokeCap.Round) }
        "minus" -> drawLine(c, o(5f, 12f), o(19f, 12f), st.width, StrokeCap.Round)
        "chevron" -> drawPath(p(9f to 5f, 16f to 12f, 9f to 19f), c, style = st)
        "back" -> drawPath(p(15f to 5f, 8f to 12f, 15f to 19f), c, style = st)
        "play" -> drawPath(p(7f to 4f, 20f to 12f, 7f to 20f, close = true), c, style = Fill)
        "pause" -> { drawRoundRect(c, o(6f, 4f), Size(4f * u, 16f * u), CornerRadius(1f * u)); drawRoundRect(c, o(14f, 4f), Size(4f * u, 16f * u), CornerRadius(1f * u)) }
        "search" -> { drawCircle(c, 6.5f * u, o(10.5f, 10.5f), style = st); drawLine(c, o(15.5f, 15.5f), o(20.5f, 20.5f), st.width, StrokeCap.Round) }
        "film" -> { drawRoundRect(c, o(3f, 5f), Size(18f * u, 14f * u), CornerRadius(2.5f * u), style = st); drawLine(c, o(8f, 5f), o(8f, 19f), st.width); drawLine(c, o(16f, 5f), o(16f, 19f), st.width) }
        "trophy" -> { drawPath(p(7f to 4f, 17f to 4f, 17f to 9f, 12f to 14f, 7f to 9f, close = true), c, style = st); drawLine(c, o(12f, 14f), o(12f, 19f), st.width); drawLine(c, o(8f, 20f), o(16f, 20f), st.width, StrokeCap.Round) }
        "star" -> drawPath(p(12f to 3f, 14.6f to 9f, 21f to 9.5f, 16f to 13.8f, 17.6f to 20.5f, 12f to 17f, 6.4f to 20.5f, 8f to 13.8f, 3f to 9.5f, 9.4f to 9f, close = true), c, style = st)
        "spark" -> { drawLine(c, o(12f, 3f), o(12f, 21f), st.width, StrokeCap.Round); drawLine(c, o(3f, 12f), o(21f, 12f), st.width, StrokeCap.Round); drawLine(c, o(6f, 6f), o(18f, 18f), st.width, StrokeCap.Round); drawLine(c, o(18f, 6f), o(6f, 18f), st.width, StrokeCap.Round) }
        "power" -> { drawArc(c, -60f, 300f, false, o(4f, 5f), Size(16f * u, 16f * u), style = st); drawLine(c, o(12f, 2.5f), o(12f, 11f), st.width, StrokeCap.Round) }
        "input" -> { drawRoundRect(c, o(3f, 5f), Size(18f * u, 12f * u), CornerRadius(2f * u), style = st); drawLine(c, o(9f, 20.5f), o(15f, 20.5f), st.width, StrokeCap.Round) }
        "dot" -> drawCircle(c, 4f * u, o(12f, 12f))
        "kid" -> { drawCircle(c, 9f * u, o(12f, 12f), style = st); drawCircle(c, 1.2f * u, o(9f, 10f)); drawCircle(c, 1.2f * u, o(15f, 10f)); drawArc(c, 20f, 140f, false, o(8f, 9f), Size(8f * u, 7f * u), style = st) }
        "update" -> { drawArc(c, 200f, 280f, false, o(4f, 4f), Size(16f * u, 16f * u), style = st); drawPath(p(12f to 1.5f, 15.5f to 4f, 12f to 6.5f), c, style = st) }
        "link" -> { drawRoundRect(c, o(2.5f, 8f), Size(11f * u, 8f * u), CornerRadius(4f * u), style = st); drawRoundRect(c, o(10.5f, 8f), Size(11f * u, 8f * u), CornerRadius(4f * u), style = st) }
        else -> drawCircle(c, 3f * u, o(12f, 12f))
    }
}
