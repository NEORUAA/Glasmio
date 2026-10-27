package io.github.neoruaa.glasmio.ui

import androidx.compose.ui.geometry.Rect

/** Selects the usable side of an anchor, then keeps the overlay inside the safe viewport. */
internal fun editingOverlayTop(
    anchor: Rect,
    viewport: Rect,
    height: Float,
    gap: Float,
    preferAbove: Boolean,
): Float {
    require(height.isFinite() && height >= 0f) { "Overlay height must be finite and non-negative." }
    require(gap.isFinite() && gap >= 0f) { "Overlay gap must be finite and non-negative." }
    val above = anchor.top - gap - height
    val below = anchor.bottom + gap
    val aboveFits = above >= viewport.top && above + height <= viewport.bottom
    val belowFits = below >= viewport.top && below + height <= viewport.bottom
    val aboveSpace = (anchor.top - gap - viewport.top).coerceAtLeast(0f)
    val belowSpace = (viewport.bottom - anchor.bottom - gap).coerceAtLeast(0f)
    val useAbove = when {
        preferAbove && aboveFits -> true
        !preferAbove && belowFits -> false
        aboveFits -> true
        belowFits -> false
        aboveSpace == belowSpace -> preferAbove
        else -> aboveSpace > belowSpace
    }
    val maximumTop = (viewport.bottom - height).coerceAtLeast(viewport.top)
    return (if (useAbove) above else below).coerceIn(viewport.top, maximumTop)
}
