package io.github.neoruaa.glasmio.glass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ColorSpace
import android.graphics.Shader
import android.util.Half
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/** Immutable boundary snapshot shared by visible and affine-transformed material passes. */
internal class NavigationContactContour(approximation: FloatArray) {
    val edges: FloatArray
    val bounds: Rect

    init {
        // Keep points in the original packed approximation. Dragging rebuilds these
        // snapshots every frame; boxing each point and retaining a second edge graph
        // adds allocations without contributing to the field or its cache key.
        val points = IntArray(approximation.size / 3)
        val packed = FloatArray(points.size * 6)
        var written = 0
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        fun point(index: Int): Offset = Offset(approximation[index + 1], approximation[index + 2])
        var cursor = 0
        while (cursor < approximation.size) {
            var pointCount = 0
            var previousFraction = -1f
            while (cursor < approximation.size) {
                val fraction = approximation[cursor]
                val current = point(cursor)
                if (pointCount > 0) {
                    val previous = point(points[pointCount - 1])
                    // A repeated length fraction marks a move to a detached contour.
                    // Leave its first point for the next outer iteration.
                    if (fraction == previousFraction && previous != current) break
                    if (previous != current) points[pointCount++] = cursor
                } else {
                    points[pointCount++] = cursor
                }
                previousFraction = fraction
                cursor += 3
            }
            if (pointCount < 3) continue
            var area = 0f
            for (i in 0 until pointCount) {
                val a = point(points[i])
                val b = point(points[if (i + 1 < pointCount) i + 1 else 0])
                area += a.x * b.y - b.x * a.y
            }
            val winding = if (area >= 0f) 1f else -1f
            for (i in 0 until pointCount) {
                val a = point(points[i])
                val b = point(points[if (i + 1 < pointCount) i + 1 else 0])
                val delta = b - a
                val length = delta.getDistance()
                if (!(length > 0.0001f)) continue
                val normal = Offset(delta.y, -delta.x) * (winding / length)
                packed[written] = a.x
                packed[written + 1] = a.y
                packed[written + 2] = b.x
                packed[written + 3] = b.y
                packed[written + 4] = normal.x
                packed[written + 5] = normal.y
                written += 6
                left = minOf(left, minOf(a.x, b.x))
                top = minOf(top, minOf(a.y, b.y))
                right = maxOf(right, maxOf(a.x, b.x))
                bottom = maxOf(bottom, maxOf(a.y, b.y))
            }
        }
        edges = if (written == packed.size) packed else packed.copyOf(written)
        // Zero-sized entering/leaving controls have no usable edge for this frame.
        bounds = if (written == 0) Rect.Zero else Rect(left, top, right, bottom)
    }
    val fingerprint = edges.contentHashCode()
}

/** Container-owned storage survives the short-lived contour snapshots of a drag. */
internal class NavigationContactField {
    private val buffers = ArrayList<Grid>(3)
    private var outsideXSquared = FloatArray(0)
    private var xOffset = FloatArray(0)
    private var xProjection = FloatArray(0)
    var buildCount = 0
        private set

    internal class Grid(val width: Int, val height: Int) {
        val rgba = FloatArray(width * height * 4)
        val step: Float get() = 2f
        var origin = Offset.Zero
        var band = 0f
        var contour: NavigationContactContour? = null
        var generation = 0
        var uploadedGeneration = -1
        var bitmap: Bitmap? = null
        var shader: BitmapShader? = null
        var upload: ByteBuffer? = null
    }

    fun grid(contour: NavigationContactContour, band: Float): Grid {
        val bounds = contour.bounds
        // Every interior point is within half the contour's shorter bounding dimension
        // of an edge. Unvisited cells beyond this reach are therefore exterior, including
        // the empty space between disconnected controls.
        val effectiveBand = maxOf(band, bounds.size.minDimension * .5f)
        buffers.firstOrNull {
            it.band >= effectiveBand && (it.contour === contour || it.contour?.let { cached ->
                cached.fingerprint == contour.fingerprint && cached.edges.contentEquals(contour.edges)
            } == true)
        }?.let { return it }
        val step = 2f
        // The field now supplies visible exterior coverage/shadows, rather than only
        // clipped inner optics. Allocate the requested support instead of clamping a
        // near-boundary texel over the whole material's exterior rectangle.
        val origin = Offset(floor((bounds.left - effectiveBand) / step) * step - step,
            floor((bounds.top - effectiveBand) / step) * step - step)
        val width = ceil((bounds.right + effectiveBand - origin.x) / step).toInt() + 2
        val height = ceil((bounds.bottom + effectiveBand - origin.y) / step).toInt() + 2
        // Rotate bounded buckets. Do not recycle bitmaps still referenced by display lists.
        val reusable = buffers.takeIf { it.size == 3 }?.firstOrNull {
            it.width >= width && it.height >= height && it.width * it.height <= width * height * 2
        }
        val result = reusable ?: Grid((width + 31) / 32 * 32, (height + 15) / 16 * 16)
        if (reusable != null) buffers.remove(reusable)
        else if (buffers.size == 3) buffers.removeAt(0)
        buffers.add(result)
        result.origin = origin
        result.band = effectiveBand
        result.contour = contour
        build(contour.edges, result)
        result.generation++
        buildCount++
        return result
    }

    /** Reject impossible candidates early and resolve each cell's normal only once. */
    private fun build(edges: FloatArray, grid: Grid) {
        if (LiquidMenuNativeField.buildContact(edges, grid.origin.x, grid.origin.y, grid.step,
                grid.width, grid.height, grid.band, grid.rgba)) {
            finishExteriorCells(grid)
            return
        }
        val width = grid.width
        val height = grid.height
        val step = grid.step
        val origin = grid.origin
        val rgba = grid.rgba
        val reach = grid.band + step * 2f
        if (outsideXSquared.size < width) {
            outsideXSquared = FloatArray(width)
            xOffset = FloatArray(width)
            xProjection = FloatArray(width)
        }
        for (index in 0 until width * height) {
            val output = index * 4
            rgba[output] = reach * reach
            rgba[output + 1] = 0f
            rgba[output + 2] = 0f
            rgba[output + 3] = -1f
        }
        for (edge in edges.indices step 6) {
            val ax = edges[edge]
            val ay = edges[edge + 1]
            val bx = edges[edge + 2]
            val by = edges[edge + 3]
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            val inverseLengthSquared = 1f / lengthSquared
            val minimumX = minOf(ax, bx)
            val maximumX = maxOf(ax, bx)
            val minimumY = minOf(ay, by)
            val maximumY = maxOf(ay, by)
            val left = floor((minOf(ax, bx) - reach - origin.x) / step).toInt().coerceAtLeast(0)
            val right = ceil((maxOf(ax, bx) + reach - origin.x) / step).toInt().coerceAtMost(width - 1)
            val top = floor((minOf(ay, by) - reach - origin.y) / step).toInt().coerceAtLeast(0)
            val bottom = ceil((maxOf(ay, by) + reach - origin.y) / step).toInt().coerceAtMost(height - 1)
            // These terms depend on only one axis. Keep their Float operation order,
            // but calculate them once per column/row rather than at every band sample.
            for (x in left..right) {
                val pointX = origin.x + x * step
                val outsideX = maxOf(minimumX - pointX, pointX - maximumX, 0f)
                outsideXSquared[x] = outsideX * outsideX
                xOffset[x] = pointX - ax
                xProjection[x] = xOffset[x] * dx
            }
            for (y in top..bottom) {
                val pointY = origin.y + y * step
                val outsideY = maxOf(minimumY - pointY, pointY - maximumY, 0f)
                val outsideYSquared = outsideY * outsideY
                val py = pointY - ay
                val yProjection = py * dy
                val row = y * width * 4
                for (x in left..right) {
                    val output = row + x * 4
                    if (outsideXSquared[x] + outsideYSquared >= rgba[output]) continue
                    val t = ((xProjection[x] + yProjection) * inverseLengthSquared).coerceIn(0f, 1f)
                    val nx = xOffset[x] - t * dx
                    val ny = py - t * dy
                    val squared = nx * nx + ny * ny
                    if (squared >= rgba[output]) continue
                    val direction = if (nx * edges[edge + 4] + ny * edges[edge + 5] >= 0f) 1f else -1f
                    rgba[output] = squared
                    rgba[output + 1] = if (squared > 0.00000001f) nx * direction else edges[edge + 4]
                    rgba[output + 2] = if (squared > 0.00000001f) ny * direction else edges[edge + 5]
                    rgba[output + 3] = direction
                }
            }
        }
        for (index in 0 until width * height) {
            val output = index * 4
            val distance = sqrt(rgba[output])
            rgba[output] = distance * rgba[output + 3]
            if (distance > 0.0001f) {
                rgba[output + 1] /= distance
                rgba[output + 2] /= distance
            }
            rgba[output + 3] = 1f
        }
        finishExteriorCells(grid)
    }

    private fun finishExteriorCells(grid: Grid) {
        val rgba = grid.rgba
        for (output in rgba.indices step 4) {
            // The bounded edge raster leaves both normal channels zero only when no
            // segment came within its reach. Its old negative sentinel was safe solely
            // behind a shape clip; it must never become visible opaque glass in a gap.
            if (rgba[output] < 0f && rgba[output + 1] == 0f && rgba[output + 2] == 0f)
                rgba[output] = -rgba[output]
        }
    }

    @android.annotation.SuppressLint("HalfFloat")
    fun input(grid: Grid): BitmapShader {
        if (grid.bitmap == null) {
            // Raw FP16 data preserves signed distances and normals without color conversion.
            val bitmap = Bitmap.createBitmap(grid.width, grid.height, Bitmap.Config.RGBA_F16,
                false, ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB)).apply { setPremultiplied(false) }
            grid.bitmap = bitmap
            grid.upload = ByteBuffer.allocateDirect(bitmap.byteCount).order(ByteOrder.nativeOrder())
            grid.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
            }
        }
        if (grid.uploadedGeneration != grid.generation) {
            val bitmap = requireNotNull(grid.bitmap)
            val bytes = requireNotNull(grid.upload)
            bytes.clear()
            if (!LiquidMenuNativeField.pack(grid.rgba, grid.width, grid.height, bitmap.rowBytes, bytes)) {
                for (y in 0 until grid.height) {
                    bytes.position(y * bitmap.rowBytes)
                    for (x in 0 until grid.width * 4) bytes.putShort(Half.toHalf(grid.rgba[y * grid.width * 4 + x]))
                }
            }
            bytes.rewind()
            bitmap.copyPixelsFromBuffer(bytes)
            grid.uploadedGeneration = grid.generation
        }
        return requireNotNull(grid.shader)
    }
}
