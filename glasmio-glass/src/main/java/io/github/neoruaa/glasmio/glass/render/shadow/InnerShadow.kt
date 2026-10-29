package io.github.neoruaa.glasmio.glass.render.shadow

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp

/** Native regular-glass ring: sigma 5 pt, offset 8 pt, width 4 pt, black opacity .06. */
@Immutable
data class InnerShadow(
    val radius: Dp = 5.dp,
    val offset: DpOffset = DpOffset(0.dp, 8.dp),
    val color: Color = Color.Black.copy(alpha = 0.06f),
    val alpha: Float = 1f,
    val blendMode: BlendMode = BlendMode.SrcOver,
) {
    companion object { @Stable val Default = InnerShadow() }
}

@Stable
fun lerp(start: InnerShadow, stop: InnerShadow, fraction: Float): InnerShadow = InnerShadow(
    radius = lerp(start.radius, stop.radius, fraction),
    offset = lerp(start.offset, stop.offset, fraction),
    color = lerp(start.color, stop.color, fraction),
    alpha = lerp(start.alpha, stop.alpha, fraction),
    blendMode = if (fraction < 0.5f) start.blendMode else stop.blendMode,
)
