package io.github.neoruaa.glasmio.glass

import android.graphics.RuntimeShader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScopeImpl
import io.github.neoruaa.glasmio.glass.render.highlight.NativeGlassEdgeFunctions
import io.github.neoruaa.glasmio.glass.render.highlight.NativeCasdfHighlightMaskFunctions
import io.github.neoruaa.glasmio.glass.render.highlight.NativeVibrantColorMatrix
import io.github.neoruaa.glasmio.glass.render.highlight.NativeVibrantColorMatrixFunctions
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max

/** Known native material metadata; custom paint callbacks keep their existing separate path. */
internal data class NativeGlassCompletion(
    val recipe: NavigationMaterialRecipe,
    val isDark: Boolean,
    val clearFraction: Float,
    val materialAlpha: Float = 1f,
    val edgeAlpha: Float = 1f,
    val shadow: NativeNavigationShadowProfile? = null,
    val shadowAlpha: Float = 1f,
    val sourceRecipe: NavigationMaterialRecipe? = null,
    val sourceAlpha: Float = 0f,
    val idleColor: Color = Color.Transparent,
    val overlayColor: Color = Color.Transparent,
    val outputSize: Size? = null,
    val outputDensity: Float? = null,
    val textureScale: Float? = null,
    val highlightAlpha: Float = 0f,
    val highlightAngle: Float = 0f,
    val sourceShadow: NativeGlassSourceShadow? = null,
    val darkFraction: Float = if (isDark) 1f else 0f,
)

/** Recipe structure stays active even when an animation temporarily gives it zero opacity. */
internal data class NativeGlassCompletionTopology(
    val hasSourceRecipe: Boolean,
    val hasTargetAppearance: Boolean,
    val hasSourceAppearance: Boolean,
    val hasSourceShadow: Boolean = true,
) {
    val cacheKey: Int
        get() = (if (hasSourceRecipe) 4 else 0) or (if (hasTargetAppearance) 2 else 0) or
            (if (hasSourceAppearance) 1 else 0) or (if (hasSourceShadow) 8 else 0)
}

internal fun NativeGlassCompletion.programTopology(): NativeGlassCompletionTopology =
    NativeGlassCompletionTopology(
        hasSourceRecipe = sourceRecipe != null,
        hasTargetAppearance = recipe.appearanceAlternate != null,
        hasSourceAppearance = sourceRecipe?.appearanceAlternate != null,
        hasSourceShadow = sourceShadow != null,
    )

internal data class NativeGlassSourceShadow(
    val profile: NativeNavigationShadowProfile,
    val bounds: Rect,
    val radii: Offset,
    val alpha: Float,
)

internal fun configureNativeGlassCompletion(
    shader: RuntimeShader,
    completion: NativeGlassCompletion,
    scope: BackdropEffectScopeImpl,
    textureOrigin: Offset,
) {
    val scale = completion.textureScale ?: scope.samplingScale
    val density = completion.outputDensity ?: scope.sourceDensity.density
    val output = completion.outputSize ?: scope.fullSize.takeIf { it.width.isFinite() }
        ?: Size(scope.size.width / scale, scope.size.height / scale)
    val clear = completion.clearFraction.coerceIn(0f, 1f)
    val shadow = completion.shadow
    // Native key/fill extends by its measured height plus the device-pixel AA support.
    val shadowRadius = max(shadow?.radius ?: 0f, completion.sourceShadow?.profile?.radius ?: 0f)
    val guard = ceil(max(.5333333333333333f * density + 1f,
        if (shadowRadius > 0f) shadowRadius * density * 2f + 8f * density + 1f else 0f))
    val outputOrigin = Offset(guard, guard)
    val outputSize = IntSize(ceil(output.width + guard * 2f).toInt(), ceil(output.height + guard * 2f).toInt())
    scope.materialOutputOrigin = outputOrigin
    scope.materialOutputSize = outputSize
    scope.nativeMaterialCompleted = true
    shader.setFloatUniform("nativeOutputOrigin", outputOrigin.x, outputOrigin.y)
    shader.setFloatUniform("nativeOutputSize", output.width, output.height)
    shader.setFloatUniform("nativeOutputScale", scale)
    shader.setFloatUniform("nativeOutputDensity", density)
    shader.setFloatUniform("nativeTextureOrigin", textureOrigin.x, textureOrigin.y)
    shader.setFloatUniform("nativeClearFraction", clear)
    shader.setFloatUniform("nativeEdgeAlpha", completion.edgeAlpha.coerceIn(0f, 1f))
    shader.setFloatUniform("nativeTopHighlight", completion.highlightAlpha, completion.highlightAngle)
    val highlightClear = if (completion.recipe.tint != null) 0f else if (completion.sourceRecipe?.tint != null)
        clear * (1f - completion.sourceAlpha.coerceIn(0f, 1f)) else clear
    scope.cachedResource("Glasmio.NativeCompletion.Highlight") { NativeVibrantColorMatrix.HighlightInterpolation() }
        .configure(highlightClear, shader::setFloatUniform)
    shader.setFloatUniform("nativeKeyThreshold", navigationGlassKeyThreshold(completion.darkFraction, clear, scope.nativeGlassHeadroom))
    shader.setFloatUniform("nativeShadow", shadow?.radius?.times(density) ?: 0f,
        (shadow?.opacity ?: 0f) * completion.shadowAlpha.coerceIn(0f, 1f), 8f * density, .3f)
    val sourceShadow = completion.sourceShadow
    shader.setFloatUniform("nativeSourceShadow", (sourceShadow?.profile?.radius ?: 0f) * density,
        (sourceShadow?.profile?.opacity ?: 0f) * (sourceShadow?.alpha ?: 0f), 8f * density, .3f)
    val sourceBounds = sourceShadow?.bounds ?: Rect.Zero
    shader.setFloatUniform("nativeSourceShadowBounds", sourceBounds.center.x, sourceBounds.center.y,
        sourceBounds.width * .5f, sourceBounds.height * .5f)
    shader.setFloatUniform("nativeSourceShadowRadii", sourceShadow?.radii?.x ?: 0f, sourceShadow?.radii?.y ?: 0f)
    fun color(name: String, value: Color) = shader.setFloatUniform(name, value.red, value.green, value.blue, value.alpha)
    color("nativeIdleColor", completion.idleColor)
    color("nativeOverlayColor", completion.overlayColor)
    val minimumDimension = output.minDimension / density
    updateNavigationMaterialUniforms(completion.recipe.forMinimumDimension(minimumDimension), completion.materialAlpha) { name, value ->
        shader.setFloatUniform(name, value)
    }
    updateNavigationMaterialUniforms(completion.sourceRecipe, completion.sourceAlpha) { name, value ->
        shader.setFloatUniform("source_$name", value)
    }
}

/** CPU uniform builder interpolates the angle before cos; its default maximum headroom is 1.2. */
internal fun navigationGlassKeyThreshold(isDark: Boolean, clear: Float, headroom: Float, maximumHeadroom: Float = 1.2f): Float {
    val progress = if (maximumHeadroom > 1f) ((minOf(headroom.takeIf { it.isFinite() } ?: 1f, maximumHeadroom) - 1f)
        .coerceAtLeast(0f) / (maximumHeadroom - 1f)) else 1f
    val regular = if (isDark) 1.3089969389957472f else
        1.8500490071139892f + (1.6755160819145563f - 1.8500490071139892f) * progress
    return cos(regular + (1.3089969389957472f - regular) * clear.coerceIn(0f, 1f))
}

internal fun navigationGlassKeyThreshold(darkFraction: Float, clear: Float, headroom: Float, maximumHeadroom: Float = 1.2f): Float {
    val fraction = darkFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    if (fraction == 0f) return navigationGlassKeyThreshold(false, clear, headroom, maximumHeadroom)
    if (fraction == 1f) return navigationGlassKeyThreshold(true, clear, headroom, maximumHeadroom)
    val progress = if (maximumHeadroom > 1f) ((minOf(headroom.takeIf { it.isFinite() } ?: 1f, maximumHeadroom) - 1f)
        .coerceAtLeast(0f) / (maximumHeadroom - 1f)) else 1f
    val light = 1.8500490071139892f + (1.6755160819145563f - 1.8500490071139892f) * progress
    val regular = light + (1.3089969389957472f - light) * fraction
    return cos(regular + (1.3089969389957472f - regular) * clear.coerceIn(0f, 1f))
}

/**
 * AIR glass_background_all_lpf coverage, stroke_mode, ring and backdrop-relative completion.
 * Geometry is evaluated on the final device raster; only texture addresses use capture scale.
 */
private val NativeGlassCompletionShaderPrefix = NavigationMaterialNativeShaderFunctions +
    NavigationMaterialSourceShaderFunctions + NativeGlassEdgeFunctions + NativeCasdfHighlightMaskFunctions +
    NativeVibrantColorMatrixFunctions + """
uniform float2 nativeOutputOrigin;
uniform float2 nativeOutputSize;
uniform float nativeOutputScale;
uniform float nativeOutputDensity;
uniform float2 nativeTextureOrigin;
uniform float nativeClearFraction;
uniform float nativeEdgeAlpha;
uniform float nativeKeyThreshold;
uniform float2 nativeTopHighlight;
uniform float4 nativeShadow;
uniform float4 nativeSourceShadow;
uniform float4 nativeSourceShadowBounds;
uniform float2 nativeSourceShadowRadii;
uniform float4 nativeIdleColor;
uniform float4 nativeOverlayColor;
float3 nativeCompletionField(float2 point) {
    float3 field = navigationGlassShapeField(point * nativeOutputScale);
    return float3(field.x / nativeOutputScale, field.yz);
}
float nativeCompletionShadowDistance(float2 point) {
    return navigationGlassShadowDistance(point * nativeOutputScale) / nativeOutputScale;
}
"""

private const val NativeGlassCompletionTintMask = """
float nativeCompletionTintMask(float3 field) {
    float aa = clamp(abs(field.y) + abs(field.z), .0001, 1.0);
    // Captured CASDF fill offset is negative: the mask retains the exterior half-point strip.
    float mask = clamp(.5 - (field.x - .5333333333333333 * nativeOutputDensity) / aa, 0.0, 1.0);
    if (field.x <= 0.0 || mask <= 0.0) return mask;
    // Native ramp stops [-1,0,10] keep their first two white stops. Only the exterior
    // [0,10] interval uses the captured cubic Bezier easing before the DestIn fill.
    float x = clamp(field.x / (10.0 * nativeOutputDensity), 0.0, 1.0);
    float lo = 0.0;
    float hi = 1.0;
    for (int i = 0; i < 12; i++) {
        float t = (lo + hi) * .5;
        float s = 1.0 - t;
        float q = 3.0 * s * s * t * .60938 + 3.0 * s * t * t * .47124 + t * t * t;
        if (q < x) lo = t; else hi = t;
    }
    float t = (lo + hi) * .5;
    float s = 1.0 - t;
    float eased = 3.0 * s * s * t * .00663 + 3.0 * s * t * t * .99115 + t * t * t;
    return mask * (1.0 - eased);
}
"""

private const val NativeGlassCompletionShaderBodyPrefix = """
float4 nativeCompletionAppearance(float4 filtered, float3 original, float4 shadowResult,
    float coverage, float key, float ring, float tintMask, float highlightMask,
    bool targetAlternate, bool sourceAlternate) {
    float4 faceResult = nativeCompletionFaceForAppearance(filtered, targetAlternate, sourceAlternate);
    faceResult.rgb = mix(faceResult.rgb, nativeIdleColor.rgb, nativeIdleColor.a);
    faceResult.a = nativeIdleColor.a + faceResult.a * (1.0 - nativeIdleColor.a);
    faceResult.rgb = mix(faceResult.rgb, nativeOverlayColor.rgb, nativeOverlayColor.a);
    faceResult.a = nativeOverlayColor.a + faceResult.a * (1.0 - nativeOverlayColor.a);
    float4 covered = mix(shadowResult, faceResult, coverage);
    covered.rgb *= 1.0 - ring;
    covered.a = covered.a * (1.0 - ring) + ring;
    float3 originalFace = nativeCompletionFaceForAppearance(float4(original, 1.0), targetAlternate, sourceAlternate).rgb;
    float4 completed = nativeGlassBackgroundCompletion(covered, original, originalFace, key, 1.0, -.3);
    // The tint layer sees the actual reconstructed backdrop, not signed relative RGB.
    completed = nativeCompletionTint(nativeCompletionTint(completed, original, tintMask, coverage, true, sourceAlternate),
        original, tintMask, coverage, false, targetAlternate);
    if (highlightMask > 0.0) {
        float4 actual = float4(completed.rgb + (1.0 - completed.a) * original, 1.0);
        float4 light = nativeVibrantColorMatrix(float4(0.0, 0.0, 0.0, highlightMask), actual);
        completed = light + completed * (1.0 - light.a);
    }
    return completed;
}
half4 main(float2 coordinate) {
    if (coordinate.x < 0.0 || coordinate.y < 0.0 ||
        coordinate.x >= nativeOutputSize.x + nativeOutputOrigin.x * 2.0 ||
        coordinate.y >= nativeOutputSize.y + nativeOutputOrigin.y * 2.0) return half4(0.0);
    float2 p = coordinate - nativeOutputOrigin;
    float3 field = nativeCompletionField(p);
    float coverage = nativeGlassShapeCoverage(field, 1.0);
    float key = nativeGlassStrokeKeyFill(field, nativeOutputDensity, 1.0, nativeKeyThreshold) * nativeEdgeAlpha;
    float shifted = nativeCompletionField(p - float2(0.0, 8.0 * nativeOutputDensity)).x;
    float ring = nativeGlassRingCoverage(shifted, coverage, nativeOutputDensity, nativeClearFraction) * nativeEdgeAlpha;
    float shadow = nativeShadow.x > 0.0
        ? nativeGaussianTail(nativeCompletionShadowDistance(p - float2(0.0, nativeShadow.z)) / nativeShadow.x) * nativeShadow.y : 0.0;
"""

private const val NativeGlassCompletionSourceShadowBlock = """
    if (nativeSourceShadow.x > 0.0) {
        float d = nativeGlassRoundedField(p - nativeSourceShadowBounds.xy - float2(0.0, nativeSourceShadow.z),
            nativeSourceShadowBounds.zw, nativeSourceShadowRadii, .5).x;
        float sourceShadow = nativeGaussianTail(d / nativeSourceShadow.x) * nativeSourceShadow.y;
        shadow = sourceShadow + shadow * (1.0 - sourceShadow);
    }
"""

private const val NativeGlassCompletionShaderBodySuffix = """
    if (coverage <= 0.0 && key <= .000001 && shadow <= .000001 && ring <= .000001) return half4(0.0);
    float2 samplePoint = nativeTextureOrigin + p * nativeOutputScale;
    float4 originalRaw = nativeAtlasBilinear(samplePoint, 0);
    float3 original = nativeAtlasFlush(originalRaw.rgb / max(originalRaw.a, .000001));
    float4 filtered = coverage > 0.0 ? nativeAtlasFiltered(samplePoint,
        navigationGlassField(p * nativeOutputScale)) : float4(original, 1.0);
    float4 shadowResult = float4(original * (1.0 - nativeShadow.w) * shadow, shadow);
    if (originalRaw.a < 1.0) {
        // A caller may deliberately provide no captured backdrop (emptyBackdrop). Keep
        // its traditional black-fill shadow instead of interpreting missing color as an
        // opaque black capture. This is a portable input adapter, not a native chooser.
        float capturedAlpha = clamp(originalRaw.a, 0.0, 1.0);
        shadowResult = float4(originalRaw.rgb * (1.0 - nativeShadow.w) * shadow,
            (nativeShadow.w + (1.0 - nativeShadow.w) * capturedAlpha) * shadow);
    }
    float tintMask = nativeCompletionTintMask(field);
    float highlightMask = nativeTopHighlight.x > 0.0
        ? nativeCasdfHighlightCoverage(field.x, field.yz, nativeOutputDensity,
            nativeTopHighlight.y, .75) * nativeTopHighlight.x : 0.0;
"""

private const val NativeGlassCompletionShaderSuffix = """
    return half4(nativeAtlasEncodeRelative(completed, originalRaw));
}
"""

/** Keep the full uniform layout while bounding the reachable material graph to its recipes. */
internal fun nativeGlassCompletionShader(topology: NativeGlassCompletionTopology): String {
    val sourceFace = when {
        !topology.hasSourceRecipe -> "sampled"
        topology.hasSourceAppearance -> "source_navigationMaterialFaceRawForAppearance(sampled, sourceAlternate)"
        else -> "source_endpoint_navigationMaterialFaceRaw(sampled)"
    }
    val targetFace = if (topology.hasTargetAppearance)
        "navigationMaterialFaceRawForAppearance(result, targetAlternate)" else "endpoint_navigationMaterialFaceRaw(result)"
    val sourceTint = if (topology.hasSourceRecipe) {
        val alternate = if (topology.hasSourceAppearance) "alternate" else "false"
        "source_navigationMaterialTintForAppearance(destination, original, mask, coverage, $alternate)"
    } else {
        // A missing recipe writes source_materialOpacity=0; retain the original zero-alpha over.
        "float4(0.0) + destination * (1.0 - source_materialOpacity)"
    }
    val targetAlternate = if (topology.hasTargetAppearance) "alternate" else "false"
    return NativeGlassCompletionShaderPrefix + """
float4 nativeCompletionFaceForAppearance(float4 sampled, bool targetAlternate, bool sourceAlternate) {
    float4 result = mix(sampled, $sourceFace, source_materialOpacity);
    return mix(result, $targetFace, materialOpacity);
}
float4 nativeCompletionTint(float4 destination, float3 original, float mask, float coverage, bool source, bool alternate) {
    return source ? $sourceTint
        : navigationMaterialTintForAppearance(destination, original, mask, coverage, $targetAlternate);
}
""" + NativeGlassCompletionTintMask +
        NativeGlassCompletionShaderBodyPrefix +
        (if (topology.hasSourceShadow) NativeGlassCompletionSourceShadowBlock else "") +
        NativeGlassCompletionShaderBodySuffix + buildString {
        if (topology.hasTargetAppearance) append("""
    float targetMix = materialOpacity > 0.0 ? materialAppearanceMix : 0.0;
""")
        if (topology.hasSourceRecipe && topology.hasSourceAppearance) append("""
    float sourceMix = source_materialOpacity > 0.0 ? source_materialAppearanceMix : 0.0;
""")
        append("""
    float4 completed = nativeCompletionAppearance(filtered, original, shadowResult,
        coverage, key, ring, tintMask, highlightMask, false, false);
""")
        if (topology.hasTargetAppearance) append("""
    if (targetMix > 0.0) {
        float4 alternate = nativeCompletionAppearance(filtered, original, shadowResult,
            coverage, key, ring, tintMask, highlightMask, true, false);
        completed = mix(completed, alternate, targetMix);
    }
""")
        if (topology.hasSourceRecipe && topology.hasSourceAppearance) {
            append("""
    if (sourceMix > 0.0) {
        float4 alternate = nativeCompletionAppearance(filtered, original, shadowResult,
            coverage, key, ring, tintMask, highlightMask, false, true);
""")
            if (topology.hasTargetAppearance) append("""
        if (targetMix > 0.0) {
            float4 both = nativeCompletionAppearance(filtered, original, shadowResult,
                coverage, key, ring, tintMask, highlightMask, true, true);
            alternate = mix(alternate, both, targetMix);
        }
""")
            append("""
        completed = mix(completed, alternate, sourceMix);
    }
""")
        }
    } + NativeGlassCompletionShaderSuffix
}

/** Full graph retained for callers without known recipe structure. */
internal val NativeGlassCompletionShader =
    nativeGlassCompletionShader(NativeGlassCompletionTopology(true, true, true, hasSourceShadow = true))
