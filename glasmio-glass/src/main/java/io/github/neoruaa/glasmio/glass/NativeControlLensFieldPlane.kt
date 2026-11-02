package io.github.neoruaa.glasmio.glass

import android.graphics.RuntimeShader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScopeImpl
import io.github.neoruaa.glasmio.glass.render.asAndroidRuntimeShader
import io.github.neoruaa.glasmio.glass.render.optics.GlassSdfShader
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

internal data class NativeControlLensFieldKey(
    val surfaceSize: Size,
    val radius: Float,
    val density: Float,
    val padding: Float,
    val support: Float,
    val size: IntSize,
    val origin: Offset,
    val distanceRange: Float,
    val sceneRect: IntRect,
    val consumerRect: IntRect,
)

/** A private opaque SDR data texture; its RGB channels must bypass all color codecs. */
internal class NativeControlLensFieldPlane(
    val key: NativeControlLensFieldKey,
    val producer: RuntimeShader,
    val consumerSize: IntSize,
) {
    val size: IntSize get() = key.size
    val sceneRect: IntRect get() = key.sceneRect
    val consumerRect: IntRect get() = key.consumerRect

    fun configure(shader: RuntimeShader, scene: Boolean) {
        val rect = if (scene) sceneRect else consumerRect
        shader.setFloatUniform("lensFieldOrigin", key.origin.x, key.origin.y)
        shader.setFloatUniform("lensFieldRectOrigin", rect.left.toFloat(), rect.top.toFloat())
        shader.setFloatUniform("lensFieldRange", key.distanceRange)
    }
}

internal fun BackdropEffectScopeImpl.nativeControlLensFieldPlane(
    shape: Shape,
    surfaceSize: Size,
    corners: FloatArray,
    sourceDensity: Float,
    sourcePadding: Float,
    opticalProgress: Float,
    base: IntSize,
    sceneTop: Int,
    scene: IntRect,
    storageSize: IntSize,
): NativeControlLensFieldPlane? {
    if (shape !is Capsule && shape !is ContinuousRoundedRectangle) return null
    if (!nativeGlassHardwareRendering || colorManagedNativeGlass || samplingScale != 1f) return null
    if (!surfaceSize.width.isFinite() || !surfaceSize.height.isFinite() ||
        surfaceSize.width <= 0f || surfaceSize.height <= 0f ||
        !sourceDensity.isFinite() || sourceDensity <= 0f ||
        !sourcePadding.isFinite() || sourcePadding < 0f ||
        !opticalProgress.isFinite() || opticalProgress < 0f || corners.size != 4) return null
    val radius = corners[0]
    if (!radius.isFinite() || radius < 0f || corners.any { it != radius }) return null
    val inner = -.15f * surfaceSize.height * opticalProgress
    val portal = -.2f * surfaceSize.height * opticalProgress
    val support = max(8f * sourceDensity, max(abs(inner), abs(portal) + 7f * sourceDensity * opticalProgress)) + 1f
    val margin = sourcePadding + support + 1f
    val width = ceil(surfaceSize.width + margin * 2f)
    val height = ceil(surfaceSize.height + margin * 2f)
    if (!support.isFinite() || !margin.isFinite() || !width.isFinite() || !height.isFinite() ||
        width < 1f || height < 1f || width > 4096f || height > 4096f) return null
    val extent = IntSize(width.toInt(), height.toInt())
    if (extent.width.toLong() * extent.height > 4_194_304L) return null
    // The exact field includes a half conversion. Exclude tiny radii whose far support
    // could overflow that original conversion before encoding the finite distance.
    if (radius > .000001f) {
        val expanded = max(radius, .001f) * 1.5286649465560913f
        if (sqrt(2f) * (margin + expanded) / expanded + 1f > 32752f) return null
    }
    val distanceRange = ceil(2f * (max(surfaceSize.width, surfaceSize.height) + margin + 1f))
    if (!distanceRange.isFinite() || distanceRange <= 0f) return null
    val sceneRect = IntRect(0, sceneTop, extent.width, sceneTop + extent.height)
    val consumerRect = IntRect(0, scene.bottom, extent.width, scene.bottom + extent.height)
    val consumerSize = IntSize(max(storageSize.width, consumerRect.right), max(storageSize.height, consumerRect.bottom))
    val sceneInputSize = IntSize(max(base.width, sceneRect.right), max(sceneTop, sceneRect.bottom))
    if (consumerSize.width > 4096 || consumerSize.height > 4096 ||
        sceneInputSize.width > 4096 || sceneInputSize.height > 4096 ||
        consumerSize.width.toLong() * consumerSize.height > 8_388_608L ||
        sceneInputSize.width.toLong() * sceneInputSize.height > 8_388_608L) return null
    val origin = Offset(-margin, -margin)
    val key = NativeControlLensFieldKey(surfaceSize, radius, sourceDensity, sourcePadding,
        support, extent, origin, distanceRange, sceneRect, consumerRect)
    val producer = obtainRuntimeShader("Glasmio.ControlLens.fieldPlane.producer",
        GlassSdfShader + NativeControlLensFieldProducerMain).asAndroidRuntimeShader()
    producer.setFloatUniform("size", surfaceSize.width, surfaceSize.height)
    producer.setFloatUniform("cornerRadii", corners)
    producer.setFloatUniform("fieldOrigin", origin.x, origin.y)
    producer.setFloatUniform("fieldRange", distanceRange)
    return NativeControlLensFieldPlane(key, producer, consumerSize)
}
