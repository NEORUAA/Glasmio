package io.github.neoruaa.glasmio.scaffold

import androidx.compose.animation.core.SpringSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class HeaderCollapseTest {
    private val enter = 56f
    private val exit = 24f

    @Test fun enterBreakpointNeedsADeeperScrollThanExit() {
        assertFalse(nextCollapsed(false, 0, 55, enter, exit))
        assertTrue(nextCollapsed(false, 0, 56, enter, exit))
        assertTrue("A collapsed header stays collapsed inside the hysteresis band", nextCollapsed(true, 0, 25, enter, exit))
        assertFalse("Expanding needs the offset back above the exit breakpoint", nextCollapsed(true, 0, 24, enter, exit))
    }

    @Test fun anyLaterItemKeepsTheTitleCollapsed() {
        assertTrue(nextCollapsed(false, 1, 0, enter, exit))
        assertTrue(nextCollapsed(true, 1, 0, enter, exit))
    }

    @Test fun restingInsideTheBandNeverRetriggersTheMotion() {
        var collapsed = false
        collapsed = nextCollapsed(collapsed, 0, 60, enter, exit)
        assertTrue(collapsed)
        // Scrolling back into the band must not expand: the spring already played to collapsed.
        collapsed = nextCollapsed(collapsed, 0, 40, enter, exit)
        assertTrue(collapsed)
        collapsed = nextCollapsed(collapsed, 0, 25, enter, exit)
        assertTrue(collapsed)
        collapsed = nextCollapsed(collapsed, 0, 24, enter, exit)
        assertFalse(collapsed)
        // Once expanded, the band alone cannot collapse it again either.
        collapsed = nextCollapsed(collapsed, 0, 40, enter, exit)
        assertFalse(collapsed)
        collapsed = nextCollapsed(collapsed, 0, 56, enter, exit)
        assertTrue(collapsed)
    }

    @Test fun springMatchesTheIosNavigationSpring() {
        val spec = GlasmioHeaderCollapseDefaults.Spring as SpringSpec<Float>
        assertEquals(.75f, spec.dampingRatio, .000001f)
        val omega = (2.0 * PI / .416).toFloat()
        assertEquals(omega * omega, spec.stiffness, .01f)
    }
}
