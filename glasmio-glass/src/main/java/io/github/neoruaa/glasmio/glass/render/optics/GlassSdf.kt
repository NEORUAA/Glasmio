package io.github.neoruaa.glasmio.glass.render.optics

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.shapes.RoundedRectangularShape

/** Geometry stays in logical surface pixels; shape clipping retains the original continuous path. */
internal fun glassCornerRadii(shape: Shape, size: Size, direction: LayoutDirection, density: Density): FloatArray {
    val limit = size.minDimension / 2f
    fun bounded(a: Float, b: Float, c: Float, d: Float) =
        floatArrayOf(a.coerceIn(0f, limit), b.coerceIn(0f, limit), c.coerceIn(0f, limit), d.coerceIn(0f, limit))
    if (shape is RoundedRectangularShape) {
        val corners = shape.corners(size, direction, density)
        return bounded(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft)
    }
    if (shape is CornerBasedShape) {
        val ltr = direction == LayoutDirection.Ltr
        return bounded(
            (if (ltr) shape.topStart else shape.topEnd).toPx(size, density),
            (if (ltr) shape.topEnd else shape.topStart).toPx(size, density),
            (if (ltr) shape.bottomEnd else shape.bottomStart).toPx(size, density),
            (if (ltr) shape.bottomStart else shape.bottomEnd).toPx(size, density),
        )
    }
    return when (val outline = shape.createOutline(size, direction, density)) {
        is Outline.Rectangle -> FloatArray(4)
        is Outline.Rounded -> bounded(outline.roundRect.topLeftCornerRadius.x,
            outline.roundRect.topRightCornerRadius.x, outline.roundRect.bottomRightCornerRadius.x,
            outline.roundRect.bottomLeftCornerRadius.x)
        is Outline.Generic -> FloatArray(4) { limit }
    }
}

/** iOS 27 AIR supercircle_sdf and its separate boundary/oval normal blend. */
internal const val NativeGlassSdfFunctions = """
float2 nativeGlassSafeNormal(float2 v) {
    return v / max(length(v), 0.000001);
}
float3 nativeGlassRoundedField(float2 point, float2 halfExtent, float2 radii, float normalMix) {
    if (min(abs(radii.x), abs(radii.y)) <= 0.000001) {
        float2 edge = abs(point) - halfExtent;
        float2 signs = float2(point.x < 0.0 ? -1.0 : 1.0, point.y < 0.0 ? -1.0 : 1.0);
        float2 normal = signs * (edge.x > edge.y ? float2(1.0, 0.0) : float2(0.0, 1.0));
        float2 oval = nativeGlassSafeNormal(float2(point.x, point.y * halfExtent.x / max(halfExtent.y, 0.000001)));
        return float3(max(edge.x, edge.y), nativeGlassSafeNormal(mix(normal, oval, normalMix)));
    }
    float2 r = max(abs(radii), float2(0.001));
    float radius = min(r.x, r.y);
    float2 inverseAxis = radius / r;
    float2 local = point * inverseAxis;
    float2 extent = halfExtent * inverseAxis;
    float expandedRadius = radius * 1.5286649465560913;
    float2 curvature = clamp(2.8915570351735034 -
        1.4457785175867517 * (extent * 2.0) / expandedRadius, 0.0, 1.0);
    float baseRadius = mix(expandedRadius, radius, max(curvature.x, curvature.y));
    float2 edge = abs(local) - extent;
    float2 circle = edge + baseRadius;
    float interior = min(max(circle.x, circle.y), 0.0);
    float2 z = max((edge + expandedRadius) / expandedRadius, 0.0);
    float largest = max(z.x, z.y);
    float t = largest > 0.0 ? clamp(min(z.x, z.y) / largest, 0.0, 1.0) : 0.0;
    float d = length(z);
    float polynomial = ((((-0.926054 * t + 3.15601) * t - 3.64122) * t +
        1.26803) * t + 0.268531) * t * t;
    float raw = d + 1.0 - 1.0 / (1.0 - polynomial * clamp(d, 0.0, 1.0));
    float alternate = 0.3458344340324402 +
        0.6541655659675598 * length(max(1.5286649465560913 * z - 0.528665, 0.0));
    float weight = clamp((z.y > z.x ? 1.0 : -1.0) * (t - 1.0) + 0.5, 0.0, 1.0);
    float corrected = mix(mix(raw, alternate, curvature.x), mix(raw, alternate, curvature.y), weight);
    float distance = interior + expandedRadius * float(half(corrected - 1.0));
    float2 positiveNormal = nativeGlassSafeNormal(max(circle, 0.0));
    if (positiveNormal.x + positiveNormal.y <= 0.0)
        positiveNormal = circle.x > circle.y ? float2(1.0, 0.0) : float2(0.0, 1.0);
    float2 normal = positiveNormal * float2(local.x < 0.0 ? -1.0 : 1.0, local.y < 0.0 ? -1.0 : 1.0);
    float2 oval = nativeGlassSafeNormal(float2(local.x, local.y * extent.x / max(extent.y, 0.000001)));
    normal = nativeGlassSafeNormal(mix(normal, oval, normalMix));
    // The native field transforms this normalized gradient without a second normalization.
    return float3(distance, normal * inverseAxis);
}
"""

/** Negative inside, matching the native SDF gradient's [-1, 0, 10] distance convention. */
internal const val GlassSdfShader = NativeGlassSdfFunctions + """
uniform float2 size;
uniform float4 cornerRadii;
float radiusAt(float2 p) {
    return p.y < size.y * 0.5
        ? (p.x < size.x * 0.5 ? cornerRadii.x : cornerRadii.y)
        : (p.x < size.x * 0.5 ? cornerRadii.w : cornerRadii.z);
}
float3 glassField(float2 p, float normalMix) {
    return nativeGlassRoundedField(p - size * 0.5, size * 0.5, float2(radiusAt(p)), normalMix);
}
float glassDistance(float2 p) { return glassField(p, 0.0).x; }
float2 glassNormal(float2 p) { return glassField(p, 0.0).yz; }
float2 glassDepthNormal(float2 p, float normalMix) { return glassField(p, normalMix).yz; }
"""
