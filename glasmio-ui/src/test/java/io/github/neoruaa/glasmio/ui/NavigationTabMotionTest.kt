package io.github.neoruaa.glasmio.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTabMotionTest {
    @Test
    fun centerAndWidthUseSeparateSpringsAndRetainVelocityOnRelease() {
        val bottom = motion()
        val top = motion(isTopBar = true)
        for (motion in listOf(bottom, top)) {
            motion.moveTo(230f, 108f, tracking = true)
            motion.stepFrames(4)
            assertTrue((motion.centerPx - 50f) / 180f > (motion.widthPx - 78f) / 30f)
        }
        assertEquals(bottom.centerPx, top.centerPx, .001f)
        assertTrue(bottom.widthPx > top.widthPx)

        val centerAtRelease = bottom.centerPx
        val widthAtRelease = bottom.widthPx
        bottom.moveTo(230f, 108f)
        assertEquals(centerAtRelease, bottom.centerPx, 0f)
        assertEquals(widthAtRelease, bottom.widthPx, 0f)
        bottom.stepFrames(1)
        assertTrue("The new spring inherits movement toward the release target", bottom.centerPx - centerAtRelease > 5f)
        bottom.stepFrames(120)
        assertEquals(230f, bottom.centerPx, 0f)
        assertEquals(108f, bottom.widthPx, 0f)
    }

    @Test
    fun lensRemainsLiftedUntilItCatchesUpAndSurvivesAnInterruptedRelease() {
        val motion = motion()
        motion.press()
        motion.stepFrames(42)
        assertEquals(1f, motion.pressProgress, .001f)

        motion.moveTo(230f, 78f)
        motion.release()
        motion.stepFrames(8)
        assertTrue(motion.centerPx < 222f)
        assertEquals(1f, motion.pressProgress, .001f)

        motion.stepFrames(9)
        assertTrue(motion.pressProgress < .99f)
        motion.press()
        motion.moveTo(50f, 78f)
        motion.stepFrames(42)
        assertEquals(1f, motion.pressProgress, .001f)
        motion.release()
        motion.stepFrames(120)
        assertEquals(50f, motion.centerPx, 0f)
        assertEquals(0f, motion.pressProgress, 0f)
        assertEquals(1f, motion.scaleX, 0f)
        assertEquals(1f, motion.scaleY, 0f)
        assertEquals(0f, motion.flexOffsetPx, 0f)
    }

    @Test
    fun movementStretchesAndRecoilsInBothDirections() {
        val forward = motion()
        val reverse = motion()
        reverse.snapTo(230f, 78f)
        forward.moveTo(230f, 78f)
        reverse.moveTo(50f, 78f)
        var stretched = false
        var recoiled = false
        repeat(60) {
            forward.stepFrames(1)
            reverse.stepFrames(1)
            assertEquals(forward.scaleX, reverse.scaleX, .001f)
            assertEquals(forward.scaleY, reverse.scaleY, .001f)
            assertEquals(forward.flexOffsetPx, -reverse.flexOffsetPx, .01f)
            stretched = stretched || forward.scaleX > 1.03f && forward.scaleY < 1f
            recoiled = recoiled || forward.scaleX < .97f && forward.scaleY > 1f
        }
        assertTrue("The lens widens and flattens as it accelerates", stretched)
        assertTrue("The lens narrows and grows taller when coming to rest", recoiled)
    }

    private fun motion(isTopBar: Boolean = false) = NavigationTabMotion(
        animationScope = CoroutineScope(Job().apply { cancel() }),
        isTopBar = isTopBar,
        density = 1f,
        initialCenterPx = 50f,
        initialWidthPx = 78f,
        heightPx = 54f,
        dragCoordinateSpace = { null },
        onDragStarted = {},
        onDragStopped = {},
        onDragCancelled = {},
        onDrag = { _, _ -> },
    )

    private fun NavigationTabMotion.stepFrames(frames: Int) {
        repeat(frames) { advance(1f / 60f) }
    }
}
