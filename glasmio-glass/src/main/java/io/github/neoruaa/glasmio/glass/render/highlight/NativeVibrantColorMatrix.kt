package io.github.neoruaa.glasmio.glass.render.highlight

import android.graphics.RuntimeXfermode
import androidx.annotation.RequiresApi
import io.github.neoruaa.glasmio.glass.render.GlassColorSpaceShaderFunctions

/** Native CAColorMatrix rows, evaluated on sampled backdrop color rather than mask RGB. */
internal class NativeVibrantColorMatrix(
    values: FloatArray,
    val clampLimit: Float = 1.2f,
    val preserveHue: Boolean = false,
) {
    init { require(values.size == 20) }
    private val rows = Array(4) { row -> FloatArray(4) { column -> values[row * 5 + column] } }
    private val bias = FloatArray(4) { values[it * 5 + 4] }
    private val clamp = floatArrayOf(clampLimit, if (preserveHue) 1f else 0f)

    fun configure(set: (String, FloatArray) -> Unit) {
        rows.forEachIndexed { index, row -> set("nativeVibrantRow$index", row) }
        set("nativeVibrantBias", bias)
        set("nativeVibrantClamp", clamp)
    }

    /** Portable morph interpolation between measured endpoints, not a recovered UIKit chooser. */
    class HighlightInterpolation {
        private val rows = Array(4) { FloatArray(4) }
        private val bias = FloatArray(4)
        private val clamp = FloatArray(2)
        private val names = arrayOf("nativeVibrantRow0", "nativeVibrantRow1", "nativeVibrantRow2", "nativeVibrantRow3")
        private var fraction = Float.NaN

        fun configure(clearFraction: Float, set: (String, FloatArray) -> Unit) {
            val t = clearFraction.coerceIn(0f, 1f)
            if (t <= 0f) return RegularHighlight.configure(set)
            if (t >= 1f) return ClearHighlight.configure(set)
            if (fraction != t) {
                val from = RegularHighlight
                val to = ClearHighlight
                for (row in 0..3) {
                    for (column in 0..3) rows[row][column] = from.rows[row][column] + (to.rows[row][column] - from.rows[row][column]) * t
                    bias[row] = from.bias[row] + (to.bias[row] - from.bias[row]) * t
                }
                for (index in 0..1) clamp[index] = from.clamp[index] + (to.clamp[index] - from.clamp[index]) * t
                fraction = t
            }
            for (row in 0..3) set(names[row], rows[row])
            set("nativeVibrantBias", bias)
            set("nativeVibrantClamp", clamp)
        }
    }

    companion object {
        val RegularHighlight = NativeVibrantColorMatrix(floatArrayOf(
            1.1201999187f, -.1894000024f, -.0189999994f, 0f, .1471000016f,
            -.0562999994f, .9870999455f, -.0190999992f, 0f, .1471000016f,
            -.0562999994f, -.1893000007f, 1.1574000120f, 0f, .1471000016f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val ClearHighlight = NativeVibrantColorMatrix(floatArrayOf(
            1.3191999197f, -.0477999970f, -.0046999999f, 0f, .1999999881f,
            -.0142000001f, 1.2855999470f, -.0047999998f, 0f, .1999999881f,
            -.0142000001f, -.0475999974f, 1.3284999132f, 0f, .1999999881f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }
}

/**
 * Literal transfer from UberShader::vibrant_color_matrix[_sover]. Backdrop-aware composition
 * uses a separate sampled backdrop and destination; the ordinary path supplies the same value.
 * Pure native-gamma parameters allow several matrices in one material/press program.
 */
internal const val NativeVibrantColorMatrixTransferFunctions = """
float4 nativeVibrantColorMatrixApply(float4 mask, float4 sampledBackdrop,
    float4 row0, float4 row1, float4 row2, float4 row3, float4 bias, float2 clampParameters) {
    float4 inputColor = float4(sampledBackdrop.rgb / max(sampledBackdrop.a, 0.000001), sampledBackdrop.a);
    float4 transformed = float4(dot(row0, inputColor), dot(row1, inputColor),
        dot(row2, inputColor), dot(row3, inputColor)) + bias;
    float matrixAlpha = clamp(transformed.a, 0.0, 1.0);
    float4 result = float4(transformed.rgb * matrixAlpha, matrixAlpha) * mask.a;
    if (clampParameters.x > 0.0) {
        float3 straight = result.rgb / max(result.a, 0.000001);
        if (clampParameters.y > 0.0) {
            float maximum = max(max(straight.r, straight.g), straight.b);
            if (maximum > clampParameters.x) straight *= clampParameters.x / maximum;
        } else {
            straight = clamp(straight, float3(-0.75), float3(clampParameters.x));
        }
        result.rgb = straight * result.a;
    }
    return result;
}
float4 nativeVibrantColorMatrixApplyOver(float4 mask, float4 sampledBackdrop, float4 destination,
    float4 row0, float4 row1, float4 row2, float4 row3, float4 bias, float2 clampParameters) {
    float4 source = nativeVibrantColorMatrixApply(mask, sampledBackdrop,
        row0, row1, row2, row3, bias, clampParameters);
    return source + destination * (1.0 - source.a);
}
"""

internal const val NativeVibrantColorMatrixFunctions = NativeVibrantColorMatrixTransferFunctions + """
uniform float4 nativeVibrantRow0;
uniform float4 nativeVibrantRow1;
uniform float4 nativeVibrantRow2;
uniform float4 nativeVibrantRow3;
uniform float4 nativeVibrantBias;
uniform float2 nativeVibrantClamp;
float4 nativeVibrantColorMatrix(float4 mask, float4 sampledBackdrop) {
    return nativeVibrantColorMatrixApply(mask, sampledBackdrop,
        nativeVibrantRow0, nativeVibrantRow1, nativeVibrantRow2, nativeVibrantRow3,
        nativeVibrantBias, nativeVibrantClamp);
}
float4 nativeVibrantColorMatrixOver(float4 mask, float4 sampledBackdrop, float4 destination) {
    return nativeVibrantColorMatrixApplyOver(mask, sampledBackdrop, destination,
        nativeVibrantRow0, nativeVibrantRow1, nativeVibrantRow2, nativeVibrantRow3,
        nativeVibrantBias, nativeVibrantClamp);
}
"""

@RequiresApi(36)
internal class NativeVibrantColorMatrixBlender {
    val shader = RuntimeXfermode(GlassColorSpaceShaderFunctions + NativeVibrantColorMatrixFunctions + """
half4 main(half4 source, half4 destination) {
    if (source.a <= 0.0) return destination;
    float4 target = float4(destination);
    float3 nativeRgb = glassWorkingToNativeGamma(target.rgb / max(target.a, 0.000001));
    float4 sampled = float4(nativeRgb * target.a, target.a);
    float4 vibrant = nativeVibrantColorMatrixOver(float4(source), sampled, sampled);
    float3 working = glassNativeGammaToWorking(vibrant.rgb / max(vibrant.a, 0.000001));
    return half4(working * vibrant.a, vibrant.a);
}
""")
    private var configured: NativeVibrantColorMatrix? = null
    private var configuredFraction = Float.NaN
    private val interpolation = NativeVibrantColorMatrix.HighlightInterpolation()

    fun prepare(matrix: NativeVibrantColorMatrix) {
        if (configured !== matrix || !configuredFraction.isNaN()) {
            matrix.configure(shader::setFloatUniform)
            configured = matrix
            configuredFraction = Float.NaN
        }
    }

    fun prepareHighlight(clearFraction: Float) {
        val t = clearFraction.coerceIn(0f, 1f)
        if (configured != null || configuredFraction != t) {
            interpolation.configure(t, shader::setFloatUniform)
            configured = null
            configuredFraction = t
        }
    }
}
