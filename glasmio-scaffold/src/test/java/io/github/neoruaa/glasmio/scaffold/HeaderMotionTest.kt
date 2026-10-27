package io.github.neoruaa.glasmio.scaffold

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class HeaderMotionTest {
    @Test fun integrationDoesNotDependOnDisplayRefreshRate() {
        fun sample(hz: Int): Pair<Float, Float> {
            val track = HeaderTrack(0f)
            track.target = 1f
            repeat(hz / 2) { track.advance(1f / hz) }
            return track.value to track.velocity
        }
        val a = sample(60)
        val b = sample(120)
        assertEquals(a.first, b.first, .00001f)
        assertEquals(a.second, b.second, .0001f)
    }

    @Test fun cancelAndImmediateRetargetPreservePresentationAndVelocity() {
        val track = HeaderTrack(44f)
        track.target = 154f
        track.advance(.12f)
        val position = track.value
        val velocity = track.velocity
        track.target = 44f
        assertEquals(position, track.value, 0f)
        assertEquals(velocity, track.velocity, 0f)
        repeat(240) { track.advance(1f / 120f) }
        assertEquals(44f, track.value, .001f)
        assertEquals(0f, track.velocity, .01f)
    }

    @Test fun pulseUsesLogicalPointLimitAndAllowsPhysicalOvershoot() {
        val pulse = HeaderPulse()
        assertEquals(1f, pulse.scale(44f, 16f, true), .00001f)
        pulse.trigger()
        var peak = 1f
        var anisotropic = false
        repeat(120) {
            pulse.advance(1f / 120f, false)
            val x = pulse.scale(44f, 16f, true)
            val y = pulse.scale(44f, 16f, false)
            peak = maxOf(peak, x)
            anisotropic = anisotropic || abs(x - y) > .01f
            assertEquals(x, pulse.scale(132f, 48f, true), .00001f)
        }
        assertTrue("Do not clamp spring overshoot to the pulse target", peak > 1.2f)
        assertTrue("Width and height must return on separate tracks", anisotropic)
        assertEquals(1f, pulse.scale(44f, 16f, true), .002f)
    }

    @Test fun pulseRetriggersWithoutResettingItsTwoChannels() {
        val pulse = HeaderPulse()
        pulse.trigger()
        pulse.advance(.08f, false)
        val before = pulse.scale(100f, 16f, true)
        pulse.trigger()
        assertEquals(before, pulse.scale(100f, 16f, true), 0f)
        repeat(240) { pulse.advance(1f / 120f, false) }
        assertEquals(1f, pulse.scale(100f, 16f, true), .001f)
    }
}
