package io.github.neoruaa.glasmio.glass.render.shadow

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** First exterior shadow of the native regular-glass recipe. */
@Immutable
data class Shadow(
    val radius: Dp = 4.dp,
    val offset: DpOffset = DpOffset(0.dp, 8.dp),
    val color: Color = Color.Black.copy(alpha = 0.04f),
    val alpha: Float = 1f,
    val blendMode: BlendMode = BlendMode.SrcOver,
    /** Interpret radius as a native CALayer Gaussian sigma instead of the custom blur radius. */
    val radiusIsSigma: Boolean = false,
) {
    companion object { @Stable val Default = Shadow() }
}
