package io.github.neoruaa.glasmio.sample

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavEntry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.scaffold.*
import io.github.neoruaa.glasmio.scaffold.navigation.*
import io.github.neoruaa.glasmio.ui.GlasmioVolumeSlider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the maintained NavDisplay state machine with AndroidX back-dispatch events. */
@RunWith(AndroidJUnit4::class)
class NavigationClockTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun swipeDuringPushAndExternalNavigationRetainsThePresentedPage() {
        val stack = mutableStateListOf("A")
        val motion = GlasmioNavHeaderMotion("A")
        var pops = 0
        var navigationSlop = Float.NaN
        rule.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                val density = LocalDensity.current
                val configuration = LocalViewConfiguration.current
                SideEffect {
                    navigationSlop = maxOf(configuration.touchSlop, with(density) { 10.dp.toPx() })
                }
                GlasmioNavDisplay(stack, motion, modifier = Modifier.testTag("interrupt-host"),
                    onBack = { pops++; stack.removeAt(stack.lastIndex) },
                    entryProvider = { key -> NavEntry(key, contentKey = key) {
                        Box(Modifier.fillMaxSize().background(Color.White).testTag("interrupt-$key"))
                    } })
            }
        }
        rule.mainClock.autoAdvance = false
        try {
            rule.runOnUiThread { stack.add("B") }
            rule.mainClock.advanceTimeBy(80)
            val before = rule.onNodeWithTag("interrupt-B").fetchSemanticsNode().boundsInRoot.left
            assertTrue("The pushed page must still be entering", before > 1f)
            val host = rule.onNodeWithTag("interrupt-host")
            val handoffTime = rule.mainClock.currentTime
            var injectedTravel = Float.NaN
            host.performTouchInput {
                val start = Offset(width * .05f, height * .4f)
                val end = Offset(width * .20f, height * .4f)
                injectedTravel = end.x - start.x
                down(start)
                // Input injection advances the Compose clock by its delay, even with
                // autoAdvance disabled. Sample ownership at the same presentation time.
                moveTo(end, 0)
            }
            assertEquals("Taking ownership must not advance the presentation clock", handoffTime, rule.mainClock.currentTime)
            val handoff = rule.onNodeWithTag("interrupt-B").fetchSemanticsNode().boundsInRoot.left
            assertEquals("Taking ownership at the same clock time must retain the visible position", before, handoff, 2f)
            rule.mainClock.advanceTimeBy(200)
            var trackingProgress = Float.NaN
            rule.runOnUiThread {
                assertEquals(GlasmioNavigationPhase.Tracking, motion.frame.value.phase)
                trackingProgress = motion.frame.value.gestureProgress
            }
            val interrupted = rule.onNodeWithTag("interrupt-B").fetchSemanticsNode().boundsInRoot.left
            val hostBounds = host.fetchSemanticsNode().boundsInRoot
            // Use the recognizer's composition and exact injected coordinates so the
            // expectation stays in the same pixel coordinate system as the gesture.
            val expectedTravel = injectedTravel - navigationSlop
            assertEquals("Tracking progress must retain the initial position and full finger travel",
                handoff - hostBounds.left + expectedTravel, trackingProgress * hostBounds.width, 2f)
            assertEquals("An interrupted push must not scale down the finger's travel (handoff=$handoff, progress=$trackingProgress)",
                expectedTravel, interrupted - handoff, 2f)
            host.performTouchInput { cancel() }
            rule.mainClock.advanceTimeBy(1_200)
            rule.runOnUiThread {
                assertEquals(listOf("A", "B"), stack.toList())
                assertEquals(0, pops)
            }
            assertEquals(0f, rule.onNodeWithTag("interrupt-B").fetchSemanticsNode().boundsInRoot.left, 1f)
            host.performTouchInput {
                down(Offset(width * .25f, height * .4f))
                moveTo(Offset(width * .45f, height * .4f), 150)
            }
            rule.mainClock.advanceTimeBy(32)
            rule.runOnUiThread { stack.add("C") }
            rule.mainClock.advanceTimeBy(64)
            assertTrue("External navigation must animate the new page instead of snapping it in",
                rule.onNodeWithTag("interrupt-C").fetchSemanticsNode().boundsInRoot.left > 1f)
            rule.mainClock.advanceTimeBy(1_200)
            host.performTouchInput { up() }
            rule.mainClock.advanceTimeBy(1_200)
            rule.runOnUiThread {
                assertEquals("An old pointer sequence cannot pop a newly pushed entry", listOf("A", "B", "C"), stack.toList())
                assertEquals(0, pops)
                assertEquals(GlasmioNavigationPhase.Idle, motion.frame.value.phase)
            }
            rule.runOnUiThread { stack.add("D") }
            rule.mainClock.advanceTimeBy(48)
            host.performTouchInput { down(Offset(width * .05f, height * .4f)) }
            host.performTouchInput { moveTo(Offset(width * .12f, height * .4f), 16) }
            repeat(16) { step ->
                host.performTouchInput {
                    moveTo(Offset(width * (.12f + (step + 1) * .018f), height * .4f), 40)
                }
                rule.mainClock.advanceTimeByFrame()
            }
            host.performTouchInput { up() }
            rule.mainClock.advanceTimeBy(1_200)
            rule.runOnUiThread {
                assertEquals("A short slow drag can finish a page already partly outside the screen",
                    listOf("A", "B", "C"), stack.toList())
                assertEquals(1, pops)
            }
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }

    @Test fun fullPageSwipeBackRespectsDirectionCancellationAndChildGestures() {
        val stack = mutableStateListOf("A", "B")
        val motion = GlasmioNavHeaderMotion("B")
        var direction by mutableStateOf(LayoutDirection.Ltr)
        var volume by mutableFloatStateOf(.5f)
        var pops = 0
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                    GlasmioNavDisplay(stack, motion, modifier = Modifier.testTag("full-swipe-host"), onBack = {
                        pops++
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    }, entryProvider = { key -> NavEntry(key, contentKey = key) {
                        Box(Modifier.fillMaxSize().background(Color.White).testTag("swipe-page-$key")) {
                            Column(Modifier.align(Alignment.BottomCenter).padding(40.dp)) {
                                GlasmioVolumeSlider(volume, { volume = it }, Modifier.fillMaxWidth().testTag("swipe-volume"))
                                Box(Modifier.fillMaxWidth().height(60.dp).swipeBackExcluded().testTag("swipe-excluded"))
                            }
                        }
                    } })
                }
            }
        }
        fun page() = rule.onNodeWithTag("swipe-page-B")
        fun assertUnchanged() = rule.runOnIdle {
            assertEquals(listOf("A", "B"), stack.toList())
            assertEquals(GlasmioNavigationPhase.Idle, motion.frame.value.phase)
        }
        // A row/slider may own a drag even when it starts away from the system edge.
        rule.onNodeWithTag("swipe-volume").performTouchInput {
            swipe(Offset(width * .3f, centerY), Offset(width * .8f, centerY), 500)
        }
        rule.runOnIdle { assertTrue(volume > .65f) }
        assertUnchanged()
        rule.onNodeWithTag("swipe-excluded").performTouchInput {
            swipe(Offset(width * .2f, centerY), Offset(width * .9f, centerY), 500)
        }
        assertUnchanged()
        page().performTouchInput { swipe(Offset(width * .7f, height * .3f), Offset(width * .2f, height * .3f), 500) }
        assertUnchanged()
        page().performTouchInput { swipe(Offset(width * .4f, height * .2f), Offset(width * .45f, height * .6f), 500) }
        assertUnchanged()
        rule.mainClock.autoAdvance = false
        try {
            val host = rule.onNodeWithTag("full-swipe-host")
            host.performTouchInput {
                down(Offset(width * .2f, height * .3f))
            }
            repeat(20) { frame ->
                // Dispatch through the fixed host, drain that input, then render its frame.
                // A batched native event queue can otherwise outlive its virtual test clock.
                host.performTouchInput {
                    moveTo(Offset(width * (.2f + (frame + 1) * .02f), height * .3f), 16)
                }
                rule.mainClock.advanceTimeByFrame()
            }
            val bounds = page().fetchSemanticsNode().boundsInRoot
            assertTrue("The body must follow a continuous stream before the pointer stops: bounds=$bounds, frame=${motion.frame.value}",
                bounds.left > bounds.width * .1f)
            host.performTouchInput { cancel() }
            rule.mainClock.advanceTimeBy(1_200)
        } finally {
            rule.mainClock.autoAdvance = true
        }
        assertUnchanged()
        page().performTouchInput {
            down(Offset(width * .2f, height * .3f))
            moveTo(Offset(width * .85f, height * .3f), 250)
        }
        rule.mainClock.advanceTimeBy(100)
        page().performTouchInput { cancel() }
        rule.waitForIdle()
        assertUnchanged()
        page().performTouchInput {
            down(Offset(width * .3f, height * .3f))
            advanceEventTime(800)
            moveTo(Offset(width * .9f, height * .3f), 250)
            up()
        }
        assertUnchanged()
        page().performTouchInput {
            down(0, Offset(width * .3f, height * .3f))
            down(1, Offset(width * .4f, height * .3f))
            moveTo(0, Offset(width * .9f, height * .3f), 250)
            up(0)
            up(1)
        }
        assertUnchanged()
        // Reversing before release cancels without mutating the back stack.
        page().performTouchInput {
            down(Offset(width * .3f, height * .3f))
            moveTo(Offset(width * .55f, height * .3f), 250)
        }
        rule.mainClock.advanceTimeBy(100)
        rule.runOnUiThread { assertEquals(GlasmioNavigationPhase.Tracking, motion.frame.value.phase) }
        assertTrue("A full-page drag must move the actual page, not only the header phase",
            page().fetchSemanticsNode().boundsInRoot.left > 1f)
        page().performTouchInput {
            moveTo(Offset(width * .31f, height * .3f), 250)
            up()
        }
        rule.waitForIdle()
        assertUnchanged()
        listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { layout ->
            rule.runOnIdle { direction = layout }
            val sign = if (layout == LayoutDirection.Ltr) 1f else -1f
            page().performTouchInput {
                val start = if (sign > 0f) width * .25f else width * .75f
                swipe(Offset(start, height * .3f), Offset(start + sign * width * .65f, height * .3f), 600)
            }
            rule.waitForIdle()
            rule.runOnIdle { assertEquals(listOf("A"), stack.toList()) }
            if (layout == LayoutDirection.Ltr) {
                rule.runOnIdle { stack.add("B") }
                rule.waitForIdle()
            }
        }
        rule.runOnIdle { assertEquals("One callback per committed drag", 2, pops) }
        rule.onNodeWithTag("swipe-page-A").performTouchInput {
            swipe(Offset(width * .75f, height * .3f), Offset(width * .1f, height * .3f), 600)
        }
        rule.runOnIdle { assertEquals("The root page cannot pop", 2, pops) }
    }
    @Test fun realSeekClockPublishesTrackingCancelAndCommit() {
        val stack = mutableStateListOf("A")
        val motion = GlasmioNavHeaderMotion("A")
        var headerCalls = 0
        var foregroundTime = -1L
        var pops = 0
        val actionFrames = mutableListOf<GlasmioNavigationFrame>()
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                GlasmioNavigationScaffold(motion, header = { entry ->
                    headerCalls++
                    GlasmioHeaderSpec("Title $entry", trailing = listOf(GlasmioHeaderGroup("primary", listOf(
                        GlasmioHeaderAction("same.action", "moon", "Stable action", {
                            actionFrames += motion.frame.value
                        }) {
                            val raw = motion.frame.value
                            SideEffect { foregroundTime = raw.playTimeNanos }
                            Text("M")
                        },
                    ))))
                }) {
                    GlasmioNavDisplay(stack, motion, onBack = {
                        pops++
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    },
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
        val beforeClockOnly = headerCalls
        val beforeTime = motion.frame.value.playTimeNanos
        rule.mainClock.advanceTimeByFrame()
        rule.runOnUiThread {
            assertEquals(GlasmioNavigationPhase.Settling, motion.frame.value.phase)
            assertTrue(motion.frame.value.playTimeNanos > beforeTime)
            assertEquals("A real seek clock alone must not invalidate the header specification", beforeClockOnly, headerCalls)
            assertEquals("A foreground that explicitly reads the public clock must remain live",
                motion.frame.value.playTimeNanos, foregroundTime)
        }
        rule.onNodeWithContentDescription("Stable action")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        assertEquals(listOf(motion.frame.value), actionFrames)
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
            rule.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(50f, 100f, .25f, BackEventCompat.EDGE_LEFT))
        }
        rule.mainClock.advanceTimeBy(64)
        rule.runOnUiThread {
            assertEquals(GlasmioNavigationPhase.Tracking, motion.frame.value.phase)
            assertEquals(.25f, motion.frame.value.gestureProgress, .001f)
            assertEquals("Reversing an active gesture cannot commit Back", 0, pops)
            rule.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
        }
        rule.mainClock.advanceTimeBy(700)
        assertTitleFollowsPage("B")
        assertEquals("Cancel must restore the current title", settledTitle.center.x, titleBounds("B").center.x, 1f)
        rule.runOnUiThread {
            assertEquals(listOf("A", "B"), stack.toList())
            assertEquals("Cancel must not commit Back", 0, pops)
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
            assertEquals("Committed Back must call the original callback exactly once", 1, pops)
            assertEquals(GlasmioNavigationPhase.Idle, motion.frame.value.phase)
            assertEquals("A", motion.frame.value.toEntryKey)
        }
        assertTitleFollowsPage("A")
        assertEquals("Committed Back must restore the source title", originalTitle.center.x, titleBounds("A").center.x, 1f)
        rule.onAllNodes(hasContentDescription("Stable action") and hasClickAction()).assertCountEquals(1)
    }
}
