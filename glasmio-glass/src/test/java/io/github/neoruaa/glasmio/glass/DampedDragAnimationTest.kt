package io.github.neoruaa.glasmio.glass

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class DampedDragAnimationTest {

    private class ManualDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }

        fun drain() {
            while (queue.isNotEmpty()) {
                queue.removeFirst().run()
            }
        }
    }

    @Test
    fun programmaticAnimateWithoutPressDoesNotQueueIdleReleaseThatClobbersNextPress() {
        val dispatcher = ManualDispatcher()
        var nowNanos = 0L
        val frameClock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
                nowNanos += 16_000_000L
                return onFrame(nowNanos)
            }
        }
        val animScope = CoroutineScope(dispatcher + SupervisorJob() + frameClock)
        val timeSource = TestTimeSource()
        try {
            val anim = DampedDragAnimation(
                animationScope = animScope,
                initialValue = 0f,
                valueRange = 0f..3f,
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 1.4f,
                onDragStarted = {},
                onDragStopped = {},
                onDrag = { _, _ -> },
                lensStyle = NativeControlLensStyle.TabBarIndicator,
                timeSource = timeSource,
            )

            // Programmatic route sync when resting must NOT schedule a lingering release job.
            anim.animateToValue(2f, animatePress = false)
            repeat(60) {
                timeSource += 16.milliseconds
                dispatcher.drain()
            }
            assertEquals(2f, anim.value, 0.01f)
            assertEquals(0f, anim.pressProgress, 0.001f)

            // Immediate subsequent press must lift cleanly to 1f.
            anim.press()
            repeat(40) {
                timeSource += 16.milliseconds
                dispatcher.drain()
            }
            assertTrue("Expected lens pressProgress near 1f, got ${anim.pressProgress}", anim.pressProgress > 0.99f)
        } finally {
            animScope.cancel()
        }
    }
}
