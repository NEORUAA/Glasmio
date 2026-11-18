package io.github.neoruaa.glasmio.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

internal enum class AnchoredSide { Above, Below, Left, Right }

internal data class AnchoredPlacement(
    val body: IntRect,
    val side: AnchoredSide,
    val tip: IntOffset,
)

internal fun placeAnchoredSurface(
    anchor: IntRect,
    viewport: IntRect,
    body: IntSize,
    sides: List<AnchoredSide>,
    separation: Int,
    pointerInset: Int,
    preferMaximumVisibleArea: Boolean = false,
): AnchoredPlacement {
    val viewportRight = viewport.right.coerceAtLeast(viewport.left)
    val viewportBottom = viewport.bottom.coerceAtLeast(viewport.top)
    val width = body.width.coerceIn(0, viewportRight - viewport.left)
    val height = body.height.coerceIn(0, viewportBottom - viewport.top)
    val gap = separation.coerceAtLeast(0)

    fun room(side: AnchoredSide): Int = when (side) {
        AnchoredSide.Above -> anchor.top - viewport.top
        AnchoredSide.Below -> viewportBottom - anchor.bottom
        AnchoredSide.Left -> anchor.left - viewport.left
        AnchoredSide.Right -> viewportRight - anchor.right
    }

    val anchorCenter = anchor.center
    fun origin(side: AnchoredSide): IntOffset {
        val x = when (side) {
            AnchoredSide.Left -> anchor.left - gap - width
            AnchoredSide.Right -> anchor.right + gap
            else -> anchorCenter.x - width / 2
        }
        val y = when (side) {
            AnchoredSide.Above -> anchor.top - gap - height
            AnchoredSide.Below -> anchor.bottom + gap
            else -> anchorCenter.y - height / 2
        }
        return IntOffset(x, y)
    }
    val preferred = if (preferMaximumVisibleArea) {
        sides.maxByOrNull { candidate ->
            val p = origin(candidate)
            val visibleWidth = (minOf(p.x + width, viewportRight) - maxOf(p.x, viewport.left)).coerceAtLeast(0)
            val visibleHeight = (minOf(p.y + height, viewportBottom) - maxOf(p.y, viewport.top)).coerceAtLeast(0)
            visibleWidth.toLong() * visibleHeight
        }
    } else {
        sides.firstOrNull { candidate ->
            val extent = when (candidate) {
                AnchoredSide.Above, AnchoredSide.Below -> height
                AnchoredSide.Left, AnchoredSide.Right -> width
            }
            room(candidate) - gap >= extent
        } ?: sides.maxByOrNull(::room)
    }
    val side = preferred ?: AnchoredSide.Below
    val requested = origin(side)
    val left = requested.x.coerceIn(viewport.left, viewportRight - width)
    val top = requested.y.coerceIn(viewport.top, viewportBottom - height)
    val placedBody = IntRect(left, top, left + width, top + height)
    val insetX = pointerInset.coerceIn(0, width / 2)
    val insetY = pointerInset.coerceIn(0, height / 2)
    val tipX = anchorCenter.x.coerceIn(placedBody.left + insetX, placedBody.right - insetX)
    val tipY = anchorCenter.y.coerceIn(placedBody.top + insetY, placedBody.bottom - insetY)
    val tip = when (side) {
        AnchoredSide.Above -> IntOffset(tipX, placedBody.bottom + gap)
        AnchoredSide.Below -> IntOffset(tipX, placedBody.top - gap)
        AnchoredSide.Left -> IntOffset(placedBody.right + gap, tipY)
        AnchoredSide.Right -> IntOffset(placedBody.left - gap, tipY)
    }
    return AnchoredPlacement(placedBody, side, tip)
}
