package com.amosley.hearth.tv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * The TV design is specified in 1920×1080 pixels. Inside DesignScale, 1.dp == 1 design pixel
 * whatever the panel's real resolution or density, so the spec's numbers are used as-is.
 */
@Composable
fun DesignScale(content: @Composable () -> Unit) {
    BoxWithConstraints {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.takeIf { it > 0f } ?: 1920f
        val scale = widthPx / 1920f
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
            content()
        }
    }
}

/** Scrolls so the focused item sits [pivotPx] from the container's leading edge. */
@OptIn(ExperimentalFoundationApi::class)
class PivotSpec(private val pivotPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailing = offset + size
        // Already comfortably visible: don't move.
        if (offset >= pivotPx * 0.5f && trailing <= containerSize - pivotPx * 0.5f && offset <= containerSize * 0.6f) return 0f
        return offset - pivotPx
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Pivot(pivotPx: Float, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides PivotSpec(pivotPx), content = content)
}
