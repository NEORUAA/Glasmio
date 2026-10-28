package io.github.neoruaa.glasmio.sample

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
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
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                GlasmioNavigationScaffold(motion, header = { entry ->
                    GlasmioHeaderSpec("Title $entry", trailing = listOf(GlasmioHeaderGroup("primary", listOf(
                        GlasmioHeaderAction("same.action", "moon", "Stable action", {}) { Text("M") },
                    ))))
                }) {
                    GlasmioNavDisplay(stack, motion, onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                        entryProvider = { key -> NavEntry(key = key, contentKey = key) {
                            Box(Modifier.fillMaxSize().background(Color.White)) {
                                Text("Body $key", Modifier.padding(top = 140.dp))
                                Box(Modifier.align(Alignment.Center).size(2.dp).testTag("page-marker-$key"))
                                GlasmioPageTitle(key, Modifier.testTag("page-title-$key"))
                            }
                        } })
                }
            }
        }
        rule.waitForIdle()
        fun titleBounds(key: String) = rule.onNodeWithText("Title $key").fetchSemanticsNode().boundsInRoot
        fun assertTitleFollowsPage(key: String) {
            val marker = rule.onNodeWithTag("page-marker-$key").fetchSemanticsNode().boundsInRoot
            assertEquals("The title must use its own page's transform", marker.center.x, titleBounds(key).center.x, 1f)
            rule.onAllNodesWithText("Title $key").assertCountEquals(1)
        }
        val originalTitle = titleBounds("A")
        assertTitleFollowsPage("A")
        rule.mainClock.autoAdvance = false
        val anchor = rule.onNodeWithContentDescription("Stable action").fetchSemanticsNode().boundsInRoot
        rule.runOnUiThread { stack.add("B") }
        rule.mainClock.advanceTimeBy(256)
        rule.runOnUiThread {
            assertEquals(GlasmioNavigationPhase.Settling, motion.frame.value.phase)
            assertTrue("Publish the real SeekableTransitionState clock", motion.frame.value.playTimeNanos > 0)
        }
        val moving = rule.onNodeWithContentDescription("Stable action").fetchSemanticsNode().boundsInRoot
        assertEquals(anchor.center.x, moving.center.x, .5f)
        assertTitleFollowsPage("A")
        assertTitleFollowsPage("B")
        assertTrue("The source title must depart with its page", titleBounds("A").center.x < originalTitle.center.x - 4f)
        assertTrue("The target title must enter with its page", titleBounds("B").center.x > originalTitle.center.x + 4f)
        rule.mainClock.advanceTimeBy(700)
        val settledTitle = titleBounds("B")
        assertTitleFollowsPage("B")
        rule.runOnUiThread {
            assertEquals(GlasmioNavigationPhase.Idle, motion.frame.value.phase)
            rule.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
            rule.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(50f, 100f, .25f, BackEventCompat.EDGE_LEFT))
        }
        rule.mainClock.advanceTimeBy(64)
        assertTitleFollowsPage("A")
        assertTitleFollowsPage("B")
        assertTrue("Predictive Back must move the current page's title", titleBounds("B").center.x > settledTitle.center.x + 4f)
        val trackingButton = rule.onNodeWithContentDescription("Stable action").fetchSemanticsNode().boundsInRoot
        assertEquals(anchor.center.x, trackingButton.center.x, .5f)
        rule.runOnUiThread {
            rule.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 100f, .6f, BackEventCompat.EDGE_LEFT))
        }
        rule.mainClock.advanceTimeBy(64)
        rule.runOnUiThread {
            assertEquals(GlasmioNavigationPhase.Tracking, motion.frame.value.phase)
            assertEquals(.6f, motion.frame.value.gestureProgress, .001f)
            rule.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
        }
        rule.mainClock.advanceTimeBy(700)
        assertTitleFollowsPage("B")
        assertEquals("Cancel must restore the current title", settledTitle.center.x, titleBounds("B").center.x, 1f)
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
        assertTitleFollowsPage("A")
        assertEquals("Committed Back must restore the source title", originalTitle.center.x, titleBounds("A").center.x, 1f)
        rule.onAllNodes(hasContentDescription("Stable action") and hasClickAction()).assertCountEquals(1)
    }
}
