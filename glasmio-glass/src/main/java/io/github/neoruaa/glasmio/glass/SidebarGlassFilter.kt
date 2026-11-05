package io.github.neoruaa.glasmio.glass

import android.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScope
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScopeImpl
import io.github.neoruaa.glasmio.glass.render.asAndroidRuntimeShader
import io.github.neoruaa.glasmio.glass.render.backdrops.applySidebarBackgroundExtension
import io.github.neoruaa.glasmio.glass.render.cachedResource
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow

/**
 * The planar SidebarRecipe endpoint, independent of the regular navigation platter.
 * Background extension precedes the material; the mip/face path preserves native gamma-space
 * color processing without transporting numeric gamma colors through HWUI textures.
 */
fun BackdropEffectScope.sidebarGlass(backdrop: Backdrop, isDark: Boolean) {
    val storage = this as? BackdropEffectScopeImpl ?: return
    // Spec.Blur evaluates the largest-area minimum distance; regular sidebars are rectangles.
    // Its 64...160 -> 0...4 recipe scale is doubled by the Spec-to-CA parameter conversion.
    val dimension = min(size.width, size.height) / density
    val radius = ((dimension - 64f) / 96f).coerceIn(0f, 1f) * 8.dp.toPx()
    val fillRadius = 16.dp.toPx()
    val maximumLod = nativeNavigationGlassLod(maxOf(radius, fillRadius))
    // Preserve the complete mip kernel and the preceding 10pt extension Gaussian support.
    val mipSupport = 4.5f * (2f.pow(ceil(maximumLod)) - 1f)
    padding = maxOf(padding, mipSupport + 30.dp.toPx())
    applySidebarBackgroundExtension(backdrop)
    val preceding = storage.materializedInputEffects
    val source = renderEffect?.asAndroidRenderEffect()
    val pyramid = cachedResource("Glasmio.Sidebar.MipPyramid") { NativeGlassMipPyramid(this) }
    val atlas = pyramid.atlas(source,
        IntSize(ceil(size.width + padding * 2f).toInt(), ceil(size.height + padding * 2f).toInt()),
        maximumLod, NativeGlassMipKernel.Compute13Tap,
        colorManaged = storage.colorManagedNativeGlass, nativeGammaStorage = false,
        allowHardwareLinearSampling = storage.supportsNativeGlassHardwareLinearSampling)
    val program = atlas.samplingFunctions + SidebarFaceShader
    val shader = obtainRuntimeShader("Glasmio.Sidebar.Face.${atlas.samplingLayoutKey}.${atlas.colorManaged}", program)
        .asAndroidRuntimeShader()
    atlas.configure(shader)
    shader.setFloatUniform("sidebarRadii", radius, fillRadius)
    shader.setFloatUniform("sidebarFillWeights", if (isDark) .9f else 0f, if (isDark) 0f else .9f, .5f)
    // SidebarRecipe.FaceEffect, active regular-contrast branch and ordinary background content.
    shader.setFloatUniform("sidebarFace", if (isDark) .6f else 1.2f,
        if (isDark) .1f else .4f, if (isDark) .5f else 1.03f, if (isDark) 1f else .85f)
    shader.setFloatUniform("sidebarWhiteFill", if (isDark) 0f else .2f)
    storage.materializeNativeGlassAtlas(atlas)
    storage.materializedInputEffects = preceding + storage.materializedInputEffects
    renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
}

private const val SidebarFaceShader = """
uniform float2 sidebarRadii;
uniform float3 sidebarFillWeights;
uniform float4 sidebarFace;
uniform float sidebarWhiteFill;
half4 main(float2 coordinate) {
    if (coordinate.x < 0.0 || coordinate.y < 0.0 || coordinate.x >= nativeAtlasBaseSize.x
        || coordinate.y >= nativeAtlasBaseSize.y) return half4(0.0);
    float4 primary = nativeAtlasSample(coordinate, nativeGlassLod(sidebarRadii.x));
    float3 color = primary.rgb / max(primary.a, .000001);
    float lod = nativeGlassLod(sidebarRadii.y);
    float offset = lod * .25;
    float4 sampledFill = (nativeAtlasSample(coordinate - offset, lod)
        + nativeAtlasSample(coordinate + offset, lod)) * .5;
    float3 fill = sampledFill.rgb / max(sampledFill.a, .000001);
    color = sidebarFillWeights.x * min(color, fill) + sidebarFillWeights.y * max(color, fill)
        + (1.0 - sidebarFillWeights.x - sidebarFillWeights.y) * color;
    color = mix(color, fill, sidebarFillWeights.z);
    float luma = dot(color, float3(.2126, .7152, .0722));
    float gain = clamp(1.0 - luma * (1.0 - sidebarFace.w), 0.0, 1.0);
    color = mix(float3(luma * gain), color * gain, 1.0 + .3 * (1.0 - gain));
    luma = dot(color, float3(.2126, .7152, .0722));
    color = (luma * (sidebarFace.z - sidebarFace.y) + sidebarFace.y)
        + (color - luma) * sidebarFace.x;
    color = mix(color, float3(1.0), sidebarWhiteFill);
    return half4(nativeAtlasEncode(float4(color, 1.0)));
}
"""
