/*
 * Refraction math adapted from AndroidLiquidGlass, Copyright 2025 Kyant.
 * Licensed under the Apache License, Version 2.0.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.neoruaa.glasmio.glass

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.icons.*

import io.github.neoruaa.glasmio.glass.render.BackdropEffectScope
import io.github.neoruaa.glasmio.glass.render.asAndroidRuntimeShader
import io.github.neoruaa.glasmio.glass.render.effects.lens
import io.github.neoruaa.glasmio.glass.render.effects.runtimeShaderEffect
import io.github.neoruaa.glasmio.glass.render.isRuntimeShaderSupported
import io.github.neoruaa.glasmio.glass.render.optics.NativeGlassRefractionFunctions
import io.github.neoruaa.glasmio.glass.render.optics.NativeGlassSdfFunctions
import io.github.neoruaa.glasmio.glass.render.optics.NativeGlassSpectralFunctions
import io.github.neoruaa.glasmio.glass.render.optics.CustomGlassChromaticFunctions
import androidx.compose.ui.geometry.Offset
import kotlin.math.ceil

/** One cached contour texture supplies native sampling, edges and custom centered optics. */
internal fun navigationContainerGlassField(
    shape: LiquidContactShape,
    opticalCenter: Offset? = null,
    minimumBand: Float = 0f,
): NativeGlassFilterField {
    val scale = shape.outlineScale.let { Offset(it.x.coerceAtLeast(.0001f), it.y.coerceAtLeast(.0001f)) }
    // A tab platter keeps the server's native primitive union and its mixed gradient.
    // An optical center is an explicit custom contour effect, not a native union input.
    shape.nativeUnion?.let { return it.shaderField(scale, shape.outlineTranslation) }
    val contour = shape.refractionContour()
    val field = shape.refractionField()
    val grid = field.grid(contour, maxOf(shape.refractionBand, contour.bounds.size.minDimension * .5f, minimumBand))
    val input = field.input(grid)
    return NativeGlassFilterField(NavigationContainerGlassFieldShader) { shader ->
        shader.setInputBuffer("contour", input)
        shader.setFloatUniform("fieldScale", 1f / (scale.x * grid.step), 1f / (scale.y * grid.step))
        shader.setFloatUniform("fieldOffset",
            (-shape.outlineTranslation.x / scale.x - grid.origin.x) / grid.step + .5f,
            (-shape.outlineTranslation.y / scale.y - grid.origin.y) / grid.step + .5f)
        shader.setFloatUniform("normalScale", 1f / scale.x, 1f / scale.y)
        shader.setFloatUniform("opticalCenter", opticalCenter?.x ?: 0f, opticalCenter?.y ?: 0f)
        shader.setFloatUniform("depthCenterEnabled", if (opticalCenter == null) 0f else 1f)
    }
}

/** Toolbar contours are captured in local physical pixels without an optical center. */
internal fun navigationToolbarGlassField(
    shape: ToolbarActionShape,
    minimumBand: Float,
): NativeGlassFilterField {
    shape.nativeUnion?.let { return it.shaderField(Offset(1f, 1f), Offset.Zero) }
    val contour = shape.refractionContour()
    val field = shape.refractionField()
    val grid = field.grid(contour, maxOf(contour.bounds.size.minDimension * .5f, minimumBand))
    val input = field.input(grid)
    return NativeGlassFilterField(NavigationContainerGlassFieldShader) { shader ->
        shader.setInputBuffer("contour", input)
        shader.setFloatUniform("fieldScale", 1f / grid.step, 1f / grid.step)
        shader.setFloatUniform("fieldOffset", -grid.origin.x / grid.step + .5f, -grid.origin.y / grid.step + .5f)
        shader.setFloatUniform("normalScale", 1f, 1f)
        shader.setFloatUniform("opticalCenter", 0f, 0f)
        shader.setFloatUniform("depthCenterEnabled", 0f)
    }
}

private const val NavigationContainerGlassFieldShader = """
uniform shader contour;
uniform float2 fieldScale;
uniform float2 fieldOffset;
uniform float2 normalScale;
uniform float2 opticalCenter;
uniform float depthCenterEnabled;
float2 navigationContainerNormal(float2 v) { return v / max(length(v), 0.0001); }
float3 navigationGlassShapeField(float2 point) {
    float3 field = float3(contour.eval(point * fieldScale + fieldOffset).rgb);
    float2 transformed = field.yz * normalScale;
    float distance = field.x / max(length(transformed), 0.0001);
    float2 normal = navigationContainerNormal(transformed);
    return float3(distance, normal);
}
float3 navigationGlassField(float2 point) {
    float3 field = navigationGlassShapeField(point);
    float2 normal = field.yz;
    if (depthCenterEnabled > 0.5) {
        float2 depthNormal = navigationContainerNormal(point - opticalCenter);
        normal = navigationContainerNormal(normal + depthNormal * max(dot(normal, depthNormal), 0.0));
    }
    return float3(field.x, normal);
}
"""

/** The clipping path may separate or join; its depth reference stays in container space. */
internal fun BackdropEffectScope.navigationContainerLens(
    shape: LiquidContactShape,
    opticalCenter: Offset,
    refractionHeight: Float,
    refractionAmount: Float,
    outerRefractionHeight: Float = 0f,
    outerRefractionAmount: Float = 0f,
    refractionOpacity: Float = .6f,
    chromaticAberration: Boolean = true,
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return
    if (padding > 0f) padding = (padding - refractionHeight).coerceAtLeast(0f)
    val scale = shape.outlineScale
    val distanceScale = minOf(scale.x, scale.y)
    if (distanceScale <= 0f) return
    val field = navigationContainerGlassField(shape, opticalCenter,
        maxOf(refractionHeight, outerRefractionHeight) / distanceScale)
    val native = shape.nativeUnion != null
    val functions = field.shaderFunctions + (if (native) "\nuniform float2 opticalCenter;\n" else "")
    runtimeShaderEffect(if (native) "NativeNavigationContainerLens" else "NavigationContainerLens",
        functions + NavigationContainerRefractionShader, "content") {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("offset", -padding, -padding)
        field.configure(asAndroidRuntimeShader())
        if (native) setFloatUniform("opticalCenter", opticalCenter.x, opticalCenter.y)
        setFloatUniform("refractionHeight", refractionHeight)
        setFloatUniform("refractionAmount", -refractionAmount)
        setFloatUniform("outerHeight", outerRefractionHeight.coerceAtLeast(0f))
        setFloatUniform("outerAmount", outerRefractionAmount.coerceAtLeast(0f))
        setFloatUniform("refractionOpacity", refractionOpacity.coerceIn(0f, 1f))
        setFloatUniform("refractionEdgeWidth", density)
        setFloatUniform("chromatic", if (chromaticAberration) 1f else 0f)
    }
}

private const val NavigationContainerRefractionShader = NativeGlassRefractionFunctions + """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float outerHeight;
uniform float outerAmount;
uniform float refractionOpacity;
uniform float refractionEdgeWidth;
uniform float chromatic;

float2 safeNormal(float2 v) {
    return v / max(length(v), 0.0001);
}
half4 nativeGlassSample(float2 coordinate, float radius) { return content.eval(coordinate); }
""" + NativeGlassSpectralFunctions + CustomGlassChromaticFunctions + """

half4 main(float2 coord) {
    float2 point = coord + offset;
    float3 field = navigationGlassField(point);
    float distance = field.x;
    if (distance > 0.0 || -distance >= max(refractionHeight, outerHeight)) return content.eval(coord);
    float d = nativeGlassDisplacement(distance, 1.0 / refractionHeight, refractionAmount);
    float2 depth = point - opticalCenter;
    float2 gradient = field.yz;
    float2 refracted = coord + d * gradient;
    half4 result = nativeGlassSample(refracted, 0.0);
    if (chromatic > 0.5) result = nativeGlassAberrate(refracted,
        customGlassChromaticVector(d * gradient, depth, size * 0.5), 0.0);
    if (outerHeight > 0.0 && outerAmount != 0.0 && refractionOpacity > 0.0) {
        float outerDistance = nativeGlassDisplacement(distance, 1.0 / outerHeight, outerAmount);
        half4 outer = nativeGlassSample(coord + outerDistance * gradient, 0.0);
        result = mix(result, outer, half(nativeGlassRefractionWeight(distance, -refractionEdgeWidth, 0.0, refractionOpacity)));
    }
    return result;
}
"""

/** Redirects only the horizontal edge rays facing a neighboring bottom control. */
fun BackdropEffectScope.navigationContactLens(
    refractionHeight: Float,
    refractionAmount: Float,
    contactDirection: Float,
) {
    if (!isRuntimeShaderSupported()) return
    // Cache the parsed shader at rest instead of compiling its source during first contact.
    obtainRuntimeShader("NavigationContactLens", NavigationContactRefractionShader)
    val direction = contactDirection.coerceIn(-1f, 1f)
    if (direction == 0f) {
        lens(refractionHeight, refractionAmount, chromaticAberration = true)
        return
    }
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    // Cover the full outward displacement and dispersion without resizing on each press frame.
    padding = ceil(maxOf(padding, 2f * NavigationIndicatorRefractionAmount.toPx()))
    runtimeShaderEffect("NavigationContactLens", NavigationContactRefractionShader, "content") {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("offset", -padding, -padding)
        setFloatUniform("radius", size.minDimension / 2f)
        setFloatUniform("refractionHeight", refractionHeight)
        setFloatUniform("refractionAmount", -refractionAmount)
        setFloatUniform("contactDirection", direction)
    }
}

private const val NavigationContactRefractionShader = NativeGlassSdfFunctions + NativeGlassRefractionFunctions + """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float radius;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float contactDirection;

half4 nativeGlassSample(float2 coordinate, float blurRadius) { return content.eval(coordinate); }
""" + NativeGlassSpectralFunctions + CustomGlassChromaticFunctions + """

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float3 field = nativeGlassRoundedField(centeredCoord, halfSize, float2(radius), 0.0);
    float sd = field.x;
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    float d = nativeGlassDisplacement(sd, 1.0 / refractionHeight, refractionAmount);
    float2 grad = field.yz;
    // Preserve vertical refraction and the opposite edge while opening the neighboring edge.
    float facing = smoothstep(0.0, 1.0, grad.x * sign(contactDirection));
    grad.x *= 1.0 - 2.0 * abs(contactDirection) * facing;

    float2 refractedCoord = coord + d * grad;
    return nativeGlassAberrate(refractedCoord,
        customGlassChromaticVector(d * grad, centeredCoord, halfSize), 0.0);
}
"""
