package io.github.neoruaa.glasmio.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertMotionTest {
    @Test fun matchesRecoveredUIKitCriticalSpringSamples() {
        // Golden samples from the duration solver in iOS 27.0 UIKitCore (24A434).
        val samples = listOf(
            0.10f to 0.23606869f,
            0.25f to 0.67106754f,
            0.50f to 0.94447720f,
            0.75f to 0.99221090f,
        )
        samples.forEach { (time, progress) ->
            assertEquals(progress, IosAlertMotion.Timing.transform(time), 0.000001f)
        }
        assertEquals(404, IosAlertMotion.DurationMillis)
        assertEquals(1.2f, IosAlertMotion.InitialScale, 0f)
    }

    @Test fun completesWithoutOvershootOrResidualOpacity() {
        assertEquals(0f, IosAlertMotion.Timing.transform(0f), 0f)
        var previous = 0f
        for (frame in 1..404) {
            val progress = IosAlertMotion.Timing.transform(frame / 404f)
            assertTrue(progress >= previous && progress <= 1f)
            previous = progress
        }
        assertEquals(1f, previous, 0f)
    }
}
