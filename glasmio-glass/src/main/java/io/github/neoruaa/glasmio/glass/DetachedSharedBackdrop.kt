package io.github.neoruaa.glasmio.glass

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import io.github.neoruaa.glasmio.glass.render.Backdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Owns the full and bottom textures of a single cross-window consumer. */
internal class DetachedSharedBackdrop(
    private val scope: CoroutineScope,
    private val sourceView: View,
    private val enabled: () -> Boolean,
) {
    // Read at the draw node, outside nested GraphicsLayer recordings.
    private val publication = mutableIntStateOf(0)
    private var sourceGeneration = 0L
    private var sourceChangedAt = SystemClock.uptimeMillis()
    private var consumerView: View? = null
    private var job: Job? = null
    private var active = false
    private var eligible = false
    private val consumerAttachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit

        override fun onViewDetachedFromWindow(view: View) {
            view.removeOnAttachStateChangeListener(this)
            consumerView = null
            clearRequests()
        }
    }

    val full = Slot()
    val bottom = Slot()

    fun observeConsumer(view: View) {
        // The predicate may read Compose state changed independently of the source recording.
        val allowed = enabled()
        publication.intValue
        if (consumerView !== view) {
            consumerView?.removeOnAttachStateChangeListener(consumerAttachment)
            consumerView = view
            view.addOnAttachStateChangeListener(consumerAttachment)
            clearRequests()
        }
        if (!allowed && eligible) clearRequests()
        eligible = allowed
    }

    fun attach(source: Backdrop): () -> Unit {
        active = true
        val tree = sourceView.viewTreeObserver
        val drawing = ViewTreeObserver.OnPreDrawListener {
            invalidateSource()
            true
        }
        val attachment = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = invalidateSource()
            override fun onViewDetachedFromWindow(view: View) = invalidateSource()
        }
        tree.addOnPreDrawListener(drawing)
        sourceView.addOnAttachStateChangeListener(attachment)
        val stopObserving = source.observeSourceChanges(::invalidateSource)
        return {
            active = false
            stopObserving()
            if (tree.isAlive) tree.removeOnPreDrawListener(drawing)
            else sourceView.viewTreeObserver.removeOnPreDrawListener(drawing)
            sourceView.removeOnAttachStateChangeListener(attachment)
            consumerView?.removeOnAttachStateChangeListener(consumerAttachment)
            job?.cancel()
            consumerView = null
            clearRequests()
        }
    }

    fun afterCapture(release: () -> Unit) {
        val pending = job
        if (pending == null) release()
        else pending.invokeOnCompletion { Handler(Looper.getMainLooper()).post { release() } }
    }

    private fun clearRequests() {
        sourceGeneration++
        sourceChangedAt = SystemClock.uptimeMillis()
        eligible = false
        full.forgetViewport()
        bottom.forgetViewport()
    }

    private fun invalidateSource() {
        sourceGeneration++
        sourceChangedAt = SystemClock.uptimeMillis()
        val fullChanged = full.clearImage()
        val bottomChanged = bottom.clearImage()
        if (fullChanged || bottomChanged) publish()
        scheduleCapture()
    }

    private fun publish() {
        Snapshot.withoutReadObservation { publication.intValue++ }
    }

    private fun supportsCapture(): Boolean {
        if (!active || !eligible || Build.VERSION.SDK_INT < 34) return false
        val consumer = consumerView ?: return false
        val sourceDisplayId = sourceView.display?.displayId ?: return false
        return consumer.rootView !== sourceView.rootView && consumer.display?.displayId == sourceDisplayId &&
            sourceView.isDefaultHardwareWindow() && consumer.isDefaultHardwareWindow() &&
            Snapshot.withoutReadObservation(enabled)
    }

    private fun scheduleCapture() {
        if (job != null || !supportsCapture() || (!full.needsCapture && !bottom.needsCapture)) return
        job = scope.launch {
            try {
                // Keep one pending request while the source is busy, then capture its latest generation.
                delay(QuietPeriodMs)
                while (supportsCapture() && (full.needsCapture || bottom.needsCapture)) {
                    val remaining = QuietPeriodMs - (SystemClock.uptimeMillis() - sourceChangedAt)
                    if (remaining > 0L) {
                        delay(remaining)
                    } else {
                        full.capture()
                        bottom.capture()
                    }
                }
            } finally {
                job = null
            }
        }
    }

    data class Viewport(
        val pixels: IntSize,
        val sourceSize: IntSize,
        val captureTop: Int,
        val frame: Int?,
        val density: Density,
        val direction: LayoutDirection,
    )

    private data class Request(val viewport: Viewport, val layer: GraphicsLayer, val consumer: View)

    inner class Slot {
        private var request: Request? = null
        private var attempted: Request? = null
        private var attemptedGeneration = -1L
        private var captured: ImageBitmap? = null
        private var capturedRequest: Request? = null
        var recordedImage: ImageBitmap? = null

        val needsCapture: Boolean
            get() {
                val target = request ?: return false
                return capturedRequest != target && (attempted != target || attemptedGeneration != sourceGeneration) &&
                    target.consumer === consumerView && !target.layer.isReleased &&
                    target.layer.size == target.viewport.pixels && recordedImage == null
            }

        fun image(viewport: Viewport, layer: GraphicsLayer): ImageBitmap? {
            val consumer = consumerView
            if (consumer == null || !supportsCapture() ||
                viewport.pixels.width.toLong() * viewport.pixels.height > MaxViewportPixels
            ) {
                request = null
                clearImage()
                return null
            }
            val target = Request(viewport, layer, consumer)
            request = target
            if (capturedRequest == target) return captured
            clearImage()
            return null
        }

        fun requestWhenLive() = scheduleCapture()

        suspend fun capture() {
            if (Build.VERSION.SDK_INT < 34 || !supportsCapture() || !needsCapture ||
                SystemClock.uptimeMillis() - sourceChangedAt < QuietPeriodMs
            ) return
            val target = request ?: return
            val viewport = target.viewport
            val generation = sourceGeneration
            attempted = target
            attemptedGeneration = generation
            val image = captureDetachedBackdrop(target.layer, viewport.density, viewport.direction, viewport.pixels)
            if (image != null && generation == sourceGeneration && request == target &&
                target.consumer === consumerView && supportsCapture()
            ) {
                captured = image
                capturedRequest = target
                publish()
            }
        }

        fun clearImage(): Boolean {
            if (captured == null) return false
            captured = null
            capturedRequest = null
            return true
        }

        fun forgetViewport() {
            clearImage()
            request = null
            attempted = null
        }
    }
}

internal fun View.isDefaultHardwareWindow(): Boolean =
    isAttachedToWindow && isHardwareAccelerated &&
        (rootView.layoutParams as? WindowManager.LayoutParams)?.colorMode == ActivityInfo.COLOR_MODE_DEFAULT

private const val QuietPeriodMs = 64L
private const val MaxViewportPixels = 2_000_000L
