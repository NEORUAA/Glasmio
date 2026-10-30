package io.github.neoruaa.glasmio.glass

import android.graphics.BlendMode
import android.graphics.RenderEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScope
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScopeImpl
import io.github.neoruaa.glasmio.glass.render.GlassColorSpaceShaderFunctions
import io.github.neoruaa.glasmio.glass.render.asAndroidRuntimeShader
import io.github.neoruaa.glasmio.glass.render.optics.glassCornerRadii
import io.github.neoruaa.glasmio.glass.render.optics.NativeGlassSdfFunctions
import java.util.LinkedHashMap
import kotlin.math.ceil
import kotlin.math.max

/**
 * Native face and coverage for an existing control's explicit optical recipe. The optical
 * lambda retains its own blur, track transform, lens and press physics. Its filtered sample
 * and the untouched source occupy separate GPU regions so completion always sees raw L0.
 */
fun BackdropEffectScope.nativeControlGlass(
    isDark: Boolean,
    materialAlpha: Float,
    idleColor: Color = Color.Transparent,
    overlayColor: Color = Color.Transparent,
    material: NavigationGlassMaterial? = null,
    optics: BackdropEffectScope.() -> Unit,
) = nativeControlGlassImpl(isDark, materialAlpha, idleColor, overlayColor, material, optics = optics)

internal fun BackdropEffectScope.nativeControlGlassImpl(
    isDark: Boolean,
    materialAlpha: Float,
    idleColor: Color = Color.Transparent,
    overlayColor: Color = Color.Transparent,
    material: NavigationGlassMaterial? = null,
    completion: NativeGlassCompletion? = null,
    field: NativeGlassFilterField? = null,
    sourcePadding: Float = 24.dp.toPx(),
    optics: BackdropEffectScope.() -> Unit,
) {
    val storage = this as? BackdropEffectScopeImpl
    if (storage == null) { optics(); return }
    // Existing controls use an explicit blur no larger than 8 pt and an inward lens.
    // Reserve its Gaussian source support before the lambda binds its capture origin.
    padding = max(padding, sourcePadding)
    val raw = renderEffect?.asAndroidRenderEffect()
    optics()
    val filtered = renderEffect?.asAndroidRenderEffect()
    val base = IntSize(ceil(size.width + padding * 2f).toInt().coerceAtLeast(1),
        ceil(size.height + padding * 2f).toInt().coerceAtLeast(1))
    fun region(name: String, source: RenderEffect?, y: Float): RenderEffect {
        val shader = obtainRuntimeShader("Glasmio.NativeControl.pack.$name", NativeControlRegionShader).asAndroidRuntimeShader()
        shader.setFloatUniform("regionSize", base.width.toFloat(), base.height.toFloat())
        shader.setFloatUniform("regionOffset", 0f, y)
        val mask = RenderEffect.createRuntimeShaderEffect(shader, "content")
        return source?.let { RenderEffect.createChainEffect(mask, it) } ?: mask
    }
    val producer = RenderEffect.createBlendModeEffect(region("raw", raw, 0f),
        region("filtered", filtered, base.height.toFloat()), BlendMode.PLUS)
    completeNativeFilteredGlass(base, producer, isDark, material, materialAlpha, idleColor, overlayColor,
        field = field, completion = completion)
}

private data class NativeControlCompletionSourceKey(
    val functions: String,
    val colorManaged: Boolean,
    val filteredIsNativeRaw: Boolean,
    val topology: NativeGlassCompletionTopology,
    val samplingLayoutKey: String,
)

/** Raw and explicitly filtered regions have identical grids, rather than native mip dimensions. */
internal fun BackdropEffectScope.completeNativeFilteredGlass(
    base: IntSize,
    producer: RenderEffect?,
    isDark: Boolean,
    material: NavigationGlassMaterial?,
    alpha: Float,
    idleColor: Color = Color.Transparent,
    overlayColor: Color = Color.Transparent,
    origin: Offset = Offset(padding, padding),
    field: NativeGlassFilterField? = null,
    completion: NativeGlassCompletion? = null,
    filteredIsNativeRaw: Boolean = false,
): RenderEffect {
    val storage = this as BackdropEffectScopeImpl
    val resolvedCompletion = completion ?: NativeGlassCompletion(NavigationMaterialRecipe.resolve(isDark, material), isDark,
        if (material == NavigationGlassMaterial.Clear) 1f else 0f,
        materialAlpha = alpha, edgeAlpha = alpha,
        idleColor = idleColor, overlayColor = overlayColor)
    val topology = resolvedCompletion.programTopology()
    val atlas = NativeGlassMipAtlas(base, IntSize(base.width, base.height * 2), listOf(
        IntRect(0, 0, base.width, base.height), IntRect(0, base.height, base.width, base.height * 2)),
        producer, storage.colorManagedNativeGlass)
    val functions = field?.shaderFunctions ?: NativeDefaultFieldShader
    val sources = storage.cachedResource("Glasmio.NativeControl.complete.sources") {
        object : LinkedHashMap<NativeControlCompletionSourceKey, String>(8, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<NativeControlCompletionSourceKey, String>?): Boolean =
                size > 8
        }
    }
    val source = sources.getOrPut(NativeControlCompletionSourceKey(functions, atlas.colorManaged, filteredIsNativeRaw, topology,
        samplingLayoutKey = atlas.samplingLayoutKey)) {
        nativeGlassCompletionFieldFunctions(functions) +
            (if (!functions.contains("float3 nativeGlassRoundedField(")) NativeGlassSdfFunctions else "") + atlas.samplingFunctions +
            (if (atlas.colorManaged) "" else GlassColorSpaceShaderFunctions) +
            (if (filteredIsNativeRaw) NativeControlFilteredRawFunctions else NativeControlFilteredFunctions) +
            nativeGlassCompletionShader(topology)
    }
    val shader = obtainRuntimeShader("Glasmio.NativeControl.complete.${atlas.colorManaged}.$filteredIsNativeRaw.${topology.cacheKey}.layout=${atlas.samplingLayoutKey}.${functions.hashCode()}",
        source).asAndroidRuntimeShader()
    atlas.configure(shader)
    if (field == null) {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("cornerRadii", glassCornerRadii(shape, size, layoutDirection, this))
    } else field.configure(shader)
    storage.materializeNativeGlassAtlas(atlas)
    configureNativeGlassCompletion(shader, resolvedCompletion, storage, origin)
    return RenderEffect.createRuntimeShaderEffect(shader, "content").also { renderEffect = it.asComposeRenderEffect() }
}

private const val NativeControlRegionShader = """
uniform shader content;
uniform float2 regionSize;
uniform float2 regionOffset;
half4 main(float2 p) {
    float2 local = p - regionOffset;
    if (local.x < 0.0 || local.y < 0.0 || local.x >= regionSize.x || local.y >= regionSize.y) return half4(0.0);
    return content.eval(local);
}
"""

private const val NativeControlFlushFunctions = """
float3 nativeAtlasFlush(float3 color) {
    return float3(abs(color.r) < .000001 ? 0.0 : color.r,
        abs(color.g) < .000001 ? 0.0 : color.g, abs(color.b) < .000001 ? 0.0 : color.b);
}
"""

private const val NativeControlFilteredFunctions = NativeControlFlushFunctions + """
float4 nativeAtlasFiltered(float2 coordinate, float3 field) {
    float4 sampled = nativeAtlasBilinear(coordinate, 1);
    return float4(nativeAtlasFlush(sampled.rgb / max(sampled.a, .000001)), sampled.a);
}
"""

private const val NativeControlFilteredRawFunctions = NativeControlFlushFunctions + """
float4 nativeAtlasFiltered(float2 coordinate, float3 field) {
    // Native inner/fill/outer already establishes its raw RGB/alpha contract.
    // A native outer sample with alpha below one must not be unpremultiplied here.
    return nativeAtlasBilinear(coordinate, 1);
}
"""
