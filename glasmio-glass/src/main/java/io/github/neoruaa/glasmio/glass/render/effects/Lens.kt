package io.github.neoruaa.glasmio.glass.render.effects

import io.github.neoruaa.glasmio.glass.render.BackdropEffectScope
import io.github.neoruaa.glasmio.glass.render.asAndroidRuntimeShader
import io.github.neoruaa.glasmio.glass.render.optics.GlassSdfShader
import io.github.neoruaa.glasmio.glass.render.optics.NativeGlassRefractionFunctions
import io.github.neoruaa.glasmio.glass.render.optics.NativeGlassSpectralFunctions
import io.github.neoruaa.glasmio.glass.render.optics.CustomGlassChromaticFunctions
import io.github.neoruaa.glasmio.glass.render.optics.glassCornerRadii
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.graphics.RenderEffect

/** Recovered native inner/outer rays; chromatic opt-in remains a separate custom control effect. */
fun BackdropEffectScope.lens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Boolean = false,
    outerRefractionHeight: Float = 0f,
    outerRefractionAmount: Float = 0f,
    refractionOpacity: Float = .6f,
) {
    if (refractionHeight <= 0f || refractionAmount <= 0f || size.minDimension <= 0f) return
    val shader = obtainRuntimeShader("Glasmio.SDF.Refraction", GlassRefractionShader)
    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("cornerRadii", glassCornerRadii(shape, size, layoutDirection, this))
    shader.setFloatUniform("capturePadding", padding)
    shader.setFloatUniform("height", refractionHeight)
    shader.setFloatUniform("amount", refractionAmount)
    shader.setFloatUniform("outerHeight", outerRefractionHeight.coerceAtLeast(0f))
    shader.setFloatUniform("outerAmount", outerRefractionAmount.coerceAtLeast(0f))
    shader.setFloatUniform("refractionOpacity", refractionOpacity.coerceIn(0f, 1f))
    shader.setFloatUniform("refractionEdgeWidth", density)
    shader.setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
    shader.setFloatUniform("chromatic", if (chromaticAberration) 1f else 0f)
    effect(RenderEffect.createRuntimeShaderEffect(shader.asAndroidRuntimeShader(), "content").asComposeRenderEffect())
}

private const val GlassRefractionShader = GlassSdfShader + NativeGlassRefractionFunctions + """
uniform shader content;
uniform float capturePadding;
uniform float height;
uniform float amount;
uniform float outerHeight;
uniform float outerAmount;
uniform float refractionOpacity;
uniform float refractionEdgeWidth;
uniform float depthEffect;
uniform float chromatic;
half4 nativeGlassSample(float2 coordinate, float radius) { return content.eval(coordinate); }
""" + NativeGlassSpectralFunctions + CustomGlassChromaticFunctions + """
half4 main(float2 coordinate) {
    float2 p = coordinate - capturePadding;
    float3 field = glassField(p, depthEffect * 0.5);
    float sd = field.x;
    if (sd > 0.0 || -sd >= max(height, outerHeight)) return content.eval(coordinate);
    float innerDistance = nativeGlassDisplacement(sd, 1.0 / max(height, 0.001), -amount);
    float2 displacement = field.yz * innerDistance;
    half4 result = nativeGlassSample(coordinate + displacement, 0.0);
    if (chromatic > 0.5) {
        // The legacy caller-controlled vector is not an inferred UIKit uniform default.
        result = nativeGlassAberrate(coordinate + displacement,
            customGlassChromaticVector(displacement, p - size * 0.5, size * 0.5), 0.0);
    }
    if (outerHeight > 0.0 && outerAmount != 0.0 && refractionOpacity > 0.0) {
        float outerDistance = nativeGlassDisplacement(sd, 1.0 / outerHeight, outerAmount);
        half4 outer = nativeGlassSample(coordinate + field.yz * outerDistance, 0.0);
        float weight = nativeGlassRefractionWeight(sd, -refractionEdgeWidth, 0.0, refractionOpacity);
        result = mix(result, outer, half(weight));
    }
    return result;
}
"""
