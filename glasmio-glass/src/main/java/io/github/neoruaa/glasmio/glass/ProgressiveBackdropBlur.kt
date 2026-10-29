package io.github.neoruaa.glasmio.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScope
import io.github.neoruaa.glasmio.glass.render.drawPlainBackdrop
import io.github.neoruaa.glasmio.glass.render.effects.blur
import io.github.neoruaa.glasmio.glass.render.effects.runtimeShaderEffect
import io.github.neoruaa.glasmio.glass.render.isRuntimeShaderSupported

private val HorizontalProgressiveBlurShader = progressiveBlurPassShader(isVertical = false)
private val VerticalProgressiveBlurShader = progressiveBlurPassShader(isVertical = true)

/**
 * AndroidX progressive blur uses two separable Gaussian passes and evaluates the blur radius at
 * every pixel. This local copy keeps the behavior available until the API reaches our Compose BOM.
 *
 * Source: https://android.googlesource.com/platform/frameworks/support/+/7e1430f6c57df22b6ceeaa66ff4e18b53a67edd9
 */
private fun progressiveBlurPassShader(
    isVertical: Boolean,
): String {
    val pairedOffset =
        if (isVertical) "vec2(0.0, i + weightH / weight)"
        else "vec2(i + weightH / weight, 0.0)"
    val oddOffset = if (isVertical) "vec2(0.0, r)" else "vec2(r, 0.0)"
    val boundsCheck =
        if (isVertical) {
            "return step(bounds.y, sampleCoord.y) * (1.0 - step(bounds.w, sampleCoord.y));"
        } else {
            "return step(bounds.x, sampleCoord.x) * (1.0 - step(bounds.z, sampleCoord.x));"
        }
    return """
        uniform shader content;
        uniform float blurRadius;
        uniform float4 crop;
        uniform float unbounded;
        uniform float startIntensity;
        uniform float endIntensity;
        uniform float2 startPoint;
        uniform float2 endPoint;
        const float maxRadius = 150.0;

        float inBoundsOnMovedAxis(vec2 sampleCoord, float4 bounds) {
            $boundsCheck
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

            for (float i = 1.0; i < maxRadius; i += 2.0) {
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
                float mask1 = inBoundsOnMovedAxis(newCoord1, crop);
                weightSum += weight * max(mask1, unbounded);
                if (mask1 > 0.0) {
                    result += weight * content.eval(newCoord1);
                }

                vec2 newCoord2 = coord + offset;
                float mask2 = inBoundsOnMovedAxis(newCoord2, crop);
                weightSum += weight * max(mask2, unbounded);
                if (mask2 > 0.0) {
                    result += weight * content.eval(newCoord2);
                }
            }

            float oddMask = mod(r, 2.0) * (1.0 - step(maxRadius, r));
            float oddWeight = currentWeight * weightRatio * oddMask;
            vec2 tailOffset = $oddOffset;

            vec2 oddCoord1 = coord - tailOffset;
            float oddBounds1 = inBoundsOnMovedAxis(oddCoord1, crop);
            weightSum += oddWeight * max(oddBounds1, unbounded);
            if (oddBounds1 > 0.0) {
                result += oddWeight * content.eval(oddCoord1);
            }

            vec2 oddCoord2 = coord + tailOffset;
            float oddBounds2 = inBoundsOnMovedAxis(oddCoord2, crop);
            weightSum += oddWeight * max(oddBounds2, unbounded);
            if (oddBounds2 > 0.0) {
                result += oddWeight * content.eval(oddCoord2);
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

    fun applyPass(key: String, shader: String) {
        runtimeShaderEffect(key, shader, "content") {
            setFloatUniform("blurRadius", resolvedRadius)
            setFloatUniform("crop", 0f, 0f, size.width, size.height)
            setFloatUniform("unbounded", 0f)
            setFloatUniform("startIntensity", startIntensity)
            setFloatUniform("endIntensity", endIntensity)
            setFloatUniform("startPoint", 0f, size.height * startYFraction)
            setFloatUniform("endPoint", 0f, size.height * endYFraction)
        }
    }

    applyPass("IosTopBarProgressiveBlurHorizontal", HorizontalProgressiveBlurShader)
    applyPass(
        key = "IosTopBarProgressiveBlurVertical",
        shader = VerticalProgressiveBlurShader,
    )
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
): Modifier = graphicsLayer {
    alpha = progress.coerceIn(0f, 1f)
}.drawPlainBackdrop(
    backdrop = backdrop,
    shape = { RectangleShape },
    effects = { progressiveBackdropBlur(maxRadius.toPx()) },
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
        onDrawWithContent {
            drawContent()
            drawRect(opacityMask, blendMode = BlendMode.DstIn)
        }
    }
}
