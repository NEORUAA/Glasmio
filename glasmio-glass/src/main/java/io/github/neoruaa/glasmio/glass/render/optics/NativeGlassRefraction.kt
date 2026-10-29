package io.github.neoruaa.glasmio.glass.render.optics

/**
 * Scalar transfer recovered from iOS 27 QuartzCore's glass_background_{sdf_,}r AIR.
 * Coordinates, inverse heights and signed amplitudes use the same effect-texture units.
 */
internal const val NativeGlassRefractionFunctions = """
float nativeGlassDisplacement(float signedDistance, float inverseHeight, float signedAmount) {
    float u = clamp(-signedDistance * inverseHeight, 0.0, 1.0);
    return signedAmount * (1.0 - clamp(sqrt(u * (2.0 - u)), 0.0, 1.0));
}
float2 nativeGlassDisplacementNormal(float2 gradient, float4 displacementMatrix) {
    return float2(dot(gradient, displacementMatrix.xy), dot(gradient, displacementMatrix.zw));
}
float nativeGlassRefractionWeight(float signedDistance, float threshold0, float threshold1, float opacity) {
    float width = threshold1 - threshold0;
    float progress = width != 0.0 ? clamp((signedDistance - threshold0) / width, 0.0, 1.0)
        : step(threshold1, signedDistance);
    return opacity * progress;
}
float2 nativeGlassAberrationNormal(float2 gradient, float4 matrix, float2 direction) {
    return float2(
        dot(float2(dot(matrix.zw, direction), direction.x * matrix.w - direction.y * matrix.z), gradient),
        dot(float2(dot(matrix.xy, direction), direction.x * matrix.y - direction.y * matrix.x), gradient)
    );
}
"""

/** The caller supplies nativeGlassSample(coordinate, radius) for its source-filter graph. */
internal const val NativeGlassSpectralFunctions = """
float3 nativeGlassStraightColor(half4 color) {
    float3 rgb = float3(color.rgb);
    rgb = float3(abs(rgb.r) < 0.000001 ? 0.0 : rgb.r,
        abs(rgb.g) < 0.000001 ? 0.0 : rgb.g, abs(rgb.b) < 0.000001 ? 0.0 : rgb.b);
    return rgb / max(float(color.a), 0.000001);
}
half4 nativeGlassAberrate(float2 coordinate, float2 delta, float radius) {
    half4 red = nativeGlassSample(coordinate + delta, radius);
    half4 orange = nativeGlassSample(coordinate + delta * (2.0 / 3.0), radius);
    half4 yellow = nativeGlassSample(coordinate + delta * (1.0 / 3.0), radius);
    half4 green = nativeGlassSample(coordinate, radius);
    half4 cyan = nativeGlassSample(coordinate - delta * (1.0 / 3.0), radius);
    half4 blue = nativeGlassSample(coordinate - delta * (2.0 / 3.0), radius);
    half4 purple = nativeGlassSample(coordinate - delta, radius);
    float3 r = nativeGlassStraightColor(red);
    float3 o = nativeGlassStraightColor(orange);
    float3 y = nativeGlassStraightColor(yellow);
    float3 g = nativeGlassStraightColor(green);
    float3 c = nativeGlassStraightColor(cyan);
    float3 b = nativeGlassStraightColor(blue);
    float3 p = nativeGlassStraightColor(purple);
    float3 result = float3(
        (r.r + o.r * (2.0 / 3.0) + y.r * (1.0 / 3.0)) * 0.5,
        (o.g * (1.0 / 3.0) + y.g * (2.0 / 3.0) + g.g + c.g * (2.0 / 3.0) + b.g * (1.0 / 3.0)) / 3.0,
        (c.b * (1.0 / 3.0) + b.b * (2.0 / 3.0) + p.b) * 0.5);
    float alpha = float(red.a + orange.a + yellow.a + green.a + cyan.a + blue.a + purple.a) / 7.0;
    // Literal premultiplied-mode shuffle in aberrate_texture's AIR. Opaque sources keep alpha 1.
    return half4(result.r * alpha, result.g * alpha, result.b, alpha * alpha);
}
"""

/** Explicit custom control optics retained independently of the recovered material defaults. */
internal const val CustomGlassChromaticFunctions = """
float2 customGlassChromaticVector(float2 displacement, float2 point, float2 halfExtent) {
    float crossTerm = clamp(point.x * point.y / max(halfExtent.x * halfExtent.y, 0.0001), -1.0, 1.0);
    return displacement * crossTerm;
}
"""
