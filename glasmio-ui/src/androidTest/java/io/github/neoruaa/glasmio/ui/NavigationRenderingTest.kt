package io.github.neoruaa.glasmio.ui

import io.github.neoruaa.glasmio.glass.GlassSurfaceStyle
import io.github.neoruaa.glasmio.glass.BottomNavigationContact
import io.github.neoruaa.glasmio.glass.BottomControlContact
import io.github.neoruaa.glasmio.glass.LocalBottomControlContact
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuData
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.GlassAppearance
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.defaultGlassColors
import io.github.neoruaa.glasmio.icons.SfSymbol
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.glass.LiquidMenuMorphMotion
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import io.github.neoruaa.glasmio.glass.NavigationDropShadowRenderer
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.glass.GlasmioBackdropProvider
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.glass.GlasmioMenuAnchorState
import io.github.neoruaa.glasmio.glass.glasmioMenuAnchor
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Renderer parity cases migrated with navigation so private implementation stays private. */
@RunWith(AndroidJUnit4::class)
class NavigationRenderingTest {
    @get:Rule val compose = createComposeRule()
    private val library = GlasmioTabItem("library", "Library", SfSymbol.MusicNoteList)

    @Test
    fun separatedAndJoinedNavigationKeepOneMaterialAndTheSameDividerAnchor() {
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val appearance = mutableStateOf(GlassAppearance())
        val compact = mutableFloatStateOf(0f)
        var contact: BottomControlContact? = null
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            GlasmioTheme(isDark = false, appearance = appearance.value) {
                CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                    GlasmioBackdropHost(
                        modifier = Modifier.size(360.dp, 220.dp).testTag("opticalScene"),
                        sampledContent = {
                            Canvas(Modifier.fillMaxSize()) {
                                drawRect(Color.White)
                                listOf(-24f, 0f, 24f).forEach { x ->
                                    drawRect(Color.Black,
                                        topLeft = Offset(size.width / 2f + x.dp.toPx() - 3.dp.toPx(), 0f),
                                        size = Size(6.dp.toPx(), size.height))
                                }
                            }
                        },
                        overlayContent = { backdrop ->
                            GlassSurface(
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                                    .width(340.dp).height(44.dp).testTag("opticalReference"),
                                backdrop = backdrop,
                                style = GlassSurfaceStyle.Navigation,
                            ) { }
                            BottomNavigationContact(
                                backdrop = backdrop,
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp)
                                    .width(340.dp).testTag("opticalNavigation"),
                            ) {
                                val owner = requireNotNull(LocalBottomControlContact.current)
                                SideEffect { contact = owner }
                                GlasmioNavigationBar(
                                    items = listOf(
                                        GlasmioTabItem("home", "Home", SfSymbol.House), library,
                                        GlasmioTabItem("favorites", "Favorites", SfSymbol.Heart),
                                        GlasmioTabItem("settings", "Settings", SfSymbol.Gearshape),
                                    ),
                                    selectedKey = "home", onSelected = {}, onExpand = {},
                                    compactProgress = compact, compactSize = 44.dp,
                                    backdrop = backdrop, modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.size(8.dp, 1.dp))
                                Box(Modifier.size(NavigationBarSurfaceHeight)
                                    .then(owner.neighborRecordingModifier)) {
                                    GlasmioIconButton(
                                        onClick = {}, backdrop = backdrop,
                                        style = GlassSurfaceStyle.Navigation,
                                        modifier = Modifier.size(NavigationBarSurfaceHeight),
                                    ) { }
                                }
                            }
                        },
                    )
                }
            }
        }
        try {
            listOf(GlassAppearance(), GlassAppearance(reduceTransparency = true)).forEach { mode ->
                listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { layoutDirection ->
                    compose.runOnIdle { appearance.value = mode; direction.value = layoutDirection }
                    compose.waitForIdle()
                    val owner = requireNotNull(contact)
                    val source = owner.materialBackdrop
                    val scene = compose.onNodeWithTag("opticalScene")
                    val sceneBounds = scene.fetchSemanticsNode().boundsInRoot
                    val navigation = compose.onNodeWithTag("opticalNavigation").fetchSemanticsNode().boundsInRoot
                    val reference = compose.onNodeWithTag("opticalReference").fetchSemanticsNode().boundsInRoot
                    fun dividerCenters(sampleY: Float = navigation.top - sceneBounds.top +
                        requireNotNull(owner.surface.value).bounds.top + 2f * density): List<Float> {
                        val pixels = scene.captureToImage().toPixelMap()
                        val y = sampleY.roundToInt()
                        return listOf(-24f, 0f, 24f).map { offset ->
                            val center = pixels.width / 2f + offset * density
                            var weight = 0.0
                            var moment = 0.0
                            for (x in (center - 10f * density).roundToInt()..(center + 10f * density).roundToInt()) {
                                val color = pixels[x, y]
                                val shade = (1f - (color.red + color.green + color.blue) / 3f).coerceAtLeast(0f)
                                val w = shade * shade * shade
                                weight += w
                                moment += x * w
                            }
                            assertTrue("The ruler must remain visible through the material", weight > 0.1)
                            (moment / weight).toFloat()
                        }
                    }
                    compose.runOnIdle {
                        assertTrue(owner.members.all { it.sharesSurface })
                        assertTrue(!owner.connected.value)
                    }
                    val rest = dividerCenters()
                    if (!mode.reduceTransparency) {
                        val referenceCenters = dividerCenters(reference.top - sceneBounds.top + 2f * density)
                        assertEquals("The central ruler must not shift toward the short bar's center",
                            referenceCenters[1], rest[1], 2f)
                        assertTrue("The left ruler bends left, matching a full-width reference",
                            rest[0] <= sceneBounds.width / 2f - 24f * density + 1f)
                    }
                    val home = compose.onNodeWithContentDescription("Home")
                    compose.mainClock.autoAdvance = false
                    home.performTouchInput { down(center) }
                    listOf(16L, 16L, 16L, 16L, 64L, 256L).forEach { elapsed ->
                        compose.mainClock.advanceTimeBy(elapsed)
                        compose.runOnIdle {
                            assertTrue(owner.materialBackdrop === source)
                            assertTrue(owner.members.all { it.sharesSurface })
                        }
                        if (!mode.reduceTransparency) {
                            assertEquals("The central ruler stays anchored while contact changes",
                                rest[1], dividerCenters()[1], 2f)
                        }
                    }
                    compose.runOnIdle { assertTrue(owner.connected.value) }
                    home.performTouchInput { up() }
                    compose.mainClock.advanceTimeBy(1000)
                    compose.mainClock.autoAdvance = true
                    compose.waitForIdle()
                    if (!mode.reduceTransparency) assertEquals(rest[1], dividerCenters()[1], 2f)
                    listOf(0.5f, 1f, 0f).forEach { progress ->
                        compose.runOnIdle { compact.floatValue = progress }
                        compose.waitForIdle()
                        compose.runOnIdle {
                            assertTrue("Compact geometry retains the same material owner",
                                owner.materialBackdrop === source && owner.members.all { it.sharesSurface })
                        }
                    }
                }
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun adaptiveGroupKeepsAcknowledgementsAndRoutesDragOutToMainItems() {
        val actual = mutableStateOf<Float?>(null)
        val mode = mutableStateOf("adaptive")
        val enabled = mutableStateOf(true)
        val adaptiveEnabled = mutableStateOf(true)
        val discrete = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val ordinaryCaptions = listOf("off" to "关闭", "open" to "Open", "adaptive" to "Adaptive", "quiet" to "Quiet")
        val captions = mutableStateOf(ordinaryCaptions)
        val captionOverride = mutableStateOf<String?>(null)
        val modeRequests = mutableListOf<String>()
        val strengthRequests = mutableListOf<Float>()
        var finishes = 0
        compose.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                GlasmioBackdropProvider(emptyBackdrop()) {
                    CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                        GlasmioSegmentedControl(
                            items = captions.value,
                            selected = mode.value, onSelected = { modeRequests += it },
                            adaptiveItem = "adaptive", adaptiveValue = actual.value,
                            adaptiveDiscrete = discrete.value,
                            onAdaptiveValueChange = { strengthRequests += it },
                            modifier = Modifier.width(360.dp).testTag("adaptive-group"),
                            enabled = enabled.value, adaptiveEnabled = adaptiveEnabled.value,
                            deferSelection = true, showLabels = true,
                            hiddenItems = if (mode.value == "adaptive") setOf("off") else emptySet(),
                            onAdaptiveValueChangeFinished = { finishes++ },
                            adaptiveLabels = listOf("More", "Default", "Less"),
                            captionOverride = captionOverride.value,
                        )
                    }
                }
            }
        }
        val group = compose.onNodeWithTag("adaptive-group")
        val adaptive = compose.onNodeWithContentDescription("Adaptive")
        val expandedHeight = group.fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { mode.value = "open" }
        val singleLineHeight = group.fetchSemanticsNode().boundsInRoot.height
        assertEquals("A hidden two-character caption must not add a line", singleLineHeight, expandedHeight, 1f)
        compose.mainClock.autoAdvance = false
        try {
            listOf("adaptive", "open").forEach { target ->
                compose.runOnIdle { mode.value = target }
                compose.mainClock.advanceTimeByFrame()
                listOf(16L, 80L, 160L, 240L, 500L).forEach { elapsed ->
                    compose.mainClock.advanceTimeBy(elapsed)
                    assertEquals("Caption height must stay stable while hiding or revealing Off",
                        singleLineHeight, group.fetchSemanticsNode().boundsInRoot.height, 1f)
                }
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.runOnIdle {
            captions.value = ordinaryCaptions.map { if (it.first == "quiet") it.first to "ノイズ\nキャンセル" else it }
            mode.value = "adaptive"
        }
        val multilineHeight = group.fetchSemanticsNode().boundsInRoot.height
        assertTrue("Visible multiline captions retain their natural height", multilineHeight > singleLineHeight)
        compose.runOnIdle { mode.value = "open" }
        assertEquals(multilineHeight, group.fetchSemanticsNode().boundsInRoot.height, 1f)
        compose.runOnIdle { captionOverride.value = "Put on AirPods to continue" }
        compose.onNodeWithText("Put on AirPods to continue").assertIsDisplayed()
        val overrideHeight = group.fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { mode.value = "adaptive" }
        assertEquals("A replacement caption does not measure hidden item labels",
            overrideHeight, group.fetchSemanticsNode().boundsInRoot.height, 1f)
        compose.runOnIdle { captions.value = ordinaryCaptions; captionOverride.value = null }
        assertEquals(ProgressBarRangeInfo.Indeterminate,
            adaptive.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo])
        adaptive.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(.4f)) }
        compose.runOnIdle {
            assertEquals(listOf(.4f), strengthRequests)
            assertTrue(modeRequests.isEmpty())
            assertEquals(null, actual.value)
            assertEquals(1, finishes)
            actual.value = .4f
        }
        adaptive.assertIsSelected()
        assertEquals(.4f, adaptive.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, .0001f)
        group.performTouchInput {
            down(Offset(width * 1.6f / 5f, 28.dp.toPx()))
            moveTo(Offset(width * 2.29f / 5f, 28.dp.toPx()))
        }
        compose.runOnIdle {
            assertEquals(.43f, strengthRequests.last(), .0001f)
            assertEquals(1, finishes)
            assertEquals(.4f, actual.value)
            assertTrue(modeRequests.isEmpty())
        }
        assertEquals(.43f, adaptive.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, .0001f)
        group.performTouchInput { up() }
        compose.runOnIdle { assertEquals(2, finishes) }
        assertEquals(.4f, adaptive.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, .0001f)
        compose.runOnIdle { actual.value = .43f; direction.value = LayoutDirection.Rtl }
        group.performTouchInput {
            swipe(Offset(width * 3.4f / 5f, 28.dp.toPx()), Offset(width * 2.2f / 5f, 28.dp.toPx()))
        }
        compose.runOnIdle {
            assertEquals(.6f, strengthRequests.last(), .0001f)
            assertEquals(3, finishes)
            assertTrue(modeRequests.isEmpty())
            direction.value = LayoutDirection.Ltr
        }
        val beforeExit = compose.runOnIdle { strengthRequests.size }
        group.performTouchInput {
            down(Offset(width * 2.3f / 5f, 28.dp.toPx()))
            moveTo(Offset(8.dp.toPx(), 28.dp.toPx()))
            moveTo(Offset(width * 3f / 5f, 28.dp.toPx()))
            moveTo(Offset(width - 8.dp.toPx(), 28.dp.toPx()))
            up()
        }
        compose.runOnIdle {
            assertEquals(listOf("open"), modeRequests)
            assertEquals(beforeExit, strengthRequests.size)
            assertEquals(3, finishes)
            mode.value = "open"
        }
        group.performTouchInput {
            swipe(Offset(width * 1.5f / 4f, 28.dp.toPx()), Offset(width * 3.5f / 4f, 28.dp.toPx()))
        }
        compose.runOnIdle {
            assertEquals("Collapsed ordinary cells do not accept slider drags", listOf("open"), modeRequests)
            mode.value = "adaptive"
            enabled.value = false
        }
        adaptive.assertIsNotEnabled().performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(!it(0f)) }
        group.performTouchInput { click(Offset(width * 2.5f / 5f, 28.dp.toPx())) }
        compose.runOnIdle {
            assertEquals(beforeExit, strengthRequests.size)
            assertEquals(listOf("open"), modeRequests)
            assertEquals(3, finishes)
        }
        compose.runOnIdle {
            enabled.value = true
            discrete.value = true
            actual.value = .4f
            strengthRequests.clear()
            modeRequests.clear()
        }
        val discreteReport = adaptive.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(.4f, discreteReport.current, .0001f)
        assertEquals(1, discreteReport.steps)
        compose.runOnIdle { assertTrue("Changing capability never fabricates an incoming 50% report", strengthRequests.isEmpty()) }
        listOf(.309f, .31f, .7f, .701f).forEach { requested ->
            adaptive.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(requested)) }
        }
        compose.runOnIdle {
            assertEquals(listOf(0f, .5f, 1f), strengthRequests)
            strengthRequests.clear()
        }
        val beforeDiscreteDrag = compose.runOnIdle { finishes }
        group.performTouchInput {
            down(Offset(width * 1.6f / 5f, 28.dp.toPx()))
            moveTo(Offset(width * 2.29f / 5f, 28.dp.toPx()))
        }
        compose.runOnIdle { assertEquals(listOf(.5f), strengthRequests); assertEquals(beforeDiscreteDrag, finishes) }
        assertEquals(.5f, adaptive.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, .0001f)
        group.performTouchInput { moveTo(Offset(width * 2.8f / 5f, 28.dp.toPx())) }
        compose.runOnIdle { assertEquals("Repeated movement inside one detent emits no duplicate preview", listOf(.5f), strengthRequests) }
        group.performTouchInput { moveTo(Offset(width * 3.7f / 5f, 28.dp.toPx())); up() }
        compose.runOnIdle {
            assertEquals(listOf(.5f, 1f), strengthRequests)
            assertEquals(beforeDiscreteDrag + 1, finishes)
            assertEquals(.4f, actual.value)
            assertTrue(modeRequests.isEmpty())
        }
        assertEquals(.4f, adaptive.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, .0001f)
        compose.runOnIdle {
            actual.value = .5f
            strengthRequests.clear()
        }
        adaptive.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(1f)) }
        compose.runOnIdle { assertEquals(listOf(1f), strengthRequests); strengthRequests.clear() }
        group.performTouchInput {
            down(Offset(width * 2.5f / 5f, 28.dp.toPx()))
            moveTo(Offset(width * 2.5f / 5f, 50.dp.toPx()))
            cancel()
        }
        compose.runOnIdle {
            assertTrue("A vertical scroll never writes an adaptive preview", strengthRequests.isEmpty())
            adaptiveEnabled.value = false
        }
        adaptive.assertIsNotEnabled().performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(!it(0f)) }
        group.performTouchInput { click(Offset(width * 3.7f / 5f, 28.dp.toPx())) }
        compose.runOnIdle { assertTrue("A separately disabled child ignores taps", strengthRequests.isEmpty()) }
    }

    @Test
    fun filledVolumeSliderHandlesNormalizedInputRtlAndDisabledProgress() {
        val value = mutableFloatStateOf(0.25f)
        val enabled = mutableStateOf(true)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val requests = mutableListOf<Float>()
        var finishes = 0
        compose.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                    GlasmioVolumeSlider(
                        value = value.floatValue,
                        onValueChange = { requests += it; value.floatValue = it },
                        modifier = Modifier.width(300.dp).testTag("filled-volume"),
                        enabled = enabled.value,
                        onValueChangeFinished = { finishes++ },
                    )
                }
            }
        }
        val slider = compose.onNodeWithTag("filled-volume")
        fun assertLevel(expected: Float, expectedFinishes: Int) = compose.runOnIdle {
            assertEquals(expected, value.floatValue, 0.01f)
            assertEquals(expectedFinishes, finishes)
            assertTrue(requests.all { it in 0f..1f })
        }

        slider.assertHeightIsEqualTo(52.dp).performTouchInput {
            click(Offset(width * 0.75f, height / 2f))
        }
        assertLevel(0.75f, 1)
        slider.performTouchInput {
            swipe(Offset(width * 0.75f, height / 2f), Offset(width * 0.25f, height / 2f))
        }
        assertLevel(0.25f, 2)
        val beforeVertical = compose.runOnIdle { requests.size }
        slider.performTouchInput {
            swipe(Offset(width / 2f, height * 0.1f), Offset(width / 2f, height * 0.9f))
        }
        assertLevel(0.25f, 2)
        compose.runOnIdle { assertEquals(beforeVertical, requests.size) }

        slider.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(1.5f)) }
        assertLevel(1f, 3)
        slider.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0.6f)) }
        assertLevel(0.6f, 4)

        compose.runOnIdle { direction.value = LayoutDirection.Rtl }
        slider.performTouchInput { click(Offset(width * 0.25f, height / 2f)) }
        assertLevel(0.75f, 5)
        slider.performTouchInput {
            swipe(Offset(width * 0.25f, height / 2f), Offset(width * 0.75f, height / 2f))
        }
        assertLevel(0.25f, 6)

        compose.runOnIdle { enabled.value = false }
        val beforeDisabled = compose.runOnIdle { requests.size }
        slider.assertIsNotEnabled().performTouchInput { click(Offset(width / 2f, height / 2f)) }
        slider.performTouchInput {
            swipe(Offset(width * 0.75f, height / 2f), Offset(width * 0.25f, height / 2f))
        }
        slider.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(!it(0.9f)) }
        assertLevel(0.25f, 6)
        compose.runOnIdle { assertEquals(beforeDisabled, requests.size) }
    }

    @Test
    fun groupedSeparatorsPreservePressedTopEdgeAndFollowKeyedLayout() {
        val rows = mutableStateListOf("First", "Second")
        val framed = mutableStateOf(true)
        val fieldFirst = mutableStateOf(false)
        val pressedColor = Color(0xFFB8B8B8)
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            GlasmioTheme(isDark = false, appearance = GlassAppearance(reduceTransparency = true)) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    GlasmioGroupedList(Modifier.width(300.dp).testTag("separator-group"), framed = framed.value) {
                        rows.forEach { label ->
                            key(label) {
                                if (fieldFirst.value && label == rows.first()) {
                                    GlasmioTextField("", {}, modifier = Modifier.testTag("first-field"))
                                } else {
                                    val interactions = remember { MutableInteractionSource() }
                                    val pressed by interactions.collectIsPressedAsState()
                                    GlasmioListRow(label, modifier = Modifier.testTag(label)
                                        .background(if (pressed) pressedColor else Color.White)
                                        .clickable(interactionSource = interactions, indication = null, onClick = {}))
                                }
                            }
                        }
                    }
                }
            }
        }
        fun colorError(a: Color, b: Color): Float = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
        fun assertTopMatchesRow(expected: Color) {
            val pixels = compose.onNodeWithTag("separator-group").captureToImage().toPixelMap()
            val x = pixels.width / 2
            assertTrue("The first row background must reach its top edge without a painted cover or separator",
                colorError(expected, pixels[x, 0]) < 3f / 255f)
            assertTrue("The interior separator must remain visible",
                colorError(pixels[x, (52f * density).roundToInt()], pixels[x, (56f * density).roundToInt()]) > .02f)
        }

        compose.onNodeWithTag("First").performTouchInput { down(center) }
        assertTopMatchesRow(pressedColor)
        compose.onNodeWithTag("First").performTouchInput { up() }
        compose.runOnIdle { rows.add(0, rows.removeAt(rows.lastIndex)) }
        assertTopMatchesRow(Color.White)
        compose.runOnIdle { fieldFirst.value = true }
        assertTopMatchesRow(Color.White)
        compose.runOnIdle { framed.value = false }
        val unframed = compose.onNodeWithTag("separator-group").captureToImage().toPixelMap()
        assertTrue("Unframed groups retain the standalone TextField separator",
            colorError(unframed[unframed.width / 2, 0], Color.White) > .02f)
    }

    @Test
    fun sheetVirtualRowsKeepSeparatorsInsideTheirOwnClippedSurface() {
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            GlasmioTheme(isDark = false) {
                CompositionLocalProvider(
                    LocalGlassColors provides LocalGlassColors.current.copy(separator = Color.Red),
                    LocalIosSheetMaterialContext provides IosSheetMaterialContext(false, 0f),
                ) {
                    LazyColumn(Modifier.width(300.dp).height(104.dp)) {
                        item {
                            Box(Modifier.glasmioGroupedListBackground(RectangleShape)) {
                                GlasmioListRow("First", showTopSeparator = false)
                            }
                        }
                        item {
                            Box(Modifier.testTag("sheet-virtual-row").glasmioGroupedListBackground(RectangleShape)) {
                                GlasmioListRow("Second", showTopSeparator = true)
                            }
                        }
                    }
                }
            }
        }
        val pixels = compose.onNodeWithTag("sheet-virtual-row").captureToImage().toPixelMap()
        val x = pixels.width / 2
        val boundary = pixels[x, (0.5f * density).toInt()]
        assertTrue("The separator must remain visible inside the virtual row's clip",
            boundary.red > 0.98f && boundary.green < 0.4f && boundary.blue < 0.4f)
        val carrier = pixels[x, (4f * density).roundToInt()]
        assertTrue("The separator must not replace the native opaque cell carrier",
            carrier.red > 0.98f && carrier.green > 0.98f && carrier.blue > 0.98f)
    }

    @Test
    fun segmentedControlKeepsDeferredSelectionAndUnknownDisabledSemantics() {
        val selected = mutableStateOf<Int?>(null)
        val enabled = mutableStateOf(true)
        val position = Animatable(0f)
        val requests = mutableListOf<Int>()
        val items = listOf(0 to "Quiet", 1 to "Balanced", 2 to "Open")
        compose.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                GlasmioBackdropProvider(emptyBackdrop()) {
                    GlasmioSegmentedControl(
                        items = items,
                        selected = selected.value,
                        onSelected = { requests += it },
                        modifier = Modifier.width(300.dp).testTag("segmented"),
                        positionAnimation = position,
                        height = 50.dp,
                        enabled = enabled.value,
                        deferSelection = true,
                        itemContent = { _, _ ->
                            Canvas(Modifier.size(25.dp)) { drawCircle(Color.Black) }
                        },
                    )
                }
            }
        }
        compose.onNodeWithTag("segmented").assertHeightIsEqualTo(50.dp)
        items.forEach { compose.onNodeWithContentDescription(it.second).assertIsNotSelected() }
        compose.onNodeWithContentDescription("Balanced").performClick()
        compose.runOnIdle { assertEquals(listOf(1), requests) }
        items.forEach { compose.onNodeWithContentDescription(it.second).assertIsNotSelected() }

        compose.runOnIdle { selected.value = 0 }
        compose.onNodeWithContentDescription("Quiet").assertIsSelected()
        compose.onNodeWithTag("segmented").performTouchInput {
            swipe(Offset(width / 6f, height / 2f), Offset(width * 5f / 6f, height / 2f))
        }
        compose.runOnIdle {
            assertEquals(listOf(1, 2), requests)
            assertEquals(0f, position.value, 0.001f)
        }
        compose.onNodeWithContentDescription("Quiet").assertIsSelected()
        compose.onNodeWithContentDescription("Open").assertIsNotSelected()

        compose.runOnIdle { selected.value = 2; enabled.value = false }
        compose.onNodeWithContentDescription("Open").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Balanced")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("segmented").performTouchInput {
            swipe(Offset(width * 5f / 6f, height / 2f), Offset(width / 6f, height / 2f))
        }
        compose.runOnIdle { assertEquals(listOf(1, 2), requests) }
    }

    @Test
    fun nativeSelectAllAdaptersUseTheLatestEditableValueAndCallback() {
        val value = mutableStateOf(TextFieldValue("Original text", TextRange(0, 5)))
        val callbackRevision = mutableStateOf(0)
        val ownerUpdates = mutableListOf<Int>()
        var staleNativeCalls = 0
        var forwardedSelectAll: (() -> Unit)? = null
        lateinit var legacyAdapter: TextToolbar
        lateinit var modernAdapter: TextContextMenuProvider
        lateinit var editingHost: GlasmioTextEditingHost
        lateinit var scope: CoroutineScope
        val session = object : TextContextMenuSession { override fun close() = Unit }
        val receiver = object : TextToolbar, TextContextMenuProvider {
            override val status = TextToolbarStatus.Hidden
            override fun hide() = Unit
            override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
                forwardedSelectAll = onSelectAllRequested
            }
            override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
                val item = dataProvider.data().components.filterIsInstance<TextContextMenuItem>()
                    .single { it.key == TextContextMenuKeys.SelectAllKey }
                forwardedSelectAll = { item.onClick(session) }
                awaitCancellation()
            }
        }
        val nativeData = object : TextContextMenuDataProvider {
            override fun position(destinationCoordinates: LayoutCoordinates) = Offset.Zero
            override fun contentBounds(destinationCoordinates: LayoutCoordinates) = Rect.Zero
            override fun data() = TextContextMenuData(listOf(
                TextContextMenuItem(TextContextMenuKeys.SelectAllKey, "Select All") { staleNativeCalls++ },
            ))
        }
        compose.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                GlasmioBackdropProvider(emptyBackdrop()) {
                    GlasmioTextSelectionHost {
                        CompositionLocalProvider(
                            LocalTextToolbar provides receiver,
                            LocalTextContextMenuToolbarProvider provides receiver,
                        ) {
                            val revision = callbackRevision.value
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                GlasmioEditableText(value.value, {
                                    ownerUpdates += revision
                                    value.value = it
                                }, Modifier.width(280.dp).testTag("native-select-all"), readOnly = true,
                                    decorationBox = { inner ->
                                        val currentLegacy = LocalTextToolbar.current
                                        val currentModern = checkNotNull(LocalTextContextMenuToolbarProvider.current)
                                        val currentHost = checkNotNull(LocalGlasmioTextEditingHost.current)
                                        val currentScope = rememberCoroutineScope()
                                        SideEffect {
                                            legacyAdapter = currentLegacy
                                            modernAdapter = currentModern
                                            editingHost = currentHost
                                            scope = currentScope
                                        }
                                        inner()
                                    })
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("native-select-all")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
        compose.waitUntil(10_000) { editingHost.active?.chromeVisible == true }
        listOf(false, true).forEachIndexed { index, modern ->
            compose.runOnIdle {
                forwardedSelectAll = null
                callbackRevision.value = 0
                value.value = TextFieldValue("Original text $index", TextRange(0, 5))
            }
            compose.runOnIdle {
                assertTrue("The real editor must own visible editing chrome", editingHost.active?.chromeVisible == true)
                if (modern) scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    modernAdapter.showTextContextMenu(nativeData)
                } else legacyAdapter.showMenu(Rect.Zero, null, null, null, { staleNativeCalls++ })
            }
            compose.waitUntil(10_000) { forwardedSelectAll != null }
            lateinit var selectAll: () -> Unit
            val latestText = "Current text for adapter $index"
            compose.runOnIdle {
                selectAll = checkNotNull(forwardedSelectAll)
                value.value = TextFieldValue(latestText, TextRange(0, 5))
                callbackRevision.value = 1
            }
            compose.onNodeWithTag("native-select-all").assertTextEquals(latestText)
            compose.runOnIdle {
                ownerUpdates.clear()
                selectAll()
                assertEquals(listOf(1), ownerUpdates)
                assertEquals(0, staleNativeCalls)
                assertEquals(TextRange(0, latestText.length), value.value.selection)
            }
            compose.onNodeWithContentDescription("Selection start").assertIsDisplayed()
            compose.onNodeWithContentDescription("Selection end").assertIsDisplayed()
        }
    }

    @Test
    fun selectionMenuKeepsPendingActionAcrossLegacyToModernHandoff() {
        val value = mutableStateOf(TextFieldValue("Hello World", TextRange(0, 5)))
        var legacyExecutions = 0
        var modernExecutions = 0
        var modernDataReads = 0
        var ownerCoordinates: LayoutCoordinates? = null
        lateinit var legacy: TextToolbar
        lateinit var modern: TextContextMenuProvider
        lateinit var scope: CoroutineScope
        val provider = object : TextContextMenuDataProvider {
            override fun position(destinationCoordinates: LayoutCoordinates) = contentBounds(destinationCoordinates).topLeft
            override fun contentBounds(destinationCoordinates: LayoutCoordinates) =
                destinationCoordinates.localBoundingBoxOf(checkNotNull(ownerCoordinates))

            override fun data(): TextContextMenuData {
                modernDataReads++
                return TextContextMenuData(listOf(
                    TextContextMenuItem(TextContextMenuKeys.CopyKey, "Modern copy") {},
                    TextContextMenuItem(TextContextMenuKeys.SelectAllKey, "Select All") {
                        modernExecutions++
                        value.value = value.value.copy(selection = TextRange(0, value.value.text.length))
                    },
                ))
            }
        }
        compose.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                GlasmioBackdropHost(
                    modifier = Modifier.fillMaxSize(),
                    sampledContent = { Box(Modifier.fillMaxSize().background(Color.White)) },
                    overlayContent = {
                        GlasmioTextSelectionHost {
                            val currentLegacy = LocalTextToolbar.current
                            val currentModern = checkNotNull(LocalTextContextMenuToolbarProvider.current)
                            val currentScope = rememberCoroutineScope()
                            SideEffect {
                                legacy = currentLegacy
                                modern = currentModern
                                scope = currentScope
                            }
                            // Keep the provider contract independent of editor focus and IME state.
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    value.value.text,
                                    Modifier.testTag("selection-owner")
                                        .onGloballyPositioned { ownerCoordinates = it },
                                )
                            }
                        }
                    },
                )
            }
        }
        compose.onNodeWithTag("selection-owner").assertIsDisplayed()
        compose.runOnIdle {
            legacy.showMenu(
                rect = checkNotNull(ownerCoordinates).boundsInRoot(),
                onCopyRequested = {},
                onPasteRequested = null,
                onCutRequested = null,
                onSelectAllRequested = {
                    legacyExecutions++
                    value.value = value.value.copy(selection = TextRange(0, value.value.text.length))
                },
            )
        }
        compose.onNodeWithContentDescription("More").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitUntil(10_000) { compose.onNodeWithText("Select All").isDisplayed() }
        compose.onNodeWithText("Select All").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithText("Select All")
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.runOnIdle {
                assertEquals("The legacy action must still be waiting for the closing animation", 0, legacyExecutions)
                scope.launch(start = CoroutineStart.UNDISPATCHED) { modern.showTextContextMenu(provider) }
            }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { assertTrue("The modern provider must take over before the close finishes", modernDataReads > 0) }
            compose.mainClock.advanceTimeBy(1_000)
            compose.runOnIdle {
                assertEquals("The action queued before the provider handoff must run exactly once", 1, legacyExecutions)
                assertEquals("The new provider must not replay the old click", 0, modernExecutions)
                assertEquals(TextRange(0, value.value.text.length), value.value.selection)
            }
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { legacy.hide() }
        }
    }

    @Test
    fun selectionToolbarRetainsDisabledContentDuringExitAndReversesWhenReopened() {
        lateinit var legacy: TextToolbar
        lateinit var modern: TextContextMenuProvider
        lateinit var scope: CoroutineScope
        var executions = 0
        val anchor = Rect(80f, 120f, 80f, 140f)
        val provider = object : TextContextMenuDataProvider {
            override fun position(destinationCoordinates: LayoutCoordinates) = anchor.bottomLeft
            override fun contentBounds(destinationCoordinates: LayoutCoordinates) = anchor
            override fun data() = TextContextMenuData(listOf(
                TextContextMenuItem(TextContextMenuKeys.CopyKey, "Copy") {
                    executions++
                    close()
                },
            ))
        }
        compose.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                GlasmioBackdropProvider(emptyBackdrop()) {
                    GlasmioTextSelectionHost {
                        val currentLegacy = LocalTextToolbar.current
                        val currentModern = checkNotNull(LocalTextContextMenuToolbarProvider.current)
                        val currentScope = rememberCoroutineScope()
                        SideEffect {
                            legacy = currentLegacy
                            modern = currentModern
                            scope = currentScope
                        }
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        listOf(false, true).forEachIndexed { index, useModern ->
            fun show() {
                if (useModern) scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    modern.showTextContextMenu(provider)
                } else legacy.showMenu(anchor, { executions++ }, null, null, null)
            }
            compose.runOnIdle { show() }
            compose.onNodeWithText("Copy").assertIsDisplayed().assertIsEnabled()
            val fullWidth = compose.onNodeWithText("Copy").fetchSemanticsNode().boundsInRoot.width
            compose.mainClock.autoAdvance = false
            try {
                compose.runOnIdle { legacy.hide() }
                compose.mainClock.advanceTimeByFrame()
                compose.mainClock.advanceTimeBy(32)
                compose.onNodeWithText("Copy").assertExists().assertIsNotEnabled()
                val exitingWidth = compose.onNodeWithText("Copy").fetchSemanticsNode().boundsInRoot.width
                assertTrue("Closing must shrink the retained toolbar", exitingWidth > 0f && exitingWidth < fullWidth)
                compose.runOnIdle { assertEquals(TextToolbarStatus.Hidden, legacy.status) }

                compose.runOnIdle { show() }
                compose.mainClock.advanceTimeByFrame()
                val resumedWidth = compose.onNodeWithText("Copy").fetchSemanticsNode().boundsInRoot.width
                assertEquals("Reopening must continue at the current scale", exitingWidth, resumedWidth, 1f)
                compose.mainClock.advanceTimeBy(240)
                compose.onNodeWithText("Copy").assertIsDisplayed().assertIsEnabled()
                compose.runOnIdle { assertEquals(TextToolbarStatus.Shown, legacy.status) }

                compose.onNodeWithText("Copy").performSemanticsAction(SemanticsActions.OnClick) { it() }
                compose.mainClock.advanceTimeByFrame()
                compose.mainClock.advanceTimeBy(32)
                compose.onNodeWithText("Copy").assertExists().assertIsNotEnabled()
                compose.runOnIdle { assertEquals(index + 1, executions) }
                compose.mainClock.advanceTimeBy(240)
                compose.onNodeWithText("Copy").assertDoesNotExist()
            } finally {
                compose.mainClock.autoAdvance = true
                compose.runOnIdle { legacy.hide() }
            }
        }
    }

    @Test
    fun menuTintReturnsToItsActualGlassAnchorAcrossSourceMaterials() {
        val scene = mutableStateOf(0)
        val showMorph = mutableStateOf(false)
        val motion = LiquidMenuMorphMotion()
        val anchor = GlasmioMenuAnchorState()
        var hostOrigin = Offset.Zero
        var source: Rect? = null
        var target: Rect? = null
        var density = 1f
        val names = listOf("Navigation", "Small", "Medium", "Clear", "Custom tint", "Prominent")
        compose.setContent {
            density = LocalDensity.current.density
            val material = when (scene.value) {
                1 -> NavigationGlassMaterial.RegularSmall
                2 -> NavigationGlassMaterial.RegularMedium
                3 -> NavigationGlassMaterial.Clear
                else -> null
            }
            GlasmioTheme(isDark = false) {
                GlasmioBackdropHost(
                    modifier = Modifier.size(330.dp).testTag("source-tint-handoff")
                        .onGloballyPositioned { hostOrigin = it.localToScreen(Offset.Zero) },
                    sampledContent = { Box(Modifier.fillMaxSize().background(Color(0xFF315473))) },
                    overlayContent = { backdrop ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            GlassSurface(
                                modifier = Modifier.size(144.dp, 44.dp).glasmioMenuAnchor(anchor)
                                    .onGloballyPositioned { source = Rect(it.localToScreen(Offset.Zero), Size(it.size.width.toFloat(), it.size.height.toFloat())) },
                                material = material,
                                navigationSurfaceColor = if (scene.value == 4) Color(0xFF337CCC) else null,
                                emphasis = if (scene.value == 5) GlassEmphasis.Prominent else GlassEmphasis.Regular,
                            ) {}
                            if (showMorph.value) GlasmioContextMenu(
                                visible = true, compact = true, menuWidth = 160.dp, heightOverride = 100.dp,
                                backdrop = backdrop, exportChildBackdrop = false, morphMotion = motion,
                                morphSourceHasGlassBackground = anchor.sourceHasGlassBackground,
                                morphSourceMaterial = anchor.sourceMaterial,
                                morphSourceFrostRadius = anchor.sourceFrostRadius,
                                morphSourceMaterialPaint = anchor.sourceMaterialPaint,
                                morphSourceUsesShadowLift = anchor.sourceUsesShadowLift,
                                morphSourceUsesInnerShadows = anchor.sourceUsesInnerShadows,
                                onMenuBoundsChanged = { target = it },
                            ) {}
                        }
                    },
                )
            }
        }
        names.forEachIndexed { index, name ->
            compose.runOnIdle {
                anchor.release()
                showMorph.value = false
                scene.value = index
                target = null
            }
            compose.waitForIdle()
            val static = compose.onNodeWithTag("source-tint-handoff").captureToImage().toPixelMap()
            compose.runOnIdle { anchor.acquire(); showMorph.value = true }
            compose.waitForIdle()
            lateinit var sample: Rect
            compose.runOnIdle {
                val from = requireNotNull(source)
                sample = from.translate(-hostOrigin)
                motion.initialize(from, requireNotNull(target), 22f * density, density)
                motion.retarget(true); motion.advanceBy(3f)
            }
            compose.onNodeWithTag("source-tint-handoff").captureToImage()
            compose.runOnIdle { motion.retarget(false); motion.advanceBy(3f) }
            val closing = compose.onNodeWithTag("source-tint-handoff").captureToImage().toPixelMap()
            var error = 0.0
            var maximum = 0f
            var count = 0
            for (y in (sample.top + 16f * density).roundToInt() until (sample.bottom - 16f * density).roundToInt()) {
                for (x in (sample.left + 28f * density).roundToInt() until (sample.right - 28f * density).roundToInt()) {
                    val a = static[x, y]
                    val b = closing[x, y]
                    val difference = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
                    error += difference
                    maximum = maxOf(maximum, difference)
                    count++
                }
            }
            assertTrue("$name tint must match its live anchor before release: max=$maximum mean=${error / count}",
                maximum < 4f / 255f && error / count < 2.0 / 255.0)
        }
        compose.runOnIdle { anchor.release() }
    }

    @Test
    fun glassMenuClosingRetainsItsSourceMaterialShadowUntilTheToolbarReturns() {
        val motion = LiquidMenuMorphMotion()
        val reference = mutableStateOf(false)
        var hostOrigin = Offset.Zero
        var target: Rect? = null
        var sourceLocal = Rect.Zero
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            val shadow = remember {
                NavigationDropShadowRenderer(shadowRadius = 48.dp, shadowOffsetY = 8.dp, shadowAlpha = 0.25f)
            }
            DisposableEffect(shadow) { onDispose { shadow.release() } }
            GlasmioTheme(isDark = false) {
                Box(Modifier.size(330.dp).background(Color.White).testTag("source-shadow-handoff")
                    .onGloballyPositioned { hostOrigin = it.localToScreen(Offset.Zero) }
                    .drawWithContent {
                        drawContent()
                        if (reference.value) shadow.draw(this, ContinuousRoundedRectangle(22.dp), sourceLocal)
                    }, contentAlignment = Alignment.Center) {
                    if (!reference.value) GlasmioContextMenu(
                        visible = true, compact = true, menuWidth = 160.dp, heightOverride = 100.dp,
                        backdrop = emptyBackdrop(), exportChildBackdrop = false, morphMotion = motion,
                        morphSourceMaterial = NavigationGlassMaterial.RegularMedium,
                        onMenuBoundsChanged = { target = it },
                    ) {}
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val destination = requireNotNull(target)
            val sourceSize = Size(144f * density, 44f * density)
            val source = Rect(destination.center - Offset(sourceSize.width, sourceSize.height) / 2f, sourceSize)
            sourceLocal = source.translate(-hostOrigin)
            motion.initialize(source, destination, 22f * density, density)
            motion.retarget(true); motion.advanceBy(3f)
            motion.retarget(false); motion.advanceBy(3f)
            assertEquals(0f, motion.frame.contentProgress, 0.001f)
        }
        val closing = compose.onNodeWithTag("source-shadow-handoff").captureToImage().toPixelMap()
        compose.runOnIdle { reference.value = true }
        val static = compose.onNodeWithTag("source-shadow-handoff").captureToImage().toPixelMap()
        val strips = listOf(
            Rect(sourceLocal.center.x - 12f * density, sourceLocal.top - 34f * density, sourceLocal.center.x + 12f * density, sourceLocal.top - 18f * density),
            Rect(sourceLocal.center.x - 12f * density, sourceLocal.bottom + 18f * density, sourceLocal.center.x + 12f * density, sourceLocal.bottom + 34f * density),
        )
        strips.forEach { band ->
            var observed = 0.0
            var expected = 0.0
            var samples = 0
            for (y in band.top.roundToInt() until band.bottom.roundToInt()) {
                for (x in band.left.roundToInt() until band.right.roundToInt()) {
                    observed += 1f - closing[x, y].red
                    expected += 1f - static[x, y].red
                    samples++
                }
            }
            assertTrue("The static toolbar reference must have a visible exterior shadow", expected / samples > 0.01)
            assertTrue("The final morph frame cannot lose its source shadow", observed > expected * 0.8)
            assertTrue("The final morph shadow must approach the static toolbar", abs(observed - expected) / samples < 0.015)
        }
    }

    @Test
    fun menuMaterialFrostReturnsToTheSourceOverrideWithoutRecreatingItsCapture() {
        val motion = LiquidMenuMorphMotion()
        val frost = mutableStateOf(0.dp)
        var hostOrigin = Offset.Zero
        var target: Rect? = null
        var sourceLocal = Rect.Zero
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            GlasmioTheme(isDark = false) {
                GlasmioBackdropHost(
                    modifier = Modifier.size(330.dp).testTag("source-frost-handoff")
                        .onGloballyPositioned { hostOrigin = it.localToScreen(Offset.Zero) },
                    sampledContent = {
                        Canvas(Modifier.fillMaxSize()) {
                            val cell = 4.dp.toPx()
                            for (y in 0..(size.height / cell).toInt()) for (x in 0..(size.width / cell).toInt()) {
                                drawRect(if ((x + y) % 2 == 0) Color.Black else Color.White,
                                    Offset(x * cell, y * cell), Size(cell, cell))
                            }
                        }
                    },
                    overlayContent = { backdrop ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            GlasmioContextMenu(
                                visible = true, compact = true, menuWidth = 160.dp, heightOverride = 100.dp,
                                backdrop = backdrop, exportChildBackdrop = false, morphMotion = motion,
                                morphSourceMaterial = NavigationGlassMaterial.RegularMedium,
                                morphSourceFrostRadius = frost.value,
                                onMenuBoundsChanged = { target = it },
                            ) {}
                        }
                    },
                )
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val destination = requireNotNull(target)
            val sourceSize = Size(144f * density, 44f * density)
            val source = Rect(destination.center - Offset(sourceSize.width, sourceSize.height) / 2f, sourceSize)
            sourceLocal = source.translate(-hostOrigin)
            motion.initialize(source, destination, 22f * density, density)
            motion.retarget(true); motion.advanceBy(3f)
            motion.retarget(false); motion.advanceBy(3f)
        }
        val sharp = compose.onNodeWithTag("source-frost-handoff").captureToImage().toPixelMap()
        compose.runOnIdle { frost.value = 16.dp }
        val frosted = compose.onNodeWithTag("source-frost-handoff").captureToImage().toPixelMap()
        fun contrast(image: androidx.compose.ui.graphics.PixelMap): Float {
            var low = 1f
            var high = 0f
            val region = Rect(sourceLocal.left + 36f * density, sourceLocal.top + 8f * density,
                sourceLocal.right - 36f * density, sourceLocal.bottom - 8f * density)
            for (y in region.top.roundToInt() until region.bottom.roundToInt()) {
                for (x in region.left.roundToInt() until region.right.roundToInt()) {
                    low = minOf(low, image[x, y].red); high = maxOf(high, image[x, y].red)
                }
            }
            return high - low
        }
        val sharpContrast = contrast(sharp)
        val softContrast = contrast(frosted)
        assertTrue("A zero-frost source must retain its real backdrop detail", sharpContrast > 0.08f)
        assertTrue("Updating the source frost must update the retained capture's real blur", softContrast < sharpContrast * 0.4f)
    }

    @Test
    fun clearLensPreservesDarkSourceContrastWithoutChangingNavigationDefaults() {
        compose.setContent {
            GlasmioTheme(isDark = false) {
                io.github.neoruaa.glasmio.glass.GlasmioBackdropHost(
                    modifier = Modifier.fillMaxSize(),
                    sampledContent = { Box(Modifier.fillMaxSize().background(Color.Black)) },
                    overlayContent = {
                        Row(Modifier.padding(24.dp)) {
                            listOf(false, true).forEach { preserve ->
                                io.github.neoruaa.glasmio.glass.GlassSurface(
                                    modifier = Modifier.size(128.dp, 96.dp).testTag(if (preserve) "clear-original-source" else "clear-navigation-source"),
                                    material = io.github.neoruaa.glasmio.glass.NavigationGlassMaterial.Clear,
                                    frostRadius = 0.dp,
                                    preserveBackdropColors = preserve,
                                    refractionHeight = 8.dp,
                                    refractionAmount = 24.dp,
                                ) {}
                                Spacer(Modifier.width(12.dp))
                            }
                        }
                    },
                )
            }
        }
        val original = compose.onNodeWithTag("clear-original-source").captureToImage().toPixelMap()
        val navigation = compose.onNodeWithTag("clear-navigation-source").captureToImage().toPixelMap()
        val dark = original[original.width / 2, original.height / 2]
        val lifted = navigation[navigation.width / 2, navigation.height / 2]
        assertTrue("The magnifier must retain dark source contrast", dark.red < 0.2f)
        assertTrue("Existing navigation still keeps its calibrated shadow lift", lifted.red > dark.red + 0.15f)
    }

    @Test
    fun editingOverlaysChooseTheAvailableSideWithinSystemInsets() {
        val safe = Rect(0f, 32f, 402f, 800f)
        // A lens near the status bar goes below, then returns above in open space.
        assertEquals(164f, editingOverlayTop(Rect(20f, 140f, 30f, 140f), safe, 96f, 24f, true), 0f)
        assertEquals(280f, editingOverlayTop(Rect(20f, 400f, 30f, 400f), safe, 96f, 24f, true), 0f)
        // A text menu near the navigation bar or keyboard goes above the selected line.
        assertEquals(684f, editingOverlayTop(Rect(20f, 740f, 100f, 762f), safe, 44f, 12f, false), 0f)
        val keyboardSafe = Rect(0f, 32f, 402f, 450f)
        assertEquals(324f, editingOverlayTop(Rect(20f, 380f, 100f, 402f), keyboardSafe, 44f, 12f, false), 0f)
        // A constrained viewport chooses the larger side and clamps its final bounds.
        assertEquals(32f, editingOverlayTop(Rect(20f, 90f, 100f, 112f), Rect(0f, 32f, 402f, 150f), 96f, 12f, false), 0f)
    }

    @Test
    fun opaqueLensSourceTintPreservesSelectedTabPixelsWithoutInnerFilters() {
        val progress = mutableFloatStateOf(0f)
        val press = mutableFloatStateOf(0f)
        val paletteIndex = mutableStateOf(0)
        val palettes = listOf(
            defaultGlassColors(false).copy(accent = Color(0xFFFF3B30), content = Color(0xFF263648)),
            defaultGlassColors(true).copy(accent = Color(0xFF51B6FF), content = Color.White),
            defaultGlassColors(false).copy(accent = Color(0xFFFF3B30).copy(alpha = 0.6f)),
            defaultGlassColors(false).copy(content = Color(0xFF263648).copy(alpha = 0.55f)),
        )
        val backing = Color(0xFF2E233B)
        compose.setContent {
            val colors = palettes[paletteIndex.value]
            val selectedFilter = remember(colors) {
                derivedStateOf {
                    val compactTint = androidx.compose.ui.graphics.lerp(colors.accent, colors.content, progress.floatValue)
                    ColorFilter.tint(androidx.compose.ui.graphics.lerp(compactTint, colors.content, press.floatValue))
                }
            }
            val optimizedFilter = navigationLensSourceColorFilter(colors, selectedFilter)
            val opaque = colors.accent.alpha == 1f && colors.content.alpha == 1f
            assertTrue("Only fully opaque source colors can skip the inner filter",
                if (opaque) optimizedFilter == null else optimizedFilter === selectedFilter)
            val sourceScale = remember {
                { lerp(1f, 1.2f, press.floatValue) * (1f - progress.floatValue).coerceAtLeast(0.82f) }
            }
            val labelAlpha = remember { { (1f - progress.floatValue * 1.35f).coerceIn(0f, 1f) } }
            val lateScale = remember {
                { lerp(1f, (22f / 28f) / 0.82f, compactIconLateProgress(progress.floatValue)) }
            }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalGlassColors provides colors, LocalLiquidTabScale provides sourceScale) {
                MaterialTheme {
                    Row {
                        listOf(false, true).forEachIndexed { index, optimized ->
                            if (index > 0) Spacer(Modifier.width(16.dp))
                            Box(
                                Modifier.size(160.dp, 80.dp).background(backing)
                                    .testTag(if (optimized) "optimizedLensSourceTab" else "referenceLensSourceTab"),
                                contentAlignment = Alignment.Center,
                            ) {
                                val width = lerp(160f, 48f, progress.floatValue).dp - 8.dp
                                Box(
                                    Modifier.width(width).height(56.dp).graphicsLayer {
                                        colorFilter = ColorFilter.tint(colors.accent)
                                    },
                                ) {
                                    MorphingSelectedNavigationItem(
                                        item = library,
                                        compactProgress = progress,
                                        fullWidthPx = with(density) { 160.dp.toPx() },
                                        compactWidthPx = with(density) { 48.dp.toPx() },
                                        itemCount = 3,
                                        selectedIndex = 1,
                                        isLtr = true,
                                        horizontalInsetPx = with(density) { 4.dp.toPx() },
                                        selectedContentLateScale = lateScale,
                                        labelAlpha = labelAlpha,
                                        selectedIconColorFilter = if (optimized) optimizedFilter else selectedFilter,
                                        contentColor = colors.content,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        palettes.indices.forEach { palette ->
            listOf(0f, 0.5f, 0.99f).forEach { compact ->
                listOf(0f, 0.5f, 0.99f).forEach { pressed ->
                    compose.runOnIdle {
                        paletteIndex.value = palette
                        progress.floatValue = compact
                        press.floatValue = pressed
                    }
                    compose.waitForIdle()
                    val reference = compose.onNodeWithTag("referenceLensSourceTab").captureToImage().toPixelMap()
                    val actual = compose.onNodeWithTag("optimizedLensSourceTab").captureToImage().toPixelMap()
                    assertEquals(reference.width, actual.width)
                    assertEquals(reference.height, actual.height)
                    var maximum = 0
                    var total = 0L
                    var count = 0
                    var contentPixels = 0
                    var labelPixels = 0
                    var maximumPoint = Offset.Zero
                    var maximumExpected = Color.Transparent
                    var maximumObserved = Color.Transparent
                    fun compare(expected: Float, observed: Float, x: Int, y: Int) {
                        val error = abs((expected * 255f).roundToInt() - (observed * 255f).roundToInt())
                        if (error > maximum) {
                            maximum = error
                            maximumPoint = Offset(x.toFloat(), y.toFloat())
                            maximumExpected = reference[x, y]
                            maximumObserved = actual[x, y]
                        }
                        total += error
                        count++
                    }
                    repeat(reference.height) { y ->
                        repeat(reference.width) { x ->
                            val expected = reference[x, y]
                            val observed = actual[x, y]
                            compare(expected.red, observed.red, x, y)
                            compare(expected.green, observed.green, x, y)
                            compare(expected.blue, observed.blue, x, y)
                            compare(expected.alpha, observed.alpha, x, y)
                            val contentDifference = maxOf(
                                abs(expected.red - backing.red), abs(expected.green - backing.green),
                                abs(expected.blue - backing.blue),
                            )
                            if (contentDifference > 8f / 255f) {
                                contentPixels++
                                if (y > reference.height * 0.6f) labelPixels++
                            }
                        }
                    }
                    val label = "Lens source tab: palette=$palette, compact=$compact, press=$pressed"
                    assertTrue("$label must contain the real selected icon", contentPixels > 20)
                    if (compact == 0f) assertTrue("$label must contain the visible label", labelPixels > 10)
                    assertTrue(
                        "$label maximum RGBA error: $maximum at $maximumPoint, " +
                            "expected=$maximumExpected, actual=$maximumObserved",
                        maximum <= 2,
                    )
                    assertTrue("$label mean RGBA error: ${total.toDouble() / count}", total.toDouble() / count <= 1.0)
                }
            }
        }
    }

    @Test
    fun selectedTabMeasurementMatchesOriginalCompactLayoutAtFractionalDensities() {
        val progress = mutableFloatStateOf(0f)
        val pixelDensity = mutableFloatStateOf(1.25f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(pixelDensity.floatValue)) {
                GlasmioTheme(isDark = false) {
                    val density = LocalDensity.current
                    val colors = LocalGlassColors.current
                    val fullWidthPx = with(density) { 160.dp.toPx() }
                    val compactWidthPx = with(density) { 44.dp.toPx() }
                    val insetPx = with(density) { 4.dp.toPx() }
                    val contentWidthPx = fullWidthPx - insetPx * 2f
                    val protectedRadius = with(density) { 28.dp.toPx() / 2f + 4.dp.toPx() }
                    val maximumWidth = (2f * (contentWidthPx - 2 * protectedRadius) / 4)
                        .coerceIn(1f, contentWidthPx)
                    val expandedWidth = (contentWidthPx / 3)
                        .coerceAtLeast(with(density) { 75.dp.toPx() })
                        .coerceAtMost(maximumWidth)
                    val tabScale = remember { { (1f - progress.floatValue).coerceAtLeast(0.82f) } }
                    val labelAlpha = remember { { (1f - progress.floatValue * 1.35f).coerceIn(0f, 1f) } }
                    val lateScale = remember {
                        { lerp(1f, (22f / 28f) / 0.82f, compactIconLateProgress(progress.floatValue)) }
                    }
                    CompositionLocalProvider(LocalLiquidTabScale provides tabScale) {
                        Column {
                            listOf(false, true).forEach { measured ->
                                Box(
                                    Modifier.size(160.dp, 80.dp).background(Color.White)
                                        .testTag(if (measured) "measuredCompactTab" else "originalCompactTab"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (measured) {
                                        MorphingSelectedNavigationItem(
                                            item = library,
                                            compactProgress = progress,
                                            fullWidthPx = fullWidthPx,
                                            compactWidthPx = compactWidthPx,
                                            itemCount = 3,
                                            selectedIndex = 1,
                                            isLtr = true,
                                            horizontalInsetPx = insetPx,
                                            selectedContentLateScale = lateScale,
                                            labelAlpha = labelAlpha,
                                            selectedIconColorFilter = null,
                                            contentColor = colors.content,
                                        )
                                    } else {
                                        // The original composition-driven width, padding and label
                                        // collapse are the pixel oracle for the deferred layout.
                                        val compact = progress.floatValue.coerceIn(0f, 1f)
                                        val visibility = labelAlpha()
                                        val width = with(density) {
                                            lerp(expandedWidth, (compactWidthPx - 2f * insetPx) / 3, compact)
                                                .coerceAtLeast(28.dp.toPx()).toDp()
                                        }
                                        Box(
                                            Modifier.fillMaxSize().graphicsLayer {
                                                val expandedStep = (contentWidthPx - expandedWidth) / 2
                                                val expandedCenter = expandedWidth / 2f + expandedStep
                                                val compactContentWidth = compactWidthPx - 2f * insetPx
                                                val currentContentWidth = lerp(fullWidthPx, compactWidthPx, compact) -
                                                    2f * insetPx
                                                translationX = lerp(expandedCenter, compactContentWidth / 2f, compact) -
                                                    currentContentWidth / 2f
                                                val scale = tabScale() * lateScale()
                                                scaleX = scale
                                                scaleY = scale
                                            },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Column(
                                                Modifier.width(width).fillMaxHeight(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                            ) {
                                                SfIcon(
                                                    symbol = library.symbol,
                                                    contentDescription = library.contentDescription,
                                                    tint = colors.content,
                                                    size = 28.dp,
                                                    weight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(top = 3.dp * visibility)
                                                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                                                )
                                                Text(
                                                    text = library.label,
                                                    color = colors.content,
                                                    style = IosTypography.caption.copy(fontSize = 10.sp, lineHeight = 13.sp),
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.layout { measurable, constraints ->
                                                        val placeable = measurable.measure(constraints)
                                                        val height = (placeable.height * visibility).roundToInt()
                                                            .coerceIn(constraints.minHeight, constraints.maxHeight)
                                                        layout(placeable.width, height) { placeable.placeRelative(0, 0) }
                                                    }.padding(top = 1.dp, bottom = 4.dp).graphicsLayer {
                                                        alpha = visibility
                                                        compositingStrategy = CompositingStrategy.Offscreen
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        listOf(1.25f, 2.75f, 3.25f).forEach { density ->
            listOf(0f, 0.35f, 0.74f, 0.98f, 1f, 0.5f, 0f).forEach { compact ->
                compose.runOnIdle {
                    pixelDensity.floatValue = density
                    progress.floatValue = compact
                }
                val expected = compose.onNodeWithTag("originalCompactTab").captureToImage().toPixelMap()
                val actual = compose.onNodeWithTag("measuredCompactTab").captureToImage().toPixelMap()
                assertEquals(expected.width, actual.width)
                assertEquals(expected.height, actual.height)
                var maximumError = 0f
                repeat(expected.height) { y ->
                    repeat(expected.width) { x ->
                        val before = expected[x, y]
                        val after = actual[x, y]
                        maximumError = maxOf(maximumError, abs(before.red - after.red),
                            abs(before.green - after.green), abs(before.blue - after.blue), abs(before.alpha - after.alpha))
                    }
                }
                assertTrue("density=$density, compact=$compact, maximum RGBA error=$maximumError",
                    maximumError <= 1f / 255f)
            }
        }
    }

    @Test
    fun visibleTabSemanticsFollowSelectionAndCompactState() {
        val selectedKey = mutableStateOf("home")
        val compact = mutableFloatStateOf(0f)
        compose.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                GlasmioNavigationBar(
                    items = listOf(
                        GlasmioTabItem("home", "Home", SfSymbol.House),
                        library,
                        GlasmioTabItem("favorites", "Favorites", SfSymbol.Heart),
                    ),
                    selectedKey = selectedKey.value,
                    onSelected = { selectedKey.value = it },
                    onExpand = { compact.floatValue = 0f },
                    compactProgress = compact,
                    compactSize = 48.dp,
                    backdrop = emptyBackdrop(),
                )
            }
        }
        val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        compose.onAllNodes(tab).assertCountEquals(3)
        compose.onNode(tab and hasContentDescription("Home")).assertIsSelected()
        compose.onNode(tab and hasContentDescription("Library")).assertIsNotSelected().performClick()
        compose.onNode(tab and hasContentDescription("Library")).assertIsSelected()
        compose.onNode(tab and hasContentDescription("Home")).assertIsNotSelected()
        compose.runOnIdle { compact.floatValue = 1f }
        compose.onAllNodes(tab).assertCountEquals(1)
        compose.onNode(tab and hasContentDescription("Library")).assertIsSelected()
    }

    @Test
    fun plainMenuClosingOpacityPreservesShadowBeyondEveryLogicalEdge() {
        val motion = LiquidMenuMorphMotion()
        val glassSourceReference = mutableStateOf(false)
        var hostScreenOrigin = Offset.Zero
        var targetScreenBounds: Rect? = null
        var pixelDensity = 1f
        compose.setContent {
            pixelDensity = LocalDensity.current.density
            GlasmioTheme(isDark = false) {
                Box(
                    Modifier.size(330.dp).background(Color.White)
                        .testTag("closingMenuShadowHost")
                        .onGloballyPositioned { hostScreenOrigin = it.localToScreen(Offset.Zero) },
                    contentAlignment = Alignment.Center,
                ) {
                    GlasmioContextMenu(
                        visible = true,
                        compact = true,
                        menuWidth = 160.dp,
                        heightOverride = 100.dp,
                        backdrop = emptyBackdrop(),
                        exportChildBackdrop = false,
                        morphMotion = motion,
                        morphSourceHasGlassBackground = glassSourceReference.value,
                        onMenuBoundsChanged = { targetScreenBounds = it },
                    ) { }
                }
            }
        }
        compose.waitForIdle()
        lateinit var logicalMenuBounds: Rect
        compose.runOnIdle {
            val target = requireNotNull(targetScreenBounds)
            logicalMenuBounds = target.translate(-hostScreenOrigin)
            val sourceSize = Size(84f * pixelDensity, 44f * pixelDensity)
            val source = Rect(target.center - Offset(sourceSize.width, sourceSize.height) / 2f, sourceSize)
            motion.initialize(source, target, 34f * pixelDensity, pixelDensity)
            motion.retarget(true)
            motion.advanceBy(3f)
            motion.retarget(false)
            motion.advanceBy(0.06f)
            assertTrue("The fixed frame must exercise fractional closing material opacity",
                motion.frame.contentProgress in 0.05f..0.99f)
            val sourceLobe = requireNotNull(motion.frame.sourceLobe)
            assertTrue("The reference source must remain inside the menu's incoming skin",
                motion.frame.bounds.contains(sourceLobe.topLeft) && motion.frame.bounds.contains(sourceLobe.bottomRight))
        }
        compose.waitForIdle()
        val actual = compose.onNodeWithTag("closingMenuShadowHost").captureToImage().toPixelMap()
        // A fully enclosed glass source uses the same shadow geometry, with opacity
        // applied directly to its native shadow node instead of the plain material group.
        compose.runOnIdle { glassSourceReference.value = true }
        compose.waitForIdle()
        val reference = compose.onNodeWithTag("closingMenuShadowHost").captureToImage().toPixelMap()
        assertEquals(reference.width, actual.width)
        assertEquals(reference.height, actual.height)
        val gap = 6f * pixelDensity
        val band = 8f * pixelDensity
        val span = 12f * pixelDensity
        val menu = logicalMenuBounds
        val strips = linkedMapOf(
            "top" to Rect(menu.center.x - span, menu.top - gap - band, menu.center.x + span, menu.top - gap),
            "right" to Rect(menu.right + gap, menu.center.y - span, menu.right + gap + band, menu.center.y + span),
            "bottom" to Rect(menu.center.x - span, menu.bottom + gap, menu.center.x + span, menu.bottom + gap + band),
            "left" to Rect(menu.left - gap - band, menu.center.y - span, menu.left - gap, menu.center.y + span),
        )
        strips.forEach { (edge, strip) ->
            val left = strip.left.roundToInt()
            val top = strip.top.roundToInt()
            val right = strip.right.roundToInt()
            val bottom = strip.bottom.roundToInt()
            assertTrue("The $edge shadow sample must fit outside the logical menu in the capture host",
                left >= 0 && top >= 0 && right <= actual.width && bottom <= actual.height && right > left && bottom > top)
            var actualShade = 0.0
            var referenceShade = 0.0
            var totalError = 0.0
            var maximumError = 0f
            for (y in top until bottom) for (x in left until right) {
                val expected = reference[x, y]
                val observed = actual[x, y]
                actualShade += (3f - observed.red - observed.green - observed.blue) / 3f
                referenceShade += (3f - expected.red - expected.green - expected.blue) / 3f
                val error = maxOf(abs(expected.red - observed.red), abs(expected.green - observed.green), abs(expected.blue - observed.blue))
                totalError += error
                maximumError = maxOf(maximumError, error)
            }
            val pixels = (right - left) * (bottom - top)
            assertTrue("The native reference must contain a visible $edge shadow outside the logical menu",
                referenceShade / pixels > 2.0 / 255.0)
            assertTrue("Plain material opacity must retain the $edge shadow outside its menu-sized RenderNode",
                actualShade / pixels > 2.0 / 255.0)
            assertTrue("The $edge shadow must match native post-blur opacity: max=$maximumError, mean=${totalError / pixels}",
                maximumError <= 3f / 255f && totalError / pixels <= 1.5 / 255.0)
        }
    }

    @Test
    fun popupShadowShellPreservesAnchorEdgesAboveAndBelow() {
        val expanded = mutableStateOf(false)
        val anchorAtBottom = mutableStateOf(false)
        val anchorBounds = mutableStateOf<Rect?>(null)
        val menuBounds = mutableStateOf<Rect?>(null)
        val closedCount = mutableStateOf(0)
        var pixelDensity = 1f
        compose.setContent {
            pixelDensity = LocalDensity.current.density
            GlasmioTheme(isDark = false) {
                Box(Modifier.fillMaxSize().padding(100.dp)) {
                    GlasmioMenu(
                        expanded = expanded.value,
                        onExpandedChange = { expanded.value = it },
                        itemCount = 3,
                        modifier = Modifier.align(if (anchorAtBottom.value) Alignment.BottomEnd else Alignment.TopEnd),
                        menuWidth = 238.dp,
                        backdrop = emptyBackdrop(),
                        exportChildBackdrop = false,
                        onMenuBoundsChanged = { menuBounds.value = it },
                        onClosed = { closedCount.value++ },
                        anchor = {
                            Box(Modifier.size(80.dp, 44.dp).onGloballyPositioned {
                                anchorBounds.value = Rect(it.localToScreen(Offset.Zero), Size(it.size.width.toFloat(), it.size.height.toFloat()))
                            })
                        },
                    ) { childBackdrop, close ->
                        repeat(3) { index ->
                            GlasmioMenuItem("Item ${index + 1}", close, backdrop = childBackdrop)
                        }
                    }
                }
            }
        }
        listOf(false, true).forEachIndexed { cycle, above ->
            compose.runOnIdle {
                assertEquals("The previous Popup must finish closing before moving its anchor", cycle, closedCount.value)
                anchorAtBottom.value = above
                menuBounds.value = null
            }
            compose.waitForIdle()
            compose.runOnIdle { expanded.value = true }
            compose.waitUntil(timeoutMillis = 10_000) { menuBounds.value != null }
            compose.waitForIdle()
            compose.runOnIdle {
                val anchor = requireNotNull(anchorBounds.value)
                val menu = requireNotNull(menuBounds.value)
                assertTrue("The test anchor must leave the requested menu clear of horizontal clamping",
                    anchor.right - 238f * pixelDensity >= 12f * pixelDensity)
                assertEquals("The real Popup must retain its requested menu width", 238f * pixelDensity, menu.width, 1f)
                assertEquals("The real Popup must retain its requested menu height", 152f * pixelDensity, menu.height, 1f)
                // Popup positions use integer pixels; screen-coordinate callbacks use floats.
                assertEquals("The shadow shell must preserve right alignment", anchor.right, menu.right, 1f)
                if (above) {
                    assertEquals("An upward Popup must end at its source's bottom edge", anchor.bottom, menu.bottom, 1f)
                } else {
                    assertEquals("A downward Popup must begin at its source's top edge", anchor.top, menu.top, 1f)
                }
                expanded.value = false
            }
            compose.waitUntil(timeoutMillis = 10_000) { closedCount.value == cycle + 1 }
            compose.waitForIdle()
        }
    }

}
