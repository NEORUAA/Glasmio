package io.github.neoruaa.glasmio.glass

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.HardwareBufferRenderer
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import java.time.Duration
import java.util.concurrent.Executor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val CaptureTimeoutMillis = 2_000L
private val CaptureFenceTimeout = Duration.ofMillis(CaptureTimeoutMillis)
private val CaptureCallbackExecutor = Executor { it.run() }

/**
 * Copies one already-recorded SDR viewport to a transparent sRGB image at the same pixel size.
 * The caller owns source validity and must check that the source is still current after capture.
 */
@MainThread
@RequiresApi(34)
internal suspend fun captureDetachedBackdrop(
    layer: GraphicsLayer,
    density: Density,
    layoutDirection: LayoutDirection,
    size: IntSize,
): ImageBitmap? {
    if (size.width <= 0 || size.height <= 0 || layer.isReleased || layer.size != size) return null
    currentCoroutineContext().ensureActive()

    try {
        val root = RenderNode("Glasmio backdrop capture")
        root.setPosition(0, 0, size.width, size.height)
        val canvas = root.beginRecording(size.width, size.height)
        try {
            canvas.drawColor(Color.TRANSPARENT, BlendMode.CLEAR)
            CanvasDrawScope().draw(
                density,
                layoutDirection,
                Canvas(canvas),
                Size(size.width.toFloat(), size.height.toFloat()),
            ) {
                drawLayer(layer)
            }
        } finally {
            root.endRecording()
        }

        val colorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        val buffer = HardwareBuffer.create(
            size.width,
            size.height,
            HardwareBuffer.RGBA_8888,
            1,
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE,
        )
        var renderer: HardwareBufferRenderer? = null
        // Own the fence even if cancellation races a queued or delivered result.
        val results = Channel<HardwareBufferRenderer.RenderResult>(
            capacity = 1,
            onUndeliveredElement = { it.fence.close() },
        )
        try {
            val captureRenderer = HardwareBufferRenderer(buffer)
            renderer = captureRenderer
            captureRenderer.setContentRoot(root)
            captureRenderer.obtainRenderRequest().setColorSpace(colorSpace)
                .draw(CaptureCallbackExecutor) { result ->
                    if (results.trySend(result).isFailure) result.fence.close()
                }

            return withContext(Dispatchers.IO) {
                withTimeoutOrNull(CaptureTimeoutMillis) {
                    val result = results.receive()
                    result.fence.use { fence ->
                        if (result.status != HardwareBufferRenderer.RenderResult.SUCCESS ||
                            (fence.isValid && !fence.await(CaptureFenceTimeout))
                        ) return@use null
                        currentCoroutineContext().ensureActive()
                        Bitmap.wrapHardwareBuffer(buffer, colorSpace)?.asImageBitmap()
                    }
                }
            }
        } finally {
            results.cancel()
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    renderer?.close()
                } finally {
                    // The returned Bitmap holds its own reference to the immutable buffer.
                    buffer.close()
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: RuntimeException) {
        return null
    }
}
