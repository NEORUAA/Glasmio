package io.github.neoruaa.glasmio.scaffold

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring as composeSpring
import androidx.compose.ui.unit.Dp

object GlasmioSheetDefaults {
    const val Stiffness = 300f

    fun spring() = composeSpring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Stiffness,
    )
}

/** The final nonzero gesture direction resolves a release with zero velocity. */
internal fun sheetDragOpens(velocityY: Float, lastDeltaY: Float, originOpen: Boolean): Boolean = when {
    velocityY < 0f -> true
    velocityY > 0f -> false
    lastDeltaY < 0f -> true
    lastDeltaY > 0f -> false
    else -> originOpen
}

internal fun sheetDragValue(value: Float, deltaPx: Float, density: Float, collapsed: Float, expanded: Float): Float =
    (value - deltaPx / density).coerceIn(collapsed, expanded.coerceAtLeast(collapsed))
