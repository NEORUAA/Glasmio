package io.github.neoruaa.glasmio.sample

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.neoruaa.glasmio.core.IosTypography
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/** Counts rendered Activity-window frames without requesting additional animation frames. */
@Composable
internal fun DemoFpsCounter(modifier: Modifier = Modifier) {
    val window = LocalActivity.current?.window ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var fps by remember(window) { mutableIntStateOf(0) }
    var lowFps by remember(window) { mutableIntStateOf(-1) }

    DisposableEffect(window, lifecycle) {
        val mainHandler = Handler(Looper.getMainLooper())
        var stopMonitoring: (() -> Unit)? = null

        fun startMonitoring() {
            if (stopMonitoring != null) return
            fps = 0
            lowFps = -1
            val active = AtomicBoolean(true)
            val callbackThread = HandlerThread("Glasmio FPS").apply { start() }
            val callbackHandler = Handler(callbackThread.looper)
            // Both the listener and sampler run on this handler, keeping the ring thread-confined.
            val intervals = LongArray(2_400)
            val timestamps = LongArray(intervals.size)
            var firstSample = 0
            var sampleCount = 0
            var frames = 0
            var previousVsync = 0L
            val listener = Window.OnFrameMetricsAvailableListener { _, metrics, droppedReports ->
                if (active.get()) {
                    // Dropped reports still represent rendered frames, not dropped frames.
                    frames += 1 + droppedReports
                    val vsync = metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP)
                    if (previousVsync > 0L && vsync > previousVsync && droppedReports == 0) {
                        val index = (firstSample + sampleCount) % intervals.size
                        intervals[index] = vsync - previousVsync
                        timestamps[index] = vsync
                        if (sampleCount < intervals.size) {
                            sampleCount++
                        } else {
                            firstSample = (firstSample + 1) % intervals.size
                        }
                    }
                    // A missing report breaks adjacency; never treat its gap as a slow frame.
                    previousVsync = vsync
                }
            }
            var sampleStarted = System.nanoTime()
            val update = object : Runnable {
                override fun run() {
                    if (!active.get()) return
                    val now = System.nanoTime()
                    val elapsed = now - sampleStarted
                    val measuredFps = (frames * 1_000_000_000.0 / elapsed).roundToInt()
                    frames = 0
                    sampleStarted = now
                    val cutoff = now - 10_000_000_000L
                    while (sampleCount > 0 && timestamps[firstSample] < cutoff) {
                        firstSample = (firstSample + 1) % intervals.size
                        sampleCount--
                    }
                    val measuredLow = if (sampleCount >= 100) {
                        val sorted = LongArray(sampleCount) { intervals[(firstSample + it) % intervals.size] }
                        sorted.sort()
                        val slowCount = (sampleCount + 99) / 100
                        var slowTotal = 0L
                        for (index in sorted.size - slowCount until sorted.size) slowTotal += sorted[index]
                        (1_000_000_000.0 * slowCount / slowTotal).roundToInt()
                    } else {
                        -1
                    }
                    mainHandler.post {
                        if (active.get()) {
                            fps = measuredFps
                            lowFps = measuredLow
                        }
                    }
                    callbackHandler.postDelayed(this, 1_000L)
                }
            }
            window.addOnFrameMetricsAvailableListener(listener, callbackHandler)
            callbackHandler.postDelayed(update, 1_000L)
            stopMonitoring = {
                active.set(false)
                callbackHandler.removeCallbacks(update)
                mainHandler.removeCallbacksAndMessages(null)
                window.removeOnFrameMetricsAvailableListener(listener)
                callbackThread.quitSafely()
            }
        }

        fun stop() {
            stopMonitoring?.invoke()
            stopMonitoring = null
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> startMonitoring()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_DESTROY -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) startMonitoring()
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
        }
    }

    Text(
        text = "FPS $fps · 1% Low ${if (lowFps >= 0) lowFps.toString() else "—"}",
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = Color.White,
        style = IosTypography.caption1.copy(fontFeatureSettings = "tnum"),
        maxLines = 1,
    )
}
