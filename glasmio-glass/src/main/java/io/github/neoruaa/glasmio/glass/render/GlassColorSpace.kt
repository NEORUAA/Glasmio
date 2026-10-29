package io.github.neoruaa.glasmio.glass.render

/**
 * Color-only adapters for native matrices defined in extended, gamma-encoded sRGB.
 *
 * Inputs and outputs are straight RGB. Callers retain alpha and premultiply only after the
 * final conversion. Geometric fields, coverage masks and alpha must not use these adapters.
 * Android supplies the working-space transforms for both SDR and HDR destinations.
 */
const val GlassColorSpaceShaderFunctions = """
float glassExtendedSrgbEncodeChannel(float value) {
    float magnitude = abs(value);
    float encoded = magnitude <= 0.0031308
        ? 12.92 * magnitude
        : 1.055 * pow(magnitude, 1.0 / 2.4) - 0.055;
    return sign(value) * encoded;
}
float glassExtendedSrgbDecodeChannel(float value) {
    float magnitude = abs(value);
    float decoded = magnitude <= 0.04045
        ? magnitude / 12.92
        : pow((magnitude + 0.055) / 1.055, 2.4);
    return sign(value) * decoded;
}
float3 glassLinearToNativeGamma(float3 value) {
    return float3(glassExtendedSrgbEncodeChannel(value.r),
        glassExtendedSrgbEncodeChannel(value.g), glassExtendedSrgbEncodeChannel(value.b));
}
float3 glassNativeGammaToLinear(float3 value) {
    return float3(glassExtendedSrgbDecodeChannel(value.r),
        glassExtendedSrgbDecodeChannel(value.g), glassExtendedSrgbDecodeChannel(value.b));
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
