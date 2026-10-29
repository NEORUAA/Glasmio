package io.github.neoruaa.glasmio.glass.render.highlight

/** AIR glass_background_all_lpf, key/fill and Gaussian ring transfers. */
internal const val NativeGlassEdgeFunctions = """
float nativeGaussianTail(float value) {
    float x = clamp(value, -2.0, 2.0);
    float q = x * x;
    return (((0.00295448 * q - 0.03445430) * q + 0.168213) * q - 0.560546) * x + 0.5;
}
float nativeHighlightStrip(float depth, float height, float curvature, float aa) {
    float u = clamp(depth / max(height, 0.000001), 0.0, 1.0);
    float profile = mix(u < 1.0 ? 1.0 : 0.0, 1.0 - u, curvature);
    return profile * clamp(depth / aa + 0.5, 0.0, 1.0)
        * clamp((height - depth) / aa + 0.5, 0.0, 1.0);
}
float nativeHighlightAngular(float alignment, float threshold) {
    return clamp((alignment - threshold) / max(1.0 - threshold, 0.000001), 0.0, 1.0);
}
float nativeHighlightBias(float coverage, float bias) {
    return coverage / max(1.0 + bias * (1.0 - coverage), 0.000001);
}
float nativeGlassShapeCoverage(float3 field, float shapeAlpha) {
    float aa = max(abs(field.y) + abs(field.z), 0.0001);
    return clamp(.5 - field.x / aa, 0.0, 1.0) * shapeAlpha;
}
float nativeGlassStrokeKeyFill(float3 field, float density, float shapeAlpha, float threshold) {
    float aa = max(abs(field.y) + abs(field.z), .0001);
    float coverage = nativeGlassShapeCoverage(field, shapeAlpha);
    float height = .5333333333333333 * density;
    // Captured height + offset == 0 and offset < 0 selects stroke_mode in the CPU builder.
    float strip = (1.0 - coverage) * clamp((height - field.x) / aa + .5, 0.0, 1.0) * shapeAlpha;
    float key = strip * nativeHighlightAngular(field.y, threshold);
    float fill = strip * nativeHighlightAngular(-field.y, threshold);
    return nativeHighlightBias(key, .5) + nativeHighlightBias(fill, .5);
}
float nativeGlassRingCoverage(float shiftedDistance, float coverage, float density, float clear) {
    float a = shiftedDistance / (5.0 * density) * .7071067811865476;
    float b = (shiftedDistance + 4.0 * density) / (5.0 * density) * .7071067811865476;
    return clamp(nativeGaussianTail(a) - nativeGaussianTail(b), 0.0, 1.0)
        * .06 * (1.0 - clear) * coverage;
}
float4 nativeGlassBackgroundCompletion(float4 coveredFace, float3 original, float3 originalFace,
    float keyFill, float faceOpacity, float colorBias) {
    if (keyFill <= .000001) return coveredFace;
    float alpha = coveredFace.a;
    float3 color = coveredFace.rgb;
    if (alpha < 1.0) {
        float3 corrected = colorBias < 0.0 ? min(originalFace, original) : max(originalFace, original);
        float3 completion = mix(original, corrected, faceOpacity * keyFill);
        color += (1.0 - alpha) * completion;
    }
    color *= 1.0 + colorBias * keyFill * (3.0 - 2.0 * color);
    float extendedAlpha = alpha + (1.0 - alpha) * keyFill;
    if (alpha < 1.0) color -= (1.0 - extendedAlpha) * original;
    // Backdrop-relative RGB may be signed. Coverage and completion must not clamp it.
    return float4(color, extendedAlpha);
}
"""

/** Uniforms are logical surface pixels; the caller provides the same SDF as its optical lens. */
internal const val NativeGlassBackgroundEdgeFunctions = NativeGlassEdgeFunctions + """
uniform float nativeEdgeDensity;
uniform float nativeEdgeDark;
uniform float nativeEdgeClear;
uniform float nativeEdgeOpacity;
uniform float2 nativeEdgeOrigin;
float2 nativeGlassBackgroundEdgeMasks(float2 position) {
    float2 p = position - nativeEdgeOrigin;
    float3 field = nativeEdgeField(p);
    float d = field.x;
    float2 n = field.yz;
    float aa = max(abs(n.x) + abs(n.y), 0.0001);
    float density = max(nativeEdgeDensity, 0.0001);
    float depth = -(d - 0.5333333333333333 * density);
    float profile = nativeHighlightStrip(depth, 0.5333333333333333 * density, 0.75, aa);
    float clear = clamp(nativeEdgeClear, 0.0, 1.0);
    float threshold = mix(cos(mix(1.8500490071139892, 1.3089969389957472, clamp(nativeEdgeDark, 0.0, 1.0))),
        cos(1.3089969389957472), clear);
    float key = profile * nativeHighlightAngular(n.x, threshold);
    float fill = profile * nativeHighlightAngular(-n.x, threshold);
    float highlight = nativeHighlightBias(key, 0.5) + nativeHighlightBias(fill, 0.5);
    float ring = 0.0;
    if (clear < 1.0) {
        float shifted = nativeEdgeField(p - float2(0.0, 8.0 * density)).x;
        float a = shifted / (5.0 * density) * 0.7071067811865476;
        float b = (shifted + 4.0 * density) / (5.0 * density) * 0.7071067811865476;
        ring = clamp(nativeGaussianTail(a) - nativeGaussianTail(b), 0.0, 1.0) * 0.06 * (1.0 - clear);
    }
    return float2(highlight, ring);
}
half4 nativeApplyGlassBackgroundEdges(half4 target, float2 masks, float opacity) {
    float highlight = masks.x;
    float ring = masks.y;
    half4 result = target * half(1.0 - ring);
    result.a += half(ring);
    result.rgb *= half3(1.0) + half(-0.3 * highlight) * (half3(3.0) - 2.0 * result.rgb);
    return mix(target, result, half(opacity));
}
half4 nativeGlassBackgroundEdges(half4 target, float2 position) {
    return nativeApplyGlassBackgroundEdges(target, nativeGlassBackgroundEdgeMasks(position), nativeEdgeOpacity);
}
"""
