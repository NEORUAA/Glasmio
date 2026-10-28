package io.github.neoruaa.glasmio.sample

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.scaffold.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.GlasmioTopBarStyle
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

    @Test fun contentFadeIsLinearAndLeavesTheBackgroundUntouched() {
        var edge by mutableFloatStateOf(0f)
        var density = 1f
        val background = Color(0xFF004488)
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.setContent {
            density = LocalDensity.current.density
            Box(Modifier.fillMaxSize().background(background).testTag("linear-content-fade")) {
                Box(Modifier.fillMaxSize()
                    .scrollEdgeContentFade(edge, headerHeight = 60.dp, headerTop = 20.dp)
                    .background(Color.White))
            }
        }
        rule.waitForIdle()
        fun pixels() = rule.onNodeWithTag("linear-content-fade").captureToImage().toPixelMap()
        fun assertSample(image: androidx.compose.ui.graphics.PixelMap, yDp: Float, alpha: Float) {
            val actual = image[(image.width * 0.25f).toInt(), (yDp * density).toInt()]
            val expected = Color(
                background.red + (1f - background.red) * alpha,
                background.green + (1f - background.green) * alpha,
                background.blue + (1f - background.blue) * alpha,
            )
            assertEquals(expected.red, actual.red, 3f / 255f)
            assertEquals(expected.green, actual.green, 3f / 255f)
            assertEquals(expected.blue, actual.blue, 3f / 255f)
        }
        assertSample(pixels(), 20f, 1f)
        rule.runOnIdle { edge = 0.5f }
        assertSample(pixels(), 20f, 0.625f)
        rule.runOnIdle { edge = 1f }
        val faded = pixels()
        assertSample(faded, 0f, 0.25f)
        assertSample(faded, 20f, 0.25f)
        assertSample(faded, 30f, 0.4375f)
        assertSample(faded, 40f, 0.625f)
        assertSample(faded, 50f, 0.8125f)
        assertSample(faded, 60f, 1f)
        assertSample(faded, 80f, 1f)
        rule.runOnIdle { edge = 0f }
        assertSample(pixels(), 20f, 1f)
    }

    @Test fun pageOwnedBlurPreservesColorsAndMovesIndependentlyOfTheSharedToolbar() {
        var edge by mutableFloatStateOf(0f)
        var slide by mutableFloatStateOf(0f)
        var pattern by mutableStateOf(false)
        var widthPx = 0f
        var density = 1f
        var headerTopPx = 0f
        var headerHeightPx = 0f
        var blurHeightPx = 0f
        val blue = Color(0xFF004488)
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            GlasmioTheme {
                density = LocalDensity.current.density
                val headerTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                headerTopPx = with(LocalDensity.current) { headerTop.toPx() }
                headerHeightPx = with(LocalDensity.current) { (headerTop + 62.dp).toPx() }
                blurHeightPx = headerHeightPx + 34f * density
                GlasmioNavigationScaffold(motion, header = { entry ->
                    GlasmioHeaderSpec("Page $entry",
                        scrollState = remember { GlasmioHeaderScrollState({ 1f }, { 1f }) },
                        trailing = listOf(GlasmioHeaderGroup("primary", listOf(
                            GlasmioHeaderAction("shared", "shared", "Shared action", {}) { Text("Shared") },
                        ))))
                }) { padding ->
                    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
                        widthPx = constraints.maxWidth.toFloat()
                        for (page in listOf("A", "B")) {
                            val first = if (page == "A") blue else Color(0xFF220066)
                            val second = if (page == "A") Color(0xFFFFBB77) else Color(0xFFAA88EE)
                            val stripes = remember(first, second, density) {
                                Brush.horizontalGradient(
                                    0f to first, 0.499f to first, 0.5f to second, 1f to second,
                                    endX = 8f * density, tileMode = TileMode.Repeated,
                                )
                            }
                            GlasmioPageBody(
                                padding,
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    translationX = (if (page == "A") 0f else widthPx) - slide
                                }.testTag("blur-page-$page"),
                                backgroundColor = first,
                                backgroundBrush = stripes.takeIf { pattern },
                                topBarBlurProgress = if (page == "A") edge else 0f,
                            ) {
                                if (!pattern) Box(Modifier.fillMaxSize().drawBehind {
                                    drawRect(Color.White, topLeft = Offset(size.width * 0.6f, 0f),
                                        size = Size(size.width * 0.1f, size.height))
                                })
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val root = rule.onRoot()
        val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
        val upperY = 20f * density
        val toolbarMiddleY = headerTopPx + (headerHeightPx - headerTopPx) * 0.5f
        val belowHeaderY = headerHeightPx + 8f * density
        val tailY = blurHeightPx + 8f * density
        fun stats(image: androidx.compose.ui.graphics.PixelMap, left: Float, right: Float, y: Float): Pair<Float, Color> {
            val samples = (left.toInt() until right.toInt()).map { image[it, y.toInt()] }
            return (samples.maxOf { it.red } - samples.minOf { it.red }) to Color(
                samples.map { it.red }.average().toFloat(), samples.map { it.green }.average().toFloat(),
                samples.map { it.blue }.average().toFloat(),
            )
        }
        fun assertColor(expected: Color, actual: Color) {
            assertEquals(expected.red, actual.red, 3f / 255f)
            assertEquals(expected.green, actual.green, 3f / 255f)
            assertEquals(expected.blue, actual.blue, 3f / 255f)
        }
        fun foreground(image: androidx.compose.ui.graphics.PixelMap, y: Float = upperY) =
            image[(widthPx * 0.65f).toInt(), y.toInt()]
        fun background(image: androidx.compose.ui.graphics.PixelMap) =
            image[(widthPx * 0.25f).toInt(), upperY.toInt()]
        fun whiteOverBackground(alpha: Float) = Color(
            blue.red + (1f - blue.red) * alpha,
            blue.green + (1f - blue.green) * alpha,
            blue.blue + (1f - blue.blue) * alpha,
        )
        val clear = root.captureToImage().toPixelMap()
        assertColor(Color.White, foreground(clear))
        assertColor(blue, background(clear))
        rule.runOnIdle { edge = 0.5f }
        val partial = root.captureToImage().toPixelMap()
        val headerFraction = ((upperY - headerTopPx) / (headerHeightPx - headerTopPx)).coerceIn(0f, 1f)
        assertColor(whiteOverBackground(0.625f + 0.375f * headerFraction), foreground(partial))
        assertColor(blue, background(partial))
        rule.runOnIdle { edge = 1f }
        val full = root.captureToImage().toPixelMap()
        assertColor(whiteOverBackground(0.25f + 0.75f * headerFraction), foreground(full))
        assertColor(whiteOverBackground(0.625f), foreground(full, toolbarMiddleY))
        assertColor(blue, background(full))
        assertColor(Color.White, foreground(full, belowHeaderY))
        assertColor(Color.White, foreground(full, tailY))
        rule.runOnIdle { edge = 0f }
        assertColor(Color.White, foreground(root.captureToImage().toPixelMap()))

        // Background detail must retain the original full progressive blur, independently
        // of the foreground opacity mask. No foreground is drawn in this phase.
        rule.runOnIdle { pattern = true }
        val sharpPattern = root.captureToImage().toPixelMap()
        val clearContrast = stats(sharpPattern, 24f * density, 104f * density, upperY).first
        assertTrue("Background detail must start sharp", clearContrast > 0.9f)
        rule.runOnIdle { edge = 1f }
        val blurredPattern = root.captureToImage().toPixelMap()
        val (fullContrast, mean) = stats(blurredPattern, 24f * density, 104f * density, upperY)
        assertTrue("The revealed header must preserve full blur strength", fullContrast < 0.4f)
        assertEquals("Blur must preserve the background mean", 0.5f, mean.red, 0.03f)
        assertTrue("The blur's middle must not be diluted with a sharp copy",
            stats(blurredPattern, 24f * density, 104f * density, blurHeightPx * 0.65f).first < 0.35f)
        assertEquals("The original radius profile must return to sharp at its tail", clearContrast,
            stats(blurredPattern, 24f * density, 104f * density, tailY).first, 0.02f)

        val button = rule.onNodeWithContentDescription("Shared action").fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread {
            slide = widthPx / 2f
            motion.state.value = GlasmioNavigationFrame(1, "A", "B", phase = GlasmioNavigationPhase.Settling)
        }
        rule.mainClock.advanceTimeBy(80)
        val moving = root.captureToImage().toPixelMap()
        val half = widthPx / 2f
        val source = stats(moving, half - 70f * density, half - 30f * density, upperY)
        val target = stats(moving, half + 24f * density, half + 64f * density, upperY)
        assertTrue("The departing page must retain its own blurred background", source.first < 0.4f)
        assertTrue("The entering page must retain its clear background", target.first > 0.45f)
        assertTrue("The entering page must sample its own purple background", target.second.blue > target.second.red + 0.15f)
        val after = rule.onNodeWithContentDescription("Shared action").fetchSemanticsNode().boundsInRoot
        assertEquals(button.center.x - origin.x, after.center.x - origin.x, 0.5f)
        assertEquals(button.center.y - origin.y, after.center.y - origin.y, 0.5f)
        rule.onAllNodesWithContentDescription("Shared action").assertCountEquals(1)
    }

    @Test fun pageTitlesReserveTheirOwnButtonWidthsWithoutDuplicatingSharedActions() {
        val titleA = "Collection A has a deliberately long title that must fit between its own toolbar buttons"
        val titleB = "Collection B has another deliberately long title that must fit between different toolbar buttons"
        val mountsByPage = mutableMapOf<String, Int>()
        var leadingTitleB by mutableStateOf(false)
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                GlasmioNavigationScaffold(motion, header = { entry ->
                    val page = entry.toString()
                    val first = page == "A"
                    GlasmioHeaderSpec(
                        title = if (first) titleA else titleB,
                        subtitle = if (!first && leadingTitleB) "Subtitle B" else null,
                        style = if (!first && leadingTitleB) GlasmioTopBarStyle.TwoLineLeading else GlasmioTopBarStyle.Default,
                        leading = listOf(GlasmioHeaderGroup("leading", listOf(
                            GlasmioHeaderAction("back", if (first) "previous" else "short-back", "Leading $page", {}) {
                                DisposableEffect(Unit) {
                                    mountsByPage[page] = mountsByPage.getOrDefault(page, 0) + 1
                                    onDispose {}
                                }
                                Text(if (first) "Previous" else "X")
                            },
                        ))),
                        trailing = listOf(GlasmioHeaderGroup("trailing", listOf(
                            GlasmioHeaderAction("apply", if (first) "short-apply" else "apply-changes", "Trailing $page", {}) {
                                DisposableEffect(Unit) {
                                    mountsByPage[page] = mountsByPage.getOrDefault(page, 0) + 1
                                    onDispose {}
                                }
                                Text(if (first) "X" else "Apply changes")
                            },
                        ))),
                    )
                }) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        GlasmioPageTitle("A", Modifier.testTag("width-title-A"))
                        GlasmioPageTitle("B", Modifier.offset(y = 120.dp).testTag("width-title-B"))
                    }
                }
            }
        }
        fun title(page: String) = rule.onNode(
            hasText(if (page == "A") titleA else titleB) and hasAnyAncestor(hasTestTag("width-title-$page")),
        )
        fun assertOneEllipsizedLine(page: String) {
            val layouts = mutableListOf<TextLayoutResult>()
            title(page).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            assertTrue("Long page titles must ellipsize inside the reserved space", layouts.single().isLineEllipsized(0))
        }
        rule.waitForIdle()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val pageInset = with(rule.density) { 16.dp.toPx() }
        val actionGap = with(rule.density) { 8.dp.toPx() }
        val leadingA = rule.onNodeWithContentDescription("Leading A").fetchSemanticsNode().boundsInRoot.width
        val trailingA = rule.onNodeWithContentDescription("Trailing A").fetchSemanticsNode().boundsInRoot.width
        fun assertCentered(page: String, leading: Float, trailing: Float) {
            val text = title(page).fetchSemanticsNode().boundsInRoot
            val side = pageInset + maxOf(leading, trailing) + actionGap
            assertEquals("The title must clear its own wider button side", root.left + side, text.left, 2f)
            assertEquals(root.right - side, text.right, 2f)
            assertEquals("Centered titles keep equal visible edge insets", root.center.x, text.center.x, 1f)
            assertOneEllipsizedLine(page)
        }
        assertCentered("A", leadingA, trailingA)
        assertEquals("A title must not compose copies of the shared actions", 2, mountsByPage["A"])
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread {
            motion.state.value = GlasmioNavigationFrame(1, "A", "B", phase = GlasmioNavigationPhase.Settling)
        }
        rule.mainClock.advanceTimeBy(1200)
        val leadingB = rule.onNodeWithContentDescription("Leading B").fetchSemanticsNode().boundsInRoot.width
        val trailingB = rule.onNodeWithContentDescription("Trailing B").fetchSemanticsNode().boundsInRoot.width
        assertTrue("The fixture must exercise different natural button widths", maxOf(leadingB, trailingB) > maxOf(leadingA, trailingA) + actionGap)
        assertCentered("A", leadingA, trailingA)
        assertCentered("B", leadingB, trailingB)
        assertEquals(2, mountsByPage["A"])
        assertEquals("The incoming title must not remount its buttons", 2, mountsByPage["B"])
        rule.onAllNodesWithContentDescription("Leading A").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Leading B").assertCountEquals(1)

        rule.runOnUiThread { leadingTitleB = true }
        rule.mainClock.advanceTimeBy(64)
        val leadingTitle = title("B").fetchSemanticsNode().boundsInRoot
        assertEquals(root.left + pageInset + leadingB + actionGap, leadingTitle.left, 2f)
        assertEquals(root.right - pageInset - trailingB - actionGap, leadingTitle.right, 2f)
        assertCentered("A", leadingA, trailingA)
        assertOneEllipsizedLine("B")
        rule.onNodeWithText("Subtitle B").assertIsDisplayed()
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
