package io.github.neoruaa.glasmio.glass

import android.graphics.BlendMode
import android.graphics.RenderEffect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScope
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScopeImpl
import io.github.neoruaa.glasmio.glass.render.asAndroidRuntimeShader
import io.github.neoruaa.glasmio.glass.render.cachedResource
import io.github.neoruaa.glasmio.glass.render.effects.blur
import io.github.neoruaa.glasmio.glass.render.effects.vibrancy
import io.github.neoruaa.glasmio.glass.render.isRuntimeShaderSupported
import io.github.neoruaa.glasmio.glass.render.optics.GlassSdfShader
import io.github.neoruaa.glasmio.glass.render.optics.NativeGlassRefractionFunctions
import io.github.neoruaa.glasmio.glass.render.optics.NativeGlassSdfFunctions
import io.github.neoruaa.glasmio.glass.render.optics.glassCornerRadii
import io.github.neoruaa.glasmio.glass.render.GlassColorSpaceShaderFunctions
import io.github.neoruaa.glasmio.glass.render.highlight.NativeGlassEdgeFunctions
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow

/** Reuses a cached GPU contour field in effect pixels. */
internal class NativeGlassFilterField(
    val shaderFunctions: String,
    val hasBoundedOpticalNormal: Boolean = false,
    val mipFaceRegions: Pair<Rect, Rect>? = null,
    val configure: (android.graphics.RuntimeShader) -> Unit,
)

internal fun nativeGlassCompletionFieldFunctions(functions: String): String {
    var result = functions
    val hasShapeField = functions.contains("navigationGlassShapeField(")
    if (!hasShapeField) result += """
float3 navigationGlassShapeField(float2 point) { return navigationGlassField(point); }
"""
    if (!functions.contains("navigationGlassShadowDistance(")) result += """
float navigationGlassShadowDistance(float2 point) { return navigationGlassField(point).x; }
"""
    if (!functions.contains("navigationGlassOpticalFieldFromShape(")) result += """
float3 navigationGlassOpticalFieldFromShape(float2 point, float3 rawShapeField) {
    return ${if (hasShapeField) "navigationGlassField(point)" else "rawShapeField"};
}
"""
    return result + if (functions == NativeDefaultFieldShader) NativeDefaultOffsetDistances else NativeCustomOffsetDistances
}

private const val NativeCustomOffsetDistances = """
float2 navigationGlassOffsetDistances(float2 point, bool ring, bool shadow) {
    return float2(ring ? navigationGlassShapeField(point).x : 0.0,
        shadow ? navigationGlassShadowDistance(point) : 0.0);
}
"""

private const val NativeDefaultOffsetDistances = """
float2 navigationGlassOffsetDistances(float2 point, bool ring, bool shadow) {
    float2 halfSize = size * .5;
    float radius = radiusAt(point);
    float expanded = radius * 1.5286649465560913;
    float2 circle = abs(point - halfSize) - halfSize + expanded;
    float interior = max(circle.x, circle.y);
    // Both supercircle coordinates and curvatures vanish inside this central rectangle.
    // Retain the original two operations around expanded to preserve floating-point rounding.
    if (radius >= .001 && min(halfSize.x, halfSize.y) > expanded && interior <= 0.0)
        return float2(interior - expanded);
    return float2(navigationGlassField(point).x);
}
"""

internal const val DefaultNativeGlassAtlas = true
internal const val NativeGlassAtlasBackingCopy = false

/** Recovered mip sampling and inner -> original-coordinate fill -> outer composition. */
internal fun BackdropEffectScope.nativeNavigationGlassFilter(
    profile: NavigationGlassGeometryProfile,
    isDark: Boolean,
    blurRadius: Float,
    refractionEnabled: Boolean,
    clear: Boolean,
    sourceOrigin: Offset? = null,
    field: NativeGlassFilterField? = null,
    fillFraction: Float = 1f,
    outerOpacityMultiplier: Float = 1f,
    sourceSize: Size? = null,
    useAtlas: Boolean = DefaultNativeGlassAtlas,
    completion: NativeGlassCompletion? = null,
    darkFraction: Float = if (isDark) 1f else 0f,
) {
    if (!isRuntimeShaderSupported()) {
        if (!clear) vibrancy()
        val radius = blurRadius.takeIf { it.isFinite() && it > 0f } ?: 0f
        if (radius > 0f) blur(radius)
        return
    }
    val source = renderEffect?.asAndroidRenderEffect()
    val radius = blurRadius.takeIf { it.isFinite() && it > 0f } ?: 0f
    val fillRadius = if (clear || radius <= 0f) 0f else radius * (8f / 5f)
    val fillOpacity = fillFraction.coerceIn(0f, 1f)
    val outerOpacity = if (clear || !refractionEnabled) 0f else .6f * outerOpacityMultiplier.coerceIn(0f, 1f)
    val maximumLod = nativeNavigationGlassLod(max(radius, fillRadius))
    if (sourceSize == null) {
        // The outermost compute tap reaches four previous-level texels, with a
        // half-texel bilinear footprint. Keep the complete recursively sampled source.
        val mipSupport = 4.5f * (2f.pow(ceil(maximumLod)) - 1f)
        padding = max(padding, mipSupport + if (outerOpacity > 0f) profile.outerAmount * density else 0f)
    }
    val texture = sourceSize ?: Size(size.width + padding * 2f, size.height + padding * 2f)
    val pyramid = cachedResource("Glasmio.NativeMip.Pyramid") { NativeGlassMipPyramid(this) }
    if (useAtlas && nativeAtlasNavigationFilter(pyramid, source, texture, maximumLod, profile,
            darkFraction.coerceIn(0f, 1f), radius, fillRadius, fillOpacity, outerOpacity, refractionEnabled, sourceOrigin, field, completion)) return
    val levels = pyramid.levels(source, IntSize(ceil(texture.width).toInt(), ceil(texture.height).toInt()),
        maximumLod, NativeGlassMipKernel.Compute13Tap)
    val fieldFunctions = field?.shaderFunctions ?: NativeDefaultFieldShader
    val corners = if (field == null) glassCornerRadii(shape, size, layoutDirection, this) else null

    fun input(program: String, key: String, backing: RenderEffect?, configure: (android.graphics.RuntimeShader) -> Unit): RenderEffect {
        val shader = obtainRuntimeShader(key, program).asAndroidRuntimeShader()
        configure(shader)
        val effect = RenderEffect.createRuntimeShaderEffect(shader, "content")
        return backing?.let { RenderEffect.createChainEffect(effect, it) } ?: effect
    }
    fun geometry(shader: android.graphics.RuntimeShader, points: Boolean = true) {
        if (field == null) {
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("cornerRadii", requireNotNull(corners))
        } else field.configure(shader)
        val origin = sourceOrigin ?: Offset(padding, padding)
        shader.setFloatUniform("captureOrigin", origin.x, origin.y)
        if (points) shader.setFloatUniform("pointScale", density)
    }
    fun displacement(shader: android.graphics.RuntimeShader, outer: Boolean) {
        val height = (if (outer) profile.outerHeight else profile.innerHeight) * density
        shader.setFloatUniform("inverseHeight", if (refractionEnabled && height > 0f) 1f / height else 0f)
        shader.setFloatUniform("signedAmount", if (refractionEnabled)
            (if (outer) profile.outerAmount else -profile.innerAmount) * density else 0f)
    }
    fun main(outer: Boolean): RenderEffect {
        val prefix = if (outer) "outer" else "inner"
        var result: RenderEffect? = null
        levels.forEach { level ->
            val ray = input(fieldFunctions + NativeRayShader, "Glasmio.Native.$prefix.ray.${level.index}", level.effect) {
                geometry(it, points = false)
                displacement(it, outer)
                level.configure(it, sampling = true, weighting = false)
            }
            val weighted = input(fieldFunctions + NativeBlurWeightShader,
                "Glasmio.Native.$prefix.weight.${level.index}", ray) {
                geometry(it)
                displacement(it, outer)
                level.configure(it, sampling = false, weighting = true)
                it.setFloatUniform("mainRadius", radius)
                it.setFloatUniform("blurParameters", profile.mainBlurDistance0, profile.mainBlurAlpha0, profile.mainBlurAlpha1)
            }
            result = result?.let { RenderEffect.createBlendModeEffect(it, weighted, BlendMode.PLUS) } ?: weighted
        }
        return requireNotNull(result)
    }
    var result = main(false)
    if (fillRadius > 0f && fillOpacity > 0f) {
        val lod = nativeNavigationGlassLod(fillRadius)
        val bounded = lod.coerceIn(0f, levels.last().index.toFloat())
        var fill: RenderEffect? = null
        levels.forEach { level ->
            val weight = (1f - abs(bounded - level.index)).coerceAtLeast(0f)
            if (weight > 0f) {
                val branch = input(NativeFillSampleShader, "Glasmio.Native.fill.${level.index}", level.effect) {
                    level.configure(it, sampling = true, weighting = false)
                    it.setFloatUniform("sampleOffset", lod * .25f)
                    it.setFloatUniform("sampleWeight", weight)
                }
                fill = fill?.let { RenderEffect.createBlendModeEffect(it, branch, BlendMode.PLUS) } ?: branch
            }
        }
        fun opacity(alpha: Float, key: String): RenderEffect = input(NativeOpacityShader, key, requireNotNull(fill)) {
            it.setFloatUniform("opacity", alpha)
        }
        // Native recipes have only one nonzero min/max weight. Premultiplied
        // DARKEN/LIGHTEN followed by normal fill preserves their opaque-source equation.
        result = RenderEffect.createBlendModeEffect(result, opacity(.9f * fillOpacity, "Glasmio.Native.fill.minmax"),
            if (isDark) BlendMode.DARKEN else BlendMode.LIGHTEN)
        result = RenderEffect.createBlendModeEffect(result, opacity(.5f * fillOpacity, "Glasmio.Native.fill.normal"), BlendMode.SRC_OVER)
    }
    if (outerOpacity > 0f) {
        val outer = input(fieldFunctions + NativeOuterMixShader, "Glasmio.Native.outer.edge", main(true)) {
            geometry(it)
            it.setFloatUniform("outerOpacity", outerOpacity)
        }
        result = RenderEffect.createBlendModeEffect(result, outer, BlendMode.SRC_OVER)
    }
    renderEffect = result.asComposeRenderEffect()
}

private data class NativeAtlasMaterialSourceKey(
    val functions: String,
    val colorManaged: Boolean,
    val nativeGammaStorage: Boolean,
    val completionTopology: NativeGlassCompletionTopology?,
    val samplingLayoutKey: String,
    val hasOpticalStage: Boolean,
)

/** The producer is materialized before this single sampled material program evaluates its ROI. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun BackdropEffectScope.nativeAtlasNavigationFilter(
    pyramid: NativeGlassMipPyramid,
    source: RenderEffect?,
    sourceSize: Size,
    maximumLod: Float,
    profile: NavigationGlassGeometryProfile,
    darkFraction: Float,
    radius: Float,
    fillRadius: Float,
    fillOpacity: Float,
    outerOpacity: Float,
    refractionEnabled: Boolean,
    sourceOrigin: Offset?,
    field: NativeGlassFilterField?,
    completion: NativeGlassCompletion?,
): Boolean {
    val storage = this as? BackdropEffectScopeImpl ?: return false
    // Store managed mip intermediates as working-space colors. Native-gamma channel
    // transport through HWUI layers can darken deformed or resized surfaces.
    // Decode at each sampling boundary; SDR retains its existing fast path.
    val origin = sourceOrigin ?: Offset(padding, padding)
    val finalSampleRegion = if (storage.colorManagedNativeGlass && storage.nativeGlassHardwareRendering &&
        field?.hasBoundedOpticalNormal == true && completion != null && sourceOrigin == null &&
        storage.samplingScale == 1f && completion.textureScale == null && completion.outputSize == null &&
        completion.outputDensity == null && completion.sourceShadow == null &&
        sourceSize.width in 1f..16384f && sourceSize.height in 1f..16384f &&
        padding in 0f..16384f && density in .125f..32f && maximumLod in 0f..32f &&
        storage.fullSize == size &&
        (!refractionEnabled || (profile.innerHeight.isFinite() && profile.outerHeight.isFinite()))) {
        val outputDensity = storage.sourceDensity.density
        val guard = ceil(max(.5333333333333333f * outputDensity + 1f,
            navigationShadowSupport(completion.shadow?.radius ?: 0f, outputDensity)))
        // The two-capsule union mixes unit gradients and only contracts their axes. Include
        // the complete output guard, both rays, the fill offsets and rounding of the raster.
        val ray = if (refractionEnabled) max(abs(profile.innerAmount), abs(profile.outerAmount)) * density else 0f
        val margin = guard + max(ray * 1.125f, maximumLod * .25f) + 2f
        if (margin in 0f..16384f && size.width > 0f && size.height > 0f &&
            size.maxDimension + guard * 2f <= 16384f) {
            IntRect(floor(origin.x - margin).toInt(), floor(origin.y - margin).toInt(),
                ceil(origin.x + size.width + margin).toInt(), ceil(origin.y + size.height + margin).toInt())
        } else null
    } else null
    val sparseInput = field?.mipFaceRegions?.takeIf {
        finalSampleRegion != null && profile.mainBlurAlpha0 in 0f..1f && profile.mainBlurAlpha1 in 0f..1f &&
            profile.mainBlurDistance0.isFinite()
    }?.let { (first, second) ->
        val ray = if (refractionEnabled) max(abs(profile.innerAmount), abs(profile.outerAmount)) * density else 0f
        NativeMipSampleIslands(first.translate(origin), second.translate(origin), ray * 1.125f + 2f,
            maximumLod * .25f + 2f, ceil(nativeNavigationGlassLod(radius) + .001f).toInt())
    }
    val atlas = pyramid.atlas(source, IntSize(ceil(sourceSize.width).toInt(), ceil(sourceSize.height).toInt()),
        maximumLod, NativeGlassMipKernel.Compute13Tap, colorManaged = storage.colorManagedNativeGlass,
        nativeGammaStorage = false,
        allowHardwareLinearSampling = storage.supportsNativeGlassHardwareLinearSampling,
        finalSampleRegion = finalSampleRegion, sampleIslands = sparseInput)
    val functions = field?.shaderFunctions ?: NativeDefaultFieldShader
    val requestedTopology = completion?.programTopology()
    // A morph can bind its own size and radii to the same default field program.
    val shareOptics = Build.VERSION.SDK_INT >= 36 && functions == NativeDefaultFieldShader && !atlas.colorManaged &&
        storage.supportsNativeGlassHardwareLinearSampling &&
        (if (requestedTopology == null) storage.supportsRetainedOpticalStage
         else requestedTopology == OrdinaryAtlasCompletion && (storage.nativeGlassHardwareRendering ||
             (atlas.hasRegularMipLayout && !atlas.nativeGammaStorage)))
    val completionTopology = if (shareOptics) OrdinaryAtlasCompletion else requestedTopology
    // Keep the full field source in the key so hash collisions retain source validation.
    val sources = storage.cachedResource("Glasmio.NativeAtlas.material.sources") {
        mutableMapOf<NativeAtlasMaterialSourceKey, String>()
    }
    val program = if (shareOptics && atlas.hasRegularMipLayout) NativeRetainedCompletionProgram
    else sources.getOrPut(NativeAtlasMaterialSourceKey(
        functions, atlas.colorManaged, atlas.nativeGammaStorage, completionTopology, atlas.samplingLayoutKey, shareOptics,
    )) {
        (if (completionTopology != null) nativeGlassCompletionFieldFunctions(functions) else functions) +
            (if (completionTopology?.hasSourceShadow == true &&
                !functions.contains("float3 nativeGlassRoundedField(")) NativeGlassSdfFunctions else "") +
            atlas.samplingFunctions + NativeAtlasMaterialFunctions + if (completionTopology == null) NativeAtlasMaterialMain
        else (if (!atlas.colorManaged) GlassColorSpaceShaderFunctions else "") +
            nativeGlassCompletionShader(completionTopology, if (shareOptics) NativeAtlasOpticalStage else "")
    }
    val shader = obtainRuntimeShader("Glasmio.NativeAtlas.material.${atlas.colorManaged}.${atlas.nativeGammaStorage}.${completion != null}.${completionTopology?.cacheKey ?: 0}.layout=${atlas.samplingLayoutKey}.${functions.hashCode()}",
        program).asAndroidRuntimeShader()
    atlas.configure(shader)
    if (field == null) {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("cornerRadii", glassCornerRadii(shape, size, layoutDirection, this))
    } else field.configure(shader)
    shader.setFloatUniform("captureOrigin", origin.x, origin.y)
    shader.setFloatUniform("pointScale", density)
    shader.setFloatUniform("nativeAtlasRadii", radius, fillRadius)
    shader.setFloatUniform("nativeAtlasBlurParameters", profile.mainBlurDistance0, profile.mainBlurAlpha0, profile.mainBlurAlpha1)
    shader.setFloatUniform("nativeAtlasLensParameters",
        if (refractionEnabled && profile.innerHeight > 0f) 1f / (profile.innerHeight * density) else 0f,
        if (refractionEnabled) -profile.innerAmount * density else 0f,
        if (refractionEnabled && profile.outerHeight > 0f) 1f / (profile.outerHeight * density) else 0f,
        if (refractionEnabled) profile.outerAmount * density else 0f)
    shader.setFloatUniform("nativeAtlasFillWeights", .9f * fillOpacity * darkFraction,
        .9f * fillOpacity * (1f - darkFraction), .5f * fillOpacity)
    shader.setFloatUniform("nativeAtlasOuterOpacity", outerOpacity)
    storage.materializeNativeGlassAtlas(atlas)
    if (completion != null) configureNativeGlassCompletion(shader, completion, storage, origin)
    else if (shareOptics) shader.setFloatUniform("nativeOutputScale", -1f)
    renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    return true
}

/** CPU radius packing and the AIR's piecewise explicit-LOD transform. */
internal fun nativeNavigationGlassLod(radius: Float): Float {
    val packed = radius.coerceAtLeast(0f) * 1.6f
    return log2(if (packed < 2f) 1f + packed * .5f else packed).coerceAtLeast(0f)
}

internal const val NativeDefaultFieldShader = GlassSdfShader + """
float3 navigationGlassField(float2 point) { return glassField(point, 0.0); }
float3 navigationGlassOpticalFieldFromShape(float2 point, float3 rawShapeField) { return rawShapeField; }
"""

private const val NativeRayShader = NativeGlassMipSamplingFunctions + NativeGlassRefractionFunctions + """
uniform float2 captureOrigin;
uniform float inverseHeight;
uniform float signedAmount;
half4 main(float2 coordinate) {
    float3 field = navigationGlassField(coordinate - captureOrigin);
    float displacement = nativeGlassDisplacement(field.x, inverseHeight, signedAmount);
    return half4(nativeMipBilinear(coordinate + field.yz * displacement));
}
"""

private const val NativeBlurWeightShader = NativeGlassMipSamplingFunctions + NativeGlassRefractionFunctions + """
uniform float2 captureOrigin;
uniform float pointScale;
uniform float3 blurParameters;
uniform float mainRadius;
uniform float inverseHeight;
uniform float signedAmount;
half4 main(float2 coordinate) {
    float sd = navigationGlassField(coordinate - captureOrigin).x;
    float d = (sd + nativeGlassDisplacement(sd, inverseHeight, signedAmount)) / max(pointScale, .00001);
    float opacity = d < -1.0 ? mix(blurParameters.y, blurParameters.z,
        clamp((d - blurParameters.x) / max(-1.0 - blurParameters.x, .00001), 0.0, 1.0))
        : (d < 0.0 ? mix(blurParameters.z, .5, d + 1.0) : 1.0);
    float weight = nativeMipWeight(nativeGlassLod(mainRadius * opacity));
    return weight > 0.0 ? content.eval(coordinate) * half(weight) : half4(0.0);
}
"""

private const val NativeOuterMixShader = NativeGlassRefractionFunctions + """
uniform shader content;
uniform float2 captureOrigin;
uniform float pointScale;
uniform float outerOpacity;
half4 main(float2 coordinate) {
    float sd = navigationGlassField(coordinate - captureOrigin).x;
    return content.eval(coordinate) * half(nativeGlassRefractionWeight(sd, -pointScale, 0.0, outerOpacity));
}
"""

private const val NativeFillSampleShader = NativeGlassMipSamplingFunctions + """
uniform float sampleOffset;
uniform float sampleWeight;
half4 main(float2 p) {
    return half4((nativeMipBilinear(p - sampleOffset) + nativeMipBilinear(p + sampleOffset)) * (.5 * sampleWeight));
}
"""

private const val NativeOpacityShader = """
uniform shader content;
uniform float opacity;
half4 main(float2 p) { return content.eval(p) * half(opacity); }
"""

internal const val NativeAtlasMaterialFunctions = NativeGlassRefractionFunctions + """
uniform float2 captureOrigin;
uniform float pointScale;
uniform float2 nativeAtlasRadii;
uniform float3 nativeAtlasBlurParameters;
uniform float4 nativeAtlasLensParameters;
uniform float3 nativeAtlasFillWeights;
uniform float nativeAtlasOuterOpacity;
float3 nativeAtlasFlush(float3 color) {
    return float3(abs(color.r) < .000001 ? 0.0 : color.r,
        abs(color.g) < .000001 ? 0.0 : color.g, abs(color.b) < .000001 ? 0.0 : color.b);
}
float nativeAtlasBlurOpacity(float distance) {
    float d = distance / max(pointScale, .00001);
    return d < -1.0 ? mix(nativeAtlasBlurParameters.y, nativeAtlasBlurParameters.z,
        clamp((d - nativeAtlasBlurParameters.x) / max(-1.0 - nativeAtlasBlurParameters.x, .00001), 0.0, 1.0))
        : (d < 0.0 ? mix(nativeAtlasBlurParameters.z, .5, d + 1.0) : 1.0);
}
float3 nativeAtlasRayAddress(float2 coordinate, float3 field, bool outer) {
    float inverseHeight = outer ? nativeAtlasLensParameters.z : nativeAtlasLensParameters.x;
    float amount = outer ? nativeAtlasLensParameters.w : nativeAtlasLensParameters.y;
    float displacement = nativeGlassDisplacement(field.x, inverseHeight, amount);
    float lod = nativeGlassLod(nativeAtlasRadii.x * nativeAtlasBlurOpacity(field.x + displacement));
    return float3(coordinate + field.yz * displacement, lod);
}
float4 nativeAtlasFiltered(float2 coordinate, float3 field) {
    bool hasFill = nativeAtlasRadii.y > 0.0 && nativeAtlasFillWeights.z > 0.0;
    float outerOpacity = nativeAtlasOuterOpacity > 0.0
        ? nativeGlassRefractionWeight(field.x, -pointScale, 0.0, nativeAtlasOuterOpacity) : 0.0;
    float fillLod = hasFill ? nativeGlassLod(nativeAtlasRadii.y) : 0.0;
    float4 inner = float4(0.0), outer = float4(0.0), fillPair = float4(0.0);
    // Keep one copy of the color-managed trilinear sampler in the optical program.
    for (int ray = 0; ray < 4; ++ray) {
        float3 address;
        if (ray == 0) {
            address = nativeAtlasRayAddress(coordinate, field, false);
        } else if (ray == 3) {
            if (outerOpacity <= 0.0) break;
            address = nativeAtlasRayAddress(coordinate, field, true);
        } else {
            if (!hasFill) continue;
            float offset = fillLod * .25;
            address = float3(ray == 1 ? coordinate - offset : coordinate + offset, fillLod);
        }
        float4 sampleValue = nativeAtlasSample(address.xy, address.z);
        if (ray == 0) inner = sampleValue;
        else if (ray == 1) fillPair = sampleValue;
        else if (ray == 2) fillPair = (fillPair + sampleValue) * .5;
        else outer = sampleValue;
    }
    float3 color = nativeAtlasFlush(inner.rgb / max(inner.a, .000001));
    if (hasFill) {
        float3 fill = nativeAtlasFlush(fillPair.rgb / max(fillPair.a, .000001));
        color = nativeAtlasFillWeights.x * min(color, fill) + nativeAtlasFillWeights.y * max(color, fill)
            + (1.0 - nativeAtlasFillWeights.x - nativeAtlasFillWeights.y) * color;
        color = mix(color, fill, nativeAtlasFillWeights.z);
    }
    float4 result = float4(color, 1.0);
    if (outerOpacity > 0.0) {
        outer.rgb = nativeAtlasFlush(outer.rgb);
        result = mix(result, outer, outerOpacity);
    }
    return result;
}
"""

// The retained SDR morph and the ordinary surface share a driver program. A still finishes
// on its original texture grid, so the final material sees the original quantized texels.
internal val OrdinaryAtlasCompletion = NativeGlassCompletionTopology(
    hasSourceRecipe = false, hasTargetAppearance = false, hasSourceAppearance = false,
    hasSourceShadow = false, hasTargetFills = false, hasSourceFills = false, hasSourceTintMatrix = false,
)

// A negative last level identifies the retained pair. Each member has a full base grid.
// Optical draws still use hardware-linear mips; the stored completion uses four manual taps.
internal val NativeRetainedCompletionProgram: String by lazy {
    nativeGlassCompletionFieldFunctions(NativeDefaultFieldShader) + nativeRegularHardwareSdrSampling +
        NativeAtlasMaterialFunctions + GlassColorSpaceShaderFunctions + NativeStoredPairBilinear + """
float4 nativeStoredOrOptical(float2 coordinate, float3 field) {
    if (nativeAtlasLastLevel < 0.0) return nativeStoredPairBilinear(coordinate, nativeAtlasRects[1]);
    return nativeAtlasFiltered(coordinate, field);
}
float4 nativeStoredOrOriginal(float2 coordinate) {
    if (nativeAtlasLastLevel < 0.0) return nativeStoredPairBilinear(coordinate, nativeAtlasRects[0]);
    return nativeAtlasBilinear(coordinate, 0);
}
""" + nativeGlassCompletionShader(OrdinaryAtlasCompletion, NativeAtlasOpticalStage, retainedPair = true)
}

private const val NativeAtlasMaterialOutput = """    if (coordinate.x < 0.0 || coordinate.y < 0.0 || coordinate.x >= nativeAtlasBaseSize.x
        || coordinate.y >= nativeAtlasBaseSize.y) return half4(0.0);
    return half4(nativeAtlasEncode(nativeAtlasFiltered(coordinate,
        navigationGlassField(coordinate - captureOrigin))));
"""

private const val NativeAtlasMaterialMain = """
half4 main(float2 coordinate) {
""" + NativeAtlasMaterialOutput + "}\n"

private const val NativeAtlasOpticalStage = """    if (nativeOutputScale < 0.0) {
""" + NativeAtlasMaterialOutput + "    }\n"
