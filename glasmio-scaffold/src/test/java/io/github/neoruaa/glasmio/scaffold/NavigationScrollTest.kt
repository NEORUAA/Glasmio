package io.github.neoruaa.glasmio.scaffold

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationScrollTest {
    private fun newState(
        range: Float = 300f,
        compact: Boolean = false,
        activeDistance: Float = 64f,
        atStart: () -> Boolean = { false },
        distanceToStart: () -> Float = { Float.POSITIVE_INFINITY },
        atEnd: () -> Boolean = { false },
    ) = GlasmioNavigationScrollState(compact).apply {
        bind(true, activeDistance, { range }, distanceToStart, atStart, atEnd)
    }

    private fun drag(state: GlasmioNavigationScrollState, distance: Float) {
        val delta = Offset(0f, -distance)
        assertEquals(Offset.Zero, state.nestedScrollConnection.onPreScroll(delta, NestedScrollSource.UserInput))
        assertEquals(Offset.Zero, state.nestedScrollConnection.onPostScroll(
            delta, Offset.Zero, NestedScrollSource.UserInput))
    }

    @Test fun nativeDistanceAndEligibilityRemainInPhysicalPoints() {
        for (density in listOf(1f, 2.75f)) for (eligible in listOf(false, true)) {
            val state = newState(range = 96f * density - if (eligible) 0f else .01f,
                activeDistance = 64f * density)
            drag(state, 63f * density)
            assertFalse(state.isCompact)
            assertEquals(if (eligible) 63f / 64f else 0f, state.progress, .00001f)
            drag(state, density)
            assertEquals(eligible, state.isCompact)
        }
    }

    @Test fun onlyAUserFlingCanContinueTheScrollAwayDriver() = runBlocking {
        val state = newState()
        val connection = state.nestedScrollConnection
        connection.onPostScroll(Offset(0f, -100f), Offset.Zero, NestedScrollSource.SideEffect)
        assertEquals(0f, state.progress, 0f)
        drag(state, 16f)
        connection.onPostScroll(Offset(0f, -100f), Offset.Zero, NestedScrollSource.SideEffect)
        assertEquals(.25f, state.progress, 0f)
        assertEquals(Velocity.Zero, connection.onPreFling(Velocity(0f, -2000f)))
        assertFalse(state.isTracking)
        connection.onPostScroll(Offset(0f, -48f), Offset.Zero, NestedScrollSource.SideEffect)
        assertTrue(state.isCompact)
        assertEquals(Velocity.Zero, connection.onPostFling(Velocity(0f, -2000f), Velocity.Zero))
        assertTrue(state.isCompact)

        val partial = newState()
        drag(partial, 32f)
        partial.finishGesture()
        assertEquals(0f, partial.progress, 0f)
        assertFalse(partial.isCompact)
    }

    @Test fun interiorReverseKeepsCompactUntilTheStartIsStrictlyCrossed() {
        var atStart = false
        var distance = 20f
        val state = newState(compact = true, atStart = { atStart }, distanceToStart = { distance })
        val connection = state.nestedScrollConnection
        connection.onPreScroll(Offset(0f, 20f), NestedScrollSource.UserInput)
        distance = 0f
        atStart = true
        connection.onPostScroll(Offset(0f, 20f), Offset.Zero, NestedScrollSource.UserInput)
        assertTrue("Reaching the edge exactly is not an overscroll", state.isCompact)
        connection.onPreScroll(Offset(0f, 1f), NestedScrollSource.UserInput)
        connection.onPostScroll(Offset.Zero, Offset(0f, 1f), NestedScrollSource.UserInput)
        assertFalse(state.isCompact)
        atStart = false
        distance = 64f
        drag(state, 64f)
        assertFalse("A reset interaction cannot reactivate inside the same gesture", state.isCompact)
        state.finishGesture()
        assertFalse(state.isCompact)
    }

    @Test fun anUnconsumedStartCrossingCanRevealAcrossMultipleLazyItems() {
        var atStart = false
        val state = newState(compact = true, atStart = { atStart })
        val connection = state.nestedScrollConnection
        connection.onPreScroll(Offset(0f, 400f), NestedScrollSource.UserInput)
        atStart = true
        connection.onPostScroll(Offset(0f, 300f), Offset(0f, 100f), NestedScrollSource.UserInput)
        assertFalse("Actual unconsumed top motion must work when the initial offset was not measurable", state.isCompact)
    }

    @Test fun aFurtherAwayEdgeGestureResetsCompactWithoutConsumingTheBounce() {
        for (range in listOf(300f, 20f)) {
            val state = newState(range = range, compact = true, atEnd = { true })
            val connection = state.nestedScrollConnection
            assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, -12f), NestedScrollSource.UserInput))
            assertEquals(Offset.Zero, connection.onPostScroll(
                Offset.Zero, Offset(0f, -12f), NestedScrollSource.UserInput))
            assertFalse("An edge reset precedes new-interaction eligibility", state.isCompact)
        }
    }

    @Test fun changingHostsCancelsTheOldSessionAndExplicitExpansionKeepsRoutesExternal() {
        val state = newState()
        drag(state, 24f)
        state.finishGesture()
        state.bind(true, 64f, { 500f }, { Float.POSITIVE_INFINITY }, { false }, { false })
        state.nestedScrollConnection.onPostScroll(
            Offset(0f, -200f), Offset.Zero, NestedScrollSource.SideEffect)
        assertEquals(0f, state.progress, 0f)
        drag(state, 64f)
        assertTrue(state.isCompact)
        state.bind(false, 64f, { 500f }, { Float.POSITIVE_INFINITY }, { false }, { false })
        drag(state, 100f)
        assertTrue("Disabling a hidden page's driver preserves the host's compact target", state.isCompact)
        state.expand()
        assertFalse(state.isCompact)
    }
}
