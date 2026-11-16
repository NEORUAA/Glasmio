package io.github.neoruaa.glasmio.scaffold

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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Expands a viewport through the page's hardware-safe margin and adaptive sidebar.
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
    val (left, right) = resolvePageHorizontalInsets(insets, density, layoutDirection, includeSidebar)
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

/** Adds page margins while avoiding extra hardware-safe space beside the sidebar. */
@Composable
fun glasmioHorizontalContentPadding(
    contentPadding: PaddingValues = PaddingValues(),
    insets: WindowInsets = WindowInsets.safeDrawing,
    includeSidebar: Boolean = true,
): PaddingValues {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val horizontalInsets = resolvePageHorizontalInsets(insets, density, layoutDirection, includeSidebar)
    return horizontalContentPadding(contentPadding, horizontalInsets, density, layoutDirection)
}

internal fun horizontalContentPadding(
    contentPadding: PaddingValues,
    insets: HorizontalInsets,
    density: Density,
    layoutDirection: LayoutDirection,
): PaddingValues {
    val (left, right) = insets
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
