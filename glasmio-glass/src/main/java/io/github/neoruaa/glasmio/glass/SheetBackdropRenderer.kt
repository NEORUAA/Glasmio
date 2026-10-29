package io.github.neoruaa.glasmio.glass

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.icons.*

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import io.github.neoruaa.glasmio.glass.render.Backdrop
import kotlin.math.ceil

@Composable
fun rememberSheetBackdropRenderer(backdrop: Backdrop, blurRadius: Dp): SheetBackdropRenderer {
    val density = LocalDensity.current
    val layer = rememberGraphicsLayer()
    return remember(backdrop, blurRadius, density, layer) {
        SheetBackdropRenderer(backdrop, density, with(density) { blurRadius.toPx() }, layer)
    }
}

/** Only the blurred background is filtered at reduced resolution; sheet content stays native. */
class SheetBackdropRenderer(
    private val backdrop: Backdrop,
    private val density: Density,
    private val blurRadius: Float,
    private val layer: GraphicsLayer,
) : Backdrop {
    override val isCoordinatesDependent: Boolean get() = true

    override fun observeSourceChanges(observer: () -> Unit): () -> Unit = backdrop.observeSourceChanges(observer)

    private val renderScale = 0.25f

    init {
        layer.clip = true
        layer.renderEffect = RenderEffect.createBlurEffect(
            blurRadius * renderScale, blurRadius * renderScale, Shader.TileMode.CLAMP,
        ).asComposeRenderEffect()
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val coordinates = coordinates?.takeIf { it.isAttached } ?: return
        val logicalSize = size
        // Include the blur kernel outside the sheet to avoid clamped edges while it slides.
        val padding = ceil(blurRadius * 3f)
        val bufferSize = IntSize(
            ceil((logicalSize.width + padding * 2f) * renderScale).toInt().coerceAtLeast(1),
            ceil((logicalSize.height + padding * 2f) * renderScale).toInt().coerceAtLeast(1),
        )
        // Keep evaluating screen offsets and direct backdrop state. The shared player
        // sources retain their own rasterized child layers, so this records only a small
        // composition of those layers without freezing source attachment or window movement.
        layer.record(bufferSize) {
            withTransform({
                scale(renderScale, renderScale, Offset.Zero)
                translate(padding, padding)
            }) {
                val previousSize = drawContext.size
                drawContext.size = logicalSize
                try {
                    with(backdrop) {
                        drawBackdrop(this@SheetBackdropRenderer.density, coordinates, layerBlock)
                    }
                } finally {
                    drawContext.size = previousSize
                }
            }
        }
        withTransform({
            translate(-padding, -padding)
            scale(1f / renderScale, 1f / renderScale, Offset.Zero)
        }) { drawLayer(layer) }
    }
}
