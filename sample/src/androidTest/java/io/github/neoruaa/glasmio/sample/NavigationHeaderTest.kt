package io.github.neoruaa.glasmio.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.scaffold.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.SfIcon
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationHeaderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private class Motion : GlasmioHeaderMotion {
        val state = mutableStateOf(GlasmioNavigationFrame(0, "A", "A"))
        override val frame: State<GlasmioNavigationFrame> = state
    }
    private val motion = Motion()
    private var shift by mutableFloatStateOf(0f)
    private var count by mutableIntStateOf(1)
    private var symbol by mutableStateOf("moon")
    private var showsGroup by mutableStateOf(true)
    private var clicks = 0
    private var mounts = 0
    private var collapse by mutableFloatStateOf(1f)
    private var menuAnchor: GlasmioMenuAnchorState? = null
    private var bodyColor by mutableStateOf(Color.White)
    private var usesIconSlot = false

    private fun content(usesBackdrop: Boolean = false) {
        rule.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = !usesBackdrop)) {
                GlasmioNavigationScaffold(motion, header = { entry ->
                    val icon = if (entry == "A") "moon" else symbol
                    GlasmioHeaderSpec(title = entry.toString(), scrollState = remember { GlasmioHeaderScrollState({ collapse }) },
                        trailing = if (!showsGroup) emptyList() else listOf(GlasmioHeaderGroup("primary", (0 until if (entry == "A") 1 else count).map { index ->
                            GlasmioHeaderAction("action:$index", if (index == 0) icon else "extra:$index", "Action $index", { clicks++ }, menuAnchor = if (index == 0) menuAnchor else null) {
                                DisposableEffect(Unit) { mounts++; onDispose {} }
                                if (usesIconSlot) SfIcon("moon", null) else Text(if (index == 0) icon else "X")
                            }
                        })))
                }) { padding ->
                    Box(Modifier.fillMaxSize().graphicsLayer { translationX = shift }.background(bodyColor).testTag("body")) {
                        Text("Body", Modifier.padding(padding))
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    @Test fun exitingCircleCanExpandBeyondItsOriginalSlotWithoutRectangularClipping() {
        usesIconSlot = true
        bodyColor = Color.Blue
        collapse = 0f
        content()
        val bounds = rule.onNodeWithTag("GlasmioHeaderGroup:primary").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot()
        val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
        val outside = with(rule.density) { 1.dp.toPx() }
        val sampleX = (bounds.center.x - origin.x).toInt()
        val sampleY = (bounds.top - origin.y - outside).toInt()
        val before = root.captureToImage().toPixelMap()[sampleX, sampleY]
        rule.runOnUiThread { showsGroup = false }
        rule.mainClock.advanceTimeBy(80)
        val pixels = root.captureToImage().toPixelMap()
        val topCenter = pixels[sampleX, sampleY]
        val change = maxOf(kotlin.math.abs(topCenter.red - before.red), kotlin.math.abs(topCenter.green - before.green), kotlin.math.abs(topCenter.blue - before.blue))
        assertTrue("Fading glass must draw beyond the original slot: before=$before, after=$topCenter", change > .2f)
        val corner = pixels[(bounds.left - origin.x - outside).toInt(), (bounds.top - origin.y - outside).toInt()]
        assertTrue("The expanded silhouette must stay round: $corner", corner.blue > corner.red + .2f)
    }

    @Test fun singleIconUsesSquareBoundsAndCircularSilhouette() {
        usesIconSlot = true
        bodyColor = Color.Blue
        collapse = 0f
        content()
        val node = rule.onNodeWithTag("GlasmioHeaderGroup:primary")
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertEquals("A single icon must have equal width and height", bounds.height, bounds.width, .5f)
        assertEquals(with(rule.density) { 44.dp.toPx() }, bounds.width, .5f)
        val root = rule.onRoot()
        val rootBounds = root.fetchSemanticsNode().boundsInRoot
        val pixels = root.captureToImage().toPixelMap()
        for (xFraction in listOf(.12f, .88f)) for (yFraction in listOf(.12f, .88f)) {
            val x = (bounds.left - rootBounds.left + bounds.width * xFraction).toInt()
            val y = (bounds.top - rootBounds.top + bounds.height * yFraction).toInt()
            val corner = pixels[x, y]
            assertTrue("Diagonal corners must show the body outside the circle: $corner", corner.blue > corner.red + .2f)
        }
    }

    @Test fun sameVisualKeepsScreenAnchorAndForegroundWhileBodyMoves() {
        content()
        val before = rule.onNodeWithContentDescription("Action 0").fetchSemanticsNode().boundsInRoot
        val initialMounts = mounts
        rule.runOnUiThread {
            motion.state.value = GlasmioNavigationFrame(1, "A", "B", phase = GlasmioNavigationPhase.Settling)
            shift = 160f
        }
        repeat(12) {
            rule.mainClock.advanceTimeByFrame()
            val current = rule.onNodeWithContentDescription("Action 0").fetchSemanticsNode().boundsInRoot
            assertEquals(before.center.x, current.center.x, .5f)
            assertEquals(before.center.y, current.center.y, .5f)
            rule.onAllNodesWithTag("GlasmioHeaderGroup:primary").assertCountEquals(1)
        }
        assertEquals(initialMounts, mounts)
        rule.onNodeWithContentDescription("Action 0").performClick()
        assertEquals(1, clicks)
    }

    @Test fun groupWidthRetargetsThroughOneTwoThreeTextAndDisappearance() {
        content()
        val right = rule.onNodeWithTag("GlasmioHeaderGroup:primary").fetchSemanticsNode().boundsInRoot.right
        for (members in listOf(2, 3, 1)) {
            rule.runOnUiThread {
                count = members
                symbol = if (members == 1) "Done" else "sun"
                motion.state.value = GlasmioNavigationFrame(members.toLong(), "A", "B", phase = GlasmioNavigationPhase.Settling)
            }
            repeat(10) {
                rule.mainClock.advanceTimeByFrame()
                rule.onAllNodesWithTag("GlasmioHeaderGroup:primary").assertCountEquals(1)
                val rect = rule.onNodeWithTag("GlasmioHeaderGroup:primary").fetchSemanticsNode().boundsInRoot
                assertEquals(right, rect.right, .5f)
            }
            rule.mainClock.advanceTimeBy(1200)
            rule.onAllNodes(hasContentDescription("Action 0")).assertCountEquals(1)
        }
        rule.runOnUiThread { showsGroup = false }
        rule.mainClock.advanceTimeBy(1200)
        rule.onAllNodes(hasClickAction() and hasContentDescription("Action 0")).assertCountEquals(0)
    }

    @Test fun replacementForegroundBecomesVisibleAfterStartingAtZeroAlpha() {
        content()
        rule.runOnUiThread {
            symbol = "sun"
            motion.state.value = GlasmioNavigationFrame(1, "A", "B", phase = GlasmioNavigationPhase.Settling)
        }
        rule.mainClock.advanceTimeBy(1200)
        val pixels = rule.onNodeWithTag("GlasmioHeaderGroup:primary").captureToImage().toPixelMap()
        var darkest = 1f
        for (y in pixels.height / 3 until pixels.height * 2 / 3) {
            for (x in pixels.width / 4 until pixels.width * 3 / 4) {
                val pixel = pixels[x, y]
                darkest = minOf(darkest, (pixel.red + pixel.green + pixel.blue) / 3f)
            }
        }
        assertTrue("The replacement glyph must actually draw after its zero-alpha first frame", darkest < .3f)
    }

    @Test fun glassSamplesTheCurrentBodyWithoutCapturingItsOwnForeground() {
        bodyColor = Color.Red
        content(usesBackdrop = true)
        val group = rule.onNodeWithTag("GlasmioHeaderGroup:primary")
        val first = group.captureToImage().toPixelMap()
        val red = first[first.width / 2, first.height / 5]
        rule.runOnUiThread { bodyColor = Color.Blue }
        rule.mainClock.advanceTimeBy(64)
        val second = group.captureToImage().toPixelMap()
        val blue = second[second.width / 2, second.height / 5]
        assertTrue("The first glass frame must sample the red body", red.red > red.blue + .1f)
        assertTrue("A stable shell must resample the blue body in its next frame", blue.blue > blue.red + .1f)
    }

    @Test fun originalPressPhysicsAndWholeToolbarPortalArePreserved() {
        val anchor = GlasmioMenuAnchorState()
        menuAnchor = anchor
        count = 2
        motion.state.value = GlasmioNavigationFrame(0, "B", "B")
        content()
        val group = rule.onNodeWithTag("GlasmioHeaderGroup:primary").fetchSemanticsNode().boundsInRoot
        val before = requireNotNull(anchor.liveBounds)
        assertEquals(group.width, before.width, 1f)
        rule.onNodeWithContentDescription("Action 0").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(160)
        val pressed = requireNotNull(anchor.liveBounds)
        assertTrue("The original drag/press physics must deform the fused toolbar", kotlin.math.abs(before.width - pressed.width) > .5f)
        rule.onNodeWithContentDescription("Action 0").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(1000)
        rule.runOnUiThread {
            val acquired = requireNotNull(anchor.acquire())
            assertTrue("Menu must acquire the complete connected toolbar", anchor.isGroupedSource)
            assertTrue(anchor.sourceHasGlassBackground)
            assertEquals(group.width, acquired.width, 1f)
            assertTrue("Retain both foregrounds, not just the trigger", requireNotNull(anchor.portal).sourceBounds.width > requireNotNull(anchor.previewBounds).width)
            anchor.release()
        }
        rule.onNodeWithContentDescription("Action 1").assertIsDisplayed()
    }

    @Test fun predictiveCancelRestoresSourceOwnerAndSurvivesInterruption() {
        content()
        val initial = rule.onNodeWithContentDescription("Action 0").fetchSemanticsNode().boundsInRoot
        rule.runOnUiThread {
            symbol = "sun"
            count = 3
            motion.state.value = GlasmioNavigationFrame(1, "A", "B", phase = GlasmioNavigationPhase.Tracking, gestureProgress = .65f, gestureVelocity = .8f)
        }
        rule.mainClock.advanceTimeBy(120)
        rule.onAllNodes(hasClickAction() and hasContentDescription("Action 0")).assertCountEquals(1)
        rule.onAllNodes(hasClickAction() and hasContentDescription("Action 1")).assertCountEquals(0)
        rule.runOnUiThread { motion.state.value = motion.state.value.copy(phase = GlasmioNavigationPhase.Canceling) }
        rule.mainClock.advanceTimeBy(64)
        rule.runOnUiThread {
            count = 2
            motion.state.value = GlasmioNavigationFrame(2, "A", "B", phase = GlasmioNavigationPhase.Settling)
            collapse = 0f
            shift = -100f
        }
        rule.mainClock.advanceTimeBy(80)
        rule.runOnUiThread { motion.state.value = GlasmioNavigationFrame(3, "B", "A", operation = GlasmioNavigationOperation.Pop, phase = GlasmioNavigationPhase.Settling) }
        rule.mainClock.advanceTimeBy(1500)
        val end = rule.onNodeWithContentDescription("Action 0").fetchSemanticsNode().boundsInRoot
        assertEquals("Initial=$initial, final=$end, group=${rule.onNodeWithTag("GlasmioHeaderGroup:primary").fetchSemanticsNode().boundsInRoot}", initial.center.x, end.center.x, .5f)
        assertEquals(initial.center.y, end.center.y, .5f)
        rule.onAllNodes(hasClickAction() and hasContentDescription("Action 0")).assertCountEquals(1)
    }
}
