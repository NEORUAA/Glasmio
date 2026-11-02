package io.github.neoruaa.glasmio.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.neoruaa.glasmio.glass.render.optics.NativeGlassSdfFunctions

/**
 * A compound navigation surface whose material, refraction and shadow share its primitives.
 * Bounds, smoothness and [minimumDimension] use local physical pixels. The latter describes
 * the main control, excluding detached accessory lobes from its optical size calibration.
 * [outline] supplies the matching foreground clip; primitive order defines CASDF folding.
 */
class NavigationGlassUnionShape(
    private val outline: Shape,
    capsules: List<LiquidContactCapsule>,
    private val smoothness: Float,
    val minimumDimension: Float,
) : Shape {
    private class Element(val capsule: LiquidContactCapsule) {
        val bounds = capsule.bounds
        private val radius = capsule.size.minDimension * .5f
        val radii = Offset(radius * bounds.width / capsule.size.width,
            radius * bounds.height / capsule.size.height)
        val field = RoundedMenuField(bounds, radii, normalMix = 0f)
    }

    init {
        require(capsules.size <= 16 && smoothness.isFinite() && smoothness >= 0f)
        require(minimumDimension.isFinite() && minimumDimension > 0f)
        require(capsules.all { c ->
            c.size.width > 0f && c.size.height > 0f && c.bounds.width > 0f && c.bounds.height > 0f &&
                c.size.width.isFinite() && c.size.height.isFinite() && c.bounds.left.isFinite() &&
                c.bounds.top.isFinite() && c.bounds.right.isFinite() && c.bounds.bottom.isFinite()
        })
    }

    private val elements = capsules.map(::Element)
    private val packedBounds = FloatArray(16 * 4)
    private val packedRadii = FloatArray(16 * 2)
    private val opticalField = NativeGlassFilterField(NavigationGlassUnionFieldShader) { shader ->
        shader.setFloatUniform("compoundElements", packedBounds)
        shader.setFloatUniform("compoundRadii", packedRadii)
        shader.setFloatUniform("compoundCount", elements.size.toFloat())
        shader.setFloatUniform("compoundSmoothness", smoothness)
    }

    init {
        elements.forEachIndexed { i, element ->
            packedBounds[i * 4] = element.bounds.center.x
            packedBounds[i * 4 + 1] = element.bounds.center.y
            packedBounds[i * 4 + 2] = element.bounds.width * .5f
            packedBounds[i * 4 + 3] = element.bounds.height * .5f
            packedRadii[i * 2] = element.radii.x
            packedRadii[i * 2 + 1] = element.radii.y
        }
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        outline.createOutline(size, layoutDirection, density)

    internal fun nativeField(): NativeGlassFilterField = opticalField

    /** CPU counterpart of the shader, including its unnormalized mixed normal. */
    internal fun field(x: Float, y: Float, output: FloatArray = FloatArray(3)): FloatArray {
        if (elements.isEmpty()) {
            output[0] = 100_000f; output[1] = 0f; output[2] = -1f
            return output
        }
        var distance = elements[0].field.distance(x, y)
        val firstNormal = elements[0].field.gradient(x, y)
        var nx = firstNormal.x
        var ny = firstNormal.y
        for (i in 1 until elements.size) {
            val element = elements[i]
            val otherDistance = element.field.distance(x, y)
            val normal = element.field.gradient(x, y)
            val radius = smoothness * (.5f - .5f * (nx * normal.x + ny * normal.y)).coerceIn(0f, 1f)
            val h = if (radius <= .000001f) (if (distance < otherDistance) 1f else 0f)
                else (.5f + .5f * (otherDistance - distance) / radius).coerceIn(0f, 1f)
            distance = otherDistance + (distance - otherDistance) * h - radius * h * (1f - h)
            nx = normal.x + (nx - normal.x) * h
            ny = normal.y + (ny - normal.y) * h
        }
        output[0] = distance; output[1] = nx; output[2] = ny
        return output
    }
}

private const val NavigationGlassUnionFieldShader = NativeGlassSdfFunctions + """
uniform float4 compoundElements[16];
uniform float2 compoundRadii[16];
uniform float compoundCount;
uniform float compoundSmoothness;
float3 navigationGlassShapeField(float2 point) {
    if (compoundCount < 0.5) return float3(100000.0, 0.0, -1.0);
    float4 first = compoundElements[0];
    float3 a = nativeGlassRoundedField(point - first.xy, first.zw, compoundRadii[0], 0.0);
    for (int i = 1; i < 16; ++i) {
        if (float(i) < compoundCount) {
            float4 element = compoundElements[i];
            float3 b = nativeGlassRoundedField(point - element.xy, element.zw, compoundRadii[i], 0.0);
            float radius = compoundSmoothness * clamp(0.5 - 0.5 * dot(a.yz, b.yz), 0.0, 1.0);
            float h = radius <= 0.000001 ? (a.x < b.x ? 1.0 : 0.0)
                : clamp(0.5 + 0.5 * (b.x - a.x) / radius, 0.0, 1.0);
            a = float3(mix(b.x, a.x, h) - radius * h * (1.0 - h), mix(b.yz, a.yz, h));
        }
    }
    return a;
}
float3 navigationGlassField(float2 point) { return navigationGlassShapeField(point); }
float3 navigationGlassOpticalFieldFromShape(float2 point, float3 rawShapeField) { return rawShapeField; }
"""
