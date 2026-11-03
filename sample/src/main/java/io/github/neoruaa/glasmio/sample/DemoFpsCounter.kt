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
import kotlin.math.roundToLong

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
            val display = window.decorView.display
            fun refreshPeriod(): Long {
                val rate = display?.refreshRate?.takeIf { it.isFinite() && it > 0f } ?: 60f
                return (1_000_000_000.0 / rate).roundToLong()
            }
            var framePeriod = refreshPeriod()
            // Both the listener and sampler run on this handler, keeping the ring thread-confined.
            val intervals = LongArray(2_400)
            val timestamps = LongArray(intervals.size)
            var firstSample = 0
            var sampleCount = 0
            var frames = 0
            val listener = Window.OnFrameMetricsAvailableListener { _, metrics, droppedReports ->
                if (active.get()) {
                    // Dropped reports still represent rendered frames, not dropped frames.
                    frames += 1 + droppedReports
                    val duration = activeFrameDurationNanos(
                        totalDuration = metrics.getMetric(FrameMetrics.TOTAL_DURATION),
                        deadline = metrics.getMetric(FrameMetrics.DEADLINE),
                        refreshPeriod = framePeriod,
                        firstDraw = metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L,
                    )
                    if (duration != null) {
                        val index = (firstSample + sampleCount) % intervals.size
                        intervals[index] = duration
                        timestamps[index] = System.nanoTime()
                        if (sampleCount < intervals.size) {
                            sampleCount++
                        } else {
                            firstSample = (firstSample + 1) % intervals.size
                        }
                    }
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
                    val nextPeriod = refreshPeriod()
                    if (nextPeriod != framePeriod) {
                        // A different app refresh rate starts a new, comparable frame window.
                        framePeriod = nextPeriod
                        firstSample = 0
                        sampleCount = 0
                    }
                    val cutoff = now - 10_000_000_000L
                    while (sampleCount > 0 && timestamps[firstSample] < cutoff) {
                        firstSample = (firstSample + 1) % intervals.size
                        sampleCount--
                    }
                    val measuredLow = onePercentLowFps(
                        LongArray(sampleCount) { intervals[(firstSample + it) % intervals.size] },
                    )
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

/** Normalize pipeline budget to one refresh interval, retaining all deadline overrun. */
internal fun activeFrameDurationNanos(
    totalDuration: Long,
    deadline: Long,
    refreshPeriod: Long,
    firstDraw: Boolean,
): Long? {
    if (firstDraw || totalDuration <= 0L || deadline <= 0L || refreshPeriod <= 0L) return null
    // Vsync arrival gaps include intentional idle time. TOTAL_DURATION includes UI delay
    // and GPU completion; DEADLINE accounts for the system's overlapping pipeline budget.
    return refreshPeriod + (totalDuration - deadline).coerceAtLeast(0L)
}

/** Reciprocal mean of the slowest one percent, with at least one hundred real samples. */
internal fun onePercentLowFps(durations: LongArray): Int {
    if (durations.size < 100) return -1
    durations.sort()
    val slowCount = (durations.size + 99) / 100
    var slowTotal = 0.0
    for (index in durations.size - slowCount until durations.size) slowTotal += durations[index]
    return (1_000_000_000.0 * slowCount / slowTotal).roundToInt()
}
