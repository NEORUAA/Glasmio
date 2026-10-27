package io.github.neoruaa.glasmio.sample

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation3.runtime.NavEntry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.scaffold.*
import io.github.neoruaa.glasmio.scaffold.navigation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the maintained NavDisplay state machine with AndroidX back-dispatch events. */
@RunWith(AndroidJUnit4::class)
class NavigationClockTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun realSeekClockPublishesTrackingCancelAndCommit() {
        val stack = mutableStateListOf("A")
        val motion = GlasmioNavHeaderMotion("A")
        rule.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                GlasmioNavigationScaffold(motion, header = { entry ->
                    GlasmioHeaderSpec(entry.toString(), trailing = listOf(GlasmioHeaderGroup("primary", listOf(
                        GlasmioHeaderAction("same.action", "moon", "Stable action", {}) { Text("M") },
                    ))))
                }) {
                    GlasmioNavDisplay(stack, motion, onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                        entryProvider = { key -> NavEntry(key = key, contentKey = key) { Box(Modifier.fillMaxSize().background(Color.White)) { Text(key) } } })
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        val anchor = rule.onNodeWithContentDescription("Stable action").fetchSemanticsNode().boundsInRoot
        rule.runOnUiThread { stack.add("B") }
        rule.mainClock.advanceTimeBy(128)
        rule.runOnUiThread {
            assertEquals(GlasmioNavigationPhase.Settling, motion.frame.value.phase)
            assertTrue("Publish the real SeekableTransitionState clock", motion.frame.value.playTimeNanos > 0)
        }
        val moving = rule.onNodeWithContentDescription("Stable action").fetchSemanticsNode().boundsInRoot
        assertEquals(anchor.center.x, moving.center.x, .5f)
        rule.mainClock.advanceTimeBy(700)
        rule.runOnUiThread {
            assertEquals(GlasmioNavigationPhase.Idle, motion.frame.value.phase)
            rule.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
            rule.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 100f, .6f, BackEventCompat.EDGE_LEFT))
        }
        rule.mainClock.advanceTimeBy(64)
        rule.runOnUiThread {
            assertEquals(GlasmioNavigationPhase.Tracking, motion.frame.value.phase)
            assertEquals(.6f, motion.frame.value.gestureProgress, .001f)
            rule.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
        }
        rule.mainClock.advanceTimeBy(700)
        rule.runOnUiThread {
            assertEquals(listOf("A", "B"), stack.toList())
            assertEquals(GlasmioNavigationPhase.Idle, motion.frame.value.phase)
            assertEquals("B", motion.frame.value.toEntryKey)
            rule.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
            rule.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(180f, 100f, .8f, BackEventCompat.EDGE_LEFT))
        }
        rule.mainClock.advanceTimeBy(64)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(1000)
        rule.runOnUiThread {
            assertEquals(listOf("A"), stack.toList())
            assertEquals(GlasmioNavigationPhase.Idle, motion.frame.value.phase)
            assertEquals("A", motion.frame.value.toEntryKey)
        }
        rule.onAllNodes(hasContentDescription("Stable action") and hasClickAction()).assertCountEquals(1)
    }
}
