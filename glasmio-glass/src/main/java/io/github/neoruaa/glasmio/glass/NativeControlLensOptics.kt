package io.github.neoruaa.glasmio.glass

/** Native UIKit lens roles; each role owns its optical and appearance recipe. */
enum class NativeControlLensStyle {
    SwitchThumb,
    SliderThumb,
    SegmentedIndicator,
    TabBarIndicator,
}

/** AIR UberShader::sdf_glass_displacement. The result is data (RG displacement/B coverage). */
internal const val NativeControlDisplacementFunctions = """
float4 nativeControlDisplacementMapForRaster(float4 field, float height, float curvature,
    float effectOffset, float maskOffset, float2 angle, float pixelScale) {
    float q = field.x + effectOffset;
    float depth = -q;
    float aa = max((abs(field.y) + abs(field.z)) * pixelScale, .0001);
    float maskDepth = depth - maskOffset;
    float coverage = maskDepth < -5.0 ? 0.0 : clamp(maskDepth / aa + .5, 0.0, 1.0) * field.a;
    float u = clamp(depth / height, 0.0, 1.0);
    float flatProfile = 1.0 - (u < 1.0 ? .2929 : 0.0);
    float rounded = sqrt(1.0 - (1.0 - u) * (1.0 - u));
    float profile = mix(flatProfile, rounded, curvature);
    float2 direction = float2(dot(field.yz, float2(angle.x, -angle.y)), dot(field.yz, angle.yx));
    float edge = clamp((q + height) / aa + .5, 0.0, 1.0);
    return float4(direction * (1.0 - profile) * edge, coverage, 1.0);
}
float4 nativeControlDisplacementMap(float4 field, float height, float curvature,
    float effectOffset, float maskOffset, float2 angle) {
    return nativeControlDisplacementMapForRaster(field, height, curvature, effectOffset, maskOffset, angle, 1.0);
}
float2 nativeControlMapAddress(float2 p, float4 map, float4 matrix, float2 offset) {
    float2 delta = map.rg - offset;
    return p + float2(dot(delta, matrix.xy), dot(delta, matrix.zw));
}
"""

/**
 * AIR glass_foreground_base; the caller supplies nativeControlForegroundSource(point).
 * This is a separate transfer from glass_background's aberrate_texture helper: all three
 * channels are premultiplied by averaged source alpha, SDF coverage and the field alpha.
 */
internal const val NativeControlForegroundFunctions = """
float2 nativeControlForegroundDirection(float2 gradient, float4 matrix, float2 direction) {
    return float2(dot(float2(dot(matrix.xy, direction), direction.x * matrix.y - direction.y * matrix.x), gradient),
        dot(float2(dot(matrix.zw, direction), direction.x * matrix.w - direction.y * matrix.z), gradient));
}
float nativeControlForegroundProfile(float sd, float amount, float inverseHeight, float offset) {
    // A captured zero height is packed as +infinity by the CPU. Use its defined
    // one-sided limit without sending a non-finite uniform through HWUI.
    float u = inverseHeight < 0.0 ? step(0.0, -sd - offset) : clamp((-sd - offset) * inverseHeight, 0.0, 1.0);
    return amount * (1.0 - clamp(sqrt(u * (2.0 - u)), 0.0, 1.0));
}
float3 nativeControlForegroundStraight(float4 source) {
    return source.rgb / max(source.a, .000001);
}
float4 nativeControlForegroundForRaster(float2 p, float4 field, float4 matrix,
    float3 refraction, float2 refractionDirection, float3 aberration, float2 aberrationDirection,
    float4 edge, float colorGain, float pixelScale) {
    if (field.a < .001) return float4(0.0);
    float opacity = mix(edge.z, edge.w, clamp((field.x - edge.x) / (edge.y - edge.x), 0.0, 1.0));
    float attenuation = 1.0 - opacity;
    if (attenuation <= 0.0) return float4(0.0);
    float aa = max((abs(field.y) + abs(field.z)) * pixelScale, .0001);
    float coverage = clamp(.5 - field.x / aa, 0.0, 1.0) * field.a;
    float2 refracted = p + nativeControlForegroundDirection(field.yz, matrix, refractionDirection)
        * nativeControlForegroundProfile(field.x, refraction.x, refraction.y, refraction.z);
    float2 delta = nativeControlForegroundDirection(field.yz, matrix, aberrationDirection).yx
        * nativeControlForegroundProfile(field.x, aberration.x, aberration.y, aberration.z);
    float4 r = nativeControlForegroundSource(refracted + delta);
    float4 o = r;
    float4 y = r;
    float4 g = r;
    float4 c = r;
    float4 b = r;
    float4 v = r;
    // A zero displacement addresses the same source texel seven times. Reuse only
    // that sample; preserve the native weighted RGB and alpha arithmetic below.
    if (delta.x != 0.0 || delta.y != 0.0) {
        o = nativeControlForegroundSource(refracted + delta * (2.0 / 3.0));
        y = nativeControlForegroundSource(refracted + delta * (1.0 / 3.0));
        g = nativeControlForegroundSource(refracted);
        c = nativeControlForegroundSource(refracted - delta * (1.0 / 3.0));
        b = nativeControlForegroundSource(refracted - delta * (2.0 / 3.0));
        v = nativeControlForegroundSource(refracted - delta);
    }
    float3 red = nativeControlForegroundStraight(r);
    float3 orange = nativeControlForegroundStraight(o);
    float3 yellow = nativeControlForegroundStraight(y);
    float3 green = nativeControlForegroundStraight(g);
    float3 cyan = nativeControlForegroundStraight(c);
    float3 blue = nativeControlForegroundStraight(b);
    float3 violet = nativeControlForegroundStraight(v);
    float3 color = float3((red.r + orange.r * (2.0 / 3.0) + yellow.r * (1.0 / 3.0)) * .5,
        (orange.g * (1.0 / 3.0) + yellow.g * (2.0 / 3.0) + green.g + cyan.g * (2.0 / 3.0) + blue.g * (1.0 / 3.0)) / 3.0,
        (cyan.b * (1.0 / 3.0) + blue.b * (2.0 / 3.0) + violet.b) * .5);
    float alpha = (r.a + o.a + y.a + g.a + c.a + b.a + v.a) / 7.0 * coverage;
    return float4(color * alpha * colorGain, alpha) * attenuation;
}
float4 nativeControlForeground(float2 p, float4 field, float4 matrix,
    float3 refraction, float2 refractionDirection, float3 aberration, float2 aberrationDirection,
    float4 edge, float colorGain) {
    return nativeControlForegroundForRaster(p, field, matrix, refraction, refractionDirection,
        aberration, aberrationDirection, edge, colorGain, 1.0);
}
"""
