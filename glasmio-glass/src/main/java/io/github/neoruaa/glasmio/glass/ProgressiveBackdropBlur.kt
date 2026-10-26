package io.github.neoruaa.glasmio.glass

import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.isRuntimeShaderSupported

private val HorizontalProgressiveBlurShader = progressiveBlurPassShader(isVertical = false)
private val VerticalProgressiveBlurShader = progressiveBlurPassShader(
    isVertical = true,
    appliesTint = true,
)

/**
 * AndroidX progressive blur uses two separable Gaussian passes and evaluates the blur radius at
 * every pixel. This local copy keeps the behavior available until the API reaches our Compose BOM.
 *
 * Source: https://android.googlesource.com/platform/frameworks/support/+/7e1430f6c57df22b6ceeaa66ff4e18b53a67edd9
 */
private fun progressiveBlurPassShader(
    isVertical: Boolean,
    appliesTint: Boolean = false,
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
    val tintUniforms = if (appliesTint) {
        """
        layout(color) uniform half4 tint;
        uniform float tintIntensity;
        """.trimIndent()
    } else {
        ""
    }
    val output = if (appliesTint) {
        """
            half4 blurred = half4(blur(coord, blurRadius * intensity));
            float tintMask = smoothstep(crop.w, crop.w * 0.42, coord.y);
            return mix(blurred, tint, tintIntensity * tintMask);
        """.trimIndent()
    } else {
        "return half4(blur(coord, blurRadius * intensity));"
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
        $tintUniforms
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
            $output
        }
    """.trimIndent()
}

fun BackdropEffectScope.progressiveBackdropBlur(
    maxRadius: Float,
    tint: Color = Color.Transparent,
    tintIntensity: Float = 0f,
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

    fun applyPass(key: String, shader: String, appliesTint: Boolean = false) {
        runtimeShaderEffect(key, shader, "content") {
            setFloatUniform("blurRadius", resolvedRadius)
            setFloatUniform("crop", 0f, 0f, size.width, size.height)
            setFloatUniform("unbounded", 0f)
            setFloatUniform("startIntensity", startIntensity)
            setFloatUniform("endIntensity", endIntensity)
            setFloatUniform("startPoint", 0f, size.height * startYFraction)
            setFloatUniform("endPoint", 0f, size.height * endYFraction)
            if (appliesTint) {
                setColorUniform("tint", tint)
                setFloatUniform("tintIntensity", tintIntensity)
            }
        }
    }

    applyPass("IosTopBarProgressiveBlurHorizontal", HorizontalProgressiveBlurShader)
    applyPass(
        key = "IosTopBarProgressiveBlurVerticalTint",
        shader = VerticalProgressiveBlurShader,
        appliesTint = true,
    )
}

