package io.github.neoruaa.glasmio.glass

import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.RuntimeXfermode
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import io.github.neoruaa.glasmio.glass.render.GlassColorSpaceShaderFunctions
import io.github.neoruaa.glasmio.glass.render.highlight.NativeVibrantColorMatrixTransferFunctions
import kotlin.math.pow

/** A source control's local bounds survive shared/retained material composition. */
internal data class SampledPressHighlight(
    val highlight: GlassPressHighlight,
    val bounds: Rect,
    val boundsHighlight: Boolean = true,
)

/** UIKit's two backdrop-aware flex glows, applied after the material and before its caption. */
class PressHighlightRenderer {
    private var maskShader: RuntimeShader? = null
    private var effectShader: RuntimeShader? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var blender: NativePressBlender? = null

    fun draw(
        scope: DrawScope,
        progress: Float,
        position: Offset,
        sourceBounds: Rect = Rect(Offset.Zero, scope.size),
        clampPosition: Boolean = true,
        horizontalRadius: Float? = null,
        isDark: Boolean = false,
        tint: Color? = null,
        clear: Boolean = false,
        dissipationProgress: Float = 0f,
        boundsHighlight: Boolean = true,
    ) {
        if (Build.VERSION.SDK_INT < 36 || progress <= 0f || sourceBounds.isEmpty) return
        val target = blender ?: NativePressBlender().also { blender = it }
        val mask = maskShader ?: RuntimeShader(NativePressGlowFunctions + PressMaskMain).also {
            maskShader = it
            paint.shader = it
        }
        val canvas = scope.drawContext.canvas.nativeCanvas
        if (!canvas.isHardwareAccelerated) return
        configureNativePressGlow(mask, progress, position, sourceBounds, scope.density,
            clampPosition, horizontalRadius, isDark, tint, clear, dissipationProgress, boundsHighlight)
        target.shader.setFloatUniform("glowGain", nativePressDodgeGain(isDark, tint, clear))
        paint.xfermode = target.shader
        canvas.drawRect(0f, 0f, scope.size.width, scope.size.height, paint)
    }

    /** The identical transfer for a sampled material on API 33–35. No white-overlay fallback. */
    internal fun effect(
        progress: Float,
        position: Offset,
        bounds: Rect,
        density: Float,
        isDark: Boolean,
        tint: Color?,
        clear: Boolean,
        dissipationProgress: Float = 0f,
        boundsHighlight: Boolean = true,
    ): RenderEffect {
        val effect = effectShader ?: RuntimeShader(NativePressGlowFunctions + PressEffectMain).also { effectShader = it }
        configureNativePressGlow(effect, progress, position, bounds, density,
            true, null, isDark, tint, clear, dissipationProgress, boundsHighlight)
        return RenderEffect.createRuntimeShaderEffect(effect, "content")
    }

    @RequiresApi(36)
    private class NativePressBlender {
        val shader = RuntimeXfermode(GlassColorSpaceShaderFunctions + NativePressGlowTransfer + """
uniform float glowGain;
half4 main(half4 src, half4 dst) {
    if (src.r <= 0.0 && src.g <= 0.0) return dst;
    float4 target = glassWorkingToNativeGammaPremul(float4(dst));
    return half4(glassNativeGammaToWorkingPremul(float4(nativeApplyPressGlow(half4(target), float(src.r), float(src.g), glowGain))));
}
""")
    }
}

/** Dynamic flex variants are interpolated by the minimum dimension, except compact controls. */
internal fun nativeFlexSizeProgress(size: Size, density: Float): Float =
    if (size.maxDimension / density < 120f) 0f
    else ((size.minDimension / density - 44f) / 116f).coerceIn(0f, 1f)

internal fun nativeFlexLiftPoints(size: Size, density: Float): Float =
    16f - 12f * nativeFlexSizeProgress(size, density)

internal fun nativePressDodgeGain(isDark: Boolean, tint: Color?, clear: Boolean): Float {
    val white = if (tint != null) {
        val rgb = tint.convert(ColorSpaces.Srgb)
        val luma = (.2126f * rgb.red + .7152f * rgb.green + .0722f * rgb.blue).coerceIn(0f, 1f)
        .7f - .45f * luma.pow(1.25f)
    } else if (clear) .7f else if (isDark) .75f else .4f
    return 1f / (1f - white)
}

internal fun configureNativePressGlow(
    shader: RuntimeShader,
    progress: Float,
    position: Offset,
    bounds: Rect,
    density: Float,
    clampPosition: Boolean,
    horizontalRadius: Float?,
    isDark: Boolean,
    tint: Color?,
    clear: Boolean,
    dissipationProgress: Float,
    boundsHighlight: Boolean = true,
) {
    val size = bounds.size
    val t = nativeFlexSizeProgress(size, density)
    val compact = size.maxDimension / density < 120f
    val opacity = progress.coerceIn(0f, 1f)
    val center = if (clampPosition) Offset(position.x.coerceIn(bounds.left, bounds.right),
        position.y.coerceIn(bounds.top, bounds.bottom)) else position
    val radius = minOf(size.minDimension * .75f, 80f * density)
    val expansion = 1f + 3f * dissipationProgress.coerceIn(0f, 1f)
    shader.setFloatUniform("pressCenter", center.x, center.y)
    shader.setFloatUniform("pressRadius", (horizontalRadius ?: radius) * expansion, radius * expansion)
    shader.setFloatUniform("pressOpacity", if (boundsHighlight) opacity * (1f - t) else 0f,
        opacity * if (compact) 1f else .3f - .1f * t)
    shader.setFloatUniform("pressDodgeGain", nativePressDodgeGain(isDark, tint, clear))
    shader.setFloatUniform("pressBounds", bounds.left, bounds.top, bounds.right, bounds.bottom)
}

/** Native touch matrices omit inputClamp (default zero); the separate top CASDF keeps its 1.2 cap. */
internal const val NativePressGlowApplyFunctions = """
half4 nativeApplyPressGlow(half4 target, float bigOpacity, float littleOpacity, float gain) {
    float4 result = float4(target);
    result = nativeVibrantColorMatrixApplyOver(float4(0.0, 0.0, 0.0, bigOpacity), result, result,
        float4(1.1575000286, -0.1430999935, -0.0143999998, 0.0),
        float4(-0.0425000004, 1.0569999218, -0.0144999996, 0.0),
        float4(-0.0425999984, -0.1429999918, 1.1855999231, 0.0),
        float4(0.0, 0.0, 0.0, 1.0), float4(.0499999970, .0499999970, .0499999970, 0.0), float2(0.0, 0.0));
    result = nativeVibrantColorMatrixApplyOver(float4(0.0, 0.0, 0.0, littleOpacity), result, result,
        float4(float3(1.3937, -0.3577, -0.0360) * gain, 0.0),
        float4(float3(-0.1063, 1.1424, -0.0361) * gain, 0.0),
        float4(float3(-0.1064, -0.3575, 1.4639) * gain, 0.0),
        float4(0.0, 0.0, 0.0, 1.0), float4(0.0), float2(0.0, 0.0));
    return half4(result);
}
"""

internal const val NativePressGlowTransfer =
    NativeVibrantColorMatrixTransferFunctions + NativePressGlowApplyFunctions

/** CALayer's circular white shadow is the source alpha of the little vibrant matrix layer. */
internal const val NativePressGlowMaskFunctions = """
uniform float2 pressCenter;
uniform float2 pressRadius;
uniform float2 pressOpacity;
uniform float pressDodgeGain;
uniform float4 pressBounds;
float nativePressBigMask(float2 p) {
    return step(pressBounds.x, p.x) * step(pressBounds.y, p.y)
        * step(p.x, pressBounds.z) * step(p.y, pressBounds.w);
}
float nativePressShadow(float2 p) {
    float normalizedDistance = length((p - pressCenter) / max(pressRadius, float2(0.0001)));
    float x = min(normalizedDistance, 5.0);
    float z = x * x;
    // Collect the original eight-point Gauss-Legendre quadrature's fixed degree-eight
    // Bessel expansion into powers of x squared, preserving its nodes and truncation.
    float integral = 3.32834397e-16;
    integral = integral * z + 9.64113704e-14;
    integral = integral * z + 2.17530282e-11;
    integral = integral * z + 3.68878572e-9;
    integral = integral * z + 4.48217776e-7;
    integral = integral * z + 0.0000364921361;
    integral = integral * z + 0.00179845968;
    integral = integral * z + 0.0451020040;
    integral = integral * z + 0.393469334;
    return clamp(exp(-0.5 * z) * integral, 0.0, 1.0);
}
half4 nativePressGlow(half4 target, float2 p) {
    return nativeApplyPressGlow(target, pressOpacity.x * nativePressBigMask(p), pressOpacity.y * nativePressShadow(p), pressDodgeGain);
}
"""

internal const val NativePressGlowFunctions = NativePressGlowTransfer + NativePressGlowMaskFunctions

internal const val PressMaskMain = """
half4 main(float2 p) {
    return half4(pressOpacity.x * nativePressBigMask(p), pressOpacity.y * nativePressShadow(p), pressDodgeGain / (1.0 + pressDodgeGain), 1.0);
}
"""

private const val PressEffectMain = GlassColorSpaceShaderFunctions + """
uniform shader content;
half4 main(float2 p) {
    half4 target = half4(glassWorkingToNativeGammaPremul(float4(content.eval(p))));
    return half4(glassNativeGammaToWorkingPremul(float4(nativePressGlow(target, p))));
}
"""
