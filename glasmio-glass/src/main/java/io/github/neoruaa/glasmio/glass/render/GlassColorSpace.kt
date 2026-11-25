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
    half3 v = half3(value);
    half3 magnitude = abs(v);
    half3 encoded = mix(
        half(1.055) * pow(max(magnitude, half3(0.0031308)), half3(0.41666666)) - half(0.055),
        half(12.92) * magnitude,
        step(magnitude, half3(0.0031308)));
    return float3(sign(v) * encoded);
}
float3 glassNativeGammaToLinear(float3 value) {
    half3 v = half3(value);
    half3 magnitude = abs(v);
    half3 decoded = mix(
        pow((max(magnitude, half3(0.04045)) + half(0.055)) * half(0.9478673), half3(2.4)),
        magnitude * half(0.07739938),
        step(magnitude, half3(0.04045)));
    return float3(sign(v) * decoded);
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
