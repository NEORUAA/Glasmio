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
    private data class Edge(val start: Offset, val end: Offset, val normal: Offset)
    private val contourEdges = buildList {
        val contour = ArrayList<Offset>()
        fun closeContour() {
            if (contour.size < 3) { contour.clear(); return }
            var area = 0f
            contour.indices.forEach { i ->
                val a = contour[i]
                val b = contour[(i + 1) % contour.size]
                area += a.x * b.y - b.x * a.y
            }
            val winding = if (area >= 0f) 1f else -1f
            contour.indices.forEach { i ->
                val a = contour[i]
                val b = contour[(i + 1) % contour.size]
                val delta = b - a
                val length = delta.getDistance()
                if (length > 0.0001f) add(Edge(a, b, Offset(delta.y, -delta.x) * (winding / length)))
            }
            contour.clear()
        }
        var previousFraction = -1f
        for (i in approximation.indices step 3) {
            val fraction = approximation[i]
            val point = Offset(approximation[i + 1], approximation[i + 2])
            // Android Path.approximate repeats the length fraction across a move.
            // End the old contour here; never invent an edge across a detached gap.
            if (fraction == previousFraction && contour.lastOrNull() != point) closeContour()
            if (contour.lastOrNull() != point) contour.add(point)
            previousFraction = fraction
        }
        closeContour()
    }
    val edges = FloatArray(contourEdges.size * 6).also { packed ->
        contourEdges.forEachIndexed { i, edge ->
            val start = i * 6
            packed[start] = edge.start.x
            packed[start + 1] = edge.start.y
            packed[start + 2] = edge.end.x
            packed[start + 3] = edge.end.y
            packed[start + 4] = edge.normal.x
            packed[start + 5] = edge.normal.y
        }
    }
    val bounds = Rect(
        contourEdges.minOf { minOf(it.start.x, it.end.x) }, contourEdges.minOf { minOf(it.start.y, it.end.y) },
        contourEdges.maxOf { maxOf(it.start.x, it.end.x) }, contourEdges.maxOf { maxOf(it.start.y, it.end.y) },
    )
}

/** Container-owned storage survives the short-lived contour snapshots of a drag. */
internal class NavigationContactField {
    private val buffers = ArrayList<Grid>(3)
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
        buffers.firstOrNull { it.contour === contour && it.band >= band }?.let { return it }
        val bounds = contour.bounds
        val step = 2f
        val origin = Offset(floor(bounds.left / step) * step - step, floor(bounds.top / step) * step - step)
        val width = ceil((bounds.right - origin.x) / step).toInt() + 2
        val height = ceil((bounds.bottom - origin.y) / step).toInt() + 2
        // Rotate bounded buckets. Do not recycle bitmaps still referenced by display lists.
        val reusable = buffers.takeIf { it.size == 3 }?.firstOrNull {
            it.width >= width && it.height >= height && it.width * it.height <= width * height * 2
        }
        val result = reusable ?: Grid((width + 31) / 32 * 32, (height + 15) / 16 * 16)
        if (reusable != null) buffers.remove(reusable)
        else if (buffers.size == 3) buffers.removeAt(0)
        buffers.add(result)
        result.origin = origin
        result.band = band
        result.contour = contour
        build(contour.edges, result)
        result.generation++
        buildCount++
        return result
    }

    /** Reject impossible candidates early and resolve each cell's normal only once. */
    private fun build(edges: FloatArray, grid: Grid) {
        val width = grid.width
        val height = grid.height
        val step = grid.step
        val origin = grid.origin
        val rgba = grid.rgba
        val reach = grid.band + step * 2f
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
            for (y in top..bottom) for (x in left..right) {
                val pointX = origin.x + x * step
                val pointY = origin.y + y * step
                val output = (y * width + x) * 4
                val outsideX = maxOf(minimumX - pointX, pointX - maximumX, 0f)
                val outsideY = maxOf(minimumY - pointY, pointY - maximumY, 0f)
                if (outsideX * outsideX + outsideY * outsideY >= rgba[output]) continue
                val px = pointX - ax
                val py = pointY - ay
                val t = ((px * dx + py * dy) * inverseLengthSquared).coerceIn(0f, 1f)
                val nx = px - t * dx
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
