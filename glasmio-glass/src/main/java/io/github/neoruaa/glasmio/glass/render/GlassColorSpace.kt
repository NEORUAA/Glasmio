package io.github.neoruaa.glasmio.glass.render

/**
 * Color-only adapters for native matrices defined in extended, gamma-encoded sRGB.
 *
 * Inputs and outputs are straight RGB. Callers retain alpha and premultiply only after the
 * final conversion. Geometric fields, coverage masks and alpha must not use these adapters.
 * Android supplies the working-space transforms for both SDR and HDR destinations.
 */
const val GlassColorSpaceShaderFunctions = """
float3 glassLinearToNativeGamma(float3 value) {
    float3 magnitude = abs(value);
    float3 encoded = mix(
        1.055 * pow(max(magnitude, float3(0.0031308)), float3(1.0 / 2.4)) - 0.055,
        12.92 * magnitude,
        step(magnitude, float3(0.0031308)));
    return sign(value) * encoded;
}
float3 glassNativeGammaToLinear(float3 value) {
    float3 magnitude = abs(value);
    float3 decoded = mix(
        pow((max(magnitude, float3(0.04045)) + 0.055) / 1.055, float3(2.4)),
        magnitude / 12.92,
        step(magnitude, float3(0.04045)));
    return sign(value) * decoded;
}
float3 glassWorkingToNativeGamma(float3 straightWorkingRgb) {
    return glassLinearToNativeGamma(toLinearSrgb(straightWorkingRgb));
}
float3 glassNativeGammaToWorking(float3 straightNativeRgb) {
    return fromLinearSrgb(glassNativeGammaToLinear(straightNativeRgb));
}
float4 glassWorkingToNativeGammaPremul(float4 value) {
    return float4(glassWorkingToNativeGamma(value.rgb / max(value.a, .000001)) * value.a, value.a);
}
float4 glassNativeGammaToWorkingPremul(float4 value) {
    return float4(glassNativeGammaToWorking(value.rgb / max(value.a, .000001)) * value.a, value.a);
}
"""
