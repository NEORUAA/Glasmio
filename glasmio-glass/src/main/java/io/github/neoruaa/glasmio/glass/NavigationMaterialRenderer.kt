package io.github.neoruaa.glasmio.glass

import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.RuntimeXfermode
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import io.github.neoruaa.glasmio.glass.render.GlassColorSpaceShaderFunctions
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The material's color processing is independent of its shape, sampling and control physics. */
internal data class NavigationMaterialRecipe(
    val nativeFace: Boolean,
    val automaticSize: Boolean,
    val saturation: Float,
    val black: Float,
    val white: Float,
    val faceFill: Color,
    val luminanceRollOff: Float,
    val fills: List<NavigationGlassFill>,
    val tint: Color?,
    val tintMatrix: FloatArray?,
    val overlay: Color?,
    val darkFraction: Float = 0f,
    val appearanceAlternate: NavigationMaterialRecipe? = null,
) {
    fun matches(other: NavigationMaterialRecipe?): Boolean = other != null &&
        nativeFace == other.nativeFace && automaticSize == other.automaticSize && saturation == other.saturation && black == other.black &&
        white == other.white && faceFill == other.faceFill && luminanceRollOff == other.luminanceRollOff &&
        fills == other.fills && tint == other.tint && overlay == other.overlay && darkFraction == other.darkFraction &&
        (appearanceAlternate?.matches(other.appearanceAlternate) ?: (other.appearanceAlternate == null)) &&
        (tintMatrix?.contentEquals(other.tintMatrix) ?: (other.tintMatrix == null))

    fun hasSizeDependentFace(): Boolean = automaticSize && luminanceRollOff != 0f ||
        appearanceAlternate?.hasSizeDependentFace() == true

    companion object {
        fun resolve(
            darkFraction: Float,
            material: NavigationGlassMaterial?,
            prominentTint: Color? = null,
            materialTint: Color? = null,
            alphaMultiplier: Float = 1.25f,
        ): NavigationMaterialRecipe {
            val fraction = darkFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
            if (fraction == 0f) return resolve(false, material, prominentTint, materialTint, alphaMultiplier)
            if (fraction == 1f) return resolve(true, material, prominentTint, materialTint, alphaMultiplier)
            return resolve(false, material, prominentTint, materialTint, alphaMultiplier).copy(
                darkFraction = fraction,
                appearanceAlternate = resolve(true, material, prominentTint, materialTint, alphaMultiplier),
            )
        }

        fun resolve(
            isDark: Boolean,
            material: NavigationGlassMaterial?,
            prominentTint: Color? = null,
            materialTint: Color? = null,
            alphaMultiplier: Float = 1.25f,
        ): NavigationMaterialRecipe {
            val clear = material == NavigationGlassMaterial.Clear
            val wide = material == NavigationGlassMaterial.RegularMedium || material == NavigationGlassMaterial.RegularLarge
            return NavigationMaterialRecipe(
                nativeFace = true,
                automaticSize = material == null,
                saturation = if (clear) 1.06f else if (isDark) 1.3f else 1.2f,
                black = if (clear || isDark) .125f else .4f,
                white = if (clear) 1.08f else if (isDark) 1.125f else 1.03f,
                faceFill = Color.White.copy(alpha = if (clear || isDark) 0f else .2f),
                luminanceRollOff = if (!clear && isDark) if (wide) .65f else .4f else 0f,
                fills = emptyList(),
                tint = prominentTint,
                tintMatrix = prominentTint?.let { navigationTintMatrix(it, isDark) },
                overlay = materialTint?.let { it.copy(alpha = (it.alpha * alphaMultiplier).coerceIn(0f, 1f)) },
                darkFraction = if (isDark) 1f else 0f,
            )
        }
    }
}

/**
 * Cached material program. Modern HWUI can consume the destination directly; older HWUI applies
 * the identical function to the already sampled backdrop with a RenderEffect.
 */
internal class NavigationMaterialRenderer {
    private val shader = RuntimeShader(NavigationMaterialEffectShader)
    private var effectRecipe: NavigationMaterialRecipe? = null
    private var effectOpacity = Float.NaN
    private var cachedEffect: RenderEffect? = null
    private val blender = if (Build.VERSION.SDK_INT >= 36) MaterialBlender() else null
    private var sizeBaseRecipe: NavigationMaterialRecipe? = null
    private var sizeLarge = false
    private var sizeRecipe: NavigationMaterialRecipe? = null

    fun recipeForSize(recipe: NavigationMaterialRecipe, minimumDimension: Float): NavigationMaterialRecipe {
        if (!recipe.hasSizeDependentFace()) return recipe
        // A retained anchor effect has no new geometry. Keep its last rendered size class.
        if (!minimumDimension.isFinite()) return if (recipe.matches(sizeBaseRecipe)) sizeRecipe ?: recipe else recipe
        val large = navigationGlassUsesLargeFace(minimumDimension)
        if (!recipe.matches(sizeBaseRecipe) || sizeLarge != large || sizeRecipe == null) {
            sizeBaseRecipe = recipe
            sizeLarge = large
            sizeRecipe = recipe.forMinimumDimension(minimumDimension)
        }
        return requireNotNull(sizeRecipe)
    }

    fun effect(value: NavigationMaterialRecipe, opacity: Float = 1f, minimumDimension: Float = Float.NaN): RenderEffect {
        val recipe = recipeForSize(value, minimumDimension)
        val alpha = opacity.coerceIn(0f, 1f)
        if (!recipe.matches(effectRecipe) || effectOpacity != alpha || cachedEffect == null) {
            if (!recipe.matches(effectRecipe)) updateNavigationMaterialUniforms(recipe, alpha, shader::setFloatUniform)
            else {
                shader.setFloatUniform("materialOpacity", alpha)
                shader.setFloatUniform("appearance_materialOpacity", alpha)
            }
            cachedEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
            effectRecipe = recipe
            effectOpacity = alpha
        }
        return requireNotNull(cachedEffect)
    }

    fun draw(scope: DrawScope, recipe: NavigationMaterialRecipe, opacity: Float = 1f): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val renderer = blender ?: return false
        val canvas = scope.drawContext.canvas.nativeCanvas
        if (!canvas.isHardwareAccelerated) return false
        renderer.prepare(recipeForSize(recipe, scope.size.minDimension / scope.density), opacity)
        canvas.drawRect(0f, 0f, scope.size.width, scope.size.height, renderer.paint)
        return true
    }

    @RequiresApi(36)
    private class MaterialBlender {
        private val blender = RuntimeXfermode(NavigationMaterialBlenderShader)
        val paint = Paint().apply { color = android.graphics.Color.WHITE }
        private var recipe: NavigationMaterialRecipe? = null
        private var opacity = Float.NaN

        fun prepare(value: NavigationMaterialRecipe, alpha: Float) {
            val nextOpacity = alpha.coerceIn(0f, 1f)
            if (!value.matches(recipe) || opacity != nextOpacity) {
                if (!value.matches(recipe)) updateNavigationMaterialUniforms(value, nextOpacity, blender::setFloatUniform)
                else {
                    blender.setFloatUniform("materialOpacity", nextOpacity)
                    blender.setFloatUniform("appearance_materialOpacity", nextOpacity)
                }
                // Paint snapshots the builder here. Updating uniforms alone does not reinstall it.
                paint.xfermode = blender
                recipe = value
                opacity = nextOpacity
            }
        }
    }
}

internal fun updateNavigationMaterialUniforms(
    recipe: NavigationMaterialRecipe?,
    opacity: Float,
    set: (String, FloatArray) -> Unit,
) {
    updateNavigationMaterialEndpointUniforms(recipe, opacity, set)
    val alternate = recipe?.appearanceAlternate
    set("materialAppearanceMix", floatArrayOf(if (alternate != null) recipe.darkFraction else 0f))
    updateNavigationMaterialEndpointUniforms(alternate ?: recipe, opacity) { name, value ->
        set("appearance_$name", value)
    }
}

private fun updateNavigationMaterialEndpointUniforms(
    recipe: NavigationMaterialRecipe?,
    opacity: Float,
    set: (String, FloatArray) -> Unit,
) {
    set("materialOpacity", floatArrayOf(if (recipe == null) 0f else opacity.coerceIn(0f, 1f)))
    set("face", floatArrayOf(recipe?.saturation ?: 1f, recipe?.black ?: 0f, recipe?.white ?: 1f,
        if (recipe?.nativeFace == true) 1f else 0f))
    val fill = recipe?.faceFill ?: Color.Transparent
    set("faceFill", floatArrayOf(fill.red, fill.green, fill.blue, fill.alpha))
    set("luminanceRollOff", floatArrayOf(recipe?.luminanceRollOff ?: 0f))
    val fills = recipe?.fills.orEmpty()
    set("fillCount", floatArrayOf(fills.size.toFloat()))
    repeat(4) { index ->
        val paint = fills.getOrNull(index)
        val color = paint?.color ?: Color.Transparent
        set("fill$index", floatArrayOf(color.red, color.green, color.blue, color.alpha))
    }
    set("fillModes", FloatArray(4) { index ->
        when (fills.getOrNull(index)?.blendMode) {
            BlendMode.Plus -> 1f
            BlendMode.Luminosity -> 2f
            BlendMode.Lighten -> 3f
            BlendMode.Darken -> 4f
            else -> 0f
        }
    })
    val matrix = recipe?.tintMatrix
    set("hasTint", floatArrayOf(if (matrix != null) 1f else 0f))
    repeat(3) { row ->
        set("tintRow$row", FloatArray(4) { column -> matrix?.get(row * 4 + column) ?: if (row == column) 1f else 0f })
    }
    val overlay = recipe?.overlay ?: Color.Transparent
    set("overlay", floatArrayOf(overlay.red, overlay.green, overlay.blue, overlay.alpha))
}

/** Resolved separately from drawing so native tint color construction can be verified directly. */
internal fun navigationTintMatrix(tint: Color, isDark: Boolean = false): FloatArray {
    val rgb = tint.convert(ColorSpaces.Srgb)
    val color = floatArrayOf(rgb.red, rgb.green, rgb.blue)
    val highest = max(color[0], max(color[1], color[2]))
    val lowest = min(color[0], min(color[1], color[2]))
    val chroma = highest - lowest
    val lightness = (highest + lowest) * .5f
    val saturation = if (chroma == 0f) 0f else chroma / (1f - abs(2f * lightness - 1f))
    val hue = if (chroma == 0f) 0f else when (highest) {
        color[0] -> ((color[1] - color[2]) / chroma + 6f) % 6f
        color[1] -> (color[2] - color[0]) / chroma + 2f
        else -> (color[0] - color[1]) / chroma + 4f
    }
    val mappedLightness = (if (isDark) 1.1f else .6f) * lightness
    val mappedChroma = (1f - abs(2f * mappedLightness - 1f)) * ((if (isDark) .95f else 1.2f) * saturation)
    val secondary = mappedChroma * (1f - abs(hue % 2f - 1f))
    val base = mappedLightness - mappedChroma * .5f
    val endpoint = when {
        hue < 1f -> floatArrayOf(mappedChroma, secondary, 0f)
        hue < 2f -> floatArrayOf(secondary, mappedChroma, 0f)
        hue < 3f -> floatArrayOf(0f, mappedChroma, secondary)
        hue < 4f -> floatArrayOf(0f, secondary, mappedChroma)
        hue < 5f -> floatArrayOf(secondary, 0f, mappedChroma)
        else -> floatArrayOf(mappedChroma, 0f, secondary)
    }.map { (it + base).coerceIn(0f, 1f) }
    val result = FloatArray(12)
    repeat(3) { row ->
        val scale = if (isDark) (endpoint[row] - color[row]) / .6f
            else (color[row] - endpoint[row]) / .85f
        result[row * 4] = scale * .2126f
        result[row * 4 + 1] = scale * .7152f
        result[row * 4 + 2] = scale * .0722f
        result[row * 4 + 3] = (if (isDark) color[row] else endpoint[row]) - .15f * scale
    }
    return result
}

/** UIKit's normal-contrast prominent label threshold; explicit child colors still win. */
internal fun navigationGlassProminentContentColor(
    tint: Color,
    isDark: Boolean,
    material: NavigationGlassMaterial?,
): Color {
    if (material == NavigationGlassMaterial.Clear) return if (isDark) Color.White else Color.Black
    val rgb = tint.convert(ColorSpaces.Srgb)
    val luminance = .2126f * rgb.red + .7152f * rgb.green + .0722f * rgb.blue
    return if (luminance * 255f <= 195f) Color.White else Color.Black
}

internal const val NavigationMaterialShaderFunctions = GlassColorSpaceShaderFunctions + """
uniform float materialAppearanceMix;
uniform float materialOpacity;
uniform float4 face;
uniform float4 faceFill;
uniform float luminanceRollOff;
uniform float fillCount;
uniform float4 fill0;
uniform float4 fill1;
uniform float4 fill2;
uniform float4 fill3;
uniform float4 fillModes;
uniform float hasTint;
uniform float4 tintRow0;
uniform float4 tintRow1;
uniform float4 tintRow2;
uniform float4 overlay;

half4 navigationWorkingToNative(half4 value) {
    float alpha = float(value.a);
    return half4(glassWorkingToNativeGamma(float3(value.rgb) / max(alpha, .000001)) * alpha, alpha);
}
half4 navigationNativeToWorking(half4 value) {
    float alpha = float(value.a);
    return half4(glassNativeGammaToWorking(float3(value.rgb) / max(alpha, .000001)) * alpha, alpha);
}
float materialLum(float3 color) { return dot(color, float3(.2126, .7152, .0722)); }
float3 materialClip(float3 color) {
    float luminance = dot(color, float3(.3, .59, .11));
    float lowest = min(min(color.r, color.g), color.b);
    float highest = max(max(color.r, color.g), color.b);
    if (lowest < 0.0) color = luminance + (color - luminance) * luminance / max(luminance - lowest, .00001);
    if (highest > 1.0) color = luminance + (color - luminance) * (1.0 - luminance) / max(highest - luminance, .00001);
    return color;
}
float3 materialFill(float3 color, float4 paint, float mode) {
    if (mode < .5) return mix(color, paint.rgb, paint.a);
    if (mode < 1.5) return min(color + paint.rgb * paint.a, 1.0);
    if (mode < 2.5) {
        float sourceLum = dot(paint.rgb, float3(.3, .59, .11));
        float targetLum = dot(color, float3(.3, .59, .11));
        return mix(color, materialClip(color + sourceLum - targetLum), paint.a);
    }
    if (mode < 3.5) return mix(color, max(color, paint.rgb), paint.a);
    return mix(color, min(color, paint.rgb), paint.a);
}
// The native optical kernel keeps outer-ray RGB and alpha independent at this boundary.
float4 endpoint_navigationMaterialFaceRaw(float4 sampled) {
    if (materialOpacity <= 0.0) return sampled;
    float3 color = sampled.rgb;
    if (face.w > .5) {
        // QuartzCore AIR: attenuate luma and compensate chroma before the face matrix.
        // The quadratic gray response is only the neutral-color specialization of this path.
        float luminance = materialLum(color);
        if (luminanceRollOff > 0.0) {
            float gain = clamp(1.0 - luminance * luminanceRollOff, 0.0, 1.0);
            color = mix(float3(luminance * gain), color * gain, 1.0 + .3 * (1.0 - gain));
            luminance = materialLum(color);
        }
        color = (luminance * (face.z - face.y) + face.y) + (color - luminance) * face.x;
        color = mix(color, faceFill.rgb, faceFill.a);
    }
    if (fillCount > .5) color = materialFill(color, fill0, fillModes.x);
    if (fillCount > 1.5) color = materialFill(color, fill1, fillModes.y);
    if (fillCount > 2.5) color = materialFill(color, fill2, fillModes.z);
    if (fillCount > 3.5) color = materialFill(color, fill3, fillModes.w);
    return float4(color, sampled.a);
}
half4 endpoint_navigationMaterialFace(half4 sampled) {
    if (materialOpacity <= 0.0) return sampled;
    float alpha = float(sampled.a);
    float4 faceResult = endpoint_navigationMaterialFaceRaw(float4(float3(sampled.rgb) / max(alpha, .00001), alpha));
    float resultAlpha = max(alpha, faceFill.a);
    return half4(faceResult.rgb * resultAlpha, resultAlpha);
}
half4 endpoint_navigationMaterialFinish(half4 sampled, half4 faceResult) {
    if (materialOpacity <= 0.0) return sampled;
    float resultAlpha = float(faceResult.a);
    float3 color = float3(faceResult.rgb) / max(resultAlpha, .00001);
    if (hasTint > .5) {
        float4 backdrop = float4(color, 1.0);
        color = float3(dot(tintRow0, backdrop), dot(tintRow1, backdrop), dot(tintRow2, backdrop));
    }
    color = mix(color, overlay.rgb, overlay.a);
    if (hasTint > .5) resultAlpha = 1.0;
    if (face.w < .5) color = clamp(color, 0.0, 1.0);
    half4 result = half4(color * resultAlpha, resultAlpha);
    return mix(sampled, result, materialOpacity);
}
half4 endpoint_navigationMaterial(half4 sampled) {
    return endpoint_navigationMaterialFinish(sampled, endpoint_navigationMaterialFace(sampled));
}

uniform float appearance_materialOpacity;
uniform float4 appearance_face;
uniform float4 appearance_faceFill;
uniform float appearance_luminanceRollOff;
uniform float appearance_fillCount;
uniform float4 appearance_fill0;
uniform float4 appearance_fill1;
uniform float4 appearance_fill2;
uniform float4 appearance_fill3;
uniform float4 appearance_fillModes;
uniform float appearance_hasTint;
uniform float4 appearance_tintRow0;
uniform float4 appearance_tintRow1;
uniform float4 appearance_tintRow2;
uniform float4 appearance_overlay;

half4 appearance_navigationWorkingToNative(half4 value) {
    float alpha = float(value.a);
    return half4(glassWorkingToNativeGamma(float3(value.rgb) / max(alpha, .000001)) * alpha, alpha);
}
half4 appearance_navigationNativeToWorking(half4 value) {
    float alpha = float(value.a);
    return half4(glassNativeGammaToWorking(float3(value.rgb) / max(alpha, .000001)) * alpha, alpha);
}
float appearance_materialLum(float3 color) { return dot(color, float3(.2126, .7152, .0722)); }
float3 appearance_materialClip(float3 color) {
    float luminance = dot(color, float3(.3, .59, .11));
    float lowest = min(min(color.r, color.g), color.b);
    float highest = max(max(color.r, color.g), color.b);
    if (lowest < 0.0) color = luminance + (color - luminance) * luminance / max(luminance - lowest, .00001);
    if (highest > 1.0) color = luminance + (color - luminance) * (1.0 - luminance) / max(highest - luminance, .00001);
    return color;
}
float3 appearance_materialFill(float3 color, float4 paint, float mode) {
    if (mode < .5) return mix(color, paint.rgb, paint.a);
    if (mode < 1.5) return min(color + paint.rgb * paint.a, 1.0);
    if (mode < 2.5) {
        float sourceLum = dot(paint.rgb, float3(.3, .59, .11));
        float targetLum = dot(color, float3(.3, .59, .11));
        return mix(color, appearance_materialClip(color + sourceLum - targetLum), paint.a);
    }
    if (mode < 3.5) return mix(color, max(color, paint.rgb), paint.a);
    return mix(color, min(color, paint.rgb), paint.a);
}
// The native optical kernel keeps outer-ray RGB and alpha independent at this boundary.
float4 appearance_navigationMaterialFaceRaw(float4 sampled) {
    if (appearance_materialOpacity <= 0.0) return sampled;
    float3 color = sampled.rgb;
    if (appearance_face.w > .5) {
        // QuartzCore AIR: attenuate luma and compensate chroma before the appearance_face matrix.
        // The quadratic gray response is only the neutral-color specialization of this path.
        float luminance = appearance_materialLum(color);
        if (appearance_luminanceRollOff > 0.0) {
            float gain = clamp(1.0 - luminance * appearance_luminanceRollOff, 0.0, 1.0);
            color = mix(float3(luminance * gain), color * gain, 1.0 + .3 * (1.0 - gain));
            luminance = appearance_materialLum(color);
        }
        color = (luminance * (appearance_face.z - appearance_face.y) + appearance_face.y) + (color - luminance) * appearance_face.x;
        color = mix(color, appearance_faceFill.rgb, appearance_faceFill.a);
    }
    if (appearance_fillCount > .5) color = appearance_materialFill(color, appearance_fill0, appearance_fillModes.x);
    if (appearance_fillCount > 1.5) color = appearance_materialFill(color, appearance_fill1, appearance_fillModes.y);
    if (appearance_fillCount > 2.5) color = appearance_materialFill(color, appearance_fill2, appearance_fillModes.z);
    if (appearance_fillCount > 3.5) color = appearance_materialFill(color, appearance_fill3, appearance_fillModes.w);
    return float4(color, sampled.a);
}
half4 appearance_navigationMaterialFace(half4 sampled) {
    if (appearance_materialOpacity <= 0.0) return sampled;
    float alpha = float(sampled.a);
    float4 faceResult = appearance_navigationMaterialFaceRaw(float4(float3(sampled.rgb) / max(alpha, .00001), alpha));
    float resultAlpha = max(alpha, appearance_faceFill.a);
    return half4(faceResult.rgb * resultAlpha, resultAlpha);
}
half4 appearance_navigationMaterialFinish(half4 sampled, half4 faceResult) {
    if (appearance_materialOpacity <= 0.0) return sampled;
    float resultAlpha = float(faceResult.a);
    float3 color = float3(faceResult.rgb) / max(resultAlpha, .00001);
    if (appearance_hasTint > .5) {
        float4 backdrop = float4(color, 1.0);
        color = float3(dot(appearance_tintRow0, backdrop), dot(appearance_tintRow1, backdrop), dot(appearance_tintRow2, backdrop));
    }
    color = mix(color, appearance_overlay.rgb, appearance_overlay.a);
    if (appearance_hasTint > .5) resultAlpha = 1.0;
    if (appearance_face.w < .5) color = clamp(color, 0.0, 1.0);
    half4 result = half4(color * resultAlpha, resultAlpha);
    return mix(sampled, result, appearance_materialOpacity);
}
half4 appearance_navigationMaterial(half4 sampled) {
    return appearance_navigationMaterialFinish(sampled, appearance_navigationMaterialFace(sampled));
}


// Endpoint helpers retain the two complete programs around caller-owned edge passes.
float4 navigationMaterialFaceRawForAppearance(float4 sampled, bool alternate) {
    return alternate ? appearance_navigationMaterialFaceRaw(sampled) : endpoint_navigationMaterialFaceRaw(sampled);
}
half4 navigationMaterialFaceForAppearance(half4 sampled, bool alternate) {
    return alternate ? appearance_navigationMaterialFace(sampled) : endpoint_navigationMaterialFace(sampled);
}
half4 navigationMaterialFinishForAppearance(half4 sampled, half4 faceResult, bool alternate) {
    return alternate ? appearance_navigationMaterialFinish(sampled, faceResult)
        : endpoint_navigationMaterialFinish(sampled, faceResult);
}
float4 navigationMaterialFaceRaw(float4 sampled) {
    float4 first = endpoint_navigationMaterialFaceRaw(sampled);
    if (materialAppearanceMix <= 0.0) return first;
    return mix(first, appearance_navigationMaterialFaceRaw(sampled), materialAppearanceMix);
}
half4 navigationMaterialFace(half4 sampled) {
    half4 first = endpoint_navigationMaterialFace(sampled);
    if (materialAppearanceMix <= 0.0) return first;
    return half4(mix(float4(first), float4(appearance_navigationMaterialFace(sampled)), materialAppearanceMix));
}
half4 navigationMaterialFinish(half4 sampled, half4 faceResult) {
    half4 first = endpoint_navigationMaterialFinish(sampled, faceResult);
    if (materialAppearanceMix <= 0.0) return first;
    return half4(mix(float4(first), float4(appearance_navigationMaterialFinish(sampled, faceResult)), materialAppearanceMix));
}
half4 navigationMaterial(half4 sampled) {
    half4 first = endpoint_navigationMaterial(sampled);
    if (materialAppearanceMix <= 0.0) return first;
    return half4(mix(float4(first), float4(appearance_navigationMaterial(sampled)), materialAppearanceMix));
}
float4 navigationMaterialTintForAppearance(float4 destination, float3 original, float mask, float coverage, bool alternate) {
    float3 color = destination.rgb + (1.0 - destination.a) * original;
    float3 tinted;
    float alpha;
    if (alternate) {
        tinted = appearance_hasTint > .5 ? float3(dot(appearance_tintRow0, float4(color, 1.0)),
            dot(appearance_tintRow1, float4(color, 1.0)), dot(appearance_tintRow2, float4(color, 1.0))) : appearance_overlay.rgb;
        if (appearance_hasTint > .5) tinted = mix(tinted, appearance_overlay.rgb, appearance_overlay.a);
        alpha = (appearance_hasTint > .5 ? mask : coverage * appearance_overlay.a) * appearance_materialOpacity;
    } else {
        tinted = hasTint > .5 ? float3(dot(tintRow0, float4(color, 1.0)),
            dot(tintRow1, float4(color, 1.0)), dot(tintRow2, float4(color, 1.0))) : overlay.rgb;
        if (hasTint > .5) tinted = mix(tinted, overlay.rgb, overlay.a);
        alpha = (hasTint > .5 ? mask : coverage * overlay.a) * materialOpacity;
    }
    return float4(tinted * alpha, alpha) + destination * (1.0 - alpha);
}

"""

private const val NavigationMaterialEffectShader = """
uniform shader content;
""" + NavigationMaterialShaderFunctions + """
half4 main(float2 position) {
    return navigationNativeToWorking(navigationMaterial(navigationWorkingToNative(content.eval(position))));
}
"""

/** Reuse the same face implementation in the native optical/coverage kernel. */
internal val NavigationMaterialNativeShaderFunctions =
    NavigationMaterialShaderFunctions.removePrefix(GlassColorSpaceShaderFunctions)

/** A second known recipe preserves retained source/target weighting without duplicated coefficients. */
internal val NavigationMaterialSourceShaderFunctions = run {
    val endpointNames = listOf("materialOpacity", "face", "faceFill", "luminanceRollOff", "fillCount",
        "fill0", "fill1", "fill2", "fill3", "fillModes", "hasTint", "tintRow0", "tintRow1", "tintRow2", "overlay",
        "navigationWorkingToNative", "navigationNativeToWorking", "materialLum", "materialClip", "materialFill",
        "navigationMaterialFaceRaw", "navigationMaterialFace", "navigationMaterialFinish", "navigationMaterial")
    val names = endpointNames + endpointNames.map { "appearance_$it" } +
        listOf("navigationMaterialFaceRaw", "navigationMaterialFace", "navigationMaterialFinish", "navigationMaterial")
            .map { "endpoint_$it" } +
        listOf("materialAppearanceMix", "navigationMaterialFaceRawForAppearance", "navigationMaterialFaceForAppearance",
            "navigationMaterialFinishForAppearance", "navigationMaterialTintForAppearance")
    Regex("\\b(" + names.joinToString("|") + ")\\b").replace(NavigationMaterialNativeShaderFunctions) { "source_" + it.value }
}

private const val NavigationMaterialBlenderShader = NavigationMaterialShaderFunctions + """
half4 main(half4 src, half4 dst) {
    return navigationNativeToWorking(navigationMaterial(navigationWorkingToNative(dst)));
}
"""
