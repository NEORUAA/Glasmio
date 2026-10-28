package io.github.neoruaa.glasmio.scaffold

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import io.github.neoruaa.glasmio.scaffold.navigation.IosSwipeBackArbitrator
import io.github.neoruaa.glasmio.scaffold.navigation.IosSwipeBackRelease
import io.github.neoruaa.glasmio.scaffold.navigation.IosNavigationEasing
import io.github.neoruaa.glasmio.scaffold.navigation.IosNavigationFloatSpring
import io.github.neoruaa.glasmio.scaffold.navigation.iosNavigationFraction
import io.github.neoruaa.glasmio.scaffold.navigation.iosNavigationParallaxOffset

class HeaderMotionTest {
    @Test fun navigationProjectionAndChildOwnershipFollowNativeRules() {
        val child = IosSwipeBackArbitrator(10f)
        assertEquals(0, child.observe(4f, 0f, false))
        assertEquals(-1, child.observe(12f, 0f, true))
        assertEquals("A child cannot hand its drag to the page at its scroll boundary", -1,
            child.observe(100f, 0f, false))
        val vertical = IosSwipeBackArbitrator(10f)
        assertEquals(-1, vertical.observe(2f, 20f, false))
        assertEquals(-1, vertical.observe(100f, 0f, false))
        val wrongDirection = IosSwipeBackArbitrator(10f)
        assertEquals(-1, wrongDirection.observe(-20f, 0f, false))
        assertEquals(-1, wrongDirection.observe(100f, 0f, false))
        val page = IosSwipeBackArbitrator(10f)
        assertEquals(0, page.observe(8f, 1f, false))
        assertEquals(1, page.observe(12f, 0f, false))
        assertEquals(10f, page.initialTravel, .001f)
        fun resting(progress: Float, width: Float): Boolean = IosSwipeBackRelease(width).let {
            repeat(5) { sample -> it.sample(progress, sample * 20L) }
            it.shouldCommit(progress)
        }
        assertFalse(resting(.49f, 320f))
        assertTrue(resting(.51f, 320f))
        assertFalse(resting(.30f, 600f))
        assertTrue("The native distance threshold is capped at 187.5 points", resting(.32f, 600f))
        val reversed = IosSwipeBackRelease(400f)
        listOf(.3f, .4f, .45f, .4f, .25f, .1f).forEachIndexed { index, progress ->
            reversed.sample(progress, index * 100L)
        }
        assertFalse("A reversed drag cancels even after reaching the midpoint", reversed.shouldCommit(.1f))
        assertFalse(IosSwipeBackRelease(400f).shouldCommit(.8f))
    }

    @Test fun navigationSeekInversionPreservesPhysicalPositionAcrossInterruption() {
        assertEquals("UIKit rounds parallax in points before mapping to pixels", 363,
            iosNavigationParallaxOffset(1206, 3f))
        assertEquals(121, iosNavigationParallaxOffset(402, 1f))
        assertTrue("The coordinator duration cannot truncate the physical spring",
            IosNavigationEasing.transform(1f) < 1f)
        var previous = -1f
        repeat(101) { index ->
            val fraction = index / 100f
            val physical = IosNavigationEasing.transform(fraction)
            assertTrue(physical >= previous)
            assertEquals(fraction, iosNavigationFraction(physical), .0001f)
            previous = physical
        }
        val spring = IosNavigationFloatSpring
        val duration = spring.getDurationNanos(1220f, 0f, 0f)
        val entering = spring.getValueFromNanos(48_000_000L, 1220f, 0f, 0f)
        val desired = entering + 150.5f
        val seek = iosNavigationFraction(1f - desired / 1220f, duration)
        val actual = spring.getValueFromNanos((seek.toDouble() * duration).toLong(), 1220f, 0f, 0f)
        assertEquals("Inverse seeking must preserve pixel travel between millisecond boundaries", desired, actual, .001f)
        assertTrue("The default body must evaluate sub-millisecond time without truncation",
            abs(spring.getValueFromNanos(31_625_562L, 1220f, 0f, 0f) -
                spring.getValueFromNanos(31_000_000L, 1220f, 0f, 0f)) > 5f)
        assertEquals("Retargeted springs retain their initial velocity", 180f,
            spring.getVelocityFromNanos(0L, 100f, 0f, 180f), 0f)
        assertTrue("The physical spring retains its full settle tail", duration > 350_000_000L)
    }

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
