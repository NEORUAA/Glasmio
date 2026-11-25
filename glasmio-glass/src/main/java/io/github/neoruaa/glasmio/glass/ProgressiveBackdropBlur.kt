package io.github.neoruaa.glasmio.glass

import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScope
import io.github.neoruaa.glasmio.glass.render.backdrops.emptyBackdrop
import io.github.neoruaa.glasmio.glass.render.drawPlainBackdrop
import io.github.neoruaa.glasmio.glass.render.effects.blur
import io.github.neoruaa.glasmio.glass.render.effects.runtimeShaderEffect
import io.github.neoruaa.glasmio.glass.render.isRuntimeShaderSupported

private val ProgressiveBlurSamplingScale: (Size, Density) -> Float = { _, _ -> 0.5f }

// A normalized intensity animation keeps the same programs throughout. Only the static
// loop bound differs between families; each pixel still uses its exact local radius.
private enum class ProgressiveBlurKernel(val limit: Int) {
    Short(16), Medium(32), Large(64), ExtraLarge(128), Full(150);

    val horizontal by lazy { progressiveBlurPassShader(isVertical = false, limit = limit) }
    val vertical by lazy { progressiveBlurPassShader(isVertical = true, limit = limit) }
    val verticalFade by lazy { progressiveBlurPassShader(isVertical = true, limit = limit, fadeLowRadius = true) }
}

/**
 * AndroidX progressive blur uses two separable Gaussian passes and evaluates the blur radius at
 * every pixel. This local copy keeps the behavior available until the API reaches our Compose BOM.
 *
 * Source: https://android.googlesource.com/platform/frameworks/support/+/7e1430f6c57df22b6ceeaa66ff4e18b53a67edd9
 */
private fun progressiveBlurPassShader(
    isVertical: Boolean,
    limit: Int,
    fadeLowRadius: Boolean = false,
): String {
    val pairedOffset =
        if (isVertical) "vec2(0.0, i + weightH / weight)"
        else "vec2(i + weightH / weight, 0.0)"
    val oddOffset = if (isVertical) "vec2(0.0, r)" else "vec2(r, 0.0)"
    val axis = if (isVertical) "y" else "x"
    val outputStatement = if (fadeLowRadius) {
        "float radius = blurRadius * intensity;\n" +
            "            float edgeAlpha = clamp(radius / 1.5, 0.0, 1.0);\n" +
            "            if (edgeAlpha <= 0.0) { return half4(0.0); }\n" +
            "            return half4(blur(coord, radius) * edgeAlpha);"
    } else {
        "return half4(blur(coord, blurRadius * intensity));"
    }
    return """
        uniform shader content;
        uniform float blurRadius;
        uniform float extent;
        uniform float startIntensity;
        uniform float endIntensity;
        uniform float2 startPoint;
        uniform float2 endPoint;

        bool inBounds(vec2 p) {
            return p.$axis >= 0.0 && p.$axis < extent;
        }

        vec4 blur(vec2 coord, float radius) {
            float r = floor(radius);
            if (r < 1.0) { return content.eval(coord); }

            float sigma = max(radius / 2.0, 1.0);
            float weightSum = 1.0;
            vec4 result = content.eval(coord);
            float inverseSigmaSquared = 1.0 / (sigma * sigma);
            float weightRatio = exp(-0.5 * inverseSigmaSquared);
            float weightRatioStep = exp(-inverseSigmaSquared);
            float currentWeight = 1.0;

            for (float i = 1.0; i < $limit.0; i += 2.0) {
                if (i >= r) { break; }

                currentWeight *= weightRatio;
                weightRatio *= weightRatioStep;
                float weightL = currentWeight;
                currentWeight *= weightRatio;
                weightRatio *= weightRatioStep;
                float weightH = currentWeight;
                float weight = weightL + weightH;
                vec2 offset = $pairedOffset;

                vec2 newCoord1 = coord - offset;
                if (inBounds(newCoord1)) {
                    weightSum += weight;
                    result += weight * content.eval(newCoord1);
                }

                vec2 newCoord2 = coord + offset;
                if (inBounds(newCoord2)) {
                    weightSum += weight;
                    result += weight * content.eval(newCoord2);
                }
            }

            // Even radii include their final texel in a pair.
            if (r < 150.0 && mod(r, 2.0) == 1.0) {
                float oddWeight = currentWeight * weightRatio;
                vec2 tailOffset = $oddOffset;
                vec2 oddCoord1 = coord - tailOffset;
                if (inBounds(oddCoord1)) {
                    weightSum += oddWeight;
                    result += oddWeight * content.eval(oddCoord1);
                }
                vec2 oddCoord2 = coord + tailOffset;
                if (inBounds(oddCoord2)) {
                    weightSum += oddWeight;
                    result += oddWeight * content.eval(oddCoord2);
                }
            }

            return result / weightSum;
        }

        half4 main(float2 coord) {
            float2 pa = coord - startPoint;
            float2 ba = endPoint - startPoint;
            float fraction = clamp(dot(pa, ba) / max(dot(ba, ba), 0.0001), 0.0, 1.0);
            float intensity = mix(startIntensity, endIntensity, fraction);
            $outputStatement
        }
    """.trimIndent()
}

fun BackdropEffectScope.progressiveBackdropBlur(
    maxRadius: Float,
    startYFraction: Float = 0.42f,
    endYFraction: Float = 1f,
    startIntensity: Float = 1f,
    endIntensity: Float = 0f,
) = progressiveBackdropBlurInternal(
    maxRadius = maxRadius,
    startYFraction = startYFraction,
    endYFraction = endYFraction,
    startIntensity = startIntensity,
    endIntensity = endIntensity,
    fadeLowRadius = false,
)

private fun BackdropEffectScope.progressiveBackdropBlurInternal(
    maxRadius: Float,
    startYFraction: Float = 0.42f,
    endYFraction: Float = 1f,
    startIntensity: Float = 1f,
    endIntensity: Float = 0f,
    fadeLowRadius: Boolean,
) {
    if (maxRadius <= 0f) return
    if (!isRuntimeShaderSupported()) {
        blur(maxRadius)
        return
    }

    val resolvedRadius = maxRadius.coerceAtMost(150f)

    // The gradient is a clamped interpolation. Leave one pixel for rounding at
    // family boundaries. A non-finite reach retains the original full loop bound.
    val maximumReach = resolvedRadius * maxOf(1f, startIntensity, endIntensity)
    val kernel = ProgressiveBlurKernel.entries.firstOrNull { maximumReach + 1f <= it.limit }
        ?: ProgressiveBlurKernel.Full

    fun applyPass(key: String, shader: String, extent: Float) {
        runtimeShaderEffect("$key.${kernel.limit}", shader, "content") {
            setFloatUniform("blurRadius", resolvedRadius)
            setFloatUniform("extent", extent)
            setFloatUniform("startIntensity", startIntensity)
            setFloatUniform("endIntensity", endIntensity)
            setFloatUniform("startPoint", 0f, size.height * startYFraction)
            setFloatUniform("endPoint", 0f, size.height * endYFraction)
        }
    }

    applyPass("IosTopBarProgressiveBlurHorizontal", kernel.horizontal, size.width)
    if (fadeLowRadius) {
        applyPass("IosTopBarProgressiveBlurVerticalFade", kernel.verticalFade, size.height)
    } else {
        applyPass("IosTopBarProgressiveBlurVertical", kernel.vertical, size.height)
    }
}

/** Build the same two-pass separable progressive blur RenderEffect for retained local layers. */
fun createProgressiveBackdropBlurRenderEffect(
    size: Size,
    maxRadius: Float,
    startYFraction: Float = 0.42f,
    endYFraction: Float = 1f,
    startIntensity: Float = 1f,
    endIntensity: Float = 0f,
): RenderEffect? {
    if (maxRadius <= 0f || !size.width.isFinite() || !size.height.isFinite() || size.width <= 0f || size.height <= 0f) {
        return null
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.graphics.RenderEffect.createBlurEffect(maxRadius, maxRadius, Shader.TileMode.CLAMP).asComposeRenderEffect()
        } else null
    }
    val resolvedRadius = maxRadius.coerceAtMost(150f)
    val maximumReach = resolvedRadius * maxOf(1f, startIntensity, endIntensity)
    val kernel = ProgressiveBlurKernel.entries.firstOrNull { maximumReach + 1f <= it.limit }
        ?: ProgressiveBlurKernel.Full

    fun pass(source: String, extent: Float): android.graphics.RenderEffect {
        val shader = android.graphics.RuntimeShader(source).apply {
            setFloatUniform("blurRadius", resolvedRadius)
            setFloatUniform("extent", extent)
            setFloatUniform("startIntensity", startIntensity)
            setFloatUniform("endIntensity", endIntensity)
            setFloatUniform("startPoint", 0f, size.height * startYFraction)
            setFloatUniform("endPoint", 0f, size.height * endYFraction)
        }
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
    }

    val horizontal = pass(kernel.horizontal, size.width)
    val vertical = pass(kernel.vertical, size.height)
    return android.graphics.RenderEffect.createChainEffect(vertical, horizontal).asComposeRenderEffect()
}

/** Compatibility entry point. A progressive blur preserves source colors and never adds tint. */
@Deprecated(
    message = "Tint is no longer applied. Use the untinted overload or Modifier.progressiveBlurBackdrop.",
    replaceWith = ReplaceWith("progressiveBackdropBlur(maxRadius, startYFraction, endYFraction, startIntensity, endIntensity)"),
)
@Suppress("UNUSED_PARAMETER")
fun BackdropEffectScope.progressiveBackdropBlur(
    maxRadius: Float,
    tint: Color,
    tintIntensity: Float = 0f,
    startYFraction: Float = 0.42f,
    endYFraction: Float = 1f,
    startIntensity: Float = 1f,
    endIntensity: Float = 0f,
) = progressiveBackdropBlur(maxRadius, startYFraction, endYFraction, startIntensity, endIntensity)

/** Draw the complete original blur profile; foreground fading belongs to its recorded source. */
fun Modifier.progressiveBlurBackdrop(
    backdrop: Backdrop,
    progress: Float,
    maxRadius: Dp = 10.dp,
): Modifier {
    val clampedProgress = progress.coerceIn(0f, 1f)
    return progressiveBlurBackdrop(
        backdrop = backdrop,
        active = clampedProgress > 0f,
        alpha = { clampedProgress },
        maxRadius = maxRadius,
    )
}

/**
 * Draw-phase progressive blur modifier: [alpha] is read only in [graphicsLayer] so continuous
 * scroll-edge updates never recompose callers, while [active] detaches the backdrop at rest.
 */
fun Modifier.progressiveBlurBackdrop(
    backdrop: Backdrop,
    active: Boolean,
    alpha: () -> Float,
    maxRadius: Dp = 10.dp,
): Modifier = graphicsLayer {
    this.alpha = alpha().coerceIn(0f, 1f)
}.drawPlainBackdrop(
    // HWUI prepares invisible child trees too. Keep the filter node and its
    // shader cache, but detach the unused source graph at the zero endpoint.
    backdrop = if (!active) emptyBackdrop() else backdrop,
    shape = { RectangleShape },
    samplingScale = ProgressiveBlurSamplingScale,
    effects = { progressiveBackdropBlurInternal(maxRadius.toPx(), fadeLowRadius = true) },
)

/**
 * Apply to foreground content before recording the blur source, leaving its background separate.
 * At full reveal alpha rises linearly from 25% at [headerTop] to 100% at [headerHeight].
 * The end stays aligned with the solid header's bottom; the Gaussian blur remains independent.
 */
fun Modifier.scrollEdgeContentFade(
    progress: Float,
    headerHeight: Dp,
    headerTop: Dp = 0.dp,
): Modifier {
    val reveal = progress.coerceIn(0f, 1f)
    if (reveal == 0f) return this
    return scrollEdgeContentFade({ reveal }, headerHeight, headerTop)
}

/**
 * Draw-phase scroll-edge fade. When [solidBackgroundColor] is an opaque solid color matching the
 * backdrop behind the content, blending `(1 - m) * B + m * (F_a + (1 - a) * B)` via a bounded
 * top-band SrcOver gradient is algebraically identical to DstIn on an offscreen layer while
 * avoiding any full-screen offscreen render target or second content pass.
 */
fun Modifier.scrollEdgeContentFade(
    progress: () -> Float,
    headerHeight: Dp,
    headerTop: Dp = 0.dp,
    solidBackgroundColor: Color = Color.Unspecified,
): Modifier = this then ScrollEdgeContentFadeElement(progress, headerHeight, headerTop, solidBackgroundColor)

private class ScrollEdgeContentFadeElement(
    val progress: () -> Float,
    val headerHeight: Dp,
    val headerTop: Dp,
    val solidBackgroundColor: Color,
) : ModifierNodeElement<ScrollEdgeContentFadeNode>() {
    override fun create(): ScrollEdgeContentFadeNode =
        ScrollEdgeContentFadeNode(progress, headerHeight, headerTop, solidBackgroundColor)

    override fun update(node: ScrollEdgeContentFadeNode) {
        node.progress = progress
        node.headerHeight = headerHeight
        node.headerTop = headerTop
        node.solidBackgroundColor = solidBackgroundColor
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "scrollEdgeContentFade"
        properties["headerHeight"] = headerHeight
        properties["headerTop"] = headerTop
        properties["solidBackgroundColor"] = solidBackgroundColor
    }

    override fun equals(other: Any?): Boolean = this === other || (
        other is ScrollEdgeContentFadeElement &&
            progress == other.progress &&
            headerHeight == other.headerHeight &&
            headerTop == other.headerTop &&
            solidBackgroundColor == other.solidBackgroundColor
        )

    override fun hashCode(): Int {
        var result = progress.hashCode()
        result = 31 * result + headerHeight.hashCode()
        result = 31 * result + headerTop.hashCode()
        result = 31 * result + solidBackgroundColor.hashCode()
        return result
    }
}

private class ScrollEdgeContentFadeNode(
    var progress: () -> Float,
    var headerHeight: Dp,
    var headerTop: Dp,
    var solidBackgroundColor: Color,
) : DrawModifierNode, Modifier.Node() {
    private var cachedReveal = Float.NaN
    private var cachedTop = Float.NaN
    private var cachedHeader = Float.NaN
    private var cachedSolidColor = Color.Unspecified
    private var cachedMask: Brush? = null

    private fun maskBrush(reveal: Float, top: Float, header: Float, solidColor: Color): Brush {
        val existing = cachedMask
        if (existing != null &&
            cachedReveal == reveal &&
            cachedTop == top &&
            cachedHeader == header &&
            cachedSolidColor == solidColor
        ) {
            return existing
        }
        val brush = if (solidColor != Color.Unspecified && solidColor.alpha == 1f) {
            Brush.verticalGradient(
                colors = listOf(solidColor.copy(alpha = reveal * 0.75f), solidColor.copy(alpha = 0f)),
                startY = top,
                endY = header,
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 1f - reveal * 0.75f), Color.White),
                startY = top,
                endY = header,
            )
        }
        cachedReveal = reveal
        cachedTop = top
        cachedHeader = header
        cachedSolidColor = solidColor
        cachedMask = brush
        return brush
    }

    override fun ContentDrawScope.draw() {
        val reveal = progress().coerceIn(0f, 1f)
        if (reveal <= 0f) {
            drawContent()
            return
        }
        val header = headerHeight.toPx().coerceAtLeast(1f)
        val top = headerTop.toPx().coerceIn(0f, header - 1f)
        val maskHeight = minOf(size.height, ceil(header))
        val solid = solidBackgroundColor
        if (solid != Color.Unspecified && solid.alpha == 1f) {
            // Single content pass with zero offscreen allocation: exact SrcOver equivalent of DstIn over opaque solid B.
            drawContent()
            if (maskHeight > 0f) {
                drawRect(maskBrush(reveal, top, header, solid), size = Size(size.width, maskHeight))
            }
            return
        }
        // Non-solid/wallpaper/glass background: single content pass inside offscreen saveLayer + DstIn mask.
        val nativeCanvas = drawContext.canvas.nativeCanvas
        val save = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, null)
        try {
            drawContent()
            val extent = if (nativeCanvas.isHardwareAccelerated) Size(size.width, maskHeight) else size
            drawRect(maskBrush(reveal, top, header, Color.Unspecified), size = extent, blendMode = BlendMode.DstIn)
        } finally {
            nativeCanvas.restoreToCount(save)
        }
    }
}
