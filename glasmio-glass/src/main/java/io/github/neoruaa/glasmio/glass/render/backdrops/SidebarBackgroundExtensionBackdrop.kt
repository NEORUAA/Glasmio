package io.github.neoruaa.glasmio.glass.render.backdrops

import android.graphics.Rect as AndroidRect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScope
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScopeImpl
import io.github.neoruaa.glasmio.glass.render.RuntimeShader
import io.github.neoruaa.glasmio.glass.render.cachedResource
import io.github.neoruaa.glasmio.glass.render.effects.effect
import io.github.neoruaa.glasmio.glass.render.internal.RuntimeShaderEffect
import io.github.neoruaa.glasmio.glass.render.isRuntimeShaderSupported
import kotlin.math.max

private val SidebarReflectionFalloff = Array(65) { index ->
    val distance = index / 64f
    // Normalize a shifted reciprocal to 1 at the content edge and 0 at the outer bound.
    val alpha = (1f - distance) / (1f + 3f * distance)
    distance to Color.White.copy(alpha = alpha.coerceIn(0f, 1f))
}

/**
 * The horizontal, single-edge background extension used beneath a regular-width sidebar.
 * Bounds use window pixels and are evaluated during capture/effect updates, not composition.
 * [isLeftEdge] identifies the physical edge, including when an RTL sidebar is morphing.
 * The host must retain at least as much content width as extension width; the native
 * oversized cubic-mesh fallback and its top/bottom/corner portals are separate policies.
 */
@Composable
fun rememberSidebarBackgroundExtensionBackdrop(
    source: Backdrop,
    contentBoundsInWindow: () -> Rect,
    consumerBoundsInWindow: () -> Rect,
    isLeftEdge: Boolean = true,
): Backdrop {
    val contentBounds = rememberUpdatedState(contentBoundsInWindow)
    val consumerBounds = rememberUpdatedState(consumerBoundsInWindow)
    return remember(source, isLeftEdge) {
        SidebarBackgroundExtensionBackdrop(
            source,
            { contentBounds.value() },
            { consumerBounds.value() },
            isLeftEdge,
        )
    }
}

@Stable
private class SidebarBackgroundExtensionBackdrop(
    private val source: Backdrop,
    val contentBoundsInWindow: () -> Rect,
    val consumerBoundsInWindow: () -> Rect,
    val isLeftEdge: Boolean,
) : Backdrop {
    private val captureClip = AndroidRect()
    private val reflectionPaint = Paint()
    private var reflectionMask: Brush? = null
    private var maskEdge = Float.NaN
    private var maskExtent = Float.NaN

    override val isCoordinatesDependent: Boolean get() = source.isCoordinatesDependent

    override fun observeSourceChanges(observer: () -> Unit): () -> Unit =
        source.observeSourceChanges(observer)

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        // Keep the real full-window background beneath the reflected content. The portal
        // contributes only at the adjoining edge, rather than replacing the whole pane.
        with(source) { drawBackdrop(density, coordinates, layerBlock) }
        val content = contentBoundsInWindow()
        val consumer = consumerBoundsInWindow()
        if (!hasExtension(content, consumer, isLeftEdge)) return
        val edge = (if (isLeftEdge) content.left else content.right) - consumer.left
        if (!drawContext.canvas.nativeCanvas.getClipBounds(captureClip)) return
        // Keep the reflection near the adjoining content while leaving space for a soft tail.
        val extent = minOf(content.width * .15f, with(density) { 130.dp.toPx() })
        if (maskEdge != edge || maskExtent != extent) {
            maskEdge = edge
            maskExtent = extent
            reflectionMask = Brush.linearGradient(
                *SidebarReflectionFalloff,
                start = Offset(edge, 0f),
                end = Offset(edge + if (isLeftEdge) -extent else extent, 0f),
            )
        }
        val left = max(if (isLeftEdge) edge - extent else edge, captureClip.left.toFloat())
        val right = minOf(if (isLeftEdge) edge else edge + extent, captureClip.right.toFloat())
        if (right <= left) return
        val top = captureClip.top.toFloat()
        val bottom = captureClip.bottom.toFloat()
        val canvas = drawContext.canvas
        // Only this narrow portal needs alpha isolation; the shared source remains live.
        canvas.saveLayer(Rect(left, top, right, bottom), reflectionPaint)
        try {
            clipRect(left, top, right, bottom) {
                withTransform({ scale(-1f, 1f, Offset(edge, 0f)) }) {
                    with(source) { drawBackdrop(density, coordinates, layerBlock) }
                }
                drawRect(requireNotNull(reflectionMask), topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top), blendMode = BlendMode.DstIn)
            }
        } finally {
            canvas.restore()
        }
    }
}

private fun hasExtension(content: Rect, consumer: Rect, isLeftEdge: Boolean): Boolean =
    content.left.isFinite() && content.right.isFinite() && content.width > 0f &&
        consumer.left.isFinite() && consumer.right.isFinite() && consumer.width > 0f &&
        (if (isLeftEdge) content.left > consumer.left else content.right < consumer.right)

/**
 * Apply before the sidebar glass recipe, after reserving that recipe's complete capture padding.
 * Native CABackgroundExtensionView uses a 10 pt variable blur with a 0.1-source-width mask.
 * Leading mask alpha rises from zero at the content edge to one outside that edge; trailing
 * is its reflection. The original content side is unfiltered.
 */
internal fun BackdropEffectScope.applySidebarBackgroundExtension(backdrop: Backdrop) {
    val extension = backdrop as? SidebarBackgroundExtensionBackdrop ?: return
    if (!isRuntimeShaderSupported()) return
    val content = extension.contentBoundsInWindow()
    val consumer = extension.consumerBoundsInWindow()
    if (!hasExtension(content, consumer, extension.isLeftEdge)) return
    val maximumSigma = 10.dp.toPx()
    // Gaussian support is three sigma; the host reserves its following material passes first.
    padding = max(padding, maximumSigma * 3f)
    val samplingScale = (this as? BackdropEffectScopeImpl)?.samplingScale ?: 1f
    val edge = ((if (extension.isLeftEdge) content.left else content.right) - consumer.left) * samplingScale + padding
    val maskWidth = max(content.width * samplingScale * .1f, .001f)
    val side = if (extension.isLeftEdge) 1f else -1f
    val passes = cachedResource("Glasmio.SidebarBackgroundExtension.Passes") { ExtensionBlurPasses(this) }
    passes.update(edge, maskWidth, maximumSigma, side)
    val owned = this as? BackdropEffectScopeImpl
    if (owned != null) {
        // Materialize separable passes once each. Chaining both into a later sampler would
        // multiply its source fetches by 13x13 instead of reusing two completed textures.
        owned.materializedInputEffects = owned.materializedInputEffects +
            listOfNotNull(renderEffect, passes.horizontal, passes.vertical)
        renderEffect = null
    } else {
        effect(passes.horizontal)
        effect(passes.vertical)
    }
}

private class ExtensionBlurPasses(scope: BackdropEffectScope) {
    private val horizontalShader = scope.obtainRuntimeShader("Glasmio.SidebarExtension.Horizontal", ExtensionBlurShader)
    private val verticalShader = scope.obtainRuntimeShader("Glasmio.SidebarExtension.Vertical", ExtensionBlurShader)
    lateinit var horizontal: RenderEffect
        private set
    lateinit var vertical: RenderEffect
        private set
    private var edge = Float.NaN
    private var maskWidth = Float.NaN
    private var sigma = Float.NaN
    private var side = Float.NaN

    fun update(edge: Float, maskWidth: Float, sigma: Float, side: Float) {
        if (this.edge == edge && this.maskWidth == maskWidth && this.sigma == sigma && this.side == side) return
        this.edge = edge
        this.maskWidth = maskWidth
        this.sigma = sigma
        this.side = side
        horizontalShader.update(edge, maskWidth, sigma, side, 1f, 0f)
        verticalShader.update(edge, maskWidth, sigma, side, 0f, 1f)
        // RenderEffect snapshots the RuntimeShader builder. Create filters after uniforms
        // are set, and retain them until geometry changes rather than mutating stale copies.
        horizontal = RuntimeShaderEffect(horizontalShader, "content")
        vertical = RuntimeShaderEffect(verticalShader, "content")
    }

    private fun RuntimeShader.update(
        edge: Float, maskWidth: Float, sigma: Float, side: Float, x: Float, y: Float,
    ) {
        setFloatUniform("edge", edge)
        setFloatUniform("maskWidth", maskWidth)
        setFloatUniform("maximumSigma", sigma)
        setFloatUniform("outsideDirection", side)
        setFloatUniform("axis", x, y)
    }
}

// Spatial radius and mask follow native CABackgroundExtensionView. Its private GPU kernel is
// not recovered; this normalized separable Gaussian is the Android filter implementation.
// Average premultiplied HWUI working-space pixels without clipping RGB or forcing sRGB, so
// wide/HDR samples retain their range and use the same working space as ordinary BlurEffect.
private const val ExtensionBlurShader = """
uniform shader content;
uniform float edge;
uniform float maskWidth;
uniform float maximumSigma;
uniform float outsideDirection;
uniform float2 axis;

half4 main(float2 coord) {
    float outside = (edge - coord.x) * outsideDirection;
    if (outside <= 0.0) return content.eval(coord);
    float sigma = maximumSigma * clamp(outside / maskWidth, 0.0, 1.0);
    if (sigma <= .01) return content.eval(coord);
    float2 step = axis * (sigma * .5);
    float4 color = float4(content.eval(coord)) * .1996756275;
    color += (float4(content.eval(coord + step)) + float4(content.eval(coord - step))) * .1762131228;
    color += (float4(content.eval(coord + step * 2.0)) + float4(content.eval(coord - step * 2.0))) * .1211093901;
    color += (float4(content.eval(coord + step * 3.0)) + float4(content.eval(coord - step * 3.0))) * .0648251851;
    color += (float4(content.eval(coord + step * 4.0)) + float4(content.eval(coord - step * 4.0))) * .0270231576;
    color += (float4(content.eval(coord + step * 5.0)) + float4(content.eval(coord - step * 5.0))) * .0087731348;
    color += (float4(content.eval(coord + step * 6.0)) + float4(content.eval(coord - step * 6.0))) * .0022181959;
    return half4(color);
}
"""
