package io.github.neoruaa.glasmio.ui

import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathIterator
import androidx.compose.ui.graphics.PathSegment
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.sqrt

/** The body and pointer share one contour for the glass, shadow and rim. */
internal data class PopoverShape(
    private val placement: AnchoredPlacement?,
    private val radius: Dp,
    private val pointerDepth: Dp,
    private val capsuleHeight: Int,
) : Shape {
    private data class CachedOutline(
        val size: Size,
        val layoutDirection: LayoutDirection,
        val density: Float,
        val fontScale: Float,
        val outline: Outline,
    )

    private var cachedOutline: CachedOutline? = null

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cached = cachedOutline
        if (cached != null && cached.size == size && cached.layoutDirection == layoutDirection &&
            cached.density == density.density && cached.fontScale == density.fontScale
        ) {
            return cached.outline
        }

        // Clipping, shadow, refraction and the rim all use the same contour.
        return buildOutline(size, layoutDirection, density).also {
            cachedOutline = CachedOutline(size, layoutDirection, density.density, density.fontScale, it)
        }
    }

    private fun buildOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val depth = with(density) { pointerDepth.roundToPx().toFloat() }
        val width = (size.width - depth * 2f).coerceAtLeast(0f)
        val height = (size.height - depth * 2f).coerceAtLeast(0f)
        if (width == 0f || height == 0f) {
            return Outline.Rectangle(Rect(depth, depth, depth + width, depth + height))
        }

        val side = placement?.side
        val horizontal = side == AnchoredSide.Above || side == AnchoredSide.Below
        val span = if (horizontal) width else height
        val pointerOffset = (placement?.let {
            if (horizontal) (it.tip.x - it.body.left).toFloat() else (it.tip.y - it.body.top).toFloat()
        } ?: span * 0.5f).coerceIn(0f, span)

        // Tune the projection and root span independently so a shorter pointer opens its angle.
        val halfBase = minOf(with(density) { 14.3.dp.toPx() }, minOf(pointerOffset, span - pointerOffset) * 0.72f)
        val bodyShape = if (height <= capsuleHeight) PopoverCapsule else RoundedRectangle(radius)
        val bodySize = Size(width, height)
        val outline = bodyShape.createOutline(bodySize, layoutDirection, density)
        val body = when (outline) {
            is Outline.Generic -> outline.path
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect, Path.Direction.Clockwise) }
            is Outline.Rectangle -> Path().apply { addRect(outline.rect, Path.Direction.Clockwise) }
        }
        return Outline.Generic(Path().apply {
            val withArrow = if (side != null && halfBase > 0f && depth > 0f) {
                val tipRadius = with(density) { 1.5.dp.toPx() }
                addPopoverArrow(body, bodySize, side, pointerOffset, halfBase, depth, tipRadius)
            } else false
            if (!withArrow) addPath(body)
            translate(Offset(depth, depth))
        })
    }
}

private fun Path.cubicThrough(first: Offset, second: Offset, end: Offset) =
    cubicTo(first.x, first.y, second.x, second.y, end.x, end.y)

private val PopoverCapsule = Capsule()

/** Keeps the library contour outside the two arrow roots. */
private fun Path.addPopoverArrow(
    body: Path,
    size: Size,
    side: AnchoredSide,
    pointerOffset: Float,
    halfBase: Float,
    depth: Float,
    tipRadius: Float,
): Boolean {
    // In these coordinates, the arrow faces up and its edge runs left to right.
    val center = when (side) {
        AnchoredSide.Below, AnchoredSide.Left -> pointerOffset
        AnchoredSide.Above -> size.width - pointerOffset
        AnchoredSide.Right -> size.height - pointerOffset
    }
    val contour = body.cubics { x, y ->
        when (side) {
            AnchoredSide.Below -> Offset(x, y)
            AnchoredSide.Left -> Offset(y, size.width - x)
            AnchoredSide.Above -> Offset(size.width - x, size.height - y)
            AnchoredSide.Right -> Offset(size.height - y, x)
        }
    }

    val leading = contour.findArrowRoot(center - halfBase) ?: return false
    val trailing = contour.findArrowRoot(center + halfBase) ?: return false
    val arrow = createPopoverArrow(leading.sample, trailing.sample, center, halfBase, depth, tipRadius) ?: return false
    moveTo(leading.sample.position.x, leading.sample.position.y)
    appendPopoverArrow(arrow)

    val trailingCurve = contour[trailing.segment]
    if (trailing.parameter < 1f) appendCurve(trailingCurve, trailing.parameter, 1f)
    var segment = (trailing.segment + 1) % contour.size
    while (segment != leading.segment) {
        appendCurve(contour[segment])
        segment = (segment + 1) % contour.size
    }
    if (leading.parameter > 0f) appendCurve(contour[leading.segment], 0f, leading.parameter)
    close()

    if (side != AnchoredSide.Below) {
        asAndroidPath().transform(Matrix().apply {
            when (side) {
                AnchoredSide.Left -> { setSinCos(1f, 0f); postTranslate(size.width, 0f) }
                AnchoredSide.Above -> { setSinCos(0f, -1f); postTranslate(size.width, size.height) }
                AnchoredSide.Right -> { setSinCos(-1f, 0f); postTranslate(0f, size.height) }
                AnchoredSide.Below -> Unit
            }
        })
    }
    return true
}

private fun Path.cubics(transform: (Float, Float) -> Offset): List<ContourCubic> = buildList {
    // Square outlines contain conics. Other library outlines are already lines and cubics.
    val iterator = iterator(PathIterator.ConicEvaluation.AsQuadratics, tolerance = 0.01f)
    val points = FloatArray(8)
    fun point(index: Int) = transform(points[index], points[index + 1])
    fun addLine(start: Offset, end: Offset) {
        if (start == end) return
        val third = (end - start) / 3f
        add(ContourCubic(start, start + third, end - third, end))
    }

    var start = Offset.Zero
    var current = Offset.Zero
    while (iterator.hasNext()) {
        when (iterator.next(points)) {
            PathSegment.Type.Move -> {
                start = point(0)
                current = start
            }
            PathSegment.Type.Line -> {
                current = point(2)
                addLine(point(0), current)
            }
            PathSegment.Type.Quadratic -> {
                val from = point(0)
                val control = point(2)
                current = point(4)
                val first = from + (control - from) * (2f / 3f)
                val second = current + (control - current) * (2f / 3f)
                add(ContourCubic(from, first, second, current))
            }
            PathSegment.Type.Cubic -> {
                current = point(6)
                add(ContourCubic(point(0), point(2), point(4), current))
            }
            PathSegment.Type.Close -> addLine(current, start)
            PathSegment.Type.Conic, PathSegment.Type.Done -> Unit
        }
    }
}

private data class ArrowRoot(val segment: Int, val parameter: Float, val sample: ContourSample)

private fun List<ContourCubic>.findArrowRoot(x: Float): ArrowRoot? {
    for ((index, curve) in withIndex()) {
        if (curve.end.x <= curve.start.x || x < curve.start.x || x > curve.end.x) continue
        val parameter = when (x) {
            curve.start.x -> 0f
            curve.end.x -> 1f
            else -> curve.parameterAtX(x)
        }
        val sample = curve.sample(parameter)
        if (sample.velocity.x <= 0f) continue
        val slope = sample.velocity.y / sample.velocity.x
        val second = (sample.acceleration.y - slope * sample.acceleration.x) /
            (sample.velocity.x * sample.velocity.x)
        return ArrowRoot(index, parameter, graphSample(sample.position.x, sample.position.y, slope, second))
    }
    return null
}

private fun Path.appendCurve(curve: ContourCubic, from: Float = 0f, to: Float = 1f) {
    if (from == 0f && to == 1f) {
        cubicThrough(curve.first, curve.second, curve.end)
    } else {
        val start = curve.sample(from)
        val end = curve.sample(to)
        val handle = (to - from) / 3f
        cubicThrough(start.position + start.velocity * handle, end.position - end.velocity * handle, end.position)
    }
}

private data class ContourSample(val position: Offset, val velocity: Offset, val acceleration: Offset)

private data class ContourCubic(val start: Offset, val first: Offset, val second: Offset, val end: Offset) {
    fun sample(t: Float): ContourSample {
        val s = 1f - t
        val v0 = first - start
        val v1 = second - first
        val v2 = end - second
        return ContourSample(
            start * (s * s * s) + first * (3f * s * s * t) + second * (3f * s * t * t) + end * (t * t * t),
            (v0 * (s * s) + v1 * (2f * t * s) + v2 * (t * t)) * 3f,
            ((v1 - v0) * s + (v2 - v1) * t) * 6f,
        )
    }

    fun parameterAtX(x: Float): Float {
        var lo = 0f
        var hi = 1f
        repeat(22) {
            val t = (lo + hi) / 2f
            if (sample(t).position.x < x) lo = t else hi = t
        }
        return (lo + hi) / 2f
    }
}

private data class PopoverArrowSide(
    val root: ContourSample,
    val flank: ContourSample,
    val tipJoin: ContourSample,
)

private data class PopoverArrow(val leading: PopoverArrowSide, val tip: Offset, val trailing: PopoverArrowSide)

private fun graphSample(x: Float, y: Float, slope: Float, second: Float = 0f) =
    ContourSample(Offset(x, y), Offset(1f, slope), Offset(0f, second))

private fun createPopoverArrow(
    leading: ContourSample,
    trailing: ContourSample,
    center: Float,
    halfBase: Float,
    depth: Float,
    tipRadius: Float,
): PopoverArrow? {
    val shoulder = halfBase * 0.40f
    val flankEnd = halfBase - shoulder
    val effectiveBase = halfBase - shoulder / 2f
    fun rise(root: ContourSample, direction: Float): Float =
        root.position.y + depth - direction * root.velocity.y * shoulder / 2f +
            root.acceleration.y * shoulder * shoulder / 12f

    val leadingRise = rise(leading, -1f)
    val trailingRise = rise(trailing, 1f)
    if (!leadingRise.isFinite() || !trailingRise.isFinite() || minOf(leadingRise, trailingRise) <= 0f) return null
    // The tip radius is width / (2 * slope). Limit the width for narrow or off-center arrows
    // so each side still has a straight flank between the rounded tip and shoulder.
    val maxTipWidth = flankEnd * 0.72f
    val tipWidthPerSlope = minOf(
        tipRadius * 2f,
        maxTipWidth * (effectiveBase - maxTipWidth / 3f) / maxOf(leadingRise, trailingRise),
    )

    fun side(root: ContourSample, rise: Float, direction: Float): PopoverArrowSide? {
        val discriminant = effectiveBase * effectiveBase - 4f * tipWidthPerSlope * rise / 3f
        if (discriminant < 0f) return null
        val slope = 2f * rise / (effectiveBase + sqrt(discriminant))
        val tipWidth = slope * tipWidthPerSlope
        val flank = graphSample(center + direction * flankEnd, -depth + slope * (flankEnd - tipWidth / 3f), direction * slope)
        val tipJoin = graphSample(center + direction * tipWidth, -depth + slope * tipWidth * (2f / 3f), direction * slope)
        return PopoverArrowSide(root, flank, tipJoin)
    }

    return PopoverArrow(
        leading = side(leading, leadingRise, -1f) ?: return null,
        tip = Offset(center, -depth),
        trailing = side(trailing, trailingRise, 1f) ?: return null,
    )
}

private fun Path.appendPopoverArrow(arrow: PopoverArrow) {
    val (leading, tip, trailing) = arrow
    appendSmoothSpan(leading.root, leading.flank, leading.flank.position.x - leading.root.position.x)
    lineTo(leading.tipJoin.position.x, leading.tipJoin.position.y)
    val leadingHandle = (tip.x - leading.tipJoin.position.x) / 3f
    cubicThrough(leading.tipJoin.position + leading.tipJoin.velocity * leadingHandle, tip - Offset(leadingHandle, 0f), tip)
    val trailingHandle = (trailing.tipJoin.position.x - tip.x) / 3f
    cubicThrough(tip + Offset(trailingHandle, 0f), trailing.tipJoin.position - trailing.tipJoin.velocity * trailingHandle, trailing.tipJoin.position)
    lineTo(trailing.flank.position.x, trailing.flank.position.y)
    appendSmoothSpan(trailing.flank, trailing.root, trailing.root.position.x - trailing.flank.position.x)
}

/** Fits three cubics with the given endpoint tangents and curvature and C2 internal joints. */
private fun Path.appendSmoothSpan(start: ContourSample, end: ContourSample, duration: Float) {
    val step = duration / 3f
    val startTangent = start.velocity * (step / 3f)
    val endTangent = end.velocity * (step / 3f)
    val startFirst = start.position + startTangent
    val startSecond = startFirst + startTangent + start.acceleration * (step * step / 6f)
    val endSecond = end.position - endTangent
    val endFirst = endSecond - endTangent + end.acceleration * (step * step / 6f)
    val a = startSecond * 4f - startFirst - endFirst
    val b = endFirst * 4f - endSecond - startSecond
    val firstJoint = (a * 2f + b) / 6f
    val secondJoint = (a + b * 2f) / 6f
    cubicThrough(startFirst, startSecond, firstJoint)
    cubicThrough(firstJoint * 2f - startSecond, secondJoint * 2f - endFirst, secondJoint)
    cubicThrough(endFirst, endSecond, end.position)
}
