package io.github.neoruaa.glasmio.glass

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.icons.*

import androidx.annotation.Keep
import java.nio.ByteBuffer

/** Native numerical fast path; local JVM tests retain the identical Kotlin reference. */
@Keep
object LiquidMenuNativeField {
    private val available = runCatching { System.loadLibrary("glasmio_liquid_menu_sdf") }.isSuccess

    fun warmUp(): Boolean = available

    fun build(
        geometry: FloatArray,
        originX: Float,
        originY: Float,
        step: Float,
        width: Int,
        height: Int,
        weights: FloatArray,
        radius: Int,
        rgba: FloatArray,
    ): Boolean = available && buildNative(geometry, originX, originY, step, width, height, weights, radius, rgba)

    fun pack(rgba: FloatArray, width: Int, height: Int, rowBytes: Int, target: ByteBuffer): Boolean =
        available && target.isDirect && packNative(rgba, width, height, rowBytes, target)

    fun buildContact(
        edges: FloatArray, originX: Float, originY: Float, step: Float,
        width: Int, height: Int, band: Float, rgba: FloatArray,
    ): Boolean = available && buildContactNative(edges, originX, originY, step, width, height, band, rgba)

    /** Reused contour buffer; grown when a contour needs more room. */
    private var navigationContours = FloatArray(2048)

    /**
     * Appends the native navigation union contours to [path]. The native side returns each closed
     * contour as `[n, x0, y0, ..., x(n-1), y(n-1)]` instead of calling back into the Path per vertex.
     */
    fun buildNavigationPath(geometry: FloatArray, path: android.graphics.Path): Boolean {
        if (!available) return false
        synchronized(this) {
            var count = buildNavigationPathNative(geometry, navigationContours)
            if (count < 0) {
                navigationContours = FloatArray(-count)
                count = buildNavigationPathNative(geometry, navigationContours)
            }
            if (count <= 0) return false
            appendContours(path, navigationContours, count)
            return true
        }
    }

    private fun appendContours(path: android.graphics.Path, contours: FloatArray, count: Int) {
        var index = 0
        while (index < count) {
            val points = contours[index].toInt()
            path.moveTo(contours[index + 1], contours[index + 2])
            for (point in 1 until points) {
                path.lineTo(contours[index + 1 + 2 * point], contours[index + 2 + 2 * point])
            }
            path.close()
            index += 1 + 2 * points
        }
    }

    fun release() {
        if (available) releaseNative()
    }

    private external fun buildNative(
        geometry: FloatArray, originX: Float, originY: Float, step: Float,
        width: Int, height: Int, weights: FloatArray, radius: Int, rgba: FloatArray,
    ): Boolean

    private external fun packNative(
        rgba: FloatArray, width: Int, height: Int, rowBytes: Int, target: ByteBuffer,
    ): Boolean

    private external fun buildContactNative(
        edges: FloatArray, originX: Float, originY: Float, step: Float,
        width: Int, height: Int, band: Float, rgba: FloatArray,
    ): Boolean

    private external fun buildNavigationPathNative(geometry: FloatArray, output: FloatArray): Int

    private external fun releaseNative()
}
