package io.github.neoruaa.glasmio.glass

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import io.github.neoruaa.glasmio.glass.render.isRuntimeShaderSupported
import kotlin.math.sqrt

// Single fixed-loop continuous separable Gaussian shader shared across all animated blurs.
// Skia's built-in RenderEffect.createBlurEffect specializes a distinct Vulkan pipeline per
// integer kernel radius bucket, causing 10-20 cold shader compiles during a single sweep.
// Passing axis (H/V) and bounds (CLAMP/DECAL) as uniforms keeps every animated blur in one
// compact FP16 pipeline (max 16 paired bilinear taps = 32px half-width, matching the
// progressive blur Medium kernel) across all radii, tile modes, and callers.
//
// IMPORTANT: RuntimeShader RenderEffects report unbounded output to Skia. Callers MUST apply
// this effect only to a clipped GraphicsLayer (clip = true) whose bounds already include any
// required blur halo padding, otherwise Skia expands intermediate textures to the full window.
private const val AnimatedBlurMaxSinglePassSigma = 16f
private const val AnimatedBlurDecalExtent = 1_000_000f

internal const val AnimatedGaussianBlurShader = """
uniform shader content;
uniform float sigma;
uniform float2 axis;
uniform float4 bounds;

half4 main(float2 coord) {
    float s = max(sigma, 0.001);
    float r = min(ceil(2.5 * s), 32.0);
    float invS2 = 1.0 / (s * s);
    float wRatio = exp(-0.5 * invS2);
    float wStep = exp(-invS2);
    float curW = 1.0;
    half weightSum = half(1.0);
    half4 acc = content.eval(clamp(coord, bounds.xy, bounds.zw));
    for (int k = 0; k < 16; ++k) {
        float i = float(k) * 2.0 + 1.0;
        if (i > r) { break; }
        curW *= wRatio;
        wRatio *= wStep;
        float wL = curW;
        curW *= wRatio;
        wRatio *= wStep;
        float wH = curW;
        float w = wL + wH;
        float2 offset = (i + wH / max(w, 1e-6)) * axis;
        half hw = half(w);
        weightSum += half(2.0) * hw;
        acc += content.eval(clamp(coord - offset, bounds.xy, bounds.zw)) * hw;
        acc += content.eval(clamp(coord + offset, bounds.xy, bounds.zw)) * hw;
    }
    return acc / weightSum;
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object AnimatedBlurShaders {
    private val local = ThreadLocal<RuntimeShader>()

    fun obtain(): RuntimeShader = local.get() ?: RuntimeShader(AnimatedGaussianBlurShader).also {
        local.set(it)
    }

    fun singlePass(
        sigma: Float,
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float,
        input: AndroidRenderEffect?,
    ): AndroidRenderEffect {
        val shader = obtain()
        shader.setFloatUniform("sigma", sigma)
        shader.setFloatUniform("bounds", minX, minY, maxX, maxY)
        shader.setFloatUniform("axis", 1f, 0f)
        val horiz = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
        val chainedHoriz = if (input != null) AndroidRenderEffect.createChainEffect(horiz, input) else horiz
        shader.setFloatUniform("axis", 0f, 1f)
        val vert = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
        return AndroidRenderEffect.createChainEffect(vert, chainedHoriz)
    }
}

/**
 * Continuous animated Gaussian [AndroidRenderEffect] backed by a single fixed-loop AGSL pipeline
 * on API 33+, avoiding Skia's per-radius Vulkan pipeline compilation storm during blur sweeps.
 *
 * Callers must attach this effect only to a clipped `GraphicsLayer` (`clip = true`) whose bounds
 * cover the desired blur extent so Skia never allocates full-window intermediate render targets.
 */
fun createAnimatedBlurRenderEffect(
    radiusPx: Float,
    tileMode: Shader.TileMode = Shader.TileMode.DECAL,
    bounds: Size = Size.Unspecified,
): AndroidRenderEffect? {
    if (!radiusPx.isFinite() || radiusPx <= 0.01f) return null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    if (!isRuntimeShaderSupported()) {
        return AndroidRenderEffect.createBlurEffect(radiusPx, radiusPx, tileMode)
    }
    val clamp = tileMode == Shader.TileMode.CLAMP &&
        bounds.width.isFinite() && bounds.height.isFinite() &&
        bounds.width > 0f && bounds.height > 0f
    val minX = if (clamp) 0.5f else -AnimatedBlurDecalExtent
    val minY = if (clamp) 0.5f else -AnimatedBlurDecalExtent
    val maxX = if (clamp) (bounds.width - 0.5f).coerceAtLeast(0.5f) else AnimatedBlurDecalExtent
    val maxY = if (clamp) (bounds.height - 0.5f).coerceAtLeast(0.5f) else AnimatedBlurDecalExtent
    return if (radiusPx <= AnimatedBlurMaxSinglePassSigma) {
        AnimatedBlurShaders.singlePass(radiusPx, minX, minY, maxX, maxY, input = null)
    } else {
        // Two chained passes of sigma/sqrt(2) match sigma up to ~22.6px using the same pipeline;
        // cap at 2 passes so extreme fade-out tails (alpha ~ 0) never stack >2 separable passes.
        val passSigma = (radiusPx / sqrt(2f)).coerceAtMost(AnimatedBlurMaxSinglePassSigma)
        val first = AnimatedBlurShaders.singlePass(passSigma, minX, minY, maxX, maxY, input = null)
        AnimatedBlurShaders.singlePass(passSigma, minX, minY, maxX, maxY, input = first)
    }
}

/** Compose [RenderEffect] convenience wrapper around [createAnimatedBlurRenderEffect]. */
fun createAnimatedBlurComposeRenderEffect(
    radiusPx: Float,
    tileMode: Shader.TileMode = Shader.TileMode.DECAL,
    bounds: Size = Size.Unspecified,
): RenderEffect? = createAnimatedBlurRenderEffect(radiusPx, tileMode, bounds)?.asComposeRenderEffect()
