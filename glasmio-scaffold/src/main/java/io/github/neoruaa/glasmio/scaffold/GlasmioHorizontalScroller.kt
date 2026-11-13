package io.github.neoruaa.glasmio.scaffold

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
 * Expands a viewport through the window's physical horizontal safe area and adaptive sidebar.
 *
 * For a vertical lazy parent, keep the regular item content inside the safe area by adding the
 * same insets to its [androidx.compose.foundation.layout.PaddingValues].
 */
@Composable
fun Modifier.glasmioHorizontalEdgeToEdge(
    insets: WindowInsets = WindowInsets.safeDrawing,
    includeSidebar: Boolean = true,
): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val (left, right) = resolveHorizontalInsets(insets, density, layoutDirection, includeSidebar)
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
            placeable.place(-left, 0)
        }
    }
}

/** Adds horizontal safe-area and adaptive-sidebar insets to scrolling content padding. */
@Composable
fun glasmioHorizontalContentPadding(
    contentPadding: PaddingValues = PaddingValues(),
    insets: WindowInsets = WindowInsets.safeDrawing,
    includeSidebar: Boolean = true,
): PaddingValues {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val (left, right) = resolveHorizontalInsets(insets, density, layoutDirection, includeSidebar = false)
    val sidebar = if (includeSidebar) LocalGlasmioSidebarInsets.current else null
    val sidebarStart = if (layoutDirection == LayoutDirection.Ltr) {
        sidebar?.getLeft(density, layoutDirection) ?: 0
    } else {
        sidebar?.getRight(density, layoutDirection) ?: 0
    }
    val startInset = with(density) {
        if (layoutDirection == LayoutDirection.Ltr) left.toDp() else right.toDp()
    } + with(density) { sidebarStart.toDp() }
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
    includeSidebar: Boolean,
): HorizontalInsets {
    val insetLeft = insets.getLeft(density, layoutDirection)
    val insetRight = insets.getRight(density, layoutDirection)
    val sidebar = if (includeSidebar) LocalGlasmioSidebarInsets.current else null
    val sidebarLeft = sidebar?.getLeft(density, layoutDirection) ?: 0
    val sidebarRight = sidebar?.getRight(density, layoutDirection) ?: 0
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return HorizontalInsets(insetLeft + sidebarLeft, insetRight + sidebarRight)
    }

    // windowInsetsPadding consumes Compose's WindowInsets for descendants. Read the platform
    // root as a fallback so a horizontal scroller can still expand through that consumed area.
    val rootInsets = LocalView.current.rootWindowInsets
    val systemInsets = rootInsets?.getInsetsIgnoringVisibility(
        android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout(),
    )
    val cutout = rootInsets?.displayCutout
    return HorizontalInsets(
        left = maxOf(insetLeft, systemInsets?.left ?: 0, cutout?.safeInsetLeft ?: 0) + sidebarLeft,
        right = maxOf(insetRight, systemInsets?.right ?: 0, cutout?.safeInsetRight ?: 0) + sidebarRight,
    )
}
