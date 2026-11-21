package io.github.neoruaa.glasmio.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
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

// A normalized intensity animation keeps the same programs throughout. Only the static
// loop bound differs between families; each pixel still uses its exact local radius.
private enum class ProgressiveBlurKernel(val limit: Int) {
    Short(16), Medium(32), Large(64), ExtraLarge(128), Full(150);

    val horizontal by lazy { progressiveBlurPassShader(isVertical = false, limit = limit) }
    val vertical by lazy { progressiveBlurPassShader(isVertical = true, limit = limit) }
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
): String {
    val pairedOffset =
        if (isVertical) "vec2(0.0, i + weightH / weight)"
        else "vec2(i + weightH / weight, 0.0)"
    val oddOffset = if (isVertical) "vec2(0.0, r)" else "vec2(r, 0.0)"
    val axis = if (isVertical) "y" else "x"
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
            return half4(blur(coord, blurRadius * intensity));
        }
    """.trimIndent()
}

fun BackdropEffectScope.progressiveBackdropBlur(
    maxRadius: Float,
    startYFraction: Float = 0.42f,
    endYFraction: Float = 1f,
    startIntensity: Float = 1f,
    endIntensity: Float = 0f,
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
    applyPass("IosTopBarProgressiveBlurVertical", kernel.vertical, size.height)
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
    return graphicsLayer {
        alpha = clampedProgress
    }.drawPlainBackdrop(
        // HWUI prepares invisible child trees too. Keep the filter node and its
        // shader cache, but detach the unused source graph at the zero endpoint.
        backdrop = if (clampedProgress == 0f) emptyBackdrop() else backdrop,
        shape = { RectangleShape },
        effects = { progressiveBackdropBlur(maxRadius.toPx()) },
    )
}

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
    return graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithCache {
        val header = headerHeight.toPx().coerceAtLeast(1f)
        val top = headerTop.toPx().coerceIn(0f, header - 1f)
        val opacityMask = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 1f - reveal * 0.75f), Color.White),
            startY = top,
            endY = header,
        )
        // Past the gradient, DstIn multiplies every channel by one. End the mask rectangle
        // on the next whole pixel while retaining the exact fractional gradient coordinates.
        val maskSize = Size(size.width, minOf(size.height, ceil(header)))
        onDrawWithContent {
            drawContent()
            val extent = if (drawContext.canvas.nativeCanvas.isHardwareAccelerated) maskSize else size
            drawRect(opacityMask, size = extent, blendMode = BlendMode.DstIn)
        }
    }
}
