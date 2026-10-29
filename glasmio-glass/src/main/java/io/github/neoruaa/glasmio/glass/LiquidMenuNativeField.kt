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

    fun buildNavigationPath(geometry: FloatArray, path: android.graphics.Path): Boolean =
        available && buildNavigationPathNative(geometry, path)

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

    private external fun buildNavigationPathNative(geometry: FloatArray, path: android.graphics.Path): Boolean

    private external fun releaseNative()
}
