package io.github.neoruaa.glasmio.glass.render.highlight

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.neoruaa.glasmio.glass.render.RuntimeShader
import io.github.neoruaa.glasmio.glass.render.RuntimeShaderCache
import io.github.neoruaa.glasmio.glass.render.asAndroidRuntimeShader
import io.github.neoruaa.glasmio.glass.render.optics.GlassSdfShader
import io.github.neoruaa.glasmio.glass.render.optics.glassCornerRadii
import kotlin.math.PI
import io.github.neoruaa.glasmio.glass.LiquidContactShape
import io.github.neoruaa.glasmio.glass.navigationContainerGlassField

/** Native key/fill parameters rendered with Glasmio's shared signed-distance geometry. */
@Immutable
interface HighlightStyle {
    val color: Color
    val blendMode: BlendMode

    fun DrawScope.createShader(shape: Shape, runtimeShaderCache: RuntimeShaderCache): RuntimeShader?

    @Immutable
    data class Plain(
        override val color: Color = Color.White.copy(alpha = 0.38f),
        override val blendMode: BlendMode = BlendMode.Plus,
    ) : HighlightStyle {
        override fun DrawScope.createShader(shape: Shape, runtimeShaderCache: RuntimeShaderCache): RuntimeShader? = null
    }

    @Immutable
    data class Default(
        override val color: Color = Color.White,
        override val blendMode: BlendMode = BlendMode.SrcOver,
        val angle: Float = 0f,
        val falloff: Float = 1f,
        val clear: Boolean = false,
    ) : HighlightStyle {
        override fun DrawScope.createShader(shape: Shape, runtimeShaderCache: RuntimeShaderCache): RuntimeShader =
            nativeHighlightShader(shape, runtimeShaderCache, color, angle, falloff, clear, nativeVibrantMatrix() != null)
    }

    @Immutable
    data class Ambient(val intensity: Float = 0.38f) : HighlightStyle {
        override val color: Color get() = Color.White.copy(alpha = intensity.coerceIn(0f, 1f))
        override val blendMode: BlendMode get() = BlendMode.SrcOver
        override fun DrawScope.createShader(shape: Shape, runtimeShaderCache: RuntimeShaderCache): RuntimeShader =
            nativeHighlightShader(shape, runtimeShaderCache, Color.White, 0f, 1f, false, true)
    }

    companion object {
        @Stable val Default = Default()
        @Stable val Ambient = Ambient()
        @Stable val Plain = Plain()
    }
}

private fun DrawScope.nativeHighlightShader(
    shape: Shape,
    cache: RuntimeShaderCache,
    color: Color,
    angle: Float,
    falloff: Float,
    clear: Boolean,
    nativeMask: Boolean,
): RuntimeShader {
    val field = (shape as? LiquidContactShape)?.let { navigationContainerGlassField(it) }
    val main = if (nativeMask) "nativeCasdfHighlightMask" else "nativeCasdfHighlight"
    val geometry = field?.shaderFunctions ?: GlassSdfShader
    val sample = if (field == null) "float3 field = glassField(p, 0.0);" else "float3 field = navigationGlassField(p);"
    val source = geometry + NativeCasdfHighlightFunctions + "\nhalf4 main(float2 p) { $sample return $main(field.x, field.yz); }\n"
    return cache.obtainRuntimeShader("Glasmio.NativeKeyFillHighlight.${field != null}.$nativeMask", source).apply {
    if (field == null) {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("cornerRadii", glassCornerRadii(shape, size, layoutDirection, this@nativeHighlightShader))
    } else field.configure(asAndroidRuntimeShader())
    setColorUniform("color", color.copy(alpha = 1f))
    setFloatUniform("angle", angle * (PI / 180.0).toFloat())
    setFloatUniform("curvature", 0.75f * falloff.coerceAtLeast(0.01f))
    setFloatUniform("height", density)
    if (!nativeMask) setFloatUniform("colorGain", if (clear) 1.2f else 1.0589f)
    }
}

// QuartzCore AIR sdf_diffuse_kf_highlight and CPU packing at 0x1c3a62f3c.
// Public amount .5 is packed as bias=(1/amount)-2, not an opacity multiplier.
internal const val NativeCasdfHighlightMaskFunctions = """
float nativeCasdfHighlightCoverage(float signedDistance, float2 gradient,
    float height, float angle, float curvature) {
    float depth = -signedDistance;
    if (depth < -5.0) return 0.0;
    float2 normal = normalize(gradient);
    float aa = max(abs(normal.x) + abs(normal.y), 0.0001);
    float alignment = dot(normal, float2(sin(angle), -cos(angle)));
    float direct = nativeHighlightStrip(depth, height, curvature, aa);
    float diffuse = nativeHighlightStrip(depth, height * 8.0, 1.0, aa);
    float spread = cos(1.3962634015954636);
    float diffuseSpread = cos(1.3962634015954636 * 0.65);
    float key = direct * nativeHighlightAngular(alignment, spread);
    float fill = direct * nativeHighlightAngular(-alignment, spread);
    float diffuseKey = diffuse * nativeHighlightAngular(alignment, diffuseSpread);
    float diffuseFill = diffuse * nativeHighlightAngular(-alignment, diffuseSpread);
    return key + fill + nativeHighlightBias(diffuseKey, 11.333333333333334)
        + nativeHighlightBias(diffuseFill, 11.333333333333334);
}
"""

internal const val NativeCasdfHighlightFunctions = NativeGlassEdgeFunctions + NativeCasdfHighlightMaskFunctions + """
layout(color) uniform half4 color;
uniform float angle;
uniform float curvature;
uniform float height;
uniform float colorGain;
half4 nativeCasdfHighlightMask(float signedDistance, float2 gradient) {
    return color * half(nativeCasdfHighlightCoverage(signedDistance, gradient, height, angle, curvature));
}
// Explicit custom highlight colors retain their existing gain; native white masks use vibrancy.
half4 nativeCasdfHighlight(float signedDistance, float2 gradient) {
    half4 result = nativeCasdfHighlightMask(signedDistance, gradient);
    return half4(result.rgb * half(colorGain), result.a);
}
"""

/** Native white key/fill masks are colorized by the sampled material, after prominent tint. */
internal fun HighlightStyle.nativeVibrantMatrix(): NativeVibrantColorMatrix? = when (this) {
    is HighlightStyle.Default -> if (color.copy(alpha = 1f) == Color.White && blendMode == BlendMode.SrcOver)
        if (clear) NativeVibrantColorMatrix.ClearHighlight else NativeVibrantColorMatrix.RegularHighlight else null
    is HighlightStyle.Ambient -> NativeVibrantColorMatrix.RegularHighlight
    else -> null
}
