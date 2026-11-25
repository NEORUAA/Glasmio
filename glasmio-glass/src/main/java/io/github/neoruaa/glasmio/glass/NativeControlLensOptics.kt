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
private const val NativeControlStraightColor = """float3 nativeControlForegroundStraight(float4 source) {
    return source.rgb / max(source.a, .000001);
}
"""

private const val NativeControlSpectralAccumulate = """        float3 s = nativeControlForegroundStraight(sampled);
        float rw = max(1.0 - float(band) * (1.0 / 3.0), 0.0) * 0.5;
        float gw = max(1.0 - abs(float(band) - 3.0) * (1.0 / 3.0), 0.0) * (1.0 / 3.0);
        float bw = max(1.0 - float(6 - band) * (1.0 / 3.0), 0.0) * 0.5;
        color += float3(s.r * rw, s.g * gw, s.b * bw);
        alpha += sampled.a;
    }
    alpha = alpha / 7.0 * coverage;
    return float4(color * alpha * colorGain, alpha) * attenuation;
}
"""

internal const val NativeControlTabForegroundFunctions = NativeControlStraightColor + """
float4 nativeControlTabForeground(float2 p, float3 field, float4 center, float height,
    float amount, float pixelScale) {
    float edge = -height / 5.0;
    float opacity = mix(1.0, 0.0, clamp((field.x - edge) / (0.0 - edge), 0.0, 1.0));
    float attenuation = 1.0 - opacity;
    if (attenuation <= 0.0) return float4(0.0);
    float aa = max((abs(field.y) + abs(field.z)) * pixelScale, .0001);
    float coverage = clamp(.5 - field.x / aa, 0.0, 1.0);
    // The spectral foreground has zero premultiplied color outside its coverage.
    if (coverage == 0.0) return float4(0.0);
    // Refraction is zero. A fragment with nonzero attenuation is shallower than
    // height/5, so its zero-height aberration profile is still at full amplitude.
    float2 delta = float2(dot(float2(-.2588190451025207, .9659258262890683), field.yz),
        dot(float2(.9659258262890683, .2588190451025207), field.yz)) * amount;
    bool dispersed = any(notEqual(delta, float2(0.0)));
    float colorGain = 1.0;
    float3 color = float3(0.0);
    float alpha = 0.0;
    for (int band = 0; band < 7; ++band) {
        float4 sampled = center;
        if (dispersed && band != 3) {
            float offset = (3.0 - float(band)) * (1.0 / 3.0);
            sampled = nativeControlForegroundSource(p + delta * offset);
        }
""" + NativeControlSpectralAccumulate

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
""" + NativeControlStraightColor + """float4 nativeControlForegroundForRaster(float2 p, float4 field, float4 matrix,
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
    // Sample each wavelength through the same optical path. A zero spectral shift
    // still feeds the seven original weights and alpha additions from one read.
    bool dispersed = any(notEqual(delta, float2(0.0)));
    float3 color = float3(0.0);
    float alpha = 0.0;
    float4 sampled = float4(0.0);
    for (int band = 0; band < 7; ++band) {
        if (band == 0 || dispersed) {
            float offset = (3.0 - float(band)) * (1.0 / 3.0);
            sampled = nativeControlForegroundSource(refracted + delta * offset);
        }
""" + NativeControlSpectralAccumulate + """float4 nativeControlForeground(float2 p, float4 field, float4 matrix,
    float3 refraction, float2 refractionDirection, float3 aberration, float2 aberrationDirection,
    float4 edge, float colorGain) {
    return nativeControlForegroundForRaster(p, field, matrix, refraction, refractionDirection,
        aberration, aberrationDirection, edge, colorGain, 1.0);
}
"""


/** Active scalar-radius Tab fields use a private SDR data plane, never a color sampler. */
internal const val NativeControlLensFieldUniforms = """
uniform float2 size;
uniform float4 cornerRadii;
uniform float2 lensFieldOrigin;
uniform float2 lensFieldRectOrigin;
uniform float lensFieldRange;
"""

// The atlas sampler fragment declares content before these functions are appended.
internal const val NativeControlLensFieldSamplingFunctions = """
float3 glassField(float2 p, float normalMix) {
    float4 encoded = float4(content.eval(lensFieldRectOrigin + (p - lensFieldOrigin)));
    float distance = (encoded.r * 65280.0 + encoded.g * 255.0) / 65535.0
        * (2.0 * lensFieldRange) - lensFieldRange;
    float2 positive = float2(encoded.b, 1.0 - encoded.b);
    positive /= max(length(positive), .000001);
    float2 signs = float2(p.x < size.x * .5 ? -1.0 : 1.0, p.y < size.y * .5 ? -1.0 : 1.0);
    return float3(distance, positive * signs);
}
float glassDistance(float2 p) { return glassField(p, 0.0).x; }
float2 glassNormal(float2 p) { return glassField(p, 0.0).yz; }
float2 glassDepthNormal(float2 p, float normalMix) { return glassField(p, normalMix).yz; }
"""

internal const val NativeControlLensFieldProducerMain = """
uniform float2 fieldOrigin;
uniform float fieldRange;
half4 main(float2 coordinate) {
    float3 field = glassField(coordinate + fieldOrigin, 0.0);
    float code = floor((field.x / fieldRange * .5 + .5) * 65535.0 + .5);
    float high = floor(code / 256.0);
    float low = code - high * 256.0;
    float ratio = abs(field.y) / max(abs(field.y) + abs(field.z), .000001);
    return half4(high / 255.0, low / 255.0, ratio, 1.0);
}
"""
