package io.github.neoruaa.glasmio.glass

import android.os.Build
import androidx.annotation.RequiresApi
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
    val pressGlow: NativeGlassPressGlow? = null,
    val specializeMenuTint: Boolean = false,
)

/** A present capability keeps its shader topology even when no member is pressed. */
internal data class NativeGlassPressGlow(val sample: SampledPressHighlight?)

/** Recipe structure stays active even when an animation temporarily gives it zero opacity. */
internal data class NativeGlassCompletionTopology(
    val hasSourceRecipe: Boolean,
    val hasTargetAppearance: Boolean,
    val hasSourceAppearance: Boolean,
    val hasSourceShadow: Boolean = true,
    val hasTargetFills: Boolean = true,
    val hasSourceFills: Boolean = true,
    val hasPressGlow: Boolean = false,
    val hasTargetTintMatrix: Boolean = true,
    val hasSourceTintMatrix: Boolean = true,
    val hasShadow: Boolean = true,
) {
    val cacheKey: Int
        get() = (if (hasSourceRecipe) 4 else 0) or (if (hasTargetAppearance) 2 else 0) or
            (if (hasSourceAppearance) 1 else 0) or (if (hasSourceShadow) 8 else 0) or
            (if (hasTargetFills) 16 else 0) or (if (hasSourceFills) 32 else 0) or
            (if (hasPressGlow) 64 else 0) or (if (hasTargetTintMatrix) 128 else 0) or
            (if (hasSourceTintMatrix) 256 else 0) or (if (hasShadow) 512 else 0)
}

internal fun NavigationMaterialRecipe.hasTintMatrix(): Boolean =
    tintMatrix != null || appearanceAlternate?.tintMatrix != null

internal fun NativeGlassCompletion.programTopology(): NativeGlassCompletionTopology =
    NativeGlassCompletionTopology(
        hasSourceRecipe = sourceRecipe != null,
        hasTargetAppearance = recipe.appearanceAlternate != null,
        hasSourceAppearance = sourceRecipe?.appearanceAlternate != null,
        hasSourceShadow = sourceShadow != null,
        hasTargetFills = recipe.hasFillLayers(),
        hasSourceFills = sourceRecipe?.hasFillLayers() == true,
        hasPressGlow = pressGlow != null,
        hasTargetTintMatrix = recipe.hasTintMatrix(),
        hasSourceTintMatrix = sourceRecipe?.hasTintMatrix() == true,
        hasShadow = shadow != null || sourceShadow != null,
    )

internal data class NativeGlassSourceShadow(
    val profile: NativeNavigationShadowProfile,
    val bounds: Rect,
    val radii: Offset,
    val alpha: Float,
)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
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
    val shadowRadius = max(shadow?.radius ?: 0f, completion.sourceShadow?.profile?.radius ?: 0f)
    val tintGuard = if (completion.recipe.hasTintMatrix() || completion.sourceRecipe?.hasTintMatrix() == true)
        10f * density + 1.5f else 0f
    val guard = ceil(max(max(.5333333333333333f * density + 1f, tintGuard),
        navigationShadowSupport(shadowRadius, density)))
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
    val sourceShadow = completion.sourceShadow
    if (shadow != null || sourceShadow != null) {
        shader.setFloatUniform("nativeShadow", shadow?.radius?.times(density) ?: 0f,
            (shadow?.opacity ?: 0f) * completion.shadowAlpha.coerceIn(0f, 1f), 8f * density, .3f)
        if (sourceShadow != null) {
            shader.setFloatUniform("nativeSourceShadow", sourceShadow.profile.radius * density,
                sourceShadow.profile.opacity * sourceShadow.alpha, 8f * density, .3f)
            val sourceBounds = sourceShadow.bounds
            shader.setFloatUniform("nativeSourceShadowBounds", sourceBounds.center.x, sourceBounds.center.y,
                sourceBounds.width * .5f, sourceBounds.height * .5f)
            shader.setFloatUniform("nativeSourceShadowRadii", sourceShadow.radii.x, sourceShadow.radii.y)
        }
    }
    fun color(name: String, value: Color) = shader.setFloatUniform(name, value.red, value.green, value.blue, value.alpha)
    color("nativeIdleColor", completion.idleColor)
    color("nativeOverlayColor", completion.overlayColor)
    val minimumDimension = output.minDimension / density
    // Each retained program has a fixed recipe topology. Upload only endpoints it can read.
    fun setRecipe(recipe: NavigationMaterialRecipe, alpha: Float, set: (String, FloatArray) -> Unit) {
        if (recipe.appearanceAlternate != null) updateNavigationMaterialUniforms(recipe, alpha, set)
        else updateNavigationMaterialEndpointUniforms(recipe, alpha, set)
    }
    setRecipe(completion.recipe.forMinimumDimension(minimumDimension), completion.materialAlpha) { name, value ->
        shader.setFloatUniform(name, value)
    }
    completion.sourceRecipe?.let { source ->
        setRecipe(source, completion.sourceAlpha) { name, value -> shader.setFloatUniform("source_$name", value) }
    }
    completion.pressGlow?.let { glow ->
        val sample = glow.sample
        val bounds = sample?.bounds ?: Rect.Zero
        val press = sample?.highlight
        val position = press?.position ?: Offset.Zero
        configureNativePressGlow(shader,
            progress = if (bounds.isEmpty) 0f else press?.glowProgress ?: 0f,
            position = bounds.topLeft + Offset(bounds.width * position.x, bounds.height * position.y),
            bounds = bounds, density = density, clampPosition = true, horizontalRadius = null,
            isDark = completion.isDark, tint = null, clear = false,
            dissipationProgress = press?.dissipationProgress ?: 0f,
            boundsHighlight = sample?.boundsHighlight ?: false,
        )
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
private val NativeGlassCompletionShaderPrefix = NativeGlassEdgeFunctions + NativeCasdfHighlightMaskFunctions +
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
uniform float4 nativeIdleColor;
uniform float4 nativeOverlayColor;
"""

private const val NativeGlassCompletionShadowUniforms = """
uniform float4 nativeShadow;
"""

private const val NativeGlassCompletionSourceShadowUniforms = """
uniform float4 nativeSourceShadow;
uniform float4 nativeSourceShadowBounds;
uniform float2 nativeSourceShadowRadii;
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
    float t = x;
    // The captured x curve has derivative >= .6429 throughout [0,1].
    for (int i = 0; i < 4; i++) {
        float s = 1.0 - t;
        float q = 3.0 * s * s * t * .60938 + 3.0 * s * t * t * .47124 + t * t * t;
        float derivative = 3.0 * s * s * .60938 + 6.0 * s * t * (.47124 - .60938)
            + 3.0 * t * t * (1.0 - .47124);
        t = clamp(t - (q - x) / derivative, 0.0, 1.0);
    }
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
    float3 originalFace = float3(0.0);
    if (key > .000001 && covered.a < 1.0)
        originalFace = nativeCompletionFaceForAppearance(float4(original, 1.0), targetAlternate, sourceAlternate).rgb;
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
"""

private const val NativeGlassCompletionShaderBodyPrefixWithoutShadow = """
float4 nativeCompletionAppearance(float4 filtered, float3 original,
    float coverage, float key, float ring, float tintMask, float highlightMask,
    bool targetAlternate, bool sourceAlternate) {
    float4 faceResult = nativeCompletionFaceForAppearance(filtered, targetAlternate, sourceAlternate);
    faceResult.rgb = mix(faceResult.rgb, nativeIdleColor.rgb, nativeIdleColor.a);
    faceResult.a = nativeIdleColor.a + faceResult.a * (1.0 - nativeIdleColor.a);
    faceResult.rgb = mix(faceResult.rgb, nativeOverlayColor.rgb, nativeOverlayColor.a);
    faceResult.a = nativeOverlayColor.a + faceResult.a * (1.0 - nativeOverlayColor.a);
    float4 covered = faceResult * coverage;
    covered.rgb *= 1.0 - ring;
    covered.a = covered.a * (1.0 - ring) + ring;
    float3 originalFace = float3(0.0);
    if (key > .000001 && covered.a < 1.0)
        originalFace = nativeCompletionFaceForAppearance(float4(original, 1.0), targetAlternate, sourceAlternate).rgb;
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
"""

private const val NativeGlassCompletionShapeWithShadow = """    if (coordinate.x < 0.0 || coordinate.y < 0.0 ||
        coordinate.x >= nativeOutputSize.x + nativeOutputOrigin.x * 2.0 ||
        coordinate.y >= nativeOutputSize.y + nativeOutputOrigin.y * 2.0) return half4(0.0);
    float2 p = coordinate - nativeOutputOrigin;
    float3 rawShapeField = navigationGlassShapeField(p * nativeOutputScale);
    float3 field = float3(rawShapeField.x / nativeOutputScale, rawShapeField.yz);
    float coverage = nativeGlassShapeCoverage(field, 1.0);
    float key = coverage < 1.0 && nativeEdgeAlpha > 0.0
        ? nativeGlassStrokeKeyFill(field, nativeOutputDensity, 1.0, nativeKeyThreshold) * nativeEdgeAlpha : 0.0;
    bool needsRing = coverage > 0.0 && nativeClearFraction < 1.0 && nativeEdgeAlpha > 0.0 &&
        field.x >= -36.0 * nativeOutputDensity;
    bool needsShadow = nativeShadow.x > 0.0 && nativeShadow.y > 0.0;
    float ring = 0.0, shadow = 0.0;
    if (needsRing || needsShadow) {
        // configureNativeGlassCompletion uses the native eight-point offset for both passes.
        float2 shiftedPoint = (p - float2(0.0, nativeShadow.z)) * nativeOutputScale;
        float2 distances = navigationGlassOffsetDistances(shiftedPoint, needsRing, needsShadow) / nativeOutputScale;
        if (needsRing) ring = nativeGlassRingCoverage(distances.x, coverage, nativeOutputDensity, nativeClearFraction) * nativeEdgeAlpha;
        if (needsShadow) shadow = nativeGaussianTail(distances.y / nativeShadow.x) * nativeShadow.y;
    }
"""

private const val NativeGlassCompletionShapeWithoutShadow = """    if (coordinate.x < 0.0 || coordinate.y < 0.0 ||
        coordinate.x >= nativeOutputSize.x + nativeOutputOrigin.x * 2.0 ||
        coordinate.y >= nativeOutputSize.y + nativeOutputOrigin.y * 2.0) return half4(0.0);
    float2 p = coordinate - nativeOutputOrigin;
    float3 rawShapeField = navigationGlassShapeField(p * nativeOutputScale);
    float3 field = float3(rawShapeField.x / nativeOutputScale, rawShapeField.yz);
    float coverage = nativeGlassShapeCoverage(field, 1.0);
    float key = coverage < 1.0 && nativeEdgeAlpha > 0.0
        ? nativeGlassStrokeKeyFill(field, nativeOutputDensity, 1.0, nativeKeyThreshold) * nativeEdgeAlpha : 0.0;
    bool needsRing = coverage > 0.0 && nativeClearFraction < 1.0 && nativeEdgeAlpha > 0.0 &&
        field.x >= -36.0 * nativeOutputDensity;
    float ring = 0.0;
    if (needsRing) {
        float2 shiftedPoint = (p - float2(0.0, 8.0 * nativeOutputDensity)) * nativeOutputScale;
        float ringDistance = navigationGlassOffsetDistances(shiftedPoint, true, false).x / nativeOutputScale;
        ring = nativeGlassRingCoverage(ringDistance, coverage, nativeOutputDensity, nativeClearFraction) * nativeEdgeAlpha;
    }
"""

private const val NativeGlassCompletionSourceShadowBlock = """
    if (nativeSourceShadow.x > 0.0 && nativeSourceShadow.y > 0.0) {
        float d = nativeGlassRoundedField(p - nativeSourceShadowBounds.xy - float2(0.0, nativeSourceShadow.z),
            nativeSourceShadowBounds.zw, nativeSourceShadowRadii, .5).x;
        float sourceShadow = nativeGaussianTail(d / nativeSourceShadow.x) * nativeSourceShadow.y;
        shadow = sourceShadow + shadow * (1.0 - sourceShadow);
    }
"""

private const val NativeGlassCompletionShaderBodySuffixWithShadow = """
    if (coverage <= 0.0 && key <= .000001 && shadow <= .000001 && ring <= .000001) return half4(0.0);
    float2 samplePoint = nativeTextureOrigin + p * nativeOutputScale;
    float4 filtered = float4(0.0);
    if (coverage > 0.0) filtered = """

private const val NativeGlassCompletionShaderBodySuffixWithoutShadow = """
    if (coverage <= 0.0 && key <= .000001 && ring <= .000001) return half4(0.0);
    float2 samplePoint = nativeTextureOrigin + p * nativeOutputScale;
    float4 filtered = float4(0.0);
    if (coverage > 0.0) filtered = """

private const val NativeGlassCompletionFilteredArguments = """(samplePoint,
        navigationGlassOpticalFieldFromShape(p * nativeOutputScale, rawShapeField));
"""

private const val NativeGlassCompletionAfterTintMask = """
    float highlightMask = nativeTopHighlight.x > 0.0 && -field.x < 8.0 * nativeOutputDensity + 1.0
        ? nativeCasdfHighlightCoverage(field.x, field.yz, nativeOutputDensity,
            nativeTopHighlight.y, .75) * nativeTopHighlight.x : 0.0;
    float4 originalRaw = float4(0.0);
    float3 original = float3(0.0);
    // With a live shadow, even mix(shadowResult, faceResult, 1) can round against its RGB.
    if (coverage != 1.0 || filtered.a != 1.0 || shadow != 0.0 || highlightMask > 0.0 || key > .000001) {
        originalRaw = """

private const val NativeGlassCompletionAfterTintMaskWithoutShadow = """
    float highlightMask = nativeTopHighlight.x > 0.0 && -field.x < 8.0 * nativeOutputDensity + 1.0
        ? nativeCasdfHighlightCoverage(field.x, field.yz, nativeOutputDensity,
            nativeTopHighlight.y, .75) * nativeTopHighlight.x : 0.0;
    float4 originalRaw = float4(0.0);
    float3 original = float3(0.0);
    if (coverage != 1.0 || filtered.a != 1.0 || highlightMask > 0.0 || key > .000001) {
        originalRaw = """

private const val NativeGlassCompletionOriginalSuffixWithShadow = """;
        original = nativeAtlasFlush(originalRaw.rgb / max(originalRaw.a, .000001));
    }
    if (coverage <= 0.0) filtered = float4(original, 1.0);
    float4 shadowResult = float4(original * (1.0 - nativeShadow.w) * shadow, shadow);
    if (originalRaw.a < 1.0) {
        // A caller may deliberately provide no captured backdrop (emptyBackdrop). Keep
        // its traditional black-fill shadow instead of interpreting missing color as an
        // opaque black capture. This is a portable input adapter, not a native chooser.
        float capturedAlpha = clamp(originalRaw.a, 0.0, 1.0);
        shadowResult = float4(originalRaw.rgb * (1.0 - nativeShadow.w) * shadow,
            (nativeShadow.w + (1.0 - nativeShadow.w) * capturedAlpha) * shadow);
    }
"""

private const val NativeGlassCompletionOriginalSuffixWithoutShadow = """;
        original = nativeAtlasFlush(originalRaw.rgb / max(originalRaw.a, .000001));
    }
    if (coverage <= 0.0) filtered = float4(original, 1.0);
"""

private const val NativeGlassCompletionShaderSuffix = """
    return half4(nativeAtlasEncodeRelative(completed, originalRaw));
}
"""

/** Preserve the transparent capture's transfer; only the foreground delta receives coverage. */
private const val NativeGlassCompletionPressGlowSuffix = """
    float4 encoded = float4(half4(nativeAtlasEncodeRelative(completed, originalRaw)));
    if (coverage > 0.0 && (pressOpacity.x > 0.0 || pressOpacity.y > 0.0)) {
        half2 mask = half2(pressOpacity.x * nativePressBigMask(p), pressOpacity.y * nativePressShadow(p));
        if (mask.x > 0.0 || mask.y > 0.0) {
            half4 target = half4(glassWorkingToNativeGammaPremul(encoded));
            half4 glowing = nativeApplyPressGlow(target, float(mask.x), float(mask.y), pressDodgeGain);
            float4 working = float4(half4(glassNativeGammaToWorkingPremul(float4(glowing))));
            encoded += coverage * (working - encoded);
        }
    }
    return half4(encoded);
}
"""

/** An overlay-only endpoint uses the shape coverage directly. The exterior tint ramp belongs to a matrix. */
private fun nativeCompletionTintExpression(prefix: String, hasMatrix: Boolean, hasAppearance: Boolean): String {
    val alternate = if (hasAppearance) "alternate" else "false"
    if (hasMatrix) return "${prefix}navigationMaterialTintForAppearance(destination, original, mask, coverage, $alternate)"
    fun endpoint(name: String): String = if (hasAppearance) {
        "(alternate ? ${prefix}appearance_$name : $prefix$name)"
    } else "$prefix$name"
    return "nativeCompletionOverlay(destination, coverage, ${endpoint("overlay")}, ${endpoint("materialOpacity")})"
}

/** Assemble only material recipes that can contribute to this program. */
internal fun nativeGlassCompletionShader(topology: NativeGlassCompletionTopology,
    opticalStage: String = "",
    retainedPair: Boolean = false,
    twoRateSample: Boolean = false,
): String {
    val sourceFace = if (topology.hasSourceRecipe) {
        val face = if (topology.hasSourceAppearance) "source_navigationMaterialFaceRawForAppearance(sampled, sourceAlternate)"
            else "source_endpoint_navigationMaterialFaceRaw(sampled)"
        "if (source_materialOpacity > 0.0) result = mix(sampled, $face, source_materialOpacity);"
    } else ""
    val targetFace = if (topology.hasTargetAppearance)
        "navigationMaterialFaceRawForAppearance(result, targetAlternate)" else "endpoint_navigationMaterialFaceRaw(result)"
    val sourceTint = if (topology.hasSourceRecipe) {
        nativeCompletionTintExpression("source_", topology.hasSourceTintMatrix, topology.hasSourceAppearance)
    } else {
        // The original zero-opacity source performs this over, including its signed-zero behavior.
        "float4(0.0) + destination * 1.0"
    }
    val targetTint = nativeCompletionTintExpression("", topology.hasTargetTintMatrix, topology.hasTargetAppearance)
    val needsTintMask = topology.hasTargetTintMatrix || (topology.hasSourceRecipe && topology.hasSourceTintMatrix)
    val hasShadow = topology.hasShadow
    val sourceFunctions = if (topology.hasSourceRecipe) navigationMaterialSourceShaderFunctions(
        topology.hasSourceFills,
        hasAppearance = topology.hasSourceAppearance,
        hasTint = topology.hasSourceTintMatrix,
    ) else ""
    val appearanceCallSuffix = if (hasShadow) {
        "filtered, original, shadowResult,\n        coverage, key, ring, tintMask, highlightMask"
    } else {
        "filtered, original,\n        coverage, key, ring, tintMask, highlightMask"
    }
    return navigationMaterialNativeShaderFunctions(
        topology.hasTargetFills,
        hasAppearance = topology.hasTargetAppearance,
        hasTint = topology.hasTargetTintMatrix,
    ) + sourceFunctions + NativeGlassCompletionShaderPrefix +
        (if (hasShadow) NativeGlassCompletionShadowUniforms else "") +
        (if (hasShadow && topology.hasSourceShadow) NativeGlassCompletionSourceShadowUniforms else "") +
        (if (topology.hasPressGlow) NativePressGlowApplyFunctions + NativePressGlowMaskFunctions else "") + """
float4 nativeCompletionFaceForAppearance(float4 sampled, bool targetAlternate, bool sourceAlternate) {
    float4 result = sampled;
    $sourceFace
    if (materialOpacity > 0.0) result = mix(result, $targetFace, materialOpacity);
    return result;
}
float4 nativeCompletionOverlay(float4 destination, float coverage, float4 overlayColor, float opacity) {
    float alpha = coverage * overlayColor.a * opacity;
    return float4(overlayColor.rgb * alpha, alpha) + destination * (1.0 - alpha);
}
float4 nativeCompletionTint(float4 destination, float3 original, float mask, float coverage, bool source, bool alternate) {
    return source ? $sourceTint
        : $targetTint;
}
""" + (if (needsTintMask) NativeGlassCompletionTintMask else "") +
        (if (hasShadow) NativeGlassCompletionShaderBodyPrefix else NativeGlassCompletionShaderBodyPrefixWithoutShadow) +
        opticalStage +
        (if (hasShadow) NativeGlassCompletionShapeWithShadow else NativeGlassCompletionShapeWithoutShadow) +
        (if (hasShadow && topology.hasSourceShadow) NativeGlassCompletionSourceShadowBlock else "") +
        (if (hasShadow) NativeGlassCompletionShaderBodySuffixWithShadow else NativeGlassCompletionShaderBodySuffixWithoutShadow) +
        (if (twoRateSample) "nativeTwoRateFiltered(samplePoint);\n"
         else (if (retainedPair) "nativeStoredOrOptical" else "nativeAtlasFiltered") +
            NativeGlassCompletionFilteredArguments) + (if (needsTintMask)
            "    float tintMask = coverage == 1.0 ? 1.0 : nativeCompletionTintMask(field);\n"
        else "    float tintMask = 0.0;\n") +
        (if (hasShadow) NativeGlassCompletionAfterTintMask else NativeGlassCompletionAfterTintMaskWithoutShadow) +
        (if (twoRateSample) "nativeTwoRateOriginal(samplePoint)"
         else if (retainedPair) "nativeStoredOrOriginal(samplePoint)"
         else "nativeAtlasBilinear(samplePoint, 0)") +
        (if (hasShadow) NativeGlassCompletionOriginalSuffixWithShadow else NativeGlassCompletionOriginalSuffixWithoutShadow) +
        buildString {
        if (topology.hasTargetAppearance) append("""
    float targetMix = materialOpacity > 0.0 ? materialAppearanceMix : 0.0;
""")
        if (topology.hasSourceRecipe && topology.hasSourceAppearance) append("""
    float sourceMix = source_materialOpacity > 0.0 ? source_materialAppearanceMix : 0.0;
""")
        append("""
    float4 completed = nativeCompletionAppearance($appearanceCallSuffix, false, false);
""")
        if (topology.hasTargetAppearance) append("""
    if (targetMix > 0.0) {
        float4 alternate = nativeCompletionAppearance($appearanceCallSuffix, true, false);
        completed = mix(completed, alternate, targetMix);
    }
""")
        if (topology.hasSourceRecipe && topology.hasSourceAppearance) {
            append("""
    if (sourceMix > 0.0) {
        float4 alternate = nativeCompletionAppearance($appearanceCallSuffix, false, true);
""")
            if (topology.hasTargetAppearance) append("""
        if (targetMix > 0.0) {
            float4 both = nativeCompletionAppearance($appearanceCallSuffix, true, true);
            alternate = mix(alternate, both, targetMix);
        }
""")
            append("""
        completed = mix(completed, alternate, sourceMix);
    }
""")
        }
    } + if (topology.hasPressGlow) NativeGlassCompletionPressGlowSuffix else NativeGlassCompletionShaderSuffix
}

/** Full graph retained for callers without known recipe structure. */
internal val NativeGlassCompletionShader =
    nativeGlassCompletionShader(NativeGlassCompletionTopology(true, true, true, hasSourceShadow = true))
