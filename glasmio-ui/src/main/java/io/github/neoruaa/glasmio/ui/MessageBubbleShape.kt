package io.github.neoruaa.glasmio.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * The continuous, round-tailed contour used by iOS 27 ChatKit's `CKBubblePath`.
 *
 * ChatKit delegates its contour to BubbleKit's `BubblePath.Configuration`: the default
 * Messages refresh selects `roundTailed`, with continuous corners that blend toward
 * circular curves when the body becomes a pill. The tail occupies the bottom of the
 * shape bounds and does not add horizontal width. [cornerRadius] is the requested body
 * radius; the path reduces it when necessary to fit its bounds.
 *
 * [outgoing] puts the tail on the logical end. An incoming shape is its exact mirror.
 */
@Immutable
class MessageBubbleShape(
    private val outgoing: Boolean,
    private val showTail: Boolean = true,
    private val cornerRadius: Dp = 18.dp,
) : Shape {
    init {
        require(cornerRadius.value.isFinite() && cornerRadius.value >= 0f) {
            "Message bubble corner radius must be finite and nonnegative."
        }
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val width = size.width.toDouble()
        val height = size.height.toDouble()
        if (!width.isFinite() || !height.isFinite() || width <= 0.0 || height <= 0.0) {
            return Outline.Generic(Path())
        }
        val requestedRadius = with(density) { cornerRadius.toPx().toDouble() }
        val radius = min(requestedRadius, min(width * 0.5, height / if (showTail) 2.33925 else 2.0))
        if (radius < 0.001) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        val bodyBottom = height - if (showTail) radius * 0.33925 else 0.0
        val x = cornerParameters(width, radius)
        val y = cornerParameters(bodyBottom, radius)
        val mirror = outgoing == (layoutDirection == LayoutDirection.Rtl)
        val path = Path()
        fun px(value: Double) = (if (mirror) width - value else value).toFloat()
        fun moveTo(a: Double, b: Double) = path.moveTo(px(a), b.toFloat())
        fun lineTo(a: Double, b: Double) = path.lineTo(px(a), b.toFloat())
        fun curveTo(a: Double, b: Double, c: Double, d: Double, e: Double, f: Double) =
            path.cubicTo(px(a), b.toFloat(), px(c), d.toFloat(), px(e), f.toFloat())

        moveTo(0.0, y[11])
        curveTo(y[3], y[10], y[4], y[9], y[5], y[8])
        curveTo(y[6], y[7], x[7], y[6], x[8], x[5])
        curveTo(x[9], x[4], x[10], x[3], x[11], x[2])
        lineTo(width - x[11], 0.0)
        curveTo(width - x[10], x[3], width - x[9], x[4], width - x[8], x[5])
        curveTo(width - x[7], x[6], width - x[6], y[7], width - y[5], y[8])
        curveTo(width - y[4], y[9], width - y[3], y[10], width - y[2], y[11])
        lineTo(width, bodyBottom - y[11])
        if (showTail) {
            for (segment in RoundTailCurves) {
                curveTo(
                    width + segment[0] * radius, bodyBottom + segment[1] * radius,
                    width + segment[2] * radius, bodyBottom + segment[3] * radius,
                    width + segment[4] * radius, bodyBottom + segment[5] * radius,
                )
            }
        } else {
            curveTo(width - y[3], bodyBottom - y[10], width - y[4], bodyBottom - y[9], width - y[5], bodyBottom - y[8])
            curveTo(width - x[6], bodyBottom - y[7], width - x[7], bodyBottom - y[6], width - x[8], bodyBottom - x[5])
            curveTo(width - x[9], bodyBottom - x[4], width - x[10], bodyBottom - x[3], width - x[11], bodyBottom - x[2])
        }
        lineTo(x[11], bodyBottom)
        curveTo(x[10], bodyBottom - x[3], x[9], bodyBottom - x[4], x[8], bodyBottom - x[5])
        curveTo(x[7], bodyBottom - x[6], x[6], bodyBottom - y[7], y[5], bodyBottom - y[8])
        curveTo(y[4], bodyBottom - y[9], y[3], bodyBottom - y[10], y[2], bodyBottom - y[11])
        path.close()
        return Outline.Generic(path)
    }
}

private fun cornerParameters(length: Double, radius: Double): DoubleArray {
    val circularWeight = ((length * 0.5 / radius - 1.528665) / -0.528665).coerceIn(0.0, 1.0)
    return DoubleArray(12) { index ->
        radius * (ContinuousCorner[index] * (1.0 - circularWeight) + CircularCorner[index] * circularWeight)
    }
}

// BubbleKit.CornerCurveParameters witness order, including the two leading unused slots.
private val ContinuousCorner = doubleArrayOf(
    0.0, 0.0, 0.0, 0.0, 0.0, 0.0749114, 0.16906, 0.372824,
    0.631494, 0.868407, 1.08849, 1.528665,
)
private val CircularCorner = doubleArrayOf(
    0.0, 0.0, 0.0, 0.0, 0.024092519875899457, 0.07099462715854132,
    0.1726399214289621, 0.37476586421208946, 0.6299337664259247,
    0.7476756885741366, 0.8732602355268562, 1.0,
)

// Native round-tail cubic controls and endpoints, relative to (bodyRight, bodyBottom) / radius.
private val RoundTailCurves = arrayOf(
    doubleArrayOf(0.0, -0.78469087, -0.070745, -0.57509087, -0.201395, -0.40379087),
    doubleArrayOf(-0.254335, -0.33438785, -0.31546, -0.27321571, -0.383, -0.22117087),
    doubleArrayOf(-0.47925, -0.14612087, -0.5209, -0.06782087, -0.5209, 0.02017913),
    doubleArrayOf(-0.5209, 0.07932913, -0.51045, 0.13777913, -0.4255, 0.24937913),
    doubleArrayOf(-0.38475, 0.30287913, -0.42565, 0.35747913, -0.48935, 0.33327913),
    doubleArrayOf(-0.62035, 0.28352913, -0.76955, 0.19297913, -0.90025, 0.09632913),
    doubleArrayOf(-1.01735, 0.00972913, -1.04855, 0.00097913, -1.1035, 0.00062913),
)
