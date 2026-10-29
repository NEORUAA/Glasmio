package io.github.neoruaa.glasmio.glass.render.effects

import io.github.neoruaa.glasmio.glass.render.BackdropEffectScope

/** Rec.709 chroma expansion used by the native material face, independent of blur/refraction. */
fun BackdropEffectScope.vibrancy(saturation: Float = 1.2f) {
    runtimeShaderEffect("Glasmio.YCC.Vibrancy", VibrancyShader, "content") {
        setFloatUniform("saturation", saturation.coerceAtLeast(0f))
    }
}

private const val VibrancyShader = """
uniform shader content;
uniform float saturation;
half4 main(float2 p) {
    half4 c = content.eval(p);
    if (c.a <= 0.0001) return c;
    float3 rgb = float3(c.rgb / c.a);
    float y = dot(rgb, float3(0.2126, 0.7152, 0.0722));
    return half4(half3(clamp(y + saturation * (rgb - y), 0.0, 1.0)) * c.a, c.a);
}
"""
