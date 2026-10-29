package io.github.neoruaa.glasmio.glass

import androidx.collection.MutableLongFloatMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import io.github.neoruaa.glasmio.glass.render.optics.NativeGlassSdfFunctions
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Native CASDF capsule elements, with the host's smoothness and no ovalization or Gaussian radius. */
internal class NavigationNativeUnion private constructor(
    val first: LiquidContactCapsule,
    val second: LiquidContactCapsule?,
    val smoothness: Float,
    additional: List<LiquidContactCapsule> = emptyList(),
) {
    private class Element(val capsule: LiquidContactCapsule) {
        val bounds = capsule.bounds
        private val radius = capsule.size.minDimension * .5f
        val radii = Offset(radius * bounds.width / capsule.size.width,
            radius * bounds.height / capsule.size.height)
        val field = RoundedMenuField(bounds, radii, normalMix = 0f)
    }

    private val a = Element(first)
    private val b = second?.let(::Element)
    private val multipleElements = if (additional.isEmpty()) emptyList() else buildList {
        add(a)
        b?.let { add(it) }
        additional.forEach { add(Element(it)) }
    }
    val bounds = if (multipleElements.isNotEmpty()) Rect(
        multipleElements.minOf { it.bounds.left }, multipleElements.minOf { it.bounds.top },
        multipleElements.maxOf { it.bounds.right }, multipleElements.maxOf { it.bounds.bottom },
    ) else second?.bounds?.let { Rect(min(first.bounds.left, it.left), min(first.bounds.top, it.top),
        max(first.bounds.right, it.right), max(first.bounds.bottom, it.bottom)) } ?: first.bounds
    private val centerY = if (multipleElements.isNotEmpty())
        multipleElements.sumOf { it.bounds.center.y.toDouble() }.toFloat() / multipleElements.size
    else second?.let { (first.bounds.center.y + it.bounds.center.y) * .5f } ?: first.bounds.center.y
    private val sample = FloatArray(3)
    private data class AxisRange(val left: Float, val right: Float)
    private val axisRanges by lazy(LazyThreadSafetyMode.NONE) { createAxisRanges() }
    val isConnected: Boolean = if (multipleElements.isNotEmpty()) axisRanges.size == 1 else second?.let {
        distance((first.bounds.right + it.bounds.left) * .5f, centerY) <= 0f
    } ?: false
    val path: Path by lazy(LazyThreadSafetyMode.NONE) {
        // Evaluate the same roots and subdivision rules in C++; keep the reference for JVM and 3+ nodes.
        if (multipleElements.isEmpty()) {
            fun geometry(element: Element) = floatArrayOf(
                element.bounds.left, element.bounds.top, element.bounds.right, element.bounds.bottom,
                element.radii.x, element.radii.y,
            )
            val nativePath = Path()
            val built = LiquidMenuNativeField.buildNavigationPath(
                geometry(a) + geometry(b ?: a) + floatArrayOf(smoothness, if (b == null) 0f else 1f),
                nativePath.asAndroidPath(),
            )
            if (built) nativePath else createPath()
        } else createPath()
    }

    /** ShaderUtils::sdf_union, including its deliberately unnormalized mixed gradient. */
    fun field(x: Float, y: Float, output: FloatArray = FloatArray(3)): FloatArray {
        if (multipleElements.isNotEmpty()) return multipleField(x, y, output)
        val ad = a.field.distance(x, y)
        val an = a.field.gradient(x, y)
        val other = b
        if (other == null) {
            output[0] = ad; output[1] = an.x; output[2] = an.y
            return output
        }
        val bd = other.field.distance(x, y)
        val bn = other.field.gradient(x, y)
        val radius = smoothness * (.5f - .5f * (an.x * bn.x + an.y * bn.y)).coerceIn(0f, 1f)
        // The finite nearest-field limit defines the native fast-math k==0 case.
        val h = if (radius <= .000001f) { if (ad < bd) 1f else 0f }
            else (.5f + .5f * (bd - ad) / radius).coerceIn(0f, 1f)
        output[0] = bd + (ad - bd) * h - radius * h * (1f - h)
        output[1] = bn.x + (an.x - bn.x) * h
        output[2] = bn.y + (an.y - bn.y) * h
        return output
    }

    private fun multipleField(x: Float, y: Float, output: FloatArray): FloatArray {
        var ad = a.field.distance(x, y)
        val firstNormal = a.field.gradient(x, y)
        var ax = firstNormal.x
        var ay = firstNormal.y
        for (i in 1 until multipleElements.size) {
            val element = multipleElements[i]
            val bd = element.field.distance(x, y)
            val bn = element.field.gradient(x, y)
            val radius = smoothness * (.5f - .5f * (ax * bn.x + ay * bn.y)).coerceIn(0f, 1f)
            val h = if (radius <= .000001f) { if (ad < bd) 1f else 0f }
                else (.5f + .5f * (bd - ad) / radius).coerceIn(0f, 1f)
            ad = bd + (ad - bd) * h - radius * h * (1f - h)
            ax = bn.x + (ax - bn.x) * h
            ay = bn.y + (ay - bn.y) * h
        }
        output[0] = ad; output[1] = ax; output[2] = ay
        return output
    }

    private fun distance(x: Float, y: Float): Float =
        if (b == null) a.field.distance(x, y) else field(x, y, sample)[0]

    fun shaderField(scale: Offset, translation: Offset): NativeGlassFilterField =
        if (multipleElements.isNotEmpty()) multipleShaderField(scale, translation)
        else NativeGlassFilterField(NavigationNativeUnionShader) { shader ->
            fun element(prefix: String, value: Element) {
                shader.setFloatUniform(prefix + "Center", value.bounds.center.x, value.bounds.center.y)
                shader.setFloatUniform(prefix + "Extent", value.bounds.width * .5f, value.bounds.height * .5f)
                shader.setFloatUniform(prefix + "Radii", value.radii.x, value.radii.y)
            }
            element("unionFirst", a)
            element("unionSecond", b ?: a)
            shader.setFloatUniform("unionCount", if (b == null) 1f else 2f)
            shader.setFloatUniform("unionSmoothness", smoothness)
            shader.setFloatUniform("unionScale", scale.x, scale.y)
            shader.setFloatUniform("unionTranslation", translation.x, translation.y)
        }

    private fun multipleShaderField(scale: Offset, translation: Offset): NativeGlassFilterField {
        val elements = FloatArray(MaximumUnionElements * 4)
        val radii = FloatArray(MaximumUnionElements * 2)
        multipleElements.forEachIndexed { i, element ->
            elements[i * 4] = element.bounds.center.x
            elements[i * 4 + 1] = element.bounds.center.y
            elements[i * 4 + 2] = element.bounds.width * .5f
            elements[i * 4 + 3] = element.bounds.height * .5f
            radii[i * 2] = element.radii.x
            radii[i * 2 + 1] = element.radii.y
        }
        return NativeGlassFilterField(NavigationNativeMultipleUnionShader) { shader ->
            shader.setFloatUniform("unionElements", elements)
            shader.setFloatUniform("unionRadii", radii)
            shader.setFloatUniform("unionCount", multipleElements.size.toFloat())
            shader.setFloatUniform("unionSmoothness", smoothness)
            shader.setFloatUniform("unionScale", scale.x, scale.y)
            shader.setFloatUniform("unionTranslation", translation.x, translation.y)
        }
    }

    private fun rootX(outside: Float, inside: Float): Float {
        var lo = outside
        var hi = inside
        repeat(20) {
            val mid = (lo + hi) * .5f
            if (distance(mid, centerY) > 0f) lo = mid else hi = mid
        }
        return (lo + hi) * .5f
    }

    private fun createAxisRanges(): List<AxisRange> {
        if (multipleElements.isEmpty()) return emptyList()
        val guard = smoothness * .25f * (multipleElements.size - 1) + 1f
        val ranges = ArrayList<AxisRange>(multipleElements.size)
        var left = rootX(bounds.left - guard, multipleElements.first().bounds.center.x)
        for (i in 0 until multipleElements.lastIndex) {
            val before = multipleElements[i]
            val after = multipleElements[i + 1]
            var lo = before.bounds.center.x
            var hi = after.bounds.center.x
            // Search the complete folded field. A third element may move the gap's
            // maximum away from the two original capsule edges' midpoint.
            var gap = ((before.bounds.right + after.bounds.left) * .5f).coerceIn(lo, hi)
            var maximum = distance(gap, centerY)
            repeat(28) {
                val x0 = lo + (hi - lo) / 3f
                val x1 = hi - (hi - lo) / 3f
                val d0 = distance(x0, centerY)
                val d1 = distance(x1, centerY)
                if (d0 > maximum) { maximum = d0; gap = x0 }
                if (d1 > maximum) { maximum = d1; gap = x1 }
                if (d0 < d1) lo = x0 else hi = x1
            }
            if (maximum > 0f) {
                ranges.add(AxisRange(left, rootX(gap, before.bounds.center.x)))
                left = rootX(gap, after.bounds.center.x)
            }
        }
        ranges.add(AxisRange(left, rootX(bounds.right + guard, multipleElements.last().bounds.center.x)))
        return ranges
    }

    /** A bounded foreground-only approximation of the same zero field, never the optical input. */
    private fun createPath(): Path = Path().apply {
        val guard = smoothness * .25f * maxOf(1, multipleElements.size - 1) + 1f
        val top = bounds.top - guard
        val bottom = bounds.bottom + guard
        val edgeRoots = MutableLongFloatMap()
        fun edgeY(x: Float, outer: Float): Float {
            // Parent quarter probes and child midpoint probes often have identical bits.
            // Reuse only those exact roots; preserve every binary step and path vertex.
            val key = (x.toRawBits().toLong() shl 32) or (outer.toRawBits().toLong() and 0xffffffffL)
            return edgeRoots.getOrPut(key) {
                var lo = outer
                var hi = centerY
                repeat(20) {
                    val mid = (lo + hi) * .5f
                    if (distance(x, mid) > 0f) lo = mid else hi = mid
                }
                (lo + hi) * .5f
            }
        }
        fun append(left: Float, right: Float) {
            val points = ArrayList<Offset>(64)
            fun subdivide(x0: Float, y0: Float, x1: Float, y1: Float, outer: Float, depth: Int) {
                val xm = (x0 + x1) * .5f
                val ym = edgeY(xm, outer)
                // A neck's inflection can lie on the midpoint chord while its two
                // sides do not. Check both quarters before accepting that segment.
                val refine = abs(x1 - x0) > 8f || abs(ym - (y0 + y1) * .5f) > .125f ||
                    abs(edgeY(x0 + (x1 - x0) * .25f, outer) - (y0 + (y1 - y0) * .25f)) > .125f ||
                    abs(edgeY(x0 + (x1 - x0) * .75f, outer) - (y0 + (y1 - y0) * .75f)) > .125f
                if (depth < 16 && refine) {
                    subdivide(x0, y0, xm, ym, outer, depth + 1)
                    subdivide(xm, ym, x1, y1, outer, depth + 1)
                } else points.add(Offset(x1, y1))
            }
            val stops = buildList {
                add(left); add(right)
                for (element in multipleElements.ifEmpty { listOfNotNull(a, b) }) {
                    add(element.bounds.center.x)
                    add(element.bounds.left + element.radii.x)
                    add(element.bounds.right - element.radii.x)
                    add(element.bounds.left); add(element.bounds.right)
                }
                second?.let { add((first.bounds.right + it.bounds.left) * .5f) }
            }.filter { it >= left && it <= right }.distinct().sorted()
            moveTo(left, edgeY(left, top))
            for (outer in floatArrayOf(top, bottom)) {
                points.clear()
                val ordered = if (outer == top) stops else stops.asReversed()
                for (i in 0 until ordered.lastIndex) {
                    val x0 = ordered[i]
                    val x1 = ordered[i + 1]
                    subdivide(x0, edgeY(x0, outer), x1, edgeY(x1, outer), outer, 0)
                }
                for (point in points) lineTo(point.x, point.y)
                if (outer == top) lineTo(right, edgeY(right, bottom))
            }
            close()
        }
        if (multipleElements.isNotEmpty()) {
            axisRanges.forEach { append(it.left, it.right) }
        } else if (b == null || isConnected) {
            append(rootX(bounds.left - guard, a.bounds.center.x),
                rootX(bounds.right + guard, b?.bounds?.center?.x ?: a.bounds.center.x))
        } else {
            // The gap midpoint is outside by the same topology query above. A broad
            // exterior bracket could otherwise land inside the neighboring element.
            val gap = (a.bounds.right + b.bounds.left) * .5f
            append(rootX(a.bounds.left - guard, a.bounds.center.x), rootX(gap, a.bounds.center.x))
            append(rootX(gap, b.bounds.center.x), rootX(b.bounds.right + guard, b.bounds.center.x))
        }
    }

    companion object {
        private const val MaximumUnionElements = 16

        fun create(capsules: List<LiquidContactCapsule>, smoothness: Float): NavigationNativeUnion? {
            if (capsules.isEmpty() || capsules.size > MaximumUnionElements) return null
            if (capsules.size <= 2) return create(capsules.first(), capsules.getOrNull(1), smoothness)
            if (!smoothness.isFinite() || smoothness <= 0f || capsules.any { c ->
                c.size.width <= 0f || c.size.height <= 0f || c.bounds.width <= 0f || c.bounds.height <= 0f ||
                    !c.size.width.isFinite() || !c.size.height.isFinite() || !c.bounds.width.isFinite() ||
                    !c.bounds.height.isFinite() || !c.bounds.left.isFinite() || !c.bounds.top.isFinite() ||
                    !c.bounds.right.isFinite() || !c.bounds.bottom.isFinite()
            }) return null
            if (capsules.maxOf { it.bounds.center.y } - capsules.minOf { it.bounds.center.y } > 1f) return null
            // CASDF folding follows physical order, regardless of the host's RTL order.
            val ordered = capsules.sortedWith(compareBy<LiquidContactCapsule> { it.bounds.center.x }
                .thenBy { it.bounds.left }.thenBy { it.bounds.top }.thenBy { it.bounds.right }
                .thenBy { it.bounds.bottom }.thenBy { it.size.width }.thenBy { it.size.height })
            return NavigationNativeUnion(ordered[0], ordered[1], smoothness, ordered.drop(2))
        }

        fun create(first: LiquidContactCapsule, second: LiquidContactCapsule?, smoothness: Float): NavigationNativeUnion? {
            fun valid(c: LiquidContactCapsule) = c.size.width > 0f && c.size.height > 0f &&
                c.bounds.width > 0f && c.bounds.height > 0f && c.size.width.isFinite() &&
                c.size.height.isFinite() && c.bounds.left.isFinite() && c.bounds.top.isFinite() &&
                c.bounds.right.isFinite() && c.bounds.bottom.isFinite()
            if (!valid(first) || second?.let { !valid(it) } == true || !smoothness.isFinite() || smoothness <= 0f) return null
            // Custom, noncoaxial floating-search surfaces retain their existing contour contract.
            if (second != null && abs(first.bounds.center.y - second.bounds.center.y) > 1f) return null
            val left = if (second == null || first.bounds.center.x <= second.bounds.center.x) first else second
            val right = if (second == null) null else if (left === first) second else first
            return NavigationNativeUnion(left, right, smoothness)
        }
    }
}

private const val NavigationNativeUnionShader = NativeGlassSdfFunctions + """
uniform float2 unionFirstCenter;
uniform float2 unionFirstExtent;
uniform float2 unionFirstRadii;
uniform float2 unionSecondCenter;
uniform float2 unionSecondExtent;
uniform float2 unionSecondRadii;
uniform float unionCount;
uniform float unionSmoothness;
uniform float2 unionScale;
uniform float2 unionTranslation;
float3 navigationGlassShapeField(float2 point) {
    point = (point - unionTranslation) / unionScale;
    float3 a = nativeGlassRoundedField(point - unionFirstCenter, unionFirstExtent, unionFirstRadii, 0.0);
    if (unionCount > 1.5) {
        float3 b = nativeGlassRoundedField(point - unionSecondCenter, unionSecondExtent, unionSecondRadii, 0.0);
        float radius = unionSmoothness * clamp(0.5 - 0.5 * dot(a.yz, b.yz), 0.0, 1.0);
        float h = radius <= 0.000001 ? (a.x < b.x ? 1.0 : 0.0)
            : clamp(0.5 + 0.5 * (b.x - a.x) / radius, 0.0, 1.0);
        a = float3(mix(b.x, a.x, h) - radius * h * (1.0 - h), mix(b.yz, a.yz, h));
    }
    float smallest = min(unionScale.x, unionScale.y);
    return float3(a.x * smallest, a.yz * smallest / unionScale);
}
float3 navigationGlassField(float2 point) { return navigationGlassShapeField(point); }
"""

private const val NavigationNativeMultipleUnionShader = NativeGlassSdfFunctions + """
uniform float4 unionElements[16];
uniform float2 unionRadii[16];
uniform float unionCount;
uniform float unionSmoothness;
uniform float2 unionScale;
uniform float2 unionTranslation;
float3 navigationGlassShapeField(float2 point) {
    point = (point - unionTranslation) / unionScale;
    float4 first = unionElements[0];
    float3 a = nativeGlassRoundedField(point - first.xy, first.zw, unionRadii[0], 0.0);
    for (int i = 1; i < 16; ++i) {
        if (float(i) < unionCount) {
            float4 element = unionElements[i];
            float3 b = nativeGlassRoundedField(point - element.xy, element.zw, unionRadii[i], 0.0);
            float radius = unionSmoothness * clamp(0.5 - 0.5 * dot(a.yz, b.yz), 0.0, 1.0);
            float h = radius <= 0.000001 ? (a.x < b.x ? 1.0 : 0.0)
                : clamp(0.5 + 0.5 * (b.x - a.x) / radius, 0.0, 1.0);
            a = float3(mix(b.x, a.x, h) - radius * h * (1.0 - h), mix(b.yz, a.yz, h));
        }
    }
    float smallest = min(unionScale.x, unionScale.y);
    return float3(a.x * smallest, a.yz * smallest / unionScale);
}
float3 navigationGlassField(float2 point) { return navigationGlassShapeField(point); }
"""
