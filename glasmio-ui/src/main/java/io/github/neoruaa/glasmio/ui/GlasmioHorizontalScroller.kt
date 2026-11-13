package io.github.neoruaa.glasmio.ui

import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection

/**
 * Expands a viewport through the window's physical horizontal safe area.
 *
 * Callers should pass their existing start/end spacing through
 * [glasmioHorizontalContentPaddingImpl] so that spacing remains inside the scrolling content
 * while the viewport reaches the hardware edge. A vertical lazy parent can use the same modifier
 * to expose cross-axis space for nested horizontal rows, provided its item padding includes the
 * safe-area insets.
 */
@Composable
internal fun Modifier.glasmioHorizontalEdgeToEdgeImpl(
    insets: WindowInsets = WindowInsets.safeDrawing,
): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val (left, right) = resolveHorizontalInsets(insets, density, layoutDirection)
    if (left == 0 && right == 0) return this

    val extraWidth = (left.toLong() + right.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    fun plusClamped(value: Int): Int =
        if (value > Int.MAX_VALUE - extraWidth) Int.MAX_VALUE else value + extraWidth
    return layout { measurable, constraints ->
        val expandedConstraints = constraints.copy(
            minWidth = plusClamped(constraints.minWidth),
            maxWidth = if (constraints.hasBoundedWidth) plusClamped(constraints.maxWidth) else Constraints.Infinity,
        )
        val placeable = measurable.measure(expandedConstraints)
        val width = (placeable.width - extraWidth).coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(width, placeable.height) {
            // Insets are physical pixels; keep the viewport extension physical in RTL too.
            placeable.place(-left, 0)
        }
    }
}

/** Adds the horizontal safe-area insets to existing scrolling content padding. */
@Composable
internal fun glasmioHorizontalContentPaddingImpl(
    contentPadding: PaddingValues = PaddingValues(),
    insets: WindowInsets = WindowInsets.safeDrawing,
): PaddingValues {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val (left, right) = resolveHorizontalInsets(insets, density, layoutDirection)
    val startInset = with(density) {
        if (layoutDirection == LayoutDirection.Ltr) left.toDp() else right.toDp()
    }
    val endInset = with(density) {
        if (layoutDirection == LayoutDirection.Ltr) right.toDp() else left.toDp()
    }
    return PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection) +
            startInset,
        top = contentPadding.calculateTopPadding(),
        end = contentPadding.calculateEndPadding(layoutDirection) +
            endInset,
        bottom = contentPadding.calculateBottomPadding(),
    )
}

private data class HorizontalInsets(val left: Int, val right: Int)

@Composable
private fun resolveHorizontalInsets(
    insets: WindowInsets,
    density: androidx.compose.ui.unit.Density,
    layoutDirection: LayoutDirection,
): HorizontalInsets {
    val left = insets.getLeft(density, layoutDirection)
    val right = insets.getRight(density, layoutDirection)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return HorizontalInsets(left, right)

    // Descendant WindowInsets may already be consumed by the page shell. Keep the platform root
    // as a fallback so horizontal scrollers still reach the physical display edge.
    val rootInsets = LocalView.current.rootWindowInsets
    val systemInsets = rootInsets?.getInsetsIgnoringVisibility(
        android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout(),
    )
    val cutout = rootInsets?.displayCutout
    return HorizontalInsets(
        left = maxOf(left, systemInsets?.left ?: 0, cutout?.safeInsetLeft ?: 0),
        right = maxOf(right, systemInsets?.right ?: 0, cutout?.safeInsetRight ?: 0),
    )
}
