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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
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

    private fun rawClockPlacementControl(): Boolean =
        androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("scaffoldRawBoth") == "true"

    private fun scaffoldProbe(message: String) {
        android.util.Log.i("ScaffoldFrameProbe", message)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(2,
            android.os.Bundle().apply { putString("stream", "ScaffoldFrameProbe: $message\n") })
    }

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

    @Test fun clockOnlyFramesSkipScaffoldButKeepIndependentPublicReadersLive() {
        val rawControl = rawClockPlacementControl()
        var headerCalls = 0
        var bodyCalls = 0
        var bottomCalls = 0
        var dark by mutableStateOf(false)
        var titleVersion by mutableIntStateOf(0)
        var clockReaders by mutableIntStateOf(0)
        var headerTime = -1L
        var actionTime = -1L
        var bodyTime = -1L
        var bottomTime = -1L
        var headerColor = Color.Unspecified
        val clicked = mutableListOf<GlasmioNavigationFrame>()
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.setContent {
            GlasmioTheme(isDark = dark, appearance = GlassAppearance(reduceTransparency = true)) {
                GlasmioNavigationScaffold(motion, header = { entry ->
                    headerCalls++
                    if (rawControl || clockReaders and 1 != 0) {
                        val raw = motion.frame.value
                        SideEffect { headerTime = raw.playTimeNanos }
                    }
                    val colors = LocalGlassColors.current
                    SideEffect { headerColor = colors.content }
                    GlasmioHeaderSpec("Title $entry:$titleVersion", colors = colors,
                        trailing = listOf(GlasmioHeaderGroup("primary", listOf(
                            GlasmioHeaderAction("action", "stable", "Action $entry", {
                                clicked += motion.frame.value
                            }) {
                                if (clockReaders and 2 != 0) {
                                    val raw = motion.frame.value
                                    SideEffect { actionTime = raw.playTimeNanos }
                                }
                                Text("M")
                            },
                        ))))
                }, bottomBar = {
                    SideEffect { bottomCalls++ }
                    if (clockReaders and 8 != 0) {
                        val raw = motion.frame.value
                        SideEffect { bottomTime = raw.playTimeNanos }
                    }
                }) {
                    SideEffect { bodyCalls++ }
                    if (clockReaders and 4 != 0) {
                        val raw = motion.frame.value
                        SideEffect { bodyTime = raw.playTimeNanos }
                    }
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        GlasmioPageTitle("A", Modifier.testTag("projection-title-A"))
                        GlasmioPageTitle("B", Modifier.offset(y = 120.dp).testTag("projection-title-B"))
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        try {
            val initial = Triple(headerCalls, bodyCalls, bottomCalls)
            repeat(8) { index ->
                rule.runOnUiThread {
                    motion.state.value = motion.state.value.copy(
                        playTimeNanos = (index + 1L) * 16_000_000L,
                        gestureVelocity = if (index % 2 == 0) .75f else -.5f,
                    )
                }
                rule.mainClock.advanceTimeByFrame()
                if (rawControl) {
                    assertTrue("The raw-clock control must retain the original header subscription", headerCalls > initial.first)
                } else {
                    assertEquals("Unused clock/velocity must not invalidate the scaffold or its stable slots",
                        initial, Triple(headerCalls, bodyCalls, bottomCalls))
                }
            }

            // Exercise every retained field independently, including fields with no current visual mapping.
            val retainedChanges: List<(GlasmioNavigationFrame) -> GlasmioNavigationFrame> = listOf(
                { it.copy(fromEntryKey = "B") },
                { it.copy(toEntryKey = "B") },
                { it.copy(phase = GlasmioNavigationPhase.Tracking) },
                { it.copy(gestureProgress = .4f) },
                { it.copy(transitionId = it.transitionId + 1L) },
                { it.copy(operation = GlasmioNavigationOperation.Pop) },
                { it.copy(phase = GlasmioNavigationPhase.Canceling) },
            )
            retainedChanges.forEachIndexed { index, update ->
                val before = headerCalls
                rule.runOnUiThread { motion.state.value = update(motion.state.value) }
                rule.mainClock.advanceTimeByFrame()
                assertTrue("Retained frame field $index must still invalidate its consumer", headerCalls > before)
            }
            rule.mainClock.advanceTimeBy(1_200)
            rule.onNodeWithText("Title B:0").assertIsDisplayed()
            rule.runOnUiThread { titleVersion++ }
            // Header composition publishes registry data in SideEffect; its title consumer
            // needs the following frame to compose and place the newly published text.
            rule.mainClock.advanceTimeByFrame()
            rule.mainClock.advanceTimeByFrame()
            rule.onNodeWithText("Title B:1").assertIsDisplayed()
            rule.onAllNodesWithText("Title B:0").assertCountEquals(0)
            val light = headerColor
            rule.runOnUiThread { dark = true }
            rule.mainClock.advanceTimeByFrame()
            assertTrue("Palette updates remain independent of the frame projection", light != headerColor)

            // Enable each public reader alone, so a parent clock subscription cannot hide a stale child.
            listOf(1, 2, 4, 8).forEachIndexed { index, reader ->
                rule.runOnUiThread { clockReaders = reader }
                rule.mainClock.advanceTimeByFrame()
                val before = headerCalls
                val time = 987_654_318L + index
                rule.runOnUiThread {
                    motion.state.value = motion.state.value.copy(playTimeNanos = time, gestureVelocity = -1.75f)
                }
                rule.mainClock.advanceTimeByFrame()
                val observed = when (reader) {
                    1 -> headerTime
                    2 -> actionTime
                    4 -> bodyTime
                    else -> bottomTime
                }
                assertEquals("Public reader $reader must observe the original clock independently", time, observed)
                if (reader != 1 && !rawControl) assertEquals("A child reader must not invalidate its header specification", before, headerCalls)
            }
            rule.onNodeWithContentDescription("Action B")
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            assertEquals(listOf(motion.state.value), clicked)
            assertEquals("The public motion must retain the exact original clock", 987_654_321L, motion.frame.value.playTimeNanos)
            assertEquals(-1.75f, motion.frame.value.gestureVelocity, 0f)
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }

    @Test fun replacingAnEqualFrameStateSwitchesTheLiveSourceImmediately() {
        class EqualFrameState(initial: GlasmioNavigationFrame) : State<GlasmioNavigationFrame> {
            private val backing = mutableStateOf(initial)
            override val value: GlasmioNavigationFrame get() = backing.value
            fun publish(frame: GlasmioNavigationFrame) { backing.value = frame }
            override fun equals(other: Any?): Boolean = other is EqualFrameState
            override fun hashCode(): Int = 0
        }
        val first = EqualFrameState(GlasmioNavigationFrame(0, "A", "A"))
        val second = EqualFrameState(GlasmioNavigationFrame(2, "A", "B"))
        val source = mutableStateOf<State<GlasmioNavigationFrame>>(first, referentialEqualityPolicy())
        val switchingMotion = object : GlasmioHeaderMotion {
            override val frame: State<GlasmioNavigationFrame> get() = source.value
        }
        var calls = 0
        val entries = mutableListOf<Any>()
        rule.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                GlasmioNavigationScaffold(switchingMotion, header = { entry ->
                    calls++
                    entries += entry
                    GlasmioHeaderSpec("Source $entry")
                }) {
                    Box(Modifier.fillMaxSize()) {
                        GlasmioPageTitle("A")
                        GlasmioPageTitle("B", Modifier.offset(y = 120.dp))
                    }
                }
            }
        }
        rule.waitForIdle()
        assertEquals(first, second)
        assertNotSame(first, second)
        rule.runOnIdle { entries.clear(); source.value = second }
        rule.waitForIdle()
        assertEquals("The first composition must already use the replacement source", listOf("A", "B"), entries.take(2))
        rule.onNodeWithText("Source B").assertIsDisplayed()
        val afterSwitch = calls
        rule.runOnIdle { first.publish(GlasmioNavigationFrame(1, "old", "old")) }
        rule.runOnIdle {
            assertEquals("A replaced equal source must no longer invalidate the scaffold", afterSwitch, calls)
        }
        rule.runOnIdle { second.publish(GlasmioNavigationFrame(3, "B", "B")) }
        rule.onNodeWithText("Source B").assertIsDisplayed()
        rule.runOnIdle {
            assertTrue("The new source must be live in the same composition", calls > afterSwitch)
            assertEquals("B", switchingMotion.frame.value.toEntryKey)
        }
    }

    @Test fun projectedHeaderMatchesRawSubscribedReferenceAtTheSamePresentationTime() {
        val rawControl = rawClockPlacementControl()
        val callbacks = List(2) { mutableListOf<Pair<Any, GlasmioNavigationFrame>>() }
        val headerCalls = IntArray(2)
        val selectedLane = mutableIntStateOf(0)
        var direction by mutableStateOf(androidx.compose.ui.unit.LayoutDirection.Ltr)
        var referenceTime = -1L
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides androidx.compose.ui.unit.Density(1f),
                androidx.compose.ui.platform.LocalLayoutDirection provides direction,
            ) {
                GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                    val fixtureHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 62.dp + 128.dp
                    Box(Modifier.requiredSize(240.dp, fixtureHeight).background(Color.White).testTag("projection-comparison")) {
                        repeat(2) { lane ->
                            val recording = rememberGraphicsLayer()
                            GlasmioNavigationScaffold(motion, header = { entry ->
                                headerCalls[lane]++
                                if (lane == 1 || rawControl) {
                                    // Preserve the original raw-clock subscription through a legal public reader.
                                    // Both lanes use the maintained header renderer and physical spring solver.
                                    val raw = motion.frame.value
                                    SideEffect { referenceTime = raw.playTimeNanos }
                                }
                                val icon = if (entry == "A") "moon" else symbol
                                GlasmioHeaderSpec(entry.toString(), trailing = if (!showsGroup) emptyList() else listOf(
                                    GlasmioHeaderGroup("primary", (0 until if (entry == "A") 1 else count).map { index ->
                                        GlasmioHeaderAction("action:$index", if (index == 0) icon else "extra:$index",
                                            "Action $index", { callbacks[lane] += entry to motion.frame.value }) {
                                            Text(if (index == 0) icon else "X")
                                        }
                                    }),
                                ))
                            }, modifier = Modifier.fillMaxSize().testTag("projection-lane-$lane").drawWithContent {
                                // Both trees draw at identical physical coordinates on every redraw.
                                // Selection changes only replay, preserving each tree's live recordings.
                                recording.record { this@drawWithContent.drawContent() }
                                if (selectedLane.intValue == lane) drawLayer(recording)
                            }) {
                                Box(Modifier.fillMaxSize().background(Color(0xFF336699)))
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        try {
            fun lane(index: Int) = rule.onNodeWithTag("projection-lane-$index")
            fun pixels(index: Int): IntArray {
                rule.runOnUiThread { selectedLane.intValue = index }
                rule.waitForIdle()
                val bitmap = rule.onNodeWithTag("projection-comparison").captureToImage().asAndroidBitmap()
                return IntArray(bitmap.width * bitmap.height).also {
                    bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                }
            }
            fun compare(stage: String) {
                val time = rule.mainClock.currentTime
                val groups = List(2) { laneIndex ->
                    rule.onAllNodes(hasTestTag("GlasmioHeaderGroup:primary") and
                        hasAnyAncestor(hasTestTag("projection-lane-$laneIndex")))
                        .fetchSemanticsNodes().map { node ->
                            val origin = lane(laneIndex).fetchSemanticsNode().boundsInRoot.topLeft
                            node.boundsInRoot.translate(Offset(-origin.x, -origin.y))
                        }
                }
                val expected = pixels(1)
                val actual = pixels(0)
                val changed = expected.indices.filter { expected[it] != actual[it] }
                val roots = List(2) { lane(it).fetchSemanticsNode().boundsInRoot }
                var detail = "rawControl=$rawControl roots=$roots groups=$groups calls=${headerCalls.toList()}"
                if (changed.isNotEmpty()) {
                    val width = roots[0].width.toInt()
                    val maximum = changed.maxOf { index ->
                        listOf(0, 8, 16, 24).maxOf { shift ->
                            kotlin.math.abs((expected[index] ushr shift and 255) - (actual[index] ushr shift and 255))
                        }
                    }
                    detail += " changed=${changed.size} maxChannel=$maximum bbox=" +
                        "${changed.minOf { it % width }},${changed.minOf { it / width }}:" +
                        "${changed.maxOf { it % width }},${changed.maxOf { it / width }}"
                    scaffoldProbe("$stage time=$time $detail frame=${motion.frame.value}")
                }
                assertArrayEquals("$stage must preserve every RGBA pixel at Compose time $time; $detail", expected, actual)
                assertEquals("Capturing the reference must not advance its presentation", time, rule.mainClock.currentTime)
                assertEquals("$stage must retain identical live geometry", groups[1], groups[0])
            }
            fun tick(stage: String, elapsed: Long) {
                val nextTime = (rule.mainClock.currentTime + elapsed) * 1_000_000L
                rule.runOnUiThread {
                    motion.state.value = motion.state.value.copy(
                        playTimeNanos = nextTime,
                        gestureVelocity = if (elapsed % 2L == 0L) .8f else -.6f,
                    )
                }
                rule.mainClock.advanceTimeBy(elapsed)
                assertEquals(motion.frame.value.playTimeNanos, referenceTime)
                compare(stage)
            }
            fun assertClickOwner(owner: Any) {
                repeat(2) { lane ->
                    rule.onNode(hasContentDescription("Action 0") and
                        hasAnyAncestor(hasTestTag("projection-lane-$lane")))
                        .performSemanticsAction(SemanticsActions.OnClick) { it() }
                    assertEquals(owner to motion.frame.value, callbacks[lane].last())
                }
                assertEquals(callbacks[1], callbacks[0])
            }
            listOf(androidx.compose.ui.unit.LayoutDirection.Ltr, androidx.compose.ui.unit.LayoutDirection.Rtl).forEach { layout ->
                rule.runOnUiThread {
                    direction = layout
                    symbol = "moon"
                    count = 1
                    showsGroup = true
                    motion.state.value = GlasmioNavigationFrame(0, "A", "A")
                }
                rule.mainClock.advanceTimeBy(1_200)
                compare("$layout initial")
                val candidateCalls = headerCalls[0]
                val rawCalls = headerCalls[1]
                tick("$layout clock-only", 16L)
                if (rawControl) {
                    assertTrue("The placement control must subscribe both lanes to the raw clock", headerCalls[0] > candidateCalls)
                } else {
                    assertEquals("Clock-only updates must leave the projected lane composed", candidateCalls, headerCalls[0])
                }
                assertTrue("The reference must retain the original raw-clock invalidation", headerCalls[1] > rawCalls)

                rule.runOnUiThread {
                    symbol = "sun"
                    count = 3
                    motion.state.value = GlasmioNavigationFrame(1, "A", "B",
                        phase = GlasmioNavigationPhase.Tracking, gestureProgress = .65f, gestureVelocity = .8f)
                }
                listOf(16L, 32L, 64L).forEach { tick("$layout tracking", it) }
                assertClickOwner("A")
                rule.runOnUiThread { motion.state.value = motion.state.value.copy(phase = GlasmioNavigationPhase.Canceling) }
                listOf(16L, 32L, 64L).forEach { tick("$layout cancel", it) }
                assertClickOwner("A")
                rule.runOnUiThread {
                    count = 2
                    motion.state.value = GlasmioNavigationFrame(2, "A", "B", phase = GlasmioNavigationPhase.Settling)
                }
                listOf(16L, 32L, 64L).forEach { tick("$layout retarget", it) }
                assertClickOwner("B")
                rule.runOnUiThread {
                    motion.state.value = GlasmioNavigationFrame(3, "B", "A", operation = GlasmioNavigationOperation.Pop,
                        phase = GlasmioNavigationPhase.Settling)
                }
                listOf(16L, 32L, 64L, 128L, 1_200L).forEach { tick("$layout reverse", it) }
                assertClickOwner("A")
                rule.runOnUiThread { showsGroup = false }
                listOf(16L, 64L, 1_200L).forEach { tick("$layout disappear", it) }
            }
        } finally {
            rule.mainClock.autoAdvance = true
        }
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

    @Test fun stableVisualUsesLatestOwnerContentAndClickWithoutRemounting() {
        var version by mutableIntStateOf(0)
        var renderedLabel = ""
        var foregroundMounts = 0
        var clickedVersion = -1
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                GlasmioNavigationScaffold(motion, header = { entry ->
                    // Capture values here: reading mutable state inside content would hide a stale lambda.
                    val target = entry == "B"
                    val label = if (!target) "Source" else if (version == 0) "Next" else "Latest"
                    val color = if (!target) Color.Red else if (version == 0) Color.Blue else Color.Green
                    val callbackVersion = if (target) 100 + version else version
                    GlasmioHeaderSpec(entry.toString(), trailing = listOf(GlasmioHeaderGroup("live", listOf(
                        GlasmioHeaderAction("shared", "unchanged-visual", "Action $entry", {
                            clickedVersion = callbackVersion
                        }) {
                            DisposableEffect(Unit) { foregroundMounts++; onDispose {} }
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text(label, color = color, onTextLayout = { renderedLabel = it.layoutInput.text.text })
                                Box(Modifier.size(width = 12.dp, height = 6.dp).background(color))
                            }
                        },
                    ))))
                }) { Box(Modifier.fillMaxSize().background(Color.White)) }
            }
        }
        rule.waitForIdle()
        assertEquals("Source", renderedLabel)
        rule.mainClock.autoAdvance = false
        fun assertForeground(label: String, expected: Color) {
            assertEquals(label, renderedLabel)
            val pixels = rule.onNodeWithTag("GlasmioHeaderGroup:live").captureToImage().toPixelMap()
            var matching = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (kotlin.math.abs(pixel.red - expected.red) < 0.08f &&
                    kotlin.math.abs(pixel.green - expected.green) < 0.08f &&
                    kotlin.math.abs(pixel.blue - expected.blue) < 0.08f
                ) matching++
            }
            assertTrue("The latest foreground color must actually render", matching > 12)
            assertEquals("A stable visual must reuse its one foreground", 1, foregroundMounts)
        }
        try {
            rule.runOnUiThread {
                motion.state.value = GlasmioNavigationFrame(1, "A", "B", phase = GlasmioNavigationPhase.Settling)
            }
            rule.mainClock.advanceTimeBy(1200)
            assertForeground("Next", Color.Blue)
            rule.onNodeWithContentDescription("Action B").performClick()
            assertEquals(100, clickedVersion)

            rule.runOnUiThread { version = 1 }
            rule.mainClock.advanceTimeBy(64)
            assertForeground("Latest", Color.Green)
            rule.onNodeWithContentDescription("Action B").performClick()
            assertEquals(101, clickedVersion)

            rule.runOnUiThread {
                motion.state.value = GlasmioNavigationFrame(2, "B", "A", operation = GlasmioNavigationOperation.Pop,
                    phase = GlasmioNavigationPhase.Tracking, gestureProgress = 0.5f)
            }
            rule.mainClock.advanceTimeBy(64)
            assertForeground("Latest", Color.Green)
            rule.onNodeWithContentDescription("Action B").performClick()
            assertEquals(101, clickedVersion)
            rule.onAllNodesWithContentDescription("Action A").assertCountEquals(0)
        } finally {
            rule.mainClock.autoAdvance = true
        }
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
