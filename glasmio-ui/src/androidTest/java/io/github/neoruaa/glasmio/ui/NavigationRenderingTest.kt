package io.github.neoruaa.glasmio.ui

import io.github.neoruaa.glasmio.glass.GlassSurfaceStyle
import io.github.neoruaa.glasmio.glass.BottomNavigationContact
import io.github.neoruaa.glasmio.glass.BottomControlContact
import io.github.neoruaa.glasmio.glass.BottomControlContactMember
import io.github.neoruaa.glasmio.glass.GlassPressHighlight
import io.github.neoruaa.glasmio.glass.LiquidContactCapsule
import io.github.neoruaa.glasmio.glass.completeNativeFilteredGlass
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipRect
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
import io.github.neoruaa.glasmio.glass.render.backdrops.emptyBackdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.GlassColors
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.GlassAppearance
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.defaultGlassColors
import io.github.neoruaa.glasmio.icons.SfSymbol
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.glass.LiquidMenuMorphMotion
import io.github.neoruaa.glasmio.glass.LiquidMenuContour
import io.github.neoruaa.glasmio.glass.LiquidMenuFieldTexture
import io.github.neoruaa.glasmio.glass.rememberGlassMorphRenderer
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import io.github.neoruaa.glasmio.glass.NativeControlLensStyle
import io.github.neoruaa.glasmio.glass.nativeControlLens
import io.github.neoruaa.glasmio.glass.nativeControlLensProgram
import io.github.neoruaa.glasmio.glass.nativeControlLensReferenceProducer
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScopeImpl
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import io.github.neoruaa.glasmio.glass.NavigationDropShadowRenderer
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.glass.GlasmioBackdropProvider
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.materializeNativeGlassAtlas
import io.github.neoruaa.glasmio.glass.render.drawPlainBackdrop
import io.github.neoruaa.glasmio.glass.render.sampleBackdropLuminance
import io.github.neoruaa.glasmio.glass.render.rememberBackdropLuminance
import io.github.neoruaa.glasmio.glass.render.rememberResolvedGlassMaterialContext
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCanvasBackdrop
import io.github.neoruaa.glasmio.glass.render.rememberGlassMaterialContext
import io.github.neoruaa.glasmio.glass.render.ProvideGlassMaterialContext
import io.github.neoruaa.glasmio.glass.navigationGlassBackgroundResolved
import io.github.neoruaa.glasmio.glass.rememberNavigationInnerShadows
import io.github.neoruaa.glasmio.glass.GlasmioMenuAnchorState
import io.github.neoruaa.glasmio.glass.GlasmioToolbarActionGroup
import io.github.neoruaa.glasmio.glass.GlasmioToolbarActionStyle
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
    fun controlLensConsumerPoolRetainsFullSourceSnapshotsAcrossEvictionAndFailedLeases() {
        data class Snapshot(val label: String, val actual: android.graphics.RenderEffect,
            val reference: android.graphics.RenderEffect)
        val selected = mutableStateOf(0 to false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val snapshots = remember {
                    val source = """
                        uniform shader content;
                        uniform float4 fillColor;
                        half4 main(float2 p) {
                            return half4(float4(content.eval(p)) * .375 + fillColor * .625);
                        }
                    """.trimIndent()
                    val a = source + "\n/*Aa*/"
                    val b = source + "\n/*BB*/"
                    assertTrue("The collision fixture needs different complete sources", a != b)
                    assertEquals("The fixture must exercise an actual String hash collision", a.hashCode(), b.hashCode())
                    var factoryCalls = 0
                    var privateCalls = 0
                    val constructions = mutableMapOf<String, Int>()
                    val pool = io.github.neoruaa.glasmio.glass.NativeControlLensConsumerPool(8) { program ->
                        factoryCalls++
                        constructions[program] = (constructions[program] ?: 0) + 1
                        android.graphics.RuntimeShader(program)
                    }
                    fun effect(shader: android.graphics.RuntimeShader, color: Color): android.graphics.RenderEffect {
                        shader.setFloatUniform("fillColor", color.red * color.alpha, color.green * color.alpha,
                            color.blue * color.alpha, color.alpha)
                        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
                    }
                    fun pooled(program: String, color: Color) = pool.snapshot(program,
                        privateShader = { privateCalls++; android.graphics.RuntimeShader(program) },
                        configure = { effect(it, color) })
                    fun pair(label: String, program: String, color: Color) = Snapshot(label,
                        pooled(program, color), effect(android.graphics.RuntimeShader(program), color))
                    val retained = arrayListOf(
                        pair("first A red", a, Color(.8f, .1f, .2f, .7f)),
                        pair("same A blue", a, Color(.1f, .2f, .8f, .4f)),
                        pair("colliding B green", b, Color(.1f, .8f, .2f, .9f)),
                    )
                    assertEquals("Equal complete sources reuse a constructor", 1, constructions[a])
                    assertEquals("Hash collisions retain a separate program", 1, constructions[b])
                    assertEquals(2, factoryCalls)
                    assertEquals(2, pool.retainedSourceCount)
                    val extra = (0..8).map { source + "\n/*capacity-$it*/" }
                    extra.forEach { program -> pooled(program, Color(.2f, .3f, .4f, .5f)) }
                    assertEquals("The pool keeps at most eight complete sources", 8, pool.retainedSourceCount)
                    assertEquals("Every new complete source invokes the real constructor once", 11, factoryCalls)
                    assertEquals("Eviction never needs an owner fallback without an active lease", 0, privateCalls)
                    val newest = extra.last()
                    val callsBeforeFailure = factoryCalls
                    val expectedFailure = IllegalStateException("Expected failed configuration")
                    var observedFailure: Throwable? = null
                    try {
                        pool.snapshot(newest, privateShader = {
                            privateCalls++; android.graphics.RuntimeShader(newest)
                        }) { shader ->
                            shader.setFloatUniform("fillColor", 1f, 0f, 1f, 1f)
                            throw expectedFailure
                        }
                    } catch (failure: Throwable) { observedFailure = failure }
                    assertTrue("Configuration failures must propagate", observedFailure === expectedFailure)
                    retained += pair("released failed lease", newest, Color(.7f, .6f, .1f, .6f))
                    assertEquals("A failed lease becomes idle without replacing its source", callsBeforeFailure, factoryCalls)
                    assertEquals("A failed lease must not force later owner fallback", 0, privateCalls)
                    retained
                }
                val input = rememberGraphicsLayer()
                val output = rememberGraphicsLayer()
                Box(Modifier.requiredSize(64.dp).background(Color.Black).testTag("control-consumer-pool-snapshot")
                    .drawWithContent {
                        val (index, reference) = selected.value
                        input.clip = true
                        input.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                        input.record(this, layoutDirection, androidx.compose.ui.unit.IntSize(64, 64)) {
                            drawRect(Color(.2f, .4f, .7f, .65f))
                            drawRect(Color(.9f, .3f, .1f, .5f), topLeft = Offset(9f, 7f), size = Size(27f, 41f))
                        }
                        output.clip = true
                        output.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                        output.renderEffect = (if (reference) snapshots[index].reference else snapshots[index].actual).asComposeRenderEffect()
                        output.record(this, layoutDirection, androidx.compose.ui.unit.IntSize(64, 64)) { drawLayer(input) }
                        drawLayer(output)
                    })
            }
        }
        repeat(4) { index ->
            compose.runOnIdle { selected.value = index to true }
            compose.waitForIdle()
            val reference = compose.onNodeWithTag("control-consumer-pool-snapshot").captureToImage()
            compose.runOnIdle { selected.value = index to false }
            compose.waitForIdle()
            val actual = compose.onNodeWithTag("control-consumer-pool-snapshot").captureToImage()
            assertEquals("An old snapshot must remain exact after same-source updates, eviction and failure: $index",
                0f, lensPoolPixelDifference(reference, actual), 0f)
        }
    }

    @Test
    fun nativeControlLensPooledConsumersRetainIndependentOwnerSnapshots() {
        val tabPlaneDiagnostic = androidx.test.platform.app.InstrumentationRegistry.getArguments()
            .getString("glasmioTabPlaneDiagnostic") == "true"
        class FixtureScope(sharing: Boolean) : BackdropEffectScopeImpl() {
            override val supportsNativeControlLensConsumerSharing = sharing
            override val shape = com.kyant.shapes.Capsule()
        }
        data class Case(
            val role: NativeControlLensStyle,
            val lift: Float,
            val managed: Boolean,
            val content: Boolean,
            val density: Float = 1f,
            val samplingScale: Float = 1f,
            val firstSize: Size = Size(80f, 40f),
            val secondSize: Size = Size(43f, 27f),
        )
        val cases = listOf(
            Case(NativeControlLensStyle.SwitchThumb, 0f, true, false),
            Case(NativeControlLensStyle.SwitchThumb, 1f, true, true),
            Case(NativeControlLensStyle.SliderThumb, 0f, false, false),
            Case(NativeControlLensStyle.SliderThumb, 1f, false, false),
            Case(NativeControlLensStyle.SegmentedIndicator, 1f, true, true),
            Case(NativeControlLensStyle.SegmentedIndicator, 1f, false, true),
            Case(NativeControlLensStyle.TabBarIndicator, .65f, true, true),
            Case(NativeControlLensStyle.TabBarIndicator, .65f, false, true),
            Case(NativeControlLensStyle.TabBarIndicator, 0f, true, true),
            Case(NativeControlLensStyle.TabBarIndicator, 0f, false, true),
            Case(NativeControlLensStyle.SwitchThumb, -.25f, false, false),
            Case(NativeControlLensStyle.SwitchThumb, -0f, false, false),
            Case(NativeControlLensStyle.SwitchThumb, Float.MIN_VALUE, false, true),
            Case(NativeControlLensStyle.SwitchThumb, .5f, true, true),
            Case(NativeControlLensStyle.SwitchThumb, 1.25f, false, true),
            Case(NativeControlLensStyle.SwitchThumb, Float.NaN, false, false),
            Case(NativeControlLensStyle.SwitchThumb, Float.POSITIVE_INFINITY, false, false),
            Case(NativeControlLensStyle.SwitchThumb, Float.NEGATIVE_INFINITY, false, false),
            Case(NativeControlLensStyle.SegmentedIndicator, Float.MIN_VALUE, true, true),
            Case(NativeControlLensStyle.SegmentedIndicator, Float.MIN_VALUE, false, true),
            Case(NativeControlLensStyle.TabBarIndicator, Float.MIN_VALUE, true, true),
            Case(NativeControlLensStyle.TabBarIndicator, Float.MIN_VALUE, false, true),
            Case(NativeControlLensStyle.SegmentedIndicator, 1.25f, true, true),
            Case(NativeControlLensStyle.SegmentedIndicator, 1.25f, false, true),
            Case(NativeControlLensStyle.TabBarIndicator, .65f, true, true, density = 3.25f,
                firstSize = Size(80.25f, 40.5f), secondSize = Size(43.75f, 27.25f)),
            Case(NativeControlLensStyle.TabBarIndicator, .65f, true, true, samplingScale = .65f,
                firstSize = Size(80.25f, 40.5f), secondSize = Size(43.75f, 27.25f)),
            Case(NativeControlLensStyle.TabBarIndicator, 0f, true, false, density = 3.25f, samplingScale = .5f,
                firstSize = Size(80.25f, 40.5f), secondSize = Size(43.75f, 27.25f)),
            Case(NativeControlLensStyle.TabBarIndicator, 1.25f, true, false, density = 3.25f, samplingScale = .5f,
                firstSize = Size(80.25f, 40.5f), secondSize = Size(43.75f, 27.25f)),
        )
        val selected = mutableStateOf(0 to false)
        val diagnosticView = mutableStateOf<Pair<Int, Int>?>(null)
        compose.setContent {
            val caseDensity = cases[selected.value.first / 2].density
            CompositionLocalProvider(LocalDensity provides Density(caseDensity)) {
                val snapshots = remember { arrayOfNulls<List<Pair<LensPoolFrame, LensPoolFrame>>>(cases.size) }
                val input = rememberGraphicsLayer()
                val output = rememberGraphicsLayer()
                val producers = List(4) { rememberGraphicsLayer() }
                val graphicsContext = androidx.compose.ui.platform.LocalGraphicsContext.current
                val controlPasses = remember(graphicsContext) {
                    io.github.neoruaa.glasmio.glass.NativeGlassAtlasPasses(graphicsContext)
                }
                DisposableEffect(controlPasses) { onDispose { controlPasses.release() } }
                Box(Modifier.requiredSize(if (tabPlaneDiagnostic) 240.dp else 160.dp,
                    if (tabPlaneDiagnostic) 200.dp else 120.dp)
                    .background(Color.Black).testTag("control-consumer-owner-snapshot")
                    .drawWithContent {
                        val (index, reference) = selected.value
                        val caseIndex = index / 2
                        val ownerIndex = index % 2
                        val case = cases[caseIndex]
                        val pairs = snapshots[caseIndex] ?: run {
                            val privateOwner = FixtureScope(false)
                            val secondPrivateOwner = FixtureScope(false)
                            val firstOwner = FixtureScope(true)
                            val secondOwner = FixtureScope(true)
                            val expected = lensPoolFrame(privateOwner, case.firstSize, case.role, case.lift,
                                false, case.managed, case.content, Color(.8f, .25f, .15f, .8f), Color(0xFF34C759), samplingScale = case.samplingScale)
                            val actual = lensPoolFrame(firstOwner, case.firstSize, case.role, case.lift,
                                false, case.managed, case.content, Color(.8f, .25f, .15f, .8f), Color(0xFF34C759), samplingScale = case.samplingScale)
                            // This owner uses exactly the same source, after A's effect was retained.
                            val expectedSecond = lensPoolFrame(secondPrivateOwner, case.secondSize, case.role, case.lift,
                                true, case.managed, !case.content, Color(.1f, .8f, .3f, .25f), Color(0xFFFF3B30), samplingScale = case.samplingScale)
                            val actualSecond = lensPoolFrame(secondOwner, case.secondSize, case.role, case.lift,
                                true, case.managed, !case.content, Color(.1f, .8f, .3f, .25f), Color(0xFFFF3B30), samplingScale = case.samplingScale)
                            val finiteLift = case.lift.takeIf { it.isFinite() } ?: 0f
                            val small = case.role == NativeControlLensStyle.SwitchThumb ||
                                case.role == NativeControlLensStyle.SliderThumb
                            val hasProducers = if (small) finiteLift > 0f && finiteLift < 1f
                                else case.role == NativeControlLensStyle.TabBarIndicator || finiteLift > 0f
                            listOf(expected, actual, expectedSecond, actualSecond).forEach { frame ->
                                assertEquals("The fixture must retain every real scene/blur producer: $case",
                                    hasProducers, frame.producerEffects.isNotEmpty())
                            }
                            if (case.role == NativeControlLensStyle.TabBarIndicator && case.managed) {
                                listOf(expected, expectedSecond).forEach { frame ->
                                    assertEquals("The custom-scope reference must retain its original full-atlas chain", null, frame.controlAtlas)
                                }
                                listOf(actual, actualSecond).forEach { frame ->
                                    val optimized = requireNotNull(frame.controlAtlas)
                                    assertEquals("Tab blur must use the real production control-plane renderer",
                                        if (finiteLift > 0f) 4 else 3, optimized.producerStages.size)
                                    assertEquals("The scene stays on the fourth rect at positive lift",
                                        finiteLift > 0f, optimized.rects.size == 4)
                                }
                            }
                            // Reconfigure A after B; both earlier effects are rendered only afterward.
                            lensPoolFrame(firstOwner, Size(91f, 51f), case.role, case.lift,
                                true, case.managed, !case.content, Color(.2f, .1f, .9f, .4f), Color(0xFFFF9500), samplingScale = case.samplingScale)
                            listOf(actual to expected, actualSecond to expectedSecond)
                                .also { snapshots[caseIndex] = it }
                        }
                        val pair = pairs[ownerIndex]
                        val probe = if (tabPlaneDiagnostic) diagnosticView.value else null
                        lensPoolDraw(if (reference) pair.second else pair.first, input, output, producers, controlPasses,
                            diagnostic = probe,
                            diagnosticRects = if (probe == null) emptyList() else requireNotNull(pair.first.controlAtlas).rects)
                    })
            }
        }
        if (tabPlaneDiagnostic) {
            val index = cases.indexOfFirst { it.role == NativeControlLensStyle.TabBarIndicator &&
                it.lift == .65f && it.managed && it.content && it.density == 1f && it.samplingScale == 1f }
            assertTrue("The diagnostic must retain the original failing Tab case", index >= 0)
            val probes = listOf(
                "consumer-before" to null,
                "extract/raw" to (0 to 0), "extract/portal" to (0 to 1), "extract/blur" to (0 to 2),
                "gaussian-x/raw" to (1 to 0), "gaussian-x/portal" to (1 to 1), "gaussian-x/blur" to (1 to 2),
                "gaussian-y/raw" to (2 to 0), "gaussian-y/portal" to (2 to 1), "gaussian-y/blur" to (2 to 2),
                "scene" to (3 to 3), "consumer-after" to null,
            )
            var changedViews = 0
            probes.forEach { (label, probe) ->
                compose.runOnIdle { diagnosticView.value = probe; selected.value = index * 2 to true }
                compose.waitForIdle()
                val reference = compose.onNodeWithTag("control-consumer-owner-snapshot").captureToImage()
                compose.runOnIdle { selected.value = index * 2 to false }
                compose.waitForIdle()
                val actual = compose.onNodeWithTag("control-consumer-owner-snapshot").captureToImage()
                if (lensPoolReportPixelDifference(label, reference, actual) != 0f) changedViews++
            }
            assertEquals("Diagnostic-only stage report; the full default case matrix is still required", 0, changedViews)
            return
        }
        cases.forEachIndexed { index, case ->
            repeat(2) { owner ->
                compose.runOnIdle { selected.value = index * 2 + owner to true }
                compose.waitForIdle()
                val reference = compose.onNodeWithTag("control-consumer-owner-snapshot").captureToImage()
                compose.runOnIdle { selected.value = index * 2 + owner to false }
                compose.waitForIdle()
                val actual = compose.onNodeWithTag("control-consumer-owner-snapshot").captureToImage()
                assertEquals("Both retained owners must ignore later A/B/A uniform changes: owner=$owner, $case",
                    0f, lensPoolPixelDifference(reference, actual), 0f)
            }
        }
    }

    @Test
    fun nativeControlLensConsumerPoolLeasesSurviveShapeReentry() {
        val selected = mutableStateOf(0 to 0)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val snapshots = remember { Array(2) { arrayOfNulls<LensPoolFrame>(3) } }
                val input = rememberGraphicsLayer()
                val output = rememberGraphicsLayer()
                Box(Modifier.requiredSize(160.dp, 120.dp).background(Color.Black).testTag("control-consumer-shape-reentry")
                    .drawWithContent {
                        val (case, mode) = selected.value
                        val frame = snapshots[case][mode] ?: run {
                            val unsafeShaders = mutableMapOf<String, io.github.neoruaa.glasmio.glass.render.RuntimeShader>()
                            class FixtureScope(val outer: Boolean) : BackdropEffectScopeImpl() {
                                var outline: (() -> Unit)? = null
                                var privateCalls = 0
                                override val supportsNativeControlLensConsumerSharing = mode == 1
                                override val shape: androidx.compose.ui.graphics.Shape = if (!outer) com.kyant.shapes.Capsule()
                                    else object : androidx.compose.ui.graphics.Shape {
                                        override fun createOutline(size: Size, layoutDirection: LayoutDirection,
                                            density: Density): androidx.compose.ui.graphics.Outline {
                                            outline?.invoke()
                                            return com.kyant.shapes.Capsule().createOutline(size, layoutDirection, density)
                                        }
                                    }
                                override fun obtainRuntimeShader(key: String, string: String): io.github.neoruaa.glasmio.glass.render.RuntimeShader {
                                    privateCalls++
                                    return if (mode == 2) unsafeShaders.getOrPut(string) {
                                        io.github.neoruaa.glasmio.glass.render.RuntimeShader(string)
                                    } else super.obtainRuntimeShader(key, string)
                                }
                            }
                            val outer = FixtureScope(true)
                            val inner = FixtureScope(false)
                            var callbacks = 0
                            val recordingScope = this
                            outer.outline = {
                                callbacks++
                                // The outer atlas and size have already been written at this point.
                                recordingScope.lensPoolFrame(inner, Size(30f, 12f), NativeControlLensStyle.SwitchThumb,
                                    case.toFloat(), true, true, true, Color(.1f, .8f, .2f, .3f), Color(0xFFFF3B30))
                            }
                            val actual = lensPoolFrame(outer, Size(80f, 40f), NativeControlLensStyle.SwitchThumb,
                                case.toFloat(), false, true, false, Color(.8f, .25f, .15f, .8f), Color(0xFF34C759))
                            assertEquals("The custom shape must synchronously enter the second owner", 1, callbacks)
                            if (mode == 1) {
                                assertEquals("The outer consumer uses the pool", 0, outer.privateCalls)
                                // At rest, outer packed1 and inner packed4 have different sources.
                                // Lifted owners share packed4 and still require the lease fallback.
                                assertEquals("Only same-source reentry uses the inner owner cache",
                                    if (case == 0) 0 else 1, inner.privateCalls)
                            }
                            actual.also { snapshots[case][mode] = it }
                        }
                        lensPoolDraw(frame, input, output)
                    })
            }
        }
        repeat(2) { case ->
            compose.runOnIdle { selected.value = case to 0 }
            compose.waitForIdle()
            val reference = compose.onNodeWithTag("control-consumer-shape-reentry").captureToImage()
            compose.runOnIdle { selected.value = case to 1 }
            compose.waitForIdle()
            val pooled = compose.onNodeWithTag("control-consumer-shape-reentry").captureToImage()
            assertEquals("A pooled lease must preserve the outer geometry through shape reentry: lift=$case",
                0f, lensPoolPixelDifference(reference, pooled), 0f)
            compose.runOnIdle { selected.value = case to 2 }
            compose.waitForIdle()
            val unsafe = compose.onNodeWithTag("control-consumer-shape-reentry").captureToImage()
            assertTrue("The no-lease negative control must reveal outer size/atlas pollution: lift=$case",
                lensPoolPixelDifference(reference, unsafe) > 1f / 255f)
        }
    }

    private data class LensPoolFrame(
        val effect: androidx.compose.ui.graphics.RenderEffect,
        val extent: androidx.compose.ui.unit.IntSize,
        val base: Size,
        val producerExtent: androidx.compose.ui.unit.IntSize,
        val producerEffects: List<androidx.compose.ui.graphics.RenderEffect>,
        val controlAtlas: io.github.neoruaa.glasmio.glass.NativeGlassMipAtlas?,
    )

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.lensPoolFrame(
        scope: BackdropEffectScopeImpl,
        measuredSize: Size,
        role: NativeControlLensStyle,
        lift: Float,
        dark: Boolean,
        managed: Boolean,
        content: Boolean,
        resting: Color,
        tint: Color,
        samplingScale: Float = 1f,
    ): LensPoolFrame {
        val originalSize = drawContext.size
        try {
            drawContext.size = measuredSize
            scope.update(this, samplingScale)
        } finally { drawContext.size = originalSize }
        scope.colorManagedNativeGlass = managed
        scope.nativeGlassHeadroom = if (managed) 1.2f else 1f
        scope.apply {
            nativeControlLens(role, lift, dark, liftedContent = if (content) emptyBackdrop() else null,
                restingColor = resting, tint = tint)
        }
        val controlAtlas = scope.materializedMipAtlas
        assertTrue("The control-lens fixture must retain a supported atlas renderer",
            controlAtlas == null || controlAtlas.controlBlurPlane != null)
        val atlasExtent = requireNotNull(scope.effectTextureSize)
        val outputExtent = requireNotNull(scope.materialOutputSize)
        val recordingExtent = androidx.compose.ui.unit.IntSize(
            maxOf(atlasExtent.width, outputExtent.width), maxOf(atlasExtent.height, outputExtent.height))
        return LensPoolFrame(requireNotNull(scope.renderEffect), recordingExtent,
            Size(scope.size.width + 2f * scope.padding, scope.size.height + 2f * scope.padding),
            atlasExtent, (controlAtlas?.composeProducerStages ?: scope.materializedInputEffects).toList(), controlAtlas)
    }

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.lensPoolDraw(
        frame: LensPoolFrame,
        input: androidx.compose.ui.graphics.layer.GraphicsLayer,
        output: androidx.compose.ui.graphics.layer.GraphicsLayer,
        producers: List<androidx.compose.ui.graphics.layer.GraphicsLayer> = emptyList(),
        controlPasses: io.github.neoruaa.glasmio.glass.NativeGlassAtlasPasses? = null,
        diagnostic: Pair<Int, Int>? = null,
        diagnosticRects: List<androidx.compose.ui.unit.IntRect> = emptyList(),
    ) {
        assertTrue("The fixture must materialize every retained producer", producers.size >= frame.producerEffects.size)
        val sourceExtent = if (frame.producerEffects.isEmpty()) frame.extent else frame.producerExtent
        input.clip = true
        input.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        input.record(this, layoutDirection, sourceExtent) {
            drawRect(Color(.16f, .25f, .42f, .7f), size = frame.base)
            for (x in 0 until frame.base.width.roundToInt() step 7) {
                drawRect(Color(.85f, .33f, .08f, .6f), topLeft = Offset(x.toFloat(), 0f), size = Size(3f, frame.base.height))
            }
            for (x in 0 until frame.base.width.roundToInt() step 13) {
                drawRect(Color(.92f, .12f, .46f, .65f), topLeft = Offset(x.toFloat(), frame.base.height),
                    size = Size(4f, frame.base.height))
            }
        }
        val controlAtlas = frame.controlAtlas
        // Diagnostic-only access to the real production layers also forces their command
        // lists fresh. The uninstrumented consumer probes keep the original retained path.
        val diagnosticLayers = if (diagnostic != null && controlAtlas != null)
            requireNotNull(controlPasses).layers(frame.producerEffects) else producers
        var consumerInput = input
        if (controlAtlas != null) {
            // Exercise the production extract and plane-sized Gaussian passes, including
            // the raw/portal planes retained alongside the independently blurred plane.
            assertEquals("Plane-only blur must retain Gaussian snapshots and any fourth scene snapshot",
                if (controlAtlas.rects.size == 4) 4 else 3, frame.producerEffects.size)
            requireNotNull(controlPasses).recordControlBlur(controlAtlas,
                record = { layer, targetSize, block ->
                    layer.record(this, layoutDirection, targetSize, block)
                },
                sourceBlock = { drawLayer(input) })
        } else {
            frame.producerEffects.forEachIndexed { index, effect ->
                val producer = producers[index]
                val previous = consumerInput
                producer.clip = true
                producer.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                producer.renderEffect = effect
                producer.record(this, layoutDirection, sourceExtent) { drawLayer(previous) }
                consumerInput = producer
            }
        }
        output.clip = true
        output.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        output.renderEffect = frame.effect
        output.record(this, layoutDirection, frame.extent) {
            if (controlAtlas != null) requireNotNull(controlPasses).drawControlBlurAtlas(this, controlAtlas)
            else drawLayer(consumerInput)
        }
        if (diagnostic == null) drawLayer(output)
        else {
            val (stage, plane) = diagnostic
            val fullRect = diagnosticRects[plane]
            // In the optimized chain the raw/portal inputs are the extract itself;
            // Gaussian layers contain only a local-origin third plane.
            val selectedStage = if (controlAtlas != null && stage in 1..2 && plane < 2) 0 else stage
            val rect = if (controlAtlas != null && stage in 1..2 && plane == 2)
                androidx.compose.ui.unit.IntRect(0, 0, fullRect.width, fullRect.height) else fullRect
            clipRect(0f, 0f, rect.width.toFloat(), rect.height.toFloat()) {
                withTransform({ translate(-rect.left.toFloat(), -rect.top.toFloat()) }) {
                    drawLayer(diagnosticLayers[selectedStage])
                }
            }
        }
    }

    private fun lensPoolReportPixelDifference(label: String, reference: ImageBitmap, actual: ImageBitmap): Float {
        assertEquals(reference.width, actual.width)
        assertEquals(reference.height, actual.height)
        val expected = reference.toPixelMap()
        val observed = actual.toPixelMap()
        var maximum = 0f
        var changed = 0
        var minX = actual.width
        var minY = actual.height
        var maxX = -1
        var maxY = -1
        var first = "none"
        repeat(actual.height) { y -> repeat(actual.width) { x ->
            val a = expected[x, y]
            val b = observed[x, y]
            val delta = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue), abs(a.alpha - b.alpha))
            maximum = maxOf(maximum, delta)
            if (delta != 0f) {
                if (changed == 0) first = "$x,$y:$a->$b"
                changed++
                minX = minOf(minX, x); minY = minOf(minY, y)
                maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
            }
        } }
        val message = "TabPlaneProbe: diagnosticOnly=true view=$label width=${actual.width} height=${actual.height} " +
            "changed=$changed maxRgba=$maximum bbox=$minX,$minY:$maxX,$maxY first=$first"
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0,
            android.os.Bundle().apply { putString("stream", message + "\n") })
        return maximum
    }

    private fun lensPoolPixelDifference(reference: ImageBitmap, actual: ImageBitmap): Float {
        assertEquals(reference.width, actual.width)
        assertEquals(reference.height, actual.height)
        val expected = reference.toPixelMap()
        val observed = actual.toPixelMap()
        var maximum = 0f
        repeat(actual.height) { y -> repeat(actual.width) { x ->
            val a = expected[x, y]
            val b = observed[x, y]
            maximum = maxOf(maximum, abs(a.red - b.red), abs(a.green - b.green),
                abs(a.blue - b.blue), abs(a.alpha - b.alpha))
        } }
        return maximum
    }

    @Test
    fun controlLensMaterializedGaussianPassesMatchLegacyProducer() {
        org.junit.Assume.assumeTrue("The legacy pipeline reference is limited to emulator GPUs",
            android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.HARDWARE.contains("goldfish"))
        val sampler = io.github.neoruaa.glasmio.glass.NativeGlassMipAtlas(
            androidx.compose.ui.unit.IntSize(1, 1), androidx.compose.ui.unit.IntSize(1, 1),
            listOf(androidx.compose.ui.unit.IntRect(0, 0, 1, 1)), null, colorManaged = true,
        ).samplingFunctions
        val analyticProgram = nativeControlLensProgram(sampler, fullyResting = false, small = false, tab = false)
        class FixtureScope(private val analyticReference: Boolean) : BackdropEffectScopeImpl() {
            override val shape = com.kyant.shapes.Capsule()
            override fun obtainRuntimeShader(key: String, string: String): io.github.neoruaa.glasmio.glass.render.RuntimeShader {
                return super.obtainRuntimeShader(key,
                    if (analyticReference && key.startsWith("Glasmio.ControlLens.true.")) analyticProgram else string)
            }
        }
        data class Case(val role: NativeControlLensStyle, val lift: Float, val dark: Boolean, val translucent: Boolean = false)
        val cases = listOf(
            Case(NativeControlLensStyle.TabBarIndicator, 0f, false),
            Case(NativeControlLensStyle.SliderThumb, 0f, true),
            Case(NativeControlLensStyle.SwitchThumb, .35f, false),
            Case(NativeControlLensStyle.SliderThumb, .85f, true, translucent = true),
            Case(NativeControlLensStyle.TabBarIndicator, .6f, false),
            Case(NativeControlLensStyle.SegmentedIndicator, .9f, true),
        )
        val selected = mutableStateOf(Triple(0, false, false))
        val failures = ArrayList<String>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val (index, reference, producerOnly) = selected.value
                val case = cases[index]
                val rasterizedScene = case.lift > 0f &&
                    (case.role == NativeControlLensStyle.TabBarIndicator || case.role == NativeControlLensStyle.SegmentedIndicator)
                val fixture = remember(index, reference) { FixtureScope(reference && !rasterizedScene) }
                val source = rememberGraphicsLayer()
                val passes = List(4) { rememberGraphicsLayer() }
                val output = rememberGraphicsLayer()
                val graphicsContext = androidx.compose.ui.platform.LocalGraphicsContext.current
                val controlPasses = remember(graphicsContext) { io.github.neoruaa.glasmio.glass.NativeGlassAtlasPasses(graphicsContext) }
                DisposableEffect(controlPasses) { onDispose { controlPasses.release() } }
                Box(Modifier.requiredSize(224.dp, 552.dp).background(Color.Black).testTag("control-lens-pass-parity")
                    .drawWithContent {
                        val originalSize = drawContext.size
                        try {
                            drawContext.size = Size(80f, 40f)
                            fixture.update(this)
                        } finally { drawContext.size = originalSize }
                        fixture.colorManagedNativeGlass = true
                        fixture.nativeGlassHeadroom = 1.2f
                        fixture.apply {
                            nativeControlLens(case.role, case.lift, case.dark,
                                liftedContent = if (case.role == NativeControlLensStyle.TabBarIndicator ||
                                    case.role == NativeControlLensStyle.SegmentedIndicator) emptyBackdrop() else null,
                                restingColor = Color.White, tint = Color(0xFF34C759))
                        }
                        val extent = requireNotNull(fixture.effectTextureSize)
                        val baseWidth = (fixture.size.width + fixture.padding * 2f).roundToInt()
                        val baseHeight = (fixture.size.height + fixture.padding * 2f).roundToInt()
                        source.clip = true
                        source.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                        source.record(this, layoutDirection, extent) {
                            drawRect(Color(.16f, .25f, .42f, if (case.translucent) .35f else 1f),
                                size = Size(baseWidth.toFloat(), baseHeight.toFloat()))
                            for (x in 0 until baseWidth step 7) {
                                drawRect(Color(.85f, .33f, .08f, .6f), topLeft = Offset(x.toFloat(), 0f),
                                    size = Size(3f, baseHeight.toFloat()))
                            }
                            for (y in 0 until baseHeight step 11) {
                                drawRect(Color(.06f, .72f, .88f, .5f), topLeft = Offset(0f, y.toFloat()),
                                    size = Size(baseWidth.toFloat(), 2f))
                            }
                            // An independent transparent portal makes accidental plane loss observable.
                            for (x in 0 until baseWidth step 13) {
                                drawRect(Color(.92f, .12f, .46f, .65f),
                                    topLeft = Offset(x.toFloat(), baseHeight.toFloat()),
                                    size = Size(4f, baseHeight.toFloat()))
                            }
                        }
                        val controlAtlas = fixture.materializedMipAtlas?.takeIf { !reference && it.controlBlurPlane != null }
                        val effects = if (reference) buildList<android.graphics.RenderEffect> {
                            fixture.nativeControlLensReferenceProducer(case.role, case.lift)?.let(::add)
                            if (rasterizedScene) {
                                // Native spectral rays sample a rasterized scene. Preserve that
                                // independent pass on both sides of the Gaussian producer comparison.
                                add(fixture.materializedInputEffects.last().asAndroidRenderEffect())
                            }
                        } else fixture.materializedInputEffects.map { it.asAndroidRenderEffect() }
                        assertTrue("Control fixture needs at most four independent passes", effects.size <= passes.size)
                        var previous = source
                        if (controlAtlas != null) {
                            var recordings = 0
                            var sourceCalls = 0
                            controlPasses.recordControlBlur(controlAtlas,
                                record = { layer, targetSize, block ->
                                    recordings++
                                    layer.record(this, layoutDirection, targetSize, block)
                                },
                                sourceBlock = { sourceCalls++; drawLayer(source) })
                            assertEquals("Plane-only blur retains three producer recordings", 3, recordings)
                            assertEquals("Plane-only blur records the actual source once", 1, sourceCalls)
                            recordings = 0
                            sourceCalls = 0
                            controlPasses.recordControlBlur(controlAtlas,
                                record = { layer, targetSize, block ->
                                    recordings++
                                    layer.record(this, layoutDirection, targetSize, block)
                                }, sourceBlock = { sourceCalls++; drawLayer(source) })
                            assertEquals("A stable blur retains only its private final-pass display list", 2, recordings)
                            assertEquals("A retained final pass still records its actual source", 1, sourceCalls)
                        } else {
                            effects.forEachIndexed { passIndex, effect ->
                                val input = previous
                                val layer = passes[passIndex]
                                layer.clip = true
                                layer.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                                layer.renderEffect = effect.asComposeRenderEffect()
                                layer.record(this, layoutDirection, extent) { drawLayer(input) }
                                previous = layer
                            }
                        }
                        if (producerOnly) {
                            // Compare the three original source planes before the final optical transfer.
                            if (controlAtlas != null) controlPasses.drawControlBlurAtlas(this, controlAtlas)
                            else drawLayer(previous)
                        } else {
                            val input = previous
                            output.clip = true
                            output.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                            output.renderEffect = fixture.renderEffect
                            output.record(this, layoutDirection, extent) {
                                if (controlAtlas != null) controlPasses.drawControlBlurAtlas(this, controlAtlas)
                                else drawLayer(input)
                            }
                            drawLayer(output)
                        }
                    })
            }
        }
        cases.forEachIndexed { index, case ->
            for (producerOnly in listOf(true, false)) {
                compose.runOnIdle { selected.value = Triple(index, true, producerOnly) }
                compose.waitForIdle()
                val reference = compose.onNodeWithTag("control-lens-pass-parity").captureToImage().toPixelMap()
                compose.runOnIdle { selected.value = Triple(index, false, producerOnly) }
                compose.waitForIdle()
                val staged = compose.onNodeWithTag("control-lens-pass-parity").captureToImage().toPixelMap()
                val width = if (producerOnly) 224 else 130
                val height = if (producerOnly) 368 + (if (case.role == NativeControlLensStyle.TabBarIndicator) 46 else 184) else 90
                var maximum = 0f
                var total = 0f
                var maximumPoint = ""
                val outlierDetails = ArrayList<String>()
                repeat(height) { y -> repeat(width) { x ->
                    val a = reference[x, y]
                    val b = staged[x, y]
                    val difference = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue), abs(a.alpha - b.alpha))
                    total += difference
                    if (difference > maximum) { maximum = difference; maximumPoint = "$x,$y" }
                    if (difference > 2f / 255f && outlierDetails.size < 8) outlierDetails += "$x,$y diff=$difference"
                } }
                val mean = total / (width * height)
                val detail = "role=${case.role} lift=${case.lift} producer=$producerOnly max=$maximum at=$maximumPoint mean=$mean" +
                    " $outlierDetails"
                println("GLASMIO_CONTROL_GAUSSIAN_PARITY $detail")
                if (maximum > 2f / 255f || mean > .5f / 255f) failures += detail
            }
        }
        assertTrue("Materialized Gaussian producers must preserve the legacy control rendering: $failures", failures.isEmpty())
    }

    @Test
    fun nativeControlLensesRestoreTheirRestingBodiesAfterLifting() {
        val lift = mutableFloatStateOf(0f)
        val dark = mutableStateOf(false)
        val naturalSource = mutableStateOf(0)
        val roles = NativeControlLensStyle.entries
        val transitionDifferences = ArrayList<String>()
        compose.setContent {
            GlasmioTheme(isDark = dark.value) {
                val portal = rememberLayerBackdrop(onDraw = {
                    // A transparent source must not make the resting body translucent.
                    for (x in listOf(82.dp, 226.dp)) {
                        drawRect(Color(0xFF0088FF), topLeft = Offset(x.toPx(), 0f),
                            size = Size(12.dp.toPx(), size.height))
                    }
                })
                val translucent = rememberLayerBackdrop(onDraw = {
                    drawRect(Color(.2f, .3f, .4f, .4f))
                })
                GlasmioBackdropHost(
                    modifier = Modifier.size(300.dp, 180.dp),
                    sampledContent = {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRect(Color(0xFF34C759))
                            val stripe = 8.dp.toPx()
                            for (index in 0..(size.width / stripe).toInt()) {
                                if (index % 2 == 0) drawRect(Color(0xFF183858),
                                    topLeft = Offset(index * stripe, 0f), size = Size(stripe, size.height))
                            }
                        }
                    },
                    overlayContent = { natural ->
                        Box(Modifier.fillMaxSize().layerBackdrop(portal))
                        Box(Modifier.fillMaxSize().layerBackdrop(translucent))
                        roles.forEachIndexed { index, role ->
                            key(naturalSource.value, role) {
                                Box(Modifier.offset((14 + index % 2 * 144).dp, (16 + index / 2 * 80).dp)
                                    .size(128.dp, 52.dp).testTag("resting-control-$role")
                                    .drawPlainBackdrop(
                                        backdrop = when (naturalSource.value) {
                                            1 -> emptyBackdrop()
                                            2 -> translucent
                                            else -> natural
                                        },
                                        shape = { com.kyant.shapes.Capsule() },
                                        effects = {
                                            nativeControlLens(role, lift.floatValue, dark.value,
                                                liftedContent = if (role == NativeControlLensStyle.SegmentedIndicator ||
                                                    role == NativeControlLensStyle.TabBarIndicator) portal else null,
                                                restingColor = Color.White)
                                        },
                                    ))
                            }
                        }
                    },
                )
            }
        }
        val scenarios = listOf(0 to false, 1 to false, 2 to false, 0 to true, 1 to true, 2 to true)
        scenarios.forEach { (sourceKind, isDark) ->
            compose.runOnIdle { dark.value = isDark; naturalSource.value = sourceKind; lift.floatValue = 0f }
            compose.waitForIdle()
            val before = roles.associateWith { role ->
                compose.onNodeWithTag("resting-control-$role").captureToImage().toPixelMap()
            }
            roles.filter { it != NativeControlLensStyle.TabBarIndicator }.forEach { role ->
                val pixels = requireNotNull(before[role])
                // Native idle Small/Segment views contain only their opaque resting body.
                // Check the lower interior too: a permanent ClearGlass inner shadow fails here.
                for (fraction in listOf(.12f, .5f, .88f)) {
                    val color = pixels[(pixels.width * .4f).roundToInt(), (pixels.height * fraction).roundToInt()]
                    assertTrue("$role must have a plain white resting body: dark=$isDark, y=$fraction, color=$color",
                        minOf(color.red, color.green, color.blue) > .985f)
                }
            }
            listOf(NativeControlLensStyle.SegmentedIndicator, NativeControlLensStyle.TabBarIndicator).forEach { role ->
                val pixels = requireNotNull(before[role])
                for (fraction in listOf(.06f, .5f, .94f)) {
                    val color = pixels[(pixels.width * 74f / 128f).roundToInt(), (pixels.height * fraction).roundToInt()]
                    assertEquals("$role resting content must stay unwarped and untinted", 0f, color.red, .01f)
                    assertEquals("$role resting content must keep its blue tint", 136f / 255f, color.green, .01f)
                    assertEquals("$role resting content must keep its blue tint", 1f, color.blue, .01f)
                }
            }
            compose.runOnIdle { lift.floatValue = .0001f }
            compose.waitForIdle()
            roles.forEach { role ->
                val original = requireNotNull(before[role])
                val transitioning = compose.onNodeWithTag("resting-control-$role").captureToImage().toPixelMap()
                var difference = 0f
                for (y in 0 until transitioning.height) for (x in 0 until transitioning.width) {
                    val a = original[x, y]
                    val b = transitioning[x, y]
                    difference = maxOf(difference, abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
                }
                if (difference >= .01f) transitionDifferences += "$role dark=$isDark source=$sourceKind: $difference"
            }
            compose.runOnIdle { lift.floatValue = 1f }
            compose.waitForIdle()
            compose.runOnIdle { lift.floatValue = 0f }
            compose.waitForIdle()
            roles.forEach { role ->
                val original = requireNotNull(before[role])
                val after = compose.onNodeWithTag("resting-control-$role").captureToImage().toPixelMap()
                var difference = 0f
                for (y in 0 until after.height) for (x in 0 until after.width) {
                    val a = original[x, y]
                    val b = after[x, y]
                    difference = maxOf(difference, abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
                }
                assertTrue("$role must restore its resting rendering after release: $difference", difference < 1f / 255f)
            }
        }
        assertTrue("Control materials must approach their resting state continuously: $transitionDifferences",
            transitionDifferences.isEmpty())
    }

    @Test
    fun nativeControlLensesKeepNeutralThumbsAndSeparateTheLiftedContentPlane() {
        val flatPortal = mutableStateOf(false)
        val emptyNatural = mutableStateOf(false)
        val roles = NativeControlLensStyle.entries
        compose.setContent {
            GlasmioTheme(isDark = false) {
                val portal = rememberLayerBackdrop(onDraw = {
                    drawRect(Color(.25f, .25f, .25f))
                    if (!flatPortal.value) {
                        val stripe = 8.dp.toPx()
                        for (index in 0..(size.width / stripe).toInt()) {
                            drawRect(if (index % 2 == 0) Color.White else Color.Black,
                                topLeft = Offset(index * stripe, 0f), size = Size(stripe, size.height))
                        }
                    }
                })
                GlasmioBackdropHost(
                    modifier = Modifier.size(300.dp, 180.dp),
                    sampledContent = {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRect(Color(.5f, .5f, .5f))
                            val stripe = 8.dp.toPx()
                            for (index in 0..(size.width / stripe).toInt()) {
                                drawRect(if (index % 2 == 0) Color.White else Color.Black,
                                    topLeft = Offset(index * stripe, 0f), size = Size(stripe, size.height))
                            }
                        }
                    },
                    overlayContent = { natural ->
                        // Record the portal independently without painting it into the natural source.
                        Box(Modifier.fillMaxSize().layerBackdrop(portal))
                        roles.forEachIndexed { index, role ->
                            val hasLiftedContent = role == NativeControlLensStyle.SegmentedIndicator ||
                                role == NativeControlLensStyle.TabBarIndicator
                            key(emptyNatural.value, role) {
                                Box(Modifier.offset((14 + index % 2 * 144).dp, (16 + index / 2 * 80).dp)
                                    .size(128.dp, 52.dp).testTag("native-control-$role")
                                    .drawPlainBackdrop(
                                        backdrop = if (emptyNatural.value) emptyBackdrop() else natural,
                                        shape = { com.kyant.shapes.Capsule() },
                                        effects = {
                                            nativeControlLens(role, 1f, false,
                                                liftedContent = if (hasLiftedContent) portal else null)
                                        },
                                    ))
                            }
                        }
                    },
                )
            }
        }
        listOf(false, true).forEach { noNatural ->
            compose.runOnIdle { emptyNatural.value = noNatural; flatPortal.value = false }
            compose.waitForIdle()
            val before = roles.associateWith { role ->
                compose.onNodeWithTag("native-control-$role").captureToImage().toPixelMap()
            }
            listOf(NativeControlLensStyle.SwitchThumb, NativeControlLensStyle.SliderThumb).forEach { role ->
                val pixels = requireNotNull(before[role])
                var maximumChroma = 0f
                for (y in 2 until pixels.height - 2) for (x in 2 until pixels.width - 2) {
                    val c = pixels[x, y]
                    maximumChroma = maxOf(maximumChroma, maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue))
                }
                // Real UIKit style 0 has zero foreground aberration for both thumb roles.
                assertTrue("The native $role must keep grayscale sources neutral: empty=$noNatural, chroma=$maximumChroma",
                    maximumChroma < .015f)
            }
            compose.runOnIdle { flatPortal.value = true }
            compose.waitForIdle()
            roles.forEach { role ->
                val original = requireNotNull(before[role])
                val after = compose.onNodeWithTag("native-control-$role").captureToImage().toPixelMap()
                var difference = 0.0
                for (y in 0 until after.height) for (x in 0 until after.width) {
                    val a = original[x, y]
                    val b = after[x, y]
                    difference += maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
                }
                val mean = difference / (after.width * after.height)
                if (role == NativeControlLensStyle.SegmentedIndicator || role == NativeControlLensStyle.TabBarIndicator)
                    assertTrue("The active $role must sample its lifted content independently: $mean", mean > .005)
                else assertTrue("Changing the portal must not change the natural-only $role: $mean", mean < 1.0 / 255.0)
            }
        }
    }

    @Test
    fun nativeMipGridsMatchTheOriginalMetalKernelOnOddAndEvenTextures() {
        val json = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("native-mip-fixture.json").bufferedReader().use { org.json.JSONObject(it.readText()) }
        val cases = json.getJSONArray("datasets")
        val sizes = ArrayList<androidx.compose.ui.unit.IntSize>()
        val pyramids = ArrayList<List<io.github.neoruaa.glasmio.glass.NativeGlassMipLevel>>()
        val atlases = ArrayList<List<io.github.neoruaa.glasmio.glass.NativeGlassMipAtlas>>()
        val atlasSamples = ArrayList<List<android.graphics.RuntimeShader>>()
        repeat(cases.length()) { index ->
            val entry = cases.getJSONObject(index)
            val dimensions = entry.getJSONArray("sourceDimensions")
            val width = dimensions.getInt(0)
            val height = dimensions.getInt(1)
            val bits = entry.getJSONArray("sourceRGBA16FBits")
            val pixels = java.nio.ByteBuffer.allocateDirect(bits.length() * 2).order(java.nio.ByteOrder.nativeOrder())
            repeat(bits.length()) { pixels.putShort(bits.getInt(it).toShort()) }
            pixels.rewind()
            // Test numeric texture channels in a matching working space without an implicit EOTF.
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGBA_F16,
                true, android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
            bitmap.copyPixelsFromBuffer(pixels)
            val cache = io.github.neoruaa.glasmio.glass.render.RuntimeShaderCacheImpl()
            sizes += androidx.compose.ui.unit.IntSize(width, height)
            val source = android.graphics.RenderEffect.createBitmapEffect(bitmap)
            val pyramid = io.github.neoruaa.glasmio.glass.NativeGlassMipPyramid(cache)
            pyramids += pyramid.levels(source, sizes.last(), 3f,
                io.github.neoruaa.glasmio.glass.NativeGlassMipKernel.Compute13Tap)
            val variants = listOf(false to false, true to false, true to true).map { (managed, nativeStorage) ->
                pyramid.atlas(source, sizes.last(), 3f,
                    io.github.neoruaa.glasmio.glass.NativeGlassMipKernel.Compute13Tap,
                    colorManaged = managed, nativeGammaStorage = nativeStorage)
            }
            atlases += variants
            atlasSamples += variants.map { atlas ->
                android.graphics.RuntimeShader(atlas.samplingFunctions + """
                uniform float atlasLevel;
                uniform float2 atlasOutputSize;
                half4 main(float2 p) {
                    float4 color = nativeAtlasSample(p / atlasOutputSize * nativeAtlasBaseSize, atlasLevel);
                    return half4(nativeAtlasEncode(color));
                }
                """.trimIndent())
            }
        }
        val selection = mutableStateOf(Triple(0, 1, 0))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(1f)) {
                val (case, level, path) = selection.value
                val variant = when { path >= 5 -> 2; path >= 3 -> 1; else -> 0 }
                val atlas = atlases[case][variant]
                val storage = path == 1 || path == 3
                val consumer = path == 2 || path == 4 || path == 5
                val size = when {
                    storage -> atlas.size
                    consumer -> atlas.rects[level].let { androidx.compose.ui.unit.IntSize(it.width, it.height) }
                    else -> sizes[case]
                }
                Box(Modifier.size(size.width.dp, size.height.dp).background(Color.Black).testTag("metal-mip-grid")) {
                    if (consumer) {
                        Box(Modifier.fillMaxSize().drawPlainBackdrop(emptyBackdrop(), { RectangleShape }, effects = {
                            check(materializeNativeGlassAtlas(atlas))
                            val program = atlasSamples[case][variant]
                            atlas.configure(program)
                            program.setFloatUniform("atlasLevel", level.toFloat())
                            program.setFloatUniform("atlasOutputSize", size.width.toFloat(), size.height.toFloat())
                            renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(program, "content")
                                .asComposeRenderEffect()
                        }))
                    } else {
                        Canvas(Modifier.fillMaxSize().graphicsLayer {
                            renderEffect = requireNotNull(if (storage) atlas.producerEffect
                                else pyramids[case][level].effect).asComposeRenderEffect()
                        }) { drawRect(Color.White) }
                    }
                }
            }
        }
        // Keep the explicit legacy working-space reference and exercise native-gamma
        // storage through the actual production recorder and cropped consumer seam.
        listOf(0, 1, 2, 3, 4, 5).forEach { path ->
            repeat(cases.length()) { case ->
                val expectedLevels = cases.getJSONObject(case).getJSONArray("levels")
                repeat(expectedLevels.length()) { levelIndex ->
                    val expected = expectedLevels.getJSONObject(levelIndex)
                    val level = expected.getInt("level")
                    compose.runOnIdle { selection.value = Triple(case, level, path) }
                    compose.waitForIdle()
                    val actual = compose.onNodeWithTag("metal-mip-grid").captureToImage().toPixelMap()
                    val dimensions = expected.getJSONArray("dimensions")
                    val width = dimensions.getInt(0)
                    val height = dimensions.getInt(1)
                    val bits = expected.getJSONArray("nativeOutputRGBA16FBits")
                    var maximum = 0f
                    var total = 0f
                    repeat(height) { y -> repeat(width) { x ->
                        val pixel = if (path == 1 || path == 3) {
                            val rect = atlases[case][if (path >= 3) 1 else 0].rects[level]
                            actual[rect.left + x, rect.top + y]
                        } else actual[((x + .5f) * actual.width / width).toInt().coerceAtMost(actual.width - 1),
                            ((y + .5f) * actual.height / height).toInt().coerceAtMost(actual.height - 1)]
                        val offset = (y * width + x) * 4
                        // The captured node composites premultiplied shader output onto known black.
                        val error = maxOf(abs(pixel.red - android.util.Half.toFloat(bits.getInt(offset).toShort())),
                            abs(pixel.green - android.util.Half.toFloat(bits.getInt(offset + 1).toShort())),
                            abs(pixel.blue - android.util.Half.toFloat(bits.getInt(offset + 2).toShort())))
                        maximum = maxOf(maximum, error)
                        total += error
                    } }
                    println("GLASMIO_NATIVE_MIP path=$path case=$case level=$level max=$maximum mean=${total / (width * height)}")
                    assertTrue("Native Metal mip path=$path case=$case level=$level: max=$maximum, mean=${total / (width * height)}",
                        maximum < .018f && total / (width * height) < .009f)
                }
            }
        }
    }

    @Test
    fun nativeGlassKeepsTheThinDarkContourOutsideItsUpperHighlight() {
        var buttonBounds = Rect.Zero
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(3f)) {
                GlasmioTheme(isDark = false) {
                    GlasmioBackdropHost(
                        modifier = Modifier.size(200.dp, 100.dp).testTag("native-edge-scene"),
                        sampledContent = { Box(Modifier.fillMaxSize().background(Color(0xFFF2F2F7))) },
                        overlayContent = { backdrop ->
                            GlasmioButton(
                                onClick = {}, backdrop = backdrop,
                                modifier = Modifier.offset(30.dp, 24.dp).width(140.dp)
                                    .onGloballyPositioned { buttonBounds = it.boundsInParent() },
                                size = GlasmioButtonSize.Large,
                                emphasis = GlassEmphasis.Prominent,
                                navigationSurfaceColor = Color(0xFF0087FF),
                            ) {}
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("native-edge-scene").captureToImage().toPixelMap()
        val middle = buttonBounds.center.x.roundToInt()
        fun row(y: Int): Color {
            var red = 0f; var green = 0f; var blue = 0f
            for (x in middle - 9..middle + 9) {
                val value = pixels[x, y]
                red += value.red; green += value.green; blue += value.blue
            }
            return Color(red / 19f, green / 19f, blue / 19f)
        }
        val body = row(buttonBounds.center.y.roundToInt())
        val top = buttonBounds.top.roundToInt()
        val edge = (top - 6..top + 18).map { it to row(it) }
            .filter { (_, color) -> color.blue - color.red > .25f }
        assertTrue("The native contour must be visible in the capture", edge.isNotEmpty())
        val dark = edge.minBy { (_, color) -> color.blue }
        val bright = edge.maxBy { (_, color) -> color.green }
        // The 13 mini reference has a narrow dark dip before the cyan key/fill lobe.
        assertTrue("Keep the native dark contour: body=$body, edge=$dark", dark.second.blue < body.blue - .02f)
        assertTrue("The native cyan highlight follows the dark contour: dark=$dark, bright=$bright",
            bright.first > dark.first && bright.second.green > body.green + .15f)
    }

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 36)
    fun nativeVibrantHighlightRetainsItsColorAndHdrEnergyInAnFp16Target() {
        val usage = android.hardware.HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or
            android.hardware.HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
        org.junit.Assume.assumeTrue(android.hardware.HardwareBuffer.isSupported(
            144, 40, android.hardware.HardwareBuffer.RGBA_FP16, 1, usage))
        val nativeSpace = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.EXTENDED_SRGB)
        listOf(nativeSpace, android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.LINEAR_EXTENDED_SRGB)).forEach { space ->
            val buffer = android.hardware.HardwareBuffer.create(
                144, 40, android.hardware.HardwareBuffer.RGBA_FP16, 1, usage)
            val node = android.graphics.RenderNode("native-vibrant-hdr").apply { setPosition(0, 0, 144, 40) }
            try {
                val canvas = node.beginRecording(144, 40)
                canvas.drawColor(android.graphics.Color.WHITE)
                val blue = android.graphics.Paint().apply {
                    setColor(android.graphics.Color.pack(0f, .5f, 1f, 1f, nativeSpace))
                }
                canvas.drawRect(48f, 0f, 144f, 40f, blue)
                val vibrant = io.github.neoruaa.glasmio.glass.render.highlight.NativeVibrantColorMatrixBlender()
                vibrant.prepare(io.github.neoruaa.glasmio.glass.render.highlight.NativeVibrantColorMatrix.RegularHighlight)
                val highlight = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    xfermode = vibrant.shader
                }
                canvas.drawRect(8f, 8f, 32f, 32f, highlight)
                canvas.drawRect(64f, 8f, 88f, 32f, highlight)
                val extendedMask = android.graphics.Paint(highlight).apply {
                    shader = android.graphics.RuntimeShader("half4 main(float2 p) { return half4(1.6671348); }")
                }
                canvas.drawRect(112f, 8f, 136f, 32f, extendedMask)
                node.endRecording()
                android.graphics.HardwareBufferRenderer(buffer).use { renderer ->
                    renderer.setContentRoot(node)
                    val done = java.util.concurrent.CountDownLatch(1)
                    val error = java.util.concurrent.atomic.AtomicReference<Throwable>()
                    renderer.obtainRenderRequest().setColorSpace(space).draw({ it.run() }) { result ->
                        try {
                            check(result.status == android.graphics.HardwareBufferRenderer.RenderResult.SUCCESS)
                            result.fence.use { if (it.isValid) check(it.await(java.time.Duration.ofSeconds(3))) }
                        } catch (failure: Throwable) {
                            error.set(failure)
                        } finally {
                            done.countDown()
                        }
                    }
                    assertTrue("The FP16 render must finish", done.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    error.get()?.let { throw it }
                    val hardware = requireNotNull(android.graphics.Bitmap.wrapHardwareBuffer(buffer, space))
                    try {
                        val pixels = requireNotNull(hardware.copy(android.graphics.Bitmap.Config.RGBA_F16, false))
                        try {
                            val white = pixels.getColor(4, 20).convert(nativeSpace)
                            val litWhite = pixels.getColor(20, 20).convert(nativeSpace)
                            val untouchedBlue = pixels.getColor(52, 20).convert(nativeSpace)
                            val litBlue = pixels.getColor(76, 20).convert(nativeSpace)
                            val extendedBlue = pixels.getColor(124, 20).convert(nativeSpace)
                            assertEquals("The ordinary white point stays unchanged", 1f, white.red(), .005f)
                            assertEquals("The native highlight exceeds SDR white", 1.0589f, litWhite.red(), .01f)
                            assertEquals("The untouched backdrop stays unchanged", .5f, untouchedBlue.green(), .005f)
                            // Native regular VCM reference for gamma-sRGB (0, .5, 1), including its 1.2 clamp.
                            assertEquals("The highlight retains its blue-dependent red channel", .0334f, litBlue.red(), .015f)
                            assertEquals("The highlight retains its blue-dependent green channel", .62155f, litBlue.green(), .015f)
                            assertEquals("The native HDR blue channel survives the output", 1.2f, litBlue.blue(), .015f)
                            assertEquals("Native coverage above one must survive the mask paint", 1.333427f, extendedBlue.blue(), .02f)
                            println("GLASMIO_NATIVE_HDR space=${space.name} white=${litWhite.red()} blue=${litBlue.red()},${litBlue.green()},${litBlue.blue()} extendedBlue=${extendedBlue.red()},${extendedBlue.green()},${extendedBlue.blue()}")
                        } finally {
                            pixels.recycle()
                        }
                    } finally {
                        hardware.recycle()
                    }
                }
            } finally {
                node.discardDisplayList()
                buffer.close()
            }
        }
    }

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 34)
    fun nativeRelativeColorEncodingRetainsItsComposedFp16Scene() {
        val usage = android.hardware.HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or
            android.hardware.HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
        org.junit.Assume.assumeTrue(android.hardware.HardwareBuffer.isSupported(
            96, 64, android.hardware.HardwareBuffer.RGBA_FP16, 1, usage))
        data class Scene(val backdrop: FloatArray, val actual: FloatArray, val relativeAlpha: Float)
        val scenes = listOf(
            Scene(floatArrayOf(.3f, .5f, .8f, 1f), floatArrayOf(.1f, .7f, 1.2f), .35f),
            Scene(floatArrayOf(.75f, .2f, 1.2f, .4f), floatArrayOf(.4f, .7f, 1.25f), .3f),
            Scene(floatArrayOf(1.1f, .6f, .2f, .7f), floatArrayOf(-.03f, .15f, 1.1f), .55f),
            Scene(floatArrayOf(0f, 0f, 0f, 0f), floatArrayOf(1.1f, .15f, .3f), .25f),
        )
        val spaces = listOf(android.graphics.ColorSpace.Named.EXTENDED_SRGB,
            android.graphics.ColorSpace.Named.LINEAR_EXTENDED_SRGB)
        for (named in spaces) for (managed in listOf(false, true)) {
            val space = android.graphics.ColorSpace.get(named)
            val buffer = android.hardware.HardwareBuffer.create(
                96, 64, android.hardware.HardwareBuffer.RGBA_FP16, 1, usage)
            val node = android.graphics.RenderNode("native-relative-color").apply { setPosition(0, 0, 96, 64) }
            val expectedAlphas = ArrayList<Float>()
            try {
                val functions = io.github.neoruaa.glasmio.glass.nativeGlassAtlasColorFunctions(managed)
                fun paint(expression: String, uniforms: Map<String, FloatArray>, over: Boolean = false): android.graphics.Paint {
                    val declarations = uniforms.keys.joinToString("\n") { "uniform float4 $it;" }
                    val shader = android.graphics.RuntimeShader(functions + declarations +
                        "\nhalf4 main(float2 p) { return half4($expression); }")
                    uniforms.forEach { (name, values) -> shader.setFloatUniform(name, values) }
                    return android.graphics.Paint().apply {
                        this.shader = shader
                        blendMode = if (over) android.graphics.BlendMode.SRC_OVER else android.graphics.BlendMode.SRC
                    }
                }
                val canvas = node.beginRecording(96, 64)
                canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.BlendMode.CLEAR)
                scenes.forEachIndexed { row, scene ->
                    val backdrop = FloatArray(4) { if (it == 3) scene.backdrop[3] else scene.backdrop[it] * scene.backdrop[3] }
                    val alpha = scene.relativeAlpha + backdrop[3] * (1f - scene.relativeAlpha)
                    expectedAlphas += alpha
                    val actual = FloatArray(4) { if (it == 3) alpha else scene.actual[it] * alpha }
                    val relative = FloatArray(4) { if (it == 3) scene.relativeAlpha
                        else actual[it] - backdrop[it] * (1f - scene.relativeAlpha) }
                    val top = row * 16f
                    canvas.drawRect(0f, top, 32f, top + 16f,
                        paint("nativeAtlasEncode(actual)", mapOf("actual" to actual)))
                    canvas.drawRect(32f, top, 96f, top + 16f,
                        paint("nativeAtlasEncode(backdrop)", mapOf("backdrop" to backdrop)))
                    canvas.drawRect(32f, top, 64f, top + 16f,
                        paint("nativeAtlasEncodeRelative(relative, backdrop)",
                            mapOf("relative" to relative, "backdrop" to backdrop), over = true))
                    // Keep the old nonlinear-operator path as a discriminating control.
                    canvas.drawRect(64f, top, 96f, top + 16f,
                        paint("nativeAtlasEncode(relative)", mapOf("relative" to relative), over = true))
                }
                node.endRecording()
                android.graphics.HardwareBufferRenderer(buffer).use { renderer ->
                    renderer.setContentRoot(node)
                    val done = java.util.concurrent.CountDownLatch(1)
                    val error = java.util.concurrent.atomic.AtomicReference<Throwable>()
                    renderer.obtainRenderRequest().setColorSpace(space).draw({ it.run() }) { result ->
                        try {
                            check(result.status == android.graphics.HardwareBufferRenderer.RenderResult.SUCCESS)
                            result.fence.use { if (it.isValid) check(it.await(java.time.Duration.ofSeconds(3))) }
                        } catch (failure: Throwable) { error.set(failure) }
                        finally { done.countDown() }
                    }
                    assertTrue("The relative-color FP16 render must finish", done.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    error.get()?.let { throw it }
                    val hardware = requireNotNull(android.graphics.Bitmap.wrapHardwareBuffer(buffer, space))
                    try {
                        val pixels = requireNotNull(hardware.copy(android.graphics.Bitmap.Config.RGBA_F16, false))
                        try {
                            var legacyDifference = 0f
                            scenes.indices.forEach { row ->
                                val reference = pixels.getColor(16, row * 16 + 8).components
                                val actual = pixels.getColor(48, row * 16 + 8).components
                                val legacy = pixels.getColor(80, row * 16 + 8).components
                                repeat(4) { channel ->
                                    assertEquals("space=$named managed=$managed row=$row channel=$channel",
                                        reference[channel], actual[channel], .008f)
                                    legacyDifference = maxOf(legacyDifference, abs(reference[channel] - legacy[channel]))
                                }
                                assertEquals(expectedAlphas[row], actual[3], .002f)
                                if (row == 0) assertTrue("HDR blue must survive the composed scene", actual[2] > 1f)
                                if (row == 2) assertTrue("Negative red must survive the composed scene", actual[0] < 0f)
                            }
                            if (managed && named == android.graphics.ColorSpace.Named.LINEAR_EXTENDED_SRGB)
                                assertTrue("The fixture must reject directly converting a nonlinear relative operator", legacyDifference > .02f)
                        } finally { pixels.recycle() }
                    } finally { hardware.recycle() }
                }
            } finally {
                node.discardDisplayList()
                buffer.close()
            }
        }

        // Exercise the production texture barriers as well as the operator above. Numeric
        // native-gamma texels must match through the Activity-owned layers in both
        // working spaces, with the completed result read back into an FP16 target.
        val graphicsContext = java.util.concurrent.atomic.AtomicReference<androidx.compose.ui.graphics.GraphicsContext>()
        compose.setContent {
            graphicsContext.set(androidx.compose.ui.platform.LocalGraphicsContext.current)
            Box(Modifier.size(1.dp))
        }
        compose.waitForIdle()
        val nativeSpace = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.EXTENDED_SRGB)
        val sourceSize = androidx.compose.ui.unit.IntSize(96, 64)
        for (named in spaces) {
            val space = android.graphics.ColorSpace.get(named)
            val buffer = android.hardware.HardwareBuffer.create(
                192, 64, android.hardware.HardwareBuffer.RGBA_FP16, 1, usage)
            val node = android.graphics.RenderNode("native-relative-mip-transport").apply { setPosition(0, 0, 192, 64) }
            val passes = ArrayList<io.github.neoruaa.glasmio.glass.NativeGlassAtlasPasses>()
            val consumers = ArrayList<androidx.compose.ui.graphics.layer.GraphicsLayer>()
            try {
                compose.runOnIdle {
                    val context = graphicsContext.get()
                    val scope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
                    val canvas = node.beginRecording(192, 64)
                    canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.BlendMode.CLEAR)
                    scope.draw(Density(1f), LayoutDirection.Ltr,
                        androidx.compose.ui.graphics.Canvas(canvas), Size(192f, 64f)) {
                        val sourceShader = android.graphics.RuntimeShader(
                            io.github.neoruaa.glasmio.glass.render.GlassColorSpaceShaderFunctions + """
uniform float4 sourceBand0;
uniform float4 sourceBand1;
uniform float4 sourceBand2;
uniform float4 sourceBand3;
half4 main(float2 p) {
    float4 value = p.y < 16.0 ? sourceBand0 : (p.y < 32.0 ? sourceBand1 : (p.y < 48.0 ? sourceBand2 : sourceBand3));
    return half4(glassNativeGammaToWorkingPremul(value));
}
""")
                        scenes.forEachIndexed { row, scene ->
                            // Supply signed, extended premultiplied channels directly to the GPU.
                            val alpha = scene.backdrop[3]
                            val red = if (row == 2) -.2f else scene.actual[0]
                            sourceShader.setFloatUniform("sourceBand$row", red * alpha,
                                scene.actual[1] * alpha, scene.actual[2] * alpha, alpha)
                        }
                        val sourcePaint = android.graphics.Paint().apply {
                            shader = sourceShader
                            blendMode = android.graphics.BlendMode.SRC
                        }
                        for (nativeStorage in listOf(false, true)) {
                            val pyramid = io.github.neoruaa.glasmio.glass.NativeGlassMipPyramid(
                                io.github.neoruaa.glasmio.glass.render.RuntimeShaderCacheImpl())
                            val atlas = pyramid.atlas(null, sourceSize, 3f,
                                io.github.neoruaa.glasmio.glass.NativeGlassMipKernel.Compute13Tap,
                                colorManaged = true, nativeGammaStorage = nativeStorage)
                            val producer = io.github.neoruaa.glasmio.glass.NativeGlassAtlasPasses(context)
                            passes += producer
                            var recordings = 0
                            var sourceCalls = 0
                            val packed = producer.recordPyramid(atlas, record = { layer, size, block ->
                                recordings++
                                layer.record(this, layoutDirection, size, block)
                            }, sourceBlock = {
                                sourceCalls++
                                drawContext.canvas.nativeCanvas.drawRect(0f, 0f, 96f, 64f, sourcePaint)
                            })
                            assertEquals("Mip transport does not add a producer pass", atlas.rects.size + 1, recordings)
                            assertEquals("Mip transport records its real source once", 1, sourceCalls)
                            fun consumer(mode: Int): androidx.compose.ui.graphics.layer.GraphicsLayer {
                                val shader = android.graphics.RuntimeShader(atlas.samplingFunctions + """
float4 mipActual(float2 p, float4 backdrop) {
    float4 sampled = nativeAtlasSample(p, 1.5);
    float opacity = p.y < 16.0 ? .35 : (p.y < 32.0 ? .3 : (p.y < 48.0 ? .55 : .25));
    float alpha = opacity + backdrop.a * (1.0 - opacity);
    return float4(sampled.rgb / max(sampled.a, .00001) * alpha, alpha);
}
half4 main(float2 p) {
    float4 backdrop = nativeAtlasBilinear(p, 0);
""" + when (mode) {
                                    0 -> "return half4(nativeAtlasEncode(backdrop)); }"
                                    else -> """
    float4 actual = mipActual(p, backdrop);
    float opacity = p.y < 16.0 ? .35 : (p.y < 32.0 ? .3 : (p.y < 48.0 ? .55 : .25));
    float4 relative = float4(actual.rgb - backdrop.rgb * (1.0 - opacity), opacity);
    return half4(nativeAtlasEncodeRelative(relative, backdrop));
}
"""
                                })
                                atlas.configure(shader)
                                return context.createGraphicsLayer().also { layer ->
                                    consumers += layer
                                    layer.clip = true
                                    layer.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                                    layer.renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
                                        .asComposeRenderEffect()
                                    layer.record(this, layoutDirection, atlas.size) { drawLayer(packed) }
                                }
                            }
                            val origin = if (nativeStorage) 96f else 0f
                            withTransform({ translate(origin, 0f) }) {
                                clipRect(0f, 0f, 96f, 64f) {
                                    drawLayer(consumer(0))
                                    drawLayer(consumer(1))
                                }
                            }
                        }
                    }
                    node.endRecording()
                }
                android.graphics.HardwareBufferRenderer(buffer).use { renderer ->
                    renderer.setContentRoot(node)
                    val done = java.util.concurrent.CountDownLatch(1)
                    val error = java.util.concurrent.atomic.AtomicReference<Throwable>()
                    renderer.obtainRenderRequest().setColorSpace(space).draw({ it.run() }) { result ->
                        try {
                            check(result.status == android.graphics.HardwareBufferRenderer.RenderResult.SUCCESS)
                            result.fence.use { if (it.isValid) check(it.await(java.time.Duration.ofSeconds(3))) }
                        } catch (failure: Throwable) { error.set(failure) }
                        finally { done.countDown() }
                    }
                    assertTrue("The materialized FP16 mip render must finish", done.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    error.get()?.let { throw it }
                    val hardware = requireNotNull(android.graphics.Bitmap.wrapHardwareBuffer(buffer, space))
                    try {
                        val pixels = requireNotNull(hardware.copy(android.graphics.Bitmap.Config.RGBA_F16, false))
                        try {
                            var maximum = 0f
                            var maximumAlpha = 0f
                            var transportError = 0f
                            var transportDetail = ""
                            repeat(64) { y -> repeat(96) { x ->
                                val legacy = pixels.getColor(x, y).components
                                val transported = pixels.getColor(x + 96, y).components
                                repeat(4) { channel ->
                                    val difference = abs(legacy[channel] - transported[channel])
                                    if (difference > transportError) {
                                        transportError = difference
                                        transportDetail = "xy=$x,$y channel=$channel row=${y / 16} legacy=${legacy.contentToString()} " +
                                            "carrier=${transported.contentToString()}"
                                    }
                                    maximum = maxOf(maximum, difference)
                                }
                                maximumAlpha = maxOf(maximumAlpha, abs(legacy[3] - transported[3]))
                            } }
                            val diagnostic = "space=$named max=$maximum alpha=$maximumAlpha transport=$transportError [$transportDetail]"
                            println("GLASMIO_NATIVE_MIP_FP16 $diagnostic")
                            android.util.Log.i("GlasmioMipFp16", diagnostic)
                            assertTrue("Native-gamma mip transport retains composed FP16 values: $diagnostic", maximum <= .008f)
                            assertTrue("Native-gamma mip transport retains alpha in $named: $maximumAlpha", maximumAlpha <= .002f)
                            val blue = pixels.getColor(144, 8).convert(nativeSpace)
                            val negative = pixels.getColor(144, 40).convert(nativeSpace)
                            android.util.Log.i("GlasmioMipFp16", "space=$named blue=${blue.components.contentToString()} negative=${negative.components.contentToString()}")
                            assertTrue("The actual mip pipeline retains HDR blue", blue.blue() > 1f)
                            // These GraphicsLayers use the Activity's intermediate format,
                            // which can clip negative source colors on an SDR display even
                            // when the outer readback is FP16. Compare every transported
                            // channel above; the direct FP16 operator separately asserts
                            // negative-color preservation without that platform boundary.
                            assertTrue("The actual mip pipeline retains partial alpha", negative.alpha() in .7f.. .99f)
                        } finally { pixels.recycle() }
                    } finally { hardware.recycle() }
                }
            } finally {
                node.discardDisplayList()
                compose.runOnIdle {
                    consumers.forEach(graphicsContext.get()::releaseGraphicsLayer)
                    passes.forEach { it.release() }
                }
                buffer.close()
            }
        }

    }

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 36)
    fun nativeTouchGlowRetainsItsUnclampedHdrEnergyInAnFp16Target() {
        val width = 420
        val height = 100
        val usage = android.hardware.HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or
            android.hardware.HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
        org.junit.Assume.assumeTrue(android.hardware.HardwareBuffer.isSupported(
            width, height, android.hardware.HardwareBuffer.RGBA_FP16, 1, usage))
        val nativeSpace = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.EXTENDED_SRGB)
        val tint = Color(0xFF0088FF)
        val size = Size(140f, 50f)
        val position = Offset(70.5f, 25.5f)
        val bounds = androidx.compose.ui.geometry.Rect(Offset.Zero, size)
        // Only the erroneous cap changes in this negative control. Keep the native mask,
        // coefficients, two opacities and destination-space composition identical.
        val clampedTransfer = io.github.neoruaa.glasmio.glass.NativePressGlowTransfer
            .replace("float2(0.0, 0.0)", "float2(1.2, 0.0)")
        val negativeBlender = android.graphics.RuntimeXfermode(
            io.github.neoruaa.glasmio.glass.render.GlassColorSpaceShaderFunctions + clampedTransfer + """
uniform float glowGain;
half4 main(half4 src, half4 dst) {
    float4 target = glassWorkingToNativeGammaPremul(float4(dst));
    return half4(glassNativeGammaToWorkingPremul(float4(nativeApplyPressGlow(
        half4(target), float(src.r), float(src.g), glowGain))));
}
""")
        negativeBlender.setFloatUniform("glowGain", io.github.neoruaa.glasmio.glass.nativePressDodgeGain(false, tint, false))
        val negativeMask = android.graphics.RuntimeShader(
            io.github.neoruaa.glasmio.glass.NativePressGlowFunctions + io.github.neoruaa.glasmio.glass.PressMaskMain)
        io.github.neoruaa.glasmio.glass.configureNativePressGlow(negativeMask, 1f, position, bounds,
            1f, true, null, false, tint, false, 0f)
        val negativePaint = android.graphics.Paint().apply { shader = negativeMask; xfermode = negativeBlender }
        val production = io.github.neoruaa.glasmio.glass.PressHighlightRenderer()
        val scope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
        listOf(nativeSpace, android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.LINEAR_EXTENDED_SRGB)).forEach { space ->
            val buffer = android.hardware.HardwareBuffer.create(
                width, height, android.hardware.HardwareBuffer.RGBA_FP16, 1, usage)
            val node = android.graphics.RenderNode("native-touch-glow-hdr").apply { setPosition(0, 0, width, height) }
            try {
                val canvas = node.beginRecording(width, height)
                canvas.drawColor(android.graphics.Color.WHITE)
                val blue = android.graphics.Paint().apply {
                    setColor(android.graphics.Color.pack(0f, 136f / 255f, 1f, 1f, nativeSpace))
                }
                canvas.drawRect(0f, 50f, width.toFloat(), height.toFloat(), blue)
                for (row in 0..1) {
                    var saved = canvas.save()
                    canvas.translate(140f, row * 50f)
                    scope.draw(Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
                        androidx.compose.ui.graphics.Canvas(canvas), size) {
                        production.draw(this, 1f, position, tint = tint)
                    }
                    canvas.restoreToCount(saved)
                    saved = canvas.save()
                    canvas.translate(280f, row * 50f)
                    canvas.drawRect(0f, 0f, 140f, 50f, negativePaint)
                    canvas.restoreToCount(saved)
                }
                node.endRecording()
                android.graphics.HardwareBufferRenderer(buffer).use { renderer ->
                    renderer.setContentRoot(node)
                    val done = java.util.concurrent.CountDownLatch(1)
                    val error = java.util.concurrent.atomic.AtomicReference<Throwable>()
                    renderer.obtainRenderRequest().setColorSpace(space).draw({ it.run() }) { result ->
                        try {
                            check(result.status == android.graphics.HardwareBufferRenderer.RenderResult.SUCCESS)
                            result.fence.use { if (it.isValid) check(it.await(java.time.Duration.ofSeconds(3))) }
                        } catch (failure: Throwable) { error.set(failure) }
                        finally { done.countDown() }
                    }
                    assertTrue("The touch-glow FP16 render must finish", done.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    error.get()?.let { throw it }
                    val hardware = requireNotNull(android.graphics.Bitmap.wrapHardwareBuffer(buffer, space))
                    try {
                        val pixels = requireNotNull(hardware.copy(android.graphics.Bitmap.Config.RGBA_F16, false))
                        try {
                            val white = pixels.getColor(70, 25).convert(nativeSpace)
                            val litWhite = pixels.getColor(210, 25).convert(nativeSpace)
                            val clampedWhite = pixels.getColor(350, 25).convert(nativeSpace)
                            val untouchedBlue = pixels.getColor(70, 75).convert(nativeSpace)
                            val litBlue = pixels.getColor(210, 75).convert(nativeSpace)
                            val clampedBlue = pixels.getColor(350, 75).convert(nativeSpace)
                            assertEquals("The baseline white stays unchanged", 1f, white.red(), .005f)
                            assertEquals("The baseline blue stays unchanged", 136f / 255f, untouchedBlue.green(), .005f)
                            // Exact 13 mini held-1600 matrices/opacities at the circular shadow's center.
                            assertEquals("The touch matrices retain their native white response", 1.1858f, litWhite.red(), .01f)
                            assertEquals("The old cap suppresses white HDR energy", 1.0651f, clampedWhite.red(), .01f)
                            assertEquals("The touch response keeps its blue-dependent green", .6863f, litBlue.green(), .015f)
                            assertEquals("The touch response retains extended blue", 1.3838f, litBlue.blue(), .015f)
                            assertEquals("The old cap suppresses blue HDR energy", 1.1568f, clampedBlue.blue(), .015f)
                            assertTrue("The fixture must reject the old white cap", litWhite.red() - clampedWhite.red() > .1f)
                            assertTrue("The fixture must reject the old blue cap", litBlue.blue() - clampedBlue.blue() > .2f)
                            assertEquals("Touch lighting preserves opaque alpha", 1f, litBlue.alpha(), .003f)
                            println("GLASMIO_NATIVE_PRESS_HDR space=${space.name} white=${litWhite.red()} legacyWhite=${clampedWhite.red()} blue=${litBlue.red()},${litBlue.green()},${litBlue.blue()} legacyBlue=${clampedBlue.blue()}")
                        } finally { pixels.recycle() }
                    } finally { hardware.recycle() }
                }
            } finally {
                node.discardDisplayList()
                buffer.close()
            }
        }
    }

    @Test
    fun completionSourcesRetainFullFieldVariantsAndResetWithTheirScope() {
        val fieldA = "float3 navigationGlassField(float2 p) { return float3(-4.0, 1.0, 0.0); }\n//Aa\n"
        val fieldB = "float3 navigationGlassField(float2 p) { return float3(-4.0, 1.0, 0.0); }\n//BB\n"
        assertEquals("The fixture must exercise the runtime shader key's field-hash collision", fieldA.hashCode(), fieldB.hashCode())
        val lruFields = List(8) { fieldA + "// LRU entry $it\n" }
        val variants = listOf(Triple(fieldA, false, false), Triple(fieldA, false, false),
            Triple(fieldA, true, false), Triple(fieldA, true, false),
            Triple(fieldA, true, true), Triple(fieldA, true, true),
            Triple(fieldB, true, true), Triple(fieldB, true, true),
            Triple(fieldA, true, true), Triple(fieldA, false, false), Triple(fieldA, false, false)) +
            lruFields.take(7).map { Triple(it, false, false) } + listOf(
                Triple(fieldA, false, false), Triple(lruFields[7], false, false),
                Triple(fieldA, false, false), Triple(lruFields[6], false, false), Triple(lruFields[0], false, false))
        val selected = mutableStateOf(0)
        val sources = Array(variants.size) { ArrayList<String>() }
        class FixtureScope : BackdropEffectScopeImpl() {
            override val shape = RectangleShape
            var variant = 0
            override fun obtainRuntimeShader(key: String, string: String): io.github.neoruaa.glasmio.glass.render.RuntimeShader {
                sources[variant] += string
                return super.obtainRuntimeShader(key, string)
            }
        }
        val fixture = FixtureScope()
        compose.setContent {
            Canvas(Modifier.requiredSize(64.dp, 40.dp)) {
                val index = selected.value
                val (functions, managed, raw) = variants[index]
                fixture.variant = index
                fixture.update(this)
                fixture.colorManagedNativeGlass = managed
                fixture.apply {
                    completeNativeFilteredGlass(androidx.compose.ui.unit.IntSize(64, 40), null, false,
                        io.github.neoruaa.glasmio.glass.NavigationGlassMaterial.RegularMedium, 1f,
                        field = io.github.neoruaa.glasmio.glass.NativeGlassFilterField(functions) {},
                        filteredIsNativeRaw = raw)
                }
                drawRect(Color.White)
            }
        }
        for (index in variants.indices) {
            if (index > 0) compose.runOnIdle {
                if (index == 10) fixture.reset()
                selected.value = index
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertTrue("Each variant must build or obtain a shader", sources[index].isNotEmpty())
                sources[index].forEach { source ->
                    assertTrue("Repeated effect updates must retain the same complete source", source === sources[index].first())
                }
            }
        }
        compose.runOnIdle {
            listOf(0 to 1, 2 to 3, 4 to 5, 6 to 7, 4 to 8, 0 to 9).forEach { (first, next) ->
                assertTrue("Identical full source variants must reuse the immutable string", sources[first].first() === sources[next].first())
            }
            assertTrue("Managed and raw-storage modes retain distinct programs", sources[2].first() != sources[4].first())
            assertTrue("Colliding field hashes must retain distinct complete sources", sources[4].first() != sources[6].first())
            assertEquals("Reset preserves program contents", sources[0].first(), sources[10].first())
            assertTrue("Reset releases the scope-owned source instance", sources[0].first() !== sources[10].first())
            assertTrue("Accessing an older entry must keep it in the eight-entry LRU", sources[10].first() === sources[18].first())
            assertTrue("The recently accessed entry must survive overflow", sources[10].first() === sources[20].first())
            assertTrue("A recent distinct field must remain cached after overflow", sources[17].first() === sources[21].first())
            assertEquals("Eviction does not change the rebuilt source", sources[11].first(), sources[22].first())
            assertTrue("Overflow must release and rebuild the least recently used source", sources[11].first() !== sources[22].first())
        }
    }

    @Test
    fun retainedMipDisplayListsKeepLiveSourcePropertiesAndRecordingEnvironment() {
        val phase = mutableStateOf(0)
        val showReference = mutableStateOf(false)
        val counts = Array(11) { ArrayList<Int>() }
        val sourceCounts = Array(11) { ArrayList<Pair<Int, Int>>() }
        var retained: io.github.neoruaa.glasmio.glass.NativeGlassAtlasPasses? = null
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val context = androidx.compose.ui.platform.LocalGraphicsContext.current
                val cached = remember(context) { io.github.neoruaa.glasmio.glass.NativeGlassAtlasPasses(context) }
                val reference = remember(context) { io.github.neoruaa.glasmio.glass.NativeGlassAtlasPasses(context) }
                val pyramid = remember { io.github.neoruaa.glasmio.glass.NativeGlassMipPyramid(
                    io.github.neoruaa.glasmio.glass.render.RuntimeShaderCacheImpl()) }
                SideEffect { retained = cached }
                DisposableEffect(cached, reference) { onDispose { cached.release(); reference.release() } }
                Canvas(Modifier.requiredSize(160.dp, 96.dp).testTag("retained-mip-scene")) {
                    val current = phase.value
                    val extent = if (current < 6) androidx.compose.ui.unit.IntSize(64, 48)
                        else androidx.compose.ui.unit.IntSize(65, 49)
                    val atlas = pyramid.atlas(null, extent, 3f)
                    // A new identity forces the reference to record every mip each time.
                    val fresh = io.github.neoruaa.glasmio.glass.NativeGlassMipAtlas(
                        atlas.baseSize, atlas.size, atlas.rects, null, atlas.colorManaged,
                        emptyList(), atlas.textureStages, atlas.textureSource,
                        nativeGammaStorage = atlas.nativeGammaStorage,
                    )
                    val recordingDensity = Density(if (current >= 2) 1.25f else 1f,
                        fontScale = if (current >= 3) 1.3f else 1f)
                    val direction = if (current >= 4) LayoutDirection.Rtl else LayoutDirection.Ltr
                    var recordings = 0
                    val calls = IntArray(2)
                    fun source(index: Int): androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                        calls[index]++
                        val layer = requireNotNull(drawContext.graphicsLayer)
                        layer.topLeft = androidx.compose.ui.unit.IntOffset(current % 3, current % 2)
                        layer.alpha = if (current % 2 == 0) 1f else .73f
                        layer.scaleX = if (current % 2 == 0) 1f else .95f
                        layer.rotationZ = if (current % 3 == 0) 0f else 7f
                        layer.shadowElevation = if (current % 2 == 0) 0f else 2f
                        layer.blendMode = if (current % 2 == 0) androidx.compose.ui.graphics.BlendMode.SrcOver
                            else androidx.compose.ui.graphics.BlendMode.Src
                        layer.colorFilter = if (current % 3 == 0) null
                            else ColorFilter.tint(Color(0xFFD8E7F3), androidx.compose.ui.graphics.BlendMode.Modulate)
                        layer.setRoundRectOutline(size = Size(extent.width.toFloat(), extent.height.toFloat()),
                            cornerRadius = if (current % 2 == 0) 0f else 9f)
                        layer.setOutsets(current % 2 * 3, current % 2 * 2, current % 2 * 3, current % 2 * 2)
                        drawRect(if (current % 2 == 0) Color(0xFF163D74) else Color(0xFFE09B28))
                        for (x in 0 until extent.width step 7) {
                            drawRect(Color(0xBBDD3964), Offset(x.toFloat(), 0f), Size(3f, extent.height.toFloat()))
                        }
                        drawCircle(Color(0xAABBFF22), 12f, Offset(33f + current, 21f))
                    }
                    val actual = cached.recordPyramid(atlas, record = { layer, size, block ->
                        recordings++
                        layer.record(recordingDensity, direction, size, block)
                    }, sourceBlock = source(0))
                    val expected = reference.recordPyramid(fresh, record = { layer, size, block ->
                        layer.record(recordingDensity, direction, size, block)
                    }, sourceBlock = source(1))
                    drawRect(Color.White)
                    // Point-light shadows depend on the RenderNode's screen position.
                    // Compare both pipelines at the same physical coordinates.
                    drawLayer(if (showReference.value) expected else actual)
                    counts[current] += recordings
                    sourceCounts[current] += calls[0] to calls[1]
                }
            }
        }
        val expectedCounts = listOf(5, 3, 5, 5, 5, 3, 5, 3, 5, 5, 3)
        for (current in expectedCounts.indices) {
            if (current > 0) compose.runOnIdle {
                if (current == 8) requireNotNull(retained).release()
                if (current == 9) requireNotNull(retained).layers(emptyList())
                phase.value = current
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals("Only private descendants may reuse their recordings at phase $current",
                    expectedCounts[current], counts[current].first())
            }
            // The phase's first recording has already applied any new outsets. Both
            // captures below therefore use that same settled recording origin.
            compose.runOnIdle { showReference.value = true }
            compose.waitForIdle()
            val expected = compose.onNodeWithTag("retained-mip-scene").captureToImage().toPixelMap()
            compose.runOnIdle { showReference.value = false }
            compose.waitForIdle()
            val actual = compose.onNodeWithTag("retained-mip-scene").captureToImage().toPixelMap()
            compose.runOnIdle {
                sourceCounts[current].forEach { (actualCalls, expectedCalls) ->
                    assertEquals("Source callbacks must keep the full-recording count at phase $current",
                        expectedCalls, actualCalls)
                }
            }
            val width = if (current < 6) 64 else 65
            val height = if (current < 6) 72 else 74
            for (y in 0 until height) for (x in 0 until width) {
                assertEquals("Live source and retained mips must match at phase=$current $x,$y",
                    expected[x, y], actual[x, y])
            }
        }
    }

    @Test
    fun defaultBackdropRecordsNestedContentOnceAndPreservesPixels() {
        val capture = mutableStateOf(false)
        val redraw = mutableFloatStateOf(0f)
        val angle = mutableFloatStateOf(0f)
        val opacity = mutableFloatStateOf(1f)
        var contentDraws = 0
        compose.setContent {
            val outer = rememberLayerBackdrop()
            val middle = rememberLayerBackdrop()
            val inner = rememberLayerBackdrop()
            Box(Modifier.size(240.dp, 200.dp).background(Color(0xFFB9CBD7)).testTag("capture-replay-scene")) {
                Canvas(Modifier.offset(60.dp, 50.dp).size(120.dp, 90.dp)
                    .graphicsLayer {
                        rotationZ = angle.floatValue
                        alpha = opacity.floatValue
                        scaleX = .91f
                        scaleY = 1.07f
                    }
                    .then(if (capture.value) Modifier.layerBackdrop(outer, reuseContent = true)
                        .layerBackdrop(middle, reuseContent = true).layerBackdrop(inner, reuseContent = true) else Modifier)) {
                    redraw.floatValue
                    contentDraws++
                    // Include overflow, overlapping alpha and a destination-relative blend.
                    drawRect(Color(0xFFD52F42), Offset(-12.dp.toPx(), -9.dp.toPx()),
                        Size(size.width + 24.dp.toPx(), size.height + 18.dp.toPx()))
                    drawRect(Color.Blue.copy(alpha = .37f), Offset(20.dp.toPx(), 14.dp.toPx()),
                        Size(80.dp.toPx(), 60.dp.toPx()))
                    drawCircle(Color.White.copy(alpha = .42f), 28.dp.toPx(),
                        Offset(54.dp.toPx(), 43.dp.toPx()), blendMode = androidx.compose.ui.graphics.BlendMode.DstOut)
                }
            }
        }
        listOf(0f to 1f, 17f to .73f).forEach { (rotation, alpha) ->
            compose.runOnIdle {
                capture.value = false
                angle.floatValue = rotation
                opacity.floatValue = alpha
            }
            compose.waitForIdle()
            val expected = compose.onNodeWithTag("capture-replay-scene").captureToImage().toPixelMap()
            compose.runOnIdle { capture.value = true }
            compose.waitForIdle()
            compose.runOnIdle {
                contentDraws = 0
                redraw.floatValue++
            }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals("Nested captures must record the leaf once", 1, contentDraws) }
            val actual = compose.onNodeWithTag("capture-replay-scene").captureToImage().toPixelMap()
            for (y in 0 until expected.height) for (x in 0 until expected.width) {
                assertEquals("Capture replay must preserve rotation=$rotation alpha=$alpha at $x,$y",
                    expected[x, y], actual[x, y])
            }
        }
    }

    @Test
    fun customBackdropCapturesAndLayerPropertiesStayIndependentOfVisibleContent() {
        val redraw = mutableFloatStateOf(0f)
        val mutateDuringDraw = mutableStateOf(false)
        val contextSpecific = mutableStateOf(false)
        val sharedScene = mutableStateOf(false)
        val separateBackdrops = mutableStateOf(false)
        val showSecond = mutableStateOf(true)
        var defaultSource: io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop? = null
        var visibleDraws = 0
        var firstSiblingDraws = 0
        compose.setContent {
            val source = rememberLayerBackdrop()
            val alias = rememberLayerBackdrop(graphicsLayer = source.graphicsLayer)
            val custom = rememberLayerBackdrop(onDraw = { drawRect(Color.Magenta) })
            SideEffect { defaultSource = source }
            if (sharedScene.value) {
                Box(Modifier.size(200.dp, 100.dp).background(Color.White).testTag("shared-capture-scene")) {
                    Canvas(Modifier.size(100.dp).layerBackdrop(source, reuseContent = true)) {
                        redraw.floatValue
                        firstSiblingDraws++
                        drawRect(Color.Red)
                    }
                    if (showSecond.value) {
                        Canvas(Modifier.offset(100.dp, 0.dp).size(100.dp)
                            .layerBackdrop(if (separateBackdrops.value) alias else source, reuseContent = true)) {
                            redraw.floatValue
                            drawRect(Color.Blue)
                        }
                    }
                }
            } else {
                Box(Modifier.size(100.dp).background(Color.White).testTag("custom-capture-scene")) {
                    Canvas(Modifier.fillMaxSize().then(
                        if (contextSpecific.value) Modifier.layerBackdrop(source)
                        else Modifier.layerBackdrop(source, reuseContent = true),
                    ).layerBackdrop(custom, reuseContent = true)) {
                        redraw.floatValue
                        visibleDraws++
                        if (mutateDuringDraw.value) source.graphicsLayer.alpha = .25f
                        drawRect(if (contextSpecific.value && drawContext.graphicsLayer === source.graphicsLayer)
                            Color.Blue else Color.Red)
                    }
                }
            }
        }
        compose.waitForIdle()
        val expected = compose.onNodeWithTag("custom-capture-scene").captureToImage().toPixelMap()
        compose.runOnIdle {
            requireNotNull(defaultSource).graphicsLayer.alpha = .25f
            visibleDraws = 0
            redraw.floatValue++
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("A modified capture retains its independent drawing path", 2, visibleDraws) }
        val actual = compose.onNodeWithTag("custom-capture-scene").captureToImage().toPixelMap()
        assertEquals(Color.Red, actual[actual.width / 2, actual.height / 2])
        for (y in 0 until expected.height) for (x in 0 until expected.width) {
            assertEquals("Capture-only layer alpha must not change visible pixels at $x,$y", expected[x, y], actual[x, y])
        }
        compose.runOnIdle {
            requireNotNull(defaultSource).graphicsLayer.alpha = 1f
            mutateDuringDraw.value = true
            visibleDraws = 0
            redraw.floatValue++
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("A layer changed by its capture callback must draw visible content directly", 2, visibleDraws)
        }
        val mutated = compose.onNodeWithTag("custom-capture-scene").captureToImage().toPixelMap()
        for (y in 0 until expected.height) for (x in 0 until expected.width) {
            assertEquals("A property changed during capture must not change visible pixels at $x,$y",
                expected[x, y], mutated[x, y])
        }
        compose.runOnIdle {
            requireNotNull(defaultSource).graphicsLayer.alpha = 1f
            mutateDuringDraw.value = false
            contextSpecific.value = true
        }
        compose.waitForIdle()
        compose.runOnIdle {
            visibleDraws = 0
            redraw.floatValue++
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("The public default keeps separate visible and capture draw scopes", 2, visibleDraws)
        }
        val contextual = compose.onNodeWithTag("custom-capture-scene").captureToImage().toPixelMap()
        for (y in 0 until expected.height) for (x in 0 until expected.width) {
            assertEquals("Capture-specific drawing must not replace public default visible content at $x,$y",
                expected[x, y], contextual[x, y])
        }
        compose.runOnIdle {
            requireNotNull(defaultSource).graphicsLayer.alpha = 1f
            mutateDuringDraw.value = false
            sharedScene.value = true
        }
        listOf(false, true).forEach { differentWrappers ->
            compose.runOnIdle {
                separateBackdrops.value = differentWrappers
                showSecond.value = true
            }
            compose.waitForIdle()
            compose.runOnIdle {
                firstSiblingDraws = 0
                redraw.floatValue++
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals("Shared GraphicsLayer owners must retain direct visible drawing", 2, firstSiblingDraws)
            }
            val siblings = compose.onNodeWithTag("shared-capture-scene").captureToImage().toPixelMap()
            assertEquals("The first sibling must keep its own content", Color.Red,
                siblings[siblings.width / 4, siblings.height / 2])
            assertEquals("The second sibling must keep its own content", Color.Blue,
                siblings[siblings.width * 3 / 4, siblings.height / 2])
            compose.runOnIdle {
                firstSiblingDraws = 0
                showSecond.value = false
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals("Removing a shared owner must invalidate and restore one-pass recording", 1, firstSiblingDraws)
            }
            val remaining = compose.onNodeWithTag("shared-capture-scene").captureToImage().toPixelMap()
            assertEquals(Color.Red, remaining[remaining.width / 4, remaining.height / 2])
            assertEquals(Color.White, remaining[remaining.width * 3 / 4, remaining.height / 2])
        }
    }

    @Test
    fun sampledLayerRetainsItsAncestorScaleAndRotation() {
        val rotation = mutableFloatStateOf(0f)
        val showSample = mutableStateOf(false)
        compose.setContent {
            val source = rememberLayerBackdrop()
            Box(Modifier.size(300.dp, 220.dp).background(Color.Gray).testTag("matrix-scene")) {
                Canvas(Modifier.offset(48.dp, 40.dp).size(180.dp, 140.dp)
                    .graphicsLayer { scaleX = .5f; scaleY = .5f; rotationZ = rotation.floatValue }
                    .layerBackdrop(source)) {
                    drawRect(Color.White)
                    repeat(9) { stripe ->
                        if (stripe % 2 == 0) drawRect(Color.Black,
                            topLeft = Offset(stripe * size.width / 9f, 0f),
                            size = Size(size.width / 9f, size.height))
                    }
                }
                if (showSample.value) Box(Modifier.fillMaxSize().drawPlainBackdrop(
                    backdrop = source, shape = { RectangleShape }, effects = {},
                ))
            }
        }
        listOf(0f, 25f).forEach { angle ->
            compose.runOnIdle { rotation.floatValue = angle; showSample.value = false }
            compose.waitForIdle()
            val expected = compose.onNodeWithTag("matrix-scene").captureToImage().toPixelMap()
            compose.runOnIdle { showSample.value = true }
            compose.waitForIdle()
            val actual = compose.onNodeWithTag("matrix-scene").captureToImage().toPixelMap()
            var error = 0f
            var count = 0
            for (y in 4 until expected.height - 4 step 2) {
                for (x in 4 until expected.width - 4 step 2) {
                    val reference = expected[x, y]
                    if (reference.red > .02f && reference.red < .98f) continue
                    error += abs(actual[x, y].red - reference.red)
                    count++
                }
            }
            assertTrue("The patterned source must cover a useful sample area", count > 500)
            assertTrue("The sampled source must keep its ancestor transform at rotation=$angle, mean error=${error / count}",
                error / count < .025f)
        }
    }

    @Test
    fun luminanceOnlyProbesKeepOverflowVisibleAndTrackTheirCurrentRegion() {
        val enabled = mutableStateOf(false)
        val ownerWidth = mutableFloatStateOf(100f)
        val rightRegion = mutableStateOf(false)
        var probe: io.github.neoruaa.glasmio.glass.render.BackdropLuminance? = null
        compose.setContent {
            val luminance = rememberBackdropLuminance()
            SideEffect { probe = luminance }
            GlasmioTheme(isDark = false) {
                GlasmioBackdropHost(
                    modifier = Modifier.size(300.dp, 160.dp).testTag("luminance-overflow-scene"),
                    sampledContent = {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRect(Color.Black)
                            drawRect(Color.White, Offset(size.width / 2f, 0f), Size(size.width / 2f, size.height))
                        }
                    },
                    overlayContent = { backdrop ->
                        val density = LocalDensity.current
                        val sampling = if (!enabled.value) Modifier else Modifier.sampleBackdropLuminance(
                            backdrop, luminance, sampleBounds = {
                                with(density) {
                                    if (rightRegion.value) Rect(60.dp.toPx(), -20.dp.toPx(), 120.dp.toPx(), 40.dp.toPx())
                                    else Rect(-120.dp.toPx(), -20.dp.toPx(), -60.dp.toPx(), 40.dp.toPx())
                                }
                            })
                        Box(Modifier.align(Alignment.Center).size(ownerWidth.floatValue.dp, 44.dp).then(sampling)) {
                            Box(Modifier.requiredSize(120.dp, 44.dp).graphicsLayer {
                                scaleX = 1.7f; scaleY = 1.7f
                            }.background(Color.Red))
                        }
                    },
                )
            }
        }
        val scene = compose.onNodeWithTag("luminance-overflow-scene")
        for (width in listOf(100f, 0f)) {
            compose.runOnIdle { enabled.value = false; ownerWidth.floatValue = width }
            compose.waitForIdle()
            val reference = scene.captureToImage().toPixelMap()
            compose.runOnIdle { enabled.value = true; rightRegion.value = false }
            compose.waitUntil(10_000) { probe?.value?.let { it < .1f } == true }
            compose.waitForIdle()
            val actual = scene.captureToImage().toPixelMap()
            var visible = 0
            for (y in 0 until reference.height) for (x in 0 until reference.width) {
                val expected = reference[x, y]
                if (expected.red > .9f && expected.green < .1f && expected.blue < .1f) {
                    visible++
                    val observed = actual[x, y]
                    assertTrue("A luminance-only probe must not clip overflowing content, even with a zero-width owner: " +
                        "width=$width, x=$x, y=$y, color=$observed",
                        observed.red > .9f && observed.green < .1f && observed.blue < .1f)
                }
            }
            assertTrue("The overflow reference must contain visible content", visible > 100)
            compose.runOnIdle { rightRegion.value = true }
            compose.waitUntil(10_000) { probe?.value?.let { it > .9f } == true }
        }
        compose.runOnIdle { enabled.value = false }
    }

    @Test
    fun adaptiveGlassAnimatesMaterialAndForegroundContinuouslyAcrossRapidRetargets() {
        data class Frame(val targetDark: Boolean, val fraction: Float, val nestedFraction: Float,
            val foreground: Color, val symbolForeground: Color?, val nestedOwnsProbe: Boolean, val independentOwnsProbe: Boolean)
        val observed = java.util.concurrent.atomic.AtomicReference<Frame>()
        val controlledProbe = java.util.concurrent.atomic.AtomicReference<io.github.neoruaa.glasmio.glass.render.BackdropLuminance>()
        val appearance = GlassAppearance(adaptiveColorScheme = true)
        var unrelatedCompositions = 0
        val recordUnrelated = { unrelatedCompositions++; Unit }
        var initialUnrelatedCompositions: Int? = null
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    GlasmioTheme(isDark = false, appearance = appearance) {
                        val colors = LocalGlassColors.current
                        val probe = rememberBackdropLuminance()
                        val context = rememberResolvedGlassMaterialContext(colors, appearance, false,
                            probe, ownsLuminance = true, sampleBackdrop = true)
                        val backdrop = rememberCanvasBackdrop { drawRect(Color(.45f, .45f, .45f)) }
                        val shadows = rememberNavigationInnerShadows(true)
                        SideEffect { controlledProbe.set(probe) }
                        Box(Modifier.size(160.dp, 64.dp).testTag("adaptive-animation-material")
                            .navigationGlassBackgroundResolved(
                                backdrop = backdrop, shape = { com.kyant.shapes.Capsule() },
                                containerColor = colors.container, navigationMaterial = true,
                                innerShadows = shadows, isDark = context.isDark,
                                darkFraction = context.darkFraction, appearance = appearance,
                                // The test alone publishes luminance; real sampling must not overwrite it.
                                luminanceState = null,
                            )) {
                            Box(Modifier.offset(4.dp, 26.dp).size(12.dp).background(Color.Red))
                            ProvideGlassMaterialContext(context) {
                                val foreground = LocalContentColor.current
                                val symbolForeground = io.github.neoruaa.glasmio.core.LocalGlassContentColor.current
                                val nested = rememberGlassMaterialContext()
                                IndependentCompositionProbe(recordUnrelated)
                                Box(Modifier.offset(144.dp, 26.dp).size(12.dp).background(foreground))
                                ProvideGlassMaterialContext(rememberGlassMaterialContext(sampleBackdrop = false)) {
                                    val independent = rememberGlassMaterialContext()
                                    SideEffect {
                                        observed.set(Frame(context.isDark, context.darkFraction, nested.darkFraction,
                                            foreground, symbolForeground, nested.luminanceState != null, independent.luminanceState != null))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            fun sample(): Triple<Color, Color, Color> {
                val image = compose.onNodeWithTag("adaptive-animation-material").captureToImage().toPixelMap()
                return Triple(image[image.width / 2, image.height / 2],
                    image[(image.width * .0625f).roundToInt(), image.height / 2],
                    image[(image.width * .9375f).roundToInt(), image.height / 2])
            }
            fun difference(a: Color, b: Color) =
                (abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)) / 3f
            fun verifySharedFrame(): Frame = requireNotNull(observed.get()).also {
                assertEquals("A child must reuse its parent's presentation instead of starting another spring",
                    it.fraction, it.nestedFraction, 0f)
                assertTrue("An inherited surface has one probe writer", !it.nestedOwnsProbe)
                assertTrue("An unsampled host must let its descendant own a probe", it.independentOwnsProbe)
                assertEquals("Symbols and text must resolve the same live adaptive foreground", it.foreground, it.symbolForeground)
                initialUnrelatedCompositions?.let { initial ->
                    assertEquals("Adaptive material updates must leave unrelated content composed", initial, unrelatedCompositions)
                }
            }
            compose.mainClock.advanceTimeByFrame()
            val light = sample()
            initialUnrelatedCompositions = unrelatedCompositions
            assertEquals(0f, verifySharedFrame().fraction, 0f)
            compose.runOnIdle { requireNotNull(controlledProbe.get()).publish(.1f) }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(96)
            val intermediateFrame = verifySharedFrame()
            val intermediate = sample()
            assertTrue("A dark target must retain a continuous presentation weight: $intermediateFrame",
                intermediateFrame.targetDark && intermediateFrame.fraction in .01f.. .99f)
            assertTrue("The foreground must interpolate alongside the material",
                intermediateFrame.foreground != Color.Black && intermediateFrame.foreground != Color.White)
            assertTrue("The rendered foreground must use that interpolated color",
                difference(intermediate.third, intermediateFrame.foreground) < .02f)
            compose.mainClock.advanceTimeBy(2_000)
            val dark = sample()
            assertEquals(1f, verifySharedFrame().fraction, 0f)
            assertTrue("The endpoints must produce distinct material pixels", difference(light.first, dark.first) > .02f)
            assertTrue("An intermediate material must differ from both endpoints: light=${light.first}, " +
                "middle=${intermediate.first}, dark=${dark.first}",
                difference(intermediate.first, light.first) > .005f && difference(intermediate.first, dark.first) > .005f)
            listOf(light, intermediate, dark).forEach {
                assertTrue("Changing material weights must not fade out the control's opaque content: ${it.second}",
                    it.second.red > .98f && it.second.green < .02f && it.second.blue < .02f && it.second.alpha > .98f)
            }

            compose.runOnIdle { requireNotNull(controlledProbe.get()).publish(.8f) }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(96)
            val beforeRetarget = verifySharedFrame().fraction
            assertTrue(beforeRetarget in .01f.. .99f)
            compose.runOnIdle { requireNotNull(controlledProbe.get()).publish(.1f) }
            compose.mainClock.advanceTimeByFrame()
            val afterRetarget = verifySharedFrame()
            assertTrue("A rapid reversal must preserve its current presentation, before=$beforeRetarget, after=$afterRetarget",
                afterRetarget.targetDark && afterRetarget.fraction in .01f.. .99f &&
                    abs(afterRetarget.fraction - beforeRetarget) < .12f)
            compose.mainClock.advanceTimeBy(2_000)
            assertTrue("Retargeting must reach the unchanged dark endpoint", difference(dark.first, sample().first) < .005f)
            compose.runOnIdle { requireNotNull(controlledProbe.get()).publish(.8f) }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(2_000)
            assertEquals(0f, verifySharedFrame().fraction, 0f)
            assertTrue("The light endpoint must remain unchanged after a round trip", difference(light.first, sample().first) < .005f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun adaptiveGlassSamplesItsOwnRegionAndUpdatesItsForeground() {
        val flipped = mutableStateOf(false)
        val left = java.util.concurrent.atomic.AtomicReference(Color.Unspecified)
        val right = java.util.concurrent.atomic.AtomicReference(Color.Unspecified)
        val diagnostic = java.util.concurrent.ConcurrentHashMap<String, String>()
        compose.setContent {
            GlasmioTheme(isDark = false, appearance = GlassAppearance(adaptiveColorScheme = true)) {
                GlasmioBackdropHost(
                    modifier = Modifier.size(300.dp, 100.dp),
                    sampledContent = {
                        Canvas(Modifier.fillMaxSize()) {
                            val leftColor = if (flipped.value) Color.White else Color.Black
                            drawRect(leftColor)
                            drawRect(if (flipped.value) Color.Black else Color.White,
                                topLeft = Offset(size.width / 2f, 0f), size = Size(size.width / 2f, size.height))
                        }
                    },
                    overlayContent = { backdrop ->
                        // A non-sampling parent must not make its children inherit an unwritten probe.
                        ProvideGlassMaterialContext(rememberGlassMaterialContext(sampleBackdrop = false)) {
                            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassSurface(Modifier.size(120.dp, 56.dp).testTag("adaptive-left"), backdrop = backdrop) {
                                    val color = LocalContentColor.current
                                    val context = rememberGlassMaterialContext()
                                    SideEffect {
                                        left.set(color)
                                        diagnostic["left"] = "luma=${context.probe.value}, target=${context.isDark}, " +
                                            "fraction=${context.darkFraction}, color=$color, semantic=${context.colors.content}"
                                    }
                                }
                                GlassSurface(Modifier.size(120.dp, 56.dp).testTag("adaptive-right"), backdrop = backdrop) {
                                    val color = LocalContentColor.current
                                    val context = rememberGlassMaterialContext()
                                    SideEffect {
                                        right.set(color)
                                        diagnostic["right"] = "luma=${context.probe.value}, target=${context.isDark}, " +
                                            "fraction=${context.darkFraction}, color=$color, semantic=${context.colors.content}"
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
        try {
            compose.waitUntil(10_000) { left.get() == Color.White && right.get() == Color.Black }
            compose.runOnIdle { flipped.value = true }
            compose.waitUntil(10_000) { left.get() == Color.Black && right.get() == Color.White }
        } finally {
            println("GLASMIO_ADAPTIVE_REGION flipped=${flipped.value} $diagnostic")
        }
    }

    @Test
    fun adaptiveAlertKeepsItsContentInTheResolvedMaterialAppearance() {
        val background = mutableStateOf(Color.Black)
        val foreground = java.util.concurrent.atomic.AtomicReference(Color.Unspecified)
        val semantic = java.util.concurrent.atomic.AtomicReference(Color.Unspecified)
        val diagnostic = java.util.concurrent.atomic.AtomicReference("")
        val accent = Color(0xFF8E44AD)
        compose.setContent {
            GlasmioTheme(isDark = false, colors = defaultGlassColors(false, accent),
                appearance = GlassAppearance(adaptiveColorScheme = true)) {
                GlasmioBackdropHost(
                    modifier = Modifier.size(320.dp, 140.dp),
                    sampledContent = { Box(Modifier.fillMaxSize().background(background.value)) },
                    overlayContent = { backdrop ->
                        GlasmioAlertSurface(backdrop = backdrop, title = "Title") {
                            val contentColor = LocalContentColor.current
                            val colors = LocalGlassColors.current
                            val context = rememberGlassMaterialContext()
                            SideEffect {
                                foreground.set(contentColor)
                                semantic.set(colors.content)
                                diagnostic.set("luma=${context.probe.value}, target=${context.isDark}, fraction=${context.darkFraction}, " +
                                    "foreground=$contentColor, semantic=${colors.content}, context=${context.colors.content}")
                                assertEquals("Semantic accent must survive material adaptation", accent, colors.accent)
                            }
                        }
                    },
                )
            }
        }
        try {
            compose.waitUntil(10_000) { foreground.get() == Color.White && semantic.get() == Color.White }
            compose.runOnIdle { background.value = Color.White }
            compose.waitUntil(10_000) { foreground.get() == Color.Black && semantic.get() == Color.Black }
        } finally {
            println("GLASMIO_ADAPTIVE_ALERT background=${background.value} ${diagnostic.get()}")
        }
    }

    @Test
    fun realTouchGlowKeepsItsTintAndFollowsTheFinger() {
        compose.setContent {
            GlasmioTheme(isDark = false, colors = defaultGlassColors(false, Color(0xFF0088FF))) {
                GlasmioBackdropHost(
                    modifier = Modifier.size(220.dp, 100.dp),
                    sampledContent = { Box(Modifier.fillMaxSize().background(Color(0xFFF2F2F7))) },
                    overlayContent = { backdrop ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            GlasmioButton({}, Modifier.width(140.dp).testTag("native-touch-glow"),
                                size = GlasmioButtonSize.Large, emphasis = GlassEmphasis.Prominent,
                                backdrop = backdrop) { Text("Continue") }
                        }
                    },
                )
            }
        }
        val button = compose.onNodeWithTag("native-touch-glow")
        fun sample(): Pair<Color, Color> {
            val image = button.captureToImage().toPixelMap()
            return image[(image.width * .16f).roundToInt(), (image.height * .16f).roundToInt()] to
                image[(image.width * .84f).roundToInt(), (image.height * .16f).roundToInt()]
        }
        fun luma(c: Color) = c.red * .2126f + c.green * .7152f + c.blue * .0722f
        val idle = sample()
        compose.mainClock.autoAdvance = false
        try {
            button.performTouchInput { down(Offset(center.x * .44f, center.y)) }
            compose.mainClock.advanceTimeBy(1500)
            val held = sample()
            val near = luma(held.first) - luma(idle.first)
            val far = luma(held.second) - luma(idle.second)
            // A real iPhone hold has a whole-face vibrancy change and stronger local illumination.
            assertTrue("The native global matrix must brighten the far side: $far", far > .015f)
            assertTrue("The native local matrix must follow the finger: near=$near, far=$far", near > far + .015f)
            assertTrue("A blue prominent hold must preserve its saturated tint", held.first.red < .035f && held.second.red < .035f)
            button.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(2000)
            val released = sample()
            assertEquals("Release must restore the idle material", luma(idle.first), luma(released.first), .015f)
            assertEquals("Release must remove both vibrancy layers", luma(idle.second), luma(released.second), .015f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun sharedGlassAndProminentColorsRetainNativeBackdropResponse() {
        val source = mutableStateOf(Color.Black)
        val dark = mutableStateOf(false)
        val reduced = mutableStateOf(false)
        val tags = NavigationGlassMaterial.entries.map { it.name } + listOf("blue", "red")
        compose.setContent {
            GlasmioTheme(
                isDark = dark.value,
                colors = defaultGlassColors(dark.value, Color(0xFF0088FF)),
                appearance = GlassAppearance(reduceTransparency = reduced.value),
            ) {
                GlasmioBackdropHost(
                    modifier = Modifier.size(300.dp, 520.dp),
                    sampledContent = { Box(Modifier.fillMaxSize().background(source.value)) },
                    overlayContent = { backdrop ->
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            NavigationGlassMaterial.entries.forEach { material ->
                                GlassSurface(Modifier.size(240.dp, 56.dp).testTag(material.name),
                                    shape = ContinuousRoundedRectangle(28.dp), material = material,
                                    backdrop = backdrop) { }
                            }
                            GlasmioButton({}, Modifier.width(240.dp).testTag("blue"),
                                size = GlasmioButtonSize.Large, emphasis = GlassEmphasis.Prominent,
                                backdrop = backdrop) { }
                            GlasmioButton({}, Modifier.width(240.dp).testTag("red"),
                                size = GlasmioButtonSize.Large, emphasis = GlassEmphasis.Prominent,
                                destructive = true, backdrop = backdrop) { }
                        }
                    },
                )
            }
        }
        fun samples(): Map<String, Color> = tags.associateWith { tag ->
            val image = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            image[image.width / 2, image.height / 2]
        }
        fun difference(a: Color, b: Color): Float =
            (abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)) / 3f
        listOf(false, true).forEach { isDark ->
            compose.runOnIdle { dark.value = isDark; source.value = Color.Black }
            compose.waitForIdle()
            val black = samples()
            compose.runOnIdle { source.value = Color.White }
            compose.waitForIdle()
            val white = samples()
            tags.forEach { tag ->
                assertTrue("$tag must respond to the sampled background in dark=$isDark; black=${black[tag]}, white=${white[tag]}",
                    difference(black.getValue(tag), white.getValue(tag)) > .01f)
            }
            assertTrue("Clear must preserve more background contrast than Regular Small",
                difference(black.getValue("Clear"), white.getValue("Clear")) >
                    difference(black.getValue("RegularSmall"), white.getValue("RegularSmall")) * 1.1f)
            // Center samples captured from the 50-point UIKit controls on iPhone 13 mini.
            val nativeBlack = if (isDark) mapOf(
                "RegularSmall" to Color(.12549f, .12549f, .12549f),
                "blue" to Color(0f, .52941f, 1f),
                "red" to Color(1f, .25490f, .26275f),
            ) else mapOf(
                "RegularSmall" to Color(.52019f, .52019f, .52035f),
                "blue" to Color(0f, .41569f, .81176f),
                "red" to Color(.89412f, .09412f, .10196f),
            )
            val nativeWhite = if (isDark) mapOf(
                "RegularSmall" to Color(.72523f, .72523f, .72541f),
                "blue" to Color(.11922f, .57647f, .98039f),
                "red" to Color(.98824f, .39216f, .40392f),
            ) else mapOf(
                "RegularSmall" to Color.White,
                "blue" to Color(0f, .53725f, 1f),
                "red" to Color(1f, .22745f, .23922f),
            )
            listOf(black to nativeBlack, white to nativeWhite).forEach { (actual, native) ->
                native.forEach { (tag, expected) ->
                    val observed = actual.getValue(tag)
                    val error = maxOf(abs(observed.red - expected.red), abs(observed.green - expected.green),
                        abs(observed.blue - expected.blue))
                    assertTrue("$tag center must match UIKit dark=$isDark: expected=$expected, actual=$observed", error < .03f)
                }
            }
        }
        compose.runOnIdle { reduced.value = true; source.value = Color.Black }
        compose.waitForIdle()
        val opaqueBlack = samples()
        compose.runOnIdle { source.value = Color.White }
        compose.waitForIdle()
        val opaqueWhite = samples()
        tags.forEach { tag ->
            assertTrue("Reduce Transparency must keep $tag independent of the backdrop",
                difference(opaqueBlack.getValue(tag), opaqueWhite.getValue(tag)) < .005f)
        }
    }

    @Test
    fun contactSharingOnlyRecomposesWhenSurfaceAvailabilityChanges() {
        val active = mutableStateOf(false)
        val translation = mutableFloatStateOf(0f)
        val owner = BottomControlContact(4f).apply { nativeSmoothness = 6f }
        val member = BottomControlContactMember(
            contact = owner,
            active = mutableStateOf({ active.value }),
            visualBounds = mutableStateOf({ size: Size ->
                Rect(translation.floatValue, 0f, translation.floatValue + size.width, size.height)
            }),
            materialPressProgress = mutableStateOf({ 0f }),
            highlight = mutableStateOf({ _: Size -> GlassPressHighlight() }),
            lensSourceActive = mutableStateOf(null), lensSourceScale = mutableStateOf(null),
            lensSourcePress = mutableStateOf(null),
        )
        owner.members.add(member)
        var sharingCompositions = 0
        var observedSharing = true
        var observedSurface: Any? = null
        var observedBounds: Rect? = null
        var geometryDraws = 0
        val marker = Color(0xFF18A35C)
        val recordSharing: (Boolean) -> Unit = {
            sharingCompositions++
            observedSharing = it
        }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(160.dp, 80.dp).testTag("contact-sharing-scene")
                    .onGloballyPositioned(owner::updateCoordinates)
                    .drawWithContent {
                        val surface = owner.surface.value
                        observedSurface = surface
                        observedBounds = surface?.bounds
                        geometryDraws++
                        drawRect(Color.White)
                        drawContent()
                        surface?.bounds?.let { drawRect(marker, it.topLeft, it.size) }
                    }) {
                    Box(Modifier.size(100.dp, 40.dp).onGloballyPositioned(member::updateCoordinates))
                    ContactSharingCompositionProbe(member, recordSharing)
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(!observedSharing)
            assertEquals(null, observedSurface)
        }
        val absentCompositions = sharingCompositions
        compose.runOnIdle { active.value = true }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("An available surface must reach Boolean consumers", observedSharing)
            assertTrue(sharingCompositions > absentCompositions)
        }
        val presentCompositions = sharingCompositions
        var previousSurface = observedSurface
        listOf(1f, 7f, 13f).forEach { x ->
            val previousDraws = geometryDraws
            compose.runOnIdle { translation.floatValue = x }
            compose.waitForIdle()
            compose.runOnIdle {
                assertTrue("Geometry must publish a new live surface snapshot", observedSurface !== previousSurface)
                assertEquals(Rect(x, 0f, x + 100f, 40f), observedBounds)
                assertTrue("Geometry updates must still invalidate drawing", geometryDraws > previousDraws)
                assertEquals("A non-null surface's geometry must not recompose Boolean consumers",
                    presentCompositions, sharingCompositions)
                previousSurface = observedSurface
            }
            val pixels = compose.onNodeWithTag("contact-sharing-scene").captureToImage().toPixelMap()
            assertEquals(marker, pixels[(x + 10f).toInt(), 20])
            assertEquals(Color.White, pixels[(x + 110f).toInt(), 20])
        }
        compose.runOnIdle { active.value = false }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("Removing the surface must reach Boolean consumers", !observedSharing)
            assertEquals(null, observedSurface)
            assertTrue(sharingCompositions > presentCompositions)
        }
        val removedCompositions = sharingCompositions
        compose.runOnIdle { active.value = true }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("Restoring the surface must reach Boolean consumers", observedSharing)
            assertTrue(sharingCompositions > removedCompositions)
        }
    }

    @Composable
    private fun ContactSharingCompositionProbe(member: BottomControlContactMember, onComposition: (Boolean) -> Unit) {
        val sharing = member.sharesSurface
        SideEffect { onComposition(sharing) }
    }

    @Test
    fun separatedAndJoinedNavigationKeepOneMaterialAndTheSameDividerAnchor() {
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val appearance = mutableStateOf(GlassAppearance())
        val compact = mutableFloatStateOf(0f)
        var contact: BottomControlContact? = null
        var density = 1f
        var sharingCompositions = 0
        val recordSharing: (Boolean) -> Unit = { sharingCompositions++ }
        compose.setContent {
            density = LocalDensity.current.density
            GlasmioTheme(isDark = false, appearance = appearance.value) {
                CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                    GlasmioBackdropHost(
                        modifier = Modifier.size(360.dp, 220.dp).testTag("opticalScene"),
                        sampledContent = {
                            Canvas(Modifier.fillMaxSize()) {
                                // Broad separated markers survive the native 8-point fill and isolate coordinate mapping.
                                drawRect(Color.White)
                                listOf(-48f, 0f, 48f).forEach { x ->
                                    drawRect(Color.Black,
                                        topLeft = Offset(size.width / 2f + x.dp.toPx() - 7.dp.toPx(), 0f),
                                        size = Size(14.dp.toPx(), size.height))
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
                                owner.members.firstOrNull()?.let {
                                    ContactSharingCompositionProbe(it, recordSharing)
                                }
                            }
                        },
                    )
                }
            }
        }
        try {
            listOf(GlassAppearance(), GlassAppearance(adaptiveColorScheme = true),
                GlassAppearance(reduceTransparency = true)).forEach { mode ->
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
                        return listOf(-48f, 0f, 48f).map { offset ->
                            val center = pixels.width / 2f + offset * density
                            var weight = 0.0
                            var moment = 0.0
                            var maximumShade = 0f
                            for (x in (center - 10f * density).roundToInt()..(center + 10f * density).roundToInt()) {
                                val color = pixels[x, y]
                                val shade = (1f - (color.red + color.green + color.blue) / 3f).coerceAtLeast(0f)
                                maximumShade = maxOf(maximumShade, shade)
                                val w = shade * shade * shade
                                weight += w
                                moment += x * w
                            }
                            // Native press vibrancy changes contrast; the centroid test needs a
                            // discernible marker, not a fixed integral of its cubed brightness.
                            assertTrue("The ruler must remain visible through the material: offset=$offset, y=$y, " +
                                "weight=$weight, maximumShade=$maximumShade, direction=$layoutDirection",
                                maximumShade > .05f && weight > 0.0)
                            (moment / weight).toFloat()
                        }
                    }
                    compose.runOnIdle {
                        assertTrue(owner.members.all { it.sharesSurface })
                        assertTrue(!owner.connected.value)
                    }
                    val rest = if (mode.reduceTransparency) emptyList() else dividerCenters()
                    if (!mode.reduceTransparency) {
                        val referenceCenters = dividerCenters(reference.top - sceneBounds.top + 2f * density)
                        assertEquals("The central ruler must not shift toward the short bar's center",
                            referenceCenters[1], rest[1], 2f)
                        assertTrue("The left ruler bends left, matching a full-width reference",
                            rest[0] <= sceneBounds.width / 2f - 48f * density + 1f)
                    }
                    val home = compose.onNode(
                        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
                            hasContentDescription("Home"),
                    )
                    compose.mainClock.autoAdvance = false
                    val initialSurface = owner.surface.value
                    val initialSharingCompositions = sharingCompositions
                    var changedSurface = false
                    home.performTouchInput { down(center) }
                    listOf(16L, 16L, 16L, 16L, 64L, 256L).forEach { elapsed ->
                        compose.mainClock.advanceTimeBy(elapsed)
                        compose.runOnIdle {
                            assertTrue(owner.materialBackdrop === source)
                            assertTrue(owner.members.all { it.sharesSurface })
                            changedSurface = changedSurface || owner.surface.value !== initialSurface
                            assertEquals("Press geometry must not recompose a surface-availability consumer",
                                initialSharingCompositions, sharingCompositions)
                        }
                        if (!mode.reduceTransparency) {
                            assertEquals("The central ruler stays anchored while contact changes",
                                rest[1], dividerCenters()[1], 2f)
                        }
                    }
                    compose.runOnIdle {
                        assertTrue("The real navigation press must still update its live surface", changedSurface)
                        assertTrue("Held navigation must join its neighbor: mode=$mode, direction=$layoutDirection, " +
                            "members=${owner.members.map { it.capsule() }}, transform=${owner.lensTransform.value}, " +
                            "press=${owner.pressProgress()}", owner.connected.value)
                    }
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
                        if (progress == 1f) {
                            val pixels = scene.captureToImage().toPixelMap()
                            val centerX = pixels.width / 2
                            val centerY = (navigation.top - sceneBounds.top + 32f * density).roundToInt()
                            // The central black ruler is far outside both compact circles.
                            // No backing rectangle may replace the untouched source in this gap.
                            for (dy in -2..2) for (dx in -2..2) {
                                val color = pixels[centerX + dx, centerY + dy]
                                assertTrue("The compact gap must retain its source: " +
                                    "mode=$mode, direction=$layoutDirection, color=$color",
                                    maxOf(color.red, color.green, color.blue) < .02f)
                            }
                        }
                    }
                }
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun compactNavigationMaterialRetainsCurrentCapsuleGeometry() {
        val progress = mutableFloatStateOf(0f)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val background = Color(0xFF235578)
        val failures = ArrayList<String>()
        val drawReceipts = LinkedHashSet<String>()
        var contact: BottomControlContact? = null
        var retainedNavigationCoordinates: LayoutCoordinates? = null
        var density = 1f
        fun checkMaterial(image: ImageBitmap, capsule: LiquidContactCapsule, diagnostic: String) {
            val pixels = image.toPixelMap()
            fun difference(color: Color) = maxOf(abs(color.red - background.red),
                abs(color.green - background.green), abs(color.blue - background.blue))
            var error = 0f
            var samples = 0
            var maximumError = 0f
            // At height ~50, (6, 4) is outside a true capsule and inside
            // the narrow elliptical corner produced by an old wide source.
            for (right in listOf(false, true)) for (bottom in listOf(false, true)) {
                for (dy in -1..1) for (dx in -1..1) {
                    val x = (if (right) capsule.bounds.right - 6f * density
                        else capsule.bounds.left + 6f * density).roundToInt() + dx
                    val y = (if (bottom) capsule.bounds.bottom - 4f * density
                        else capsule.bounds.top + 4f * density).roundToInt() + dy
                    val difference = difference(pixels[x, y])
                    error += difference
                    maximumError = maxOf(maximumError, difference)
                    samples++
                }
            }
            val meanError = error / samples
            var bodyError = 0f
            val center = capsule.bounds.center
            for (dy in -1..1) for (dx in -1..1) {
                bodyError += difference(pixels[center.x.roundToInt() + dx, center.y.roundToInt() + dy])
            }
            bodyError /= 9f
            android.util.Log.i("GlasmioNavCapsule", "corners $diagnostic mean=$meanError max=$maximumError body=$bodyError")
            if (meanError >= .08f || maximumError >= .15f) failures +=
                "$diagnostic: native material fills capsule exterior, mean=$meanError max=$maximumError"
            if (bodyError <= meanError + .03f) failures +=
                "$diagnostic: native material body must remain visible, body=$bodyError exterior=$meanError"
        }
        compose.setContent {
            density = LocalDensity.current.density
            GlasmioTheme(isDark = false, appearance = GlassAppearance()) {
                CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                    GlasmioBackdropHost(
                        modifier = Modifier.requiredSize(300.dp, 192.dp).testTag("capsule-geometry-scene"),
                        sampledContent = { Box(Modifier.fillMaxSize().background(background)) },
                        overlayContent = { backdrop ->
                            BottomNavigationContact(
                                backdrop = backdrop,
                                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 16.dp)
                                    .width(263.dp).testTag("capsule-geometry-navigation"),
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
                                    compactProgress = progress, compactSize = 48.dp,
                                    backdrop = backdrop, modifier = Modifier.weight(1f)
                                        .onGloballyPositioned { retainedNavigationCoordinates = it },
                                )
                            }
                            // Replay only the real shared material. The selection lens and
                            // foreground glyphs must not masquerade as an outer contour error.
                            Box(Modifier.align(Alignment.TopStart).offset(x = 12.dp, y = 112.dp)
                                .size(263.dp, NavigationBarSurfaceHeight).testTag("capsule-geometry-material")
                                .drawWithContent {
                                    drawContent()
                                    val phase = progress.floatValue
                                    val layoutDirection = direction.value
                                    contact?.let { owner ->
                                        owner.members.firstOrNull()?.capsule()?.let { capsule ->
                                            val radius = capsule.size.minDimension / 2f
                                            val radii = Offset(radius * capsule.bounds.width / capsule.size.width,
                                                radius * capsule.bounds.height / capsule.size.height)
                                            val receipt = "draw direction=$layoutDirection p=$phase " +
                                                "source=${capsule.size} bounds=${capsule.bounds} radii=$radii " +
                                                "materialSize=${owner.materialBackdrop.graphicsLayer.size}"
                                            if (drawReceipts.add(receipt)) android.util.Log.i("GlasmioNavCapsule", receipt)
                                        }
                                        drawLayer(owner.materialBackdrop.graphicsLayer)
                                    }
                                })
                        },
                    )
                }
            }
        }
        compose.mainClock.autoAdvance = false
        try {
            val evidenceDirectory = java.io.File(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "navigation-capsule-geometry",
            ).apply { mkdirs() }
            listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { layoutDirection ->
                listOf(0f, .5f, .86f, .99f, 1f, .86f, .5f, 0f).forEachIndexed { index, phase ->
                    compose.runOnIdle { direction.value = layoutDirection; progress.floatValue = phase }
                    compose.mainClock.advanceTimeByFrame()
                    compose.waitForIdle()
                    val owner = requireNotNull(contact)
                    val capsule = compose.runOnIdle { requireNotNull(owner.members.single().capsule()) }
                    val radius = capsule.size.minDimension / 2f
                    val radii = Offset(radius * capsule.bounds.width / capsule.size.width,
                        radius * capsule.bounds.height / capsule.size.height)
                    val diagnostic = "direction=$layoutDirection p=$phase source=${capsule.size} " +
                        "bounds=${capsule.bounds} radii=$radii"
                    android.util.Log.i("GlasmioNavCapsule", "fixed $diagnostic")
                    if (abs(radii.x - radii.y) > density) failures += "$diagnostic: source ovalizes the capsule"
                    val image = compose.onNodeWithTag("capsule-geometry-material").captureToImage()
                    val evidence = java.io.File(evidenceDirectory, "${layoutDirection.name}-$index-$phase.png")
                    evidence.outputStream().use {
                        image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    android.util.Log.i("GlasmioNavCapsule", "material=$evidence")
                    if (phase >= .8f) {
                        checkMaterial(image, capsule, diagnostic)
                        // A shared material can measure before the surface's global callback.
                        // Preserve the real expanded wrapper registration to exercise that
                        // ordering without changing the native union's affine contract.
                        val retained = compose.runOnIdle {
                            owner.members.single().updateCoordinates(requireNotNull(retainedNavigationCoordinates))
                            val retained = requireNotNull(owner.members.single().capsule())
                            val retainedRadius = retained.size.minDimension / 2f
                            val retainedRadii = Offset(
                                retainedRadius * retained.bounds.width / retained.size.width,
                                retainedRadius * retained.bounds.height / retained.size.height,
                            )
                            val retainedDiagnostic = "retained direction=$layoutDirection p=$phase " +
                                "source=${retained.size} bounds=${retained.bounds} radii=$retainedRadii"
                            android.util.Log.i("GlasmioNavCapsule", retainedDiagnostic)
                            if (abs(retainedRadii.x - retainedRadii.y) > density) failures +=
                                "$retainedDiagnostic: prior registration ovalizes the current capsule"
                            retained
                        }
                        val retainedImage = compose.onNodeWithTag("capsule-geometry-material").captureToImage()
                        val retainedEvidence = java.io.File(evidenceDirectory,
                            "${layoutDirection.name}-$index-$phase-retained.png")
                        retainedEvidence.outputStream().use {
                            retainedImage.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                        }
                        android.util.Log.i("GlasmioNavCapsule", "retainedMaterial=$retainedEvidence")
                        checkMaterial(retainedImage, retained, "retained $diagnostic")
                    }
                }
            }
            compose.runOnIdle {
                val owner = requireNotNull(contact)
                val coordinates = requireNotNull(retainedNavigationCoordinates)
                val affine = BottomControlContactMember(
                    contact = owner,
                    active = mutableStateOf({ true }),
                    visualBounds = mutableStateOf({ size: Size ->
                        Rect(-.06f * size.width, .03f * size.height, 1.06f * size.width, .97f * size.height)
                    }),
                    materialPressProgress = mutableStateOf({ 0f }),
                    highlight = mutableStateOf({ _: Size -> GlassPressHighlight() }),
                    lensSourceActive = mutableStateOf(null), lensSourceScale = mutableStateOf(null),
                    lensSourcePress = mutableStateOf(null),
                )
                affine.updateCoordinates(coordinates)
                val capsule = requireNotNull(affine.capsule())
                assertEquals(coordinates.size.width.toFloat(), capsule.size.width, 0f)
                assertEquals(coordinates.size.height.toFloat(), capsule.size.height, 0f)
                assertEquals("Default members retain horizontal press deformation",
                    1.12f, capsule.bounds.width / capsule.size.width, .0001f)
                assertEquals("Default members retain vertical press deformation",
                    .94f, capsule.bounds.height / capsule.size.height, .0001f)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        assertTrue("Current capsule geometry/material failures:\n${failures.joinToString("\n")}", failures.isEmpty())
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
        assertTrue("A hidden two-character caption must not add a line: $expandedHeight > $singleLineHeight",
            expandedHeight <= singleLineHeight + 1f)
        compose.mainClock.autoAdvance = false
        try {
            listOf("adaptive", "open").forEach { target ->
                compose.runOnIdle { mode.value = target }
                compose.mainClock.advanceTimeByFrame()
                listOf(16L, 80L, 160L, 240L, 500L).forEach { elapsed ->
                    compose.mainClock.advanceTimeBy(elapsed)
                    val height = group.fetchSemanticsNode().boundsInRoot.height
                    assertTrue("Hiding or revealing Off must not add a line: $height > $singleLineHeight",
                        height <= singleLineHeight + 1f)
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
            assertEquals("An ordinary thumb drag submits a main mode only", listOf("open", "quiet"), modeRequests)
            assertEquals(beforeExit, strengthRequests.size)
            assertEquals(3, finishes)
            assertEquals("open", mode.value)
        }
        group.performTouchInput { click(Offset(width * 3.5f / 4f, 28.dp.toPx())) }
        compose.runOnIdle {
            assertEquals(listOf("open", "quiet", "quiet"), modeRequests)
            direction.value = LayoutDirection.Rtl
        }
        group.performTouchInput {
            swipe(Offset(width * 2.5f / 4f, 28.dp.toPx()), Offset(width * 1.5f / 4f, 28.dp.toPx()))
        }
        val ordinaryModeRequests = listOf("open", "quiet", "quiet", "adaptive")
        compose.runOnIdle {
            assertEquals("An RTL ordinary drag can enter the adaptive mode", ordinaryModeRequests, modeRequests)
            assertEquals(beforeExit, strengthRequests.size)
            assertEquals(3, finishes)
        }
        group.performTouchInput {
            down(Offset(width * 2.5f / 4f, 28.dp.toPx()))
            moveTo(Offset(width * 2.5f / 4f, 50.dp.toPx()))
            cancel()
        }
        compose.runOnIdle {
            assertEquals("Vertical scrolling does not select a main mode", ordinaryModeRequests, modeRequests)
            mode.value = "adaptive"
            enabled.value = false
        }
        adaptive.assertIsNotEnabled().performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(!it(0f)) }
        group.performTouchInput { click(Offset(width * 2.5f / 5f, 28.dp.toPx())) }
        compose.runOnIdle {
            assertEquals(beforeExit, strengthRequests.size)
            assertEquals(ordinaryModeRequests, modeRequests)
            assertEquals(3, finishes)
        }
        compose.runOnIdle {
            direction.value = LayoutDirection.Ltr
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
    fun sheetRefractionKeepsEachCornerIndependentOfTheOtherRadii() {
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val uniform = mutableStateOf<Float?>(null)
        compose.setContent {
            GlasmioTheme(isDark = false) {
                CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                    GlasmioBackdropHost(
                        modifier = Modifier.size(340.dp, 400.dp).testTag("sheet-corners-scene"),
                        sampledContent = {
                            Canvas(Modifier.fillMaxSize()) {
                                val step = 24.dp.toPx()
                                for (row in 0..(size.height / step).toInt()) {
                                    for (column in 0..(size.width / step).toInt()) {
                                        drawRect(if ((row + column) % 2 == 0) Color(0xFF0068D9) else Color(0xFFFFC93C),
                                            Offset(column * step, row * step), Size(step, step))
                                    }
                                }
                            }
                        },
                        overlayContent = { backdrop ->
                            val radius = uniform.value
                            GlasmioSheetSurface(
                                modifier = Modifier.align(Alignment.Center).size(300.dp, 340.dp),
                                backdrop = backdrop,
                                shape = if (radius != null) ContinuousRoundedRectangle(radius.dp)
                                    else ContinuousRoundedRectangle(topStart = 38.dp, topEnd = 30.dp,
                                        bottomStart = 20.dp, bottomEnd = 12.dp),
                                interactive = false,
                                applyDragScale = false,
                            ) {}
                        },
                    )
                }
            }
        }
        val scene = compose.onNodeWithTag("sheet-corners-scene")
        for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { direction.value = layout; uniform.value = null }
            compose.waitForIdle()
            val asymmetric = scene.captureToImage().toPixelMap()
            val density = compose.density.density
            val radii = if (layout == LayoutDirection.Ltr) listOf(38f, 30f, 12f, 20f)
                else listOf(30f, 38f, 20f, 12f)
            var largestRadiusDifference = 0f
            radii.distinct().forEach { radius ->
                compose.runOnIdle { uniform.value = radius }
                compose.waitForIdle()
                val reference = scene.captureToImage().toPixelMap()
                radii.forEachIndexed { corner, expected ->
                    val right = corner == 1 || corner == 2
                    val bottom = corner >= 2
                    var sum = 0f
                    var count = 0
                    for (dy in 1..48) for (dx in 1..48) {
                        val x = ((if (right) 320f - dx else 20f + dx) * density).roundToInt()
                        val y = ((if (bottom) 370f - dy else 30f + dy) * density).roundToInt()
                        val a = asymmetric[x, y]
                        val b = reference[x, y]
                        sum += maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
                        count++
                    }
                    val error = sum / count
                    if (expected == radius) assertTrue(
                        "Each corner must match its own uniform-radius optical reference: layout=$layout, " +
                            "corner=$corner, radius=$radius, error=$error", error < .015f)
                    else if (radius == 38f) largestRadiusDifference = maxOf(largestRadiusDifference, error)
                }
            }
            assertTrue("Small corners must remain distinguishable from the largest radius: $largestRadiusDifference",
                largestRadiusDifference > .02f)
        }
    }

    @Test
    fun sheetMaterialUpdatesOnlyRecomposeMaterialReaders() {
        val material = mutableStateOf(IosSheetMaterialContext(false, 1f))
        var unrelatedCompositions = 0
        var materialCompositions = 0
        var observedMaterial: IosSheetMaterialContext? = null
        var observedColors: GlassColors? = null
        val recordUnrelated = { unrelatedCompositions++; Unit }
        val recordMaterial: (IosSheetMaterialContext, GlassColors) -> Unit = { context, colors ->
            materialCompositions++
            observedMaterial = context
            observedColors = colors
        }
        compose.setContent {
            GlasmioTheme(isDark = false) {
                CompositionLocalProvider(LocalIosSheetMaterialContext provides material.value) {
                    GlasmioSheetSurface(
                        modifier = Modifier.size(240.dp, 200.dp),
                        backdrop = emptyBackdrop(),
                        interactive = false,
                        applyDragScale = false,
                    ) {
                        IndependentCompositionProbe(recordUnrelated)
                        SheetMaterialReader(recordMaterial)
                    }
                }
            }
        }
        compose.waitForIdle()
        val initialUnrelated = unrelatedCompositions
        listOf(
            IosSheetMaterialContext(false, .5f),
            IosSheetMaterialContext(false, 0f),
            IosSheetMaterialContext(true, .25f),
            IosSheetMaterialContext(false, 1f),
        ).forEach { context ->
            val previousMaterialCompositions = materialCompositions
            compose.runOnIdle { material.value = context }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals("Unrelated content must stay composed during the live material transition",
                    initialUnrelated, unrelatedCompositions)
                assertTrue("Material consumers must observe every live transition",
                    materialCompositions > previousMaterialCompositions)
                assertEquals(context, observedMaterial)
                assertEquals(context.tableColor, observedColors?.groupedBackground)
                assertEquals(context.cellColor, observedColors?.elevatedBackground)
                assertEquals(context.separatorColor, observedColors?.separator)
            }
        }
    }

    @Composable
    private fun IndependentCompositionProbe(onComposition: () -> Unit) {
        SideEffect(onComposition)
    }

    @Composable
    private fun SheetMaterialReader(onComposition: (IosSheetMaterialContext, GlassColors) -> Unit) {
        val material = requireNotNull(LocalIosSheetMaterialContext.current)
        val colors = LocalGlassColors.current
        SideEffect { onComposition(material, colors) }
    }

    @Test
    fun sheetForegroundSurvivesTheGlassToOpaqueDetentAndBack() {
        val glass = mutableFloatStateOf(1f)
        val marker = Color(0xFF18A35C)
        compose.setContent {
            GlasmioTheme(isDark = false) {
                GlasmioBackdropHost(
                    modifier = Modifier.size(300.dp, 260.dp),
                    sampledContent = { Box(Modifier.fillMaxSize().background(Color(0xFF335577))) },
                    overlayContent = { backdrop ->
                        GlasmioSheetSurface(
                            modifier = Modifier.align(Alignment.Center).size(240.dp, 200.dp).testTag("sheet-detent"),
                            backdrop = backdrop,
                            interactive = false,
                            applyDragScale = false,
                            glassFraction = glass.floatValue,
                        ) {
                            Text("Sheet content", modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
                                color = Color.Black)
                            Box(Modifier.align(Alignment.Center).size(64.dp, 40.dp).background(marker))
                        }
                    },
                )
            }
        }
        var initialBackplate: Color? = null
        listOf(1f, .5f, 0f, 1f).forEach { fraction ->
            compose.runOnIdle { glass.floatValue = fraction }
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag("sheet-detent").captureToImage().toPixelMap()
            val foreground = pixels[pixels.width / 2, pixels.height / 2]
            fun difference(a: Color, b: Color) = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
            assertTrue("The detent carrier must stay behind sheet content: fraction=$fraction, color=$foreground",
                difference(foreground, marker) < .01f)
            val backplate = pixels[pixels.width / 2, pixels.height * 3 / 4]
            when {
                fraction == 0f -> assertTrue("The expanded detent must retain its opaque backplate",
                    difference(backplate, Color.White) < .01f)
                initialBackplate == null -> {
                    initialBackplate = backplate
                    assertTrue("The glass detent must sample the colored backdrop",
                        difference(backplate, Color.White) > .04f)
                }
                fraction == 1f -> assertTrue("Returning to glass must restore the sampled material",
                    difference(backplate, requireNotNull(initialBackplate)) < .01f)
            }
        }
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
        val icons = mutableStateOf(true)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val position = Animatable(0f)
        val requests = mutableListOf<Int>()
        val items = listOf(0 to "Quiet", 1 to "Balanced", 2 to "Open")
        compose.setContent {
            GlasmioTheme {
                GlasmioBackdropProvider(emptyBackdrop()) {
                    CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                        GlasmioSegmentedControl(
                            items = items,
                            selected = selected.value,
                            onSelected = { requests += it },
                            modifier = Modifier.width(300.dp).testTag("segmented"),
                            positionAnimation = position,
                            height = 50.dp,
                            enabled = enabled.value,
                            deferSelection = true,
                            itemContent = if (icons.value) { { _, _ ->
                                Canvas(Modifier.size(25.dp)) { drawCircle(Color.Black) }
                            } } else null,
                        )
                    }
                }
            }
        }
        val group = compose.onNodeWithTag("segmented")
        group.assertHeightIsEqualTo(50.dp)
        items.forEach { compose.onNodeWithContentDescription(it.second).assertIsNotSelected() }
        compose.onNodeWithContentDescription("Balanced").performClick()
        compose.runOnIdle { assertEquals(listOf(1), requests) }
        items.forEach { compose.onNodeWithContentDescription(it.second).assertIsNotSelected() }

        compose.runOnIdle { selected.value = 0 }
        compose.onNodeWithContentDescription("Quiet").assertIsSelected()
        // Exercise the real pointer route with its transparent optical label recording present.
        group.performTouchInput { click(Offset(width / 2f, height / 2f)) }
        compose.runOnIdle { assertEquals(listOf(1, 1), requests) }
        compose.onNodeWithContentDescription("Quiet").assertIsSelected()
        group.performTouchInput {
            swipe(Offset(width / 6f, height / 2f), Offset(width * 5f / 6f, height / 2f))
        }
        compose.runOnIdle {
            assertEquals(listOf(1, 1, 2), requests)
            assertEquals(0f, position.value, 0.001f)
            icons.value = false
        }
        compose.onNodeWithText("Quiet").assertIsSelected()
        group.performTouchInput { click(Offset(width / 2f, height / 2f)) }
        compose.runOnIdle {
            assertEquals(listOf(1, 1, 2, 1), requests)
            direction.value = LayoutDirection.Rtl
        }
        group.performTouchInput { click(Offset(width / 6f, height / 2f)) }
        compose.runOnIdle { assertEquals(listOf(1, 1, 2, 1, 2), requests) }
        group.performTouchInput {
            swipe(Offset(width * 5f / 6f, height / 2f), Offset(width / 6f, height / 2f))
        }
        val expected = listOf(1, 1, 2, 1, 2, 2)
        compose.runOnIdle {
            assertEquals(expected, requests)
            assertEquals(0f, position.value, 0.001f)
            selected.value = 2
            enabled.value = false
        }
        compose.onNodeWithText("Open").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithText("Balanced").performSemanticsAction(SemanticsActions.OnClick) { it() }
        group.performTouchInput {
            click(Offset(width / 2f, height / 2f))
            swipe(Offset(width / 6f, height / 2f), Offset(width * 5f / 6f, height / 2f))
        }
        compose.runOnIdle { assertEquals(expected, requests) }
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
                val exitingNode = compose.onNodeWithText("Copy").fetchSemanticsNode()
                val exitingWidth = exitingNode.boundsInRoot.width
                assertTrue("Closing must shrink the retained toolbar", exitingWidth > 0f && exitingWidth < fullWidth)
                compose.runOnIdle { assertEquals(TextToolbarStatus.Hidden, legacy.status) }

                compose.runOnIdle { show() }
                // Compare continuity before advancing time. The next frame can still
                // sample the close tween before the reopening effect takes over.
                assertEquals("Reopening must retain the scale at the same clock time", exitingWidth,
                    compose.onNodeWithText("Copy").fetchSemanticsNode().boundsInRoot.width, 1f)
                compose.mainClock.advanceTimeByFrame()
                val resumedNode = compose.onNodeWithText("Copy").fetchSemanticsNode()
                val resumedWidth = resumedNode.boundsInRoot.width
                assertEquals("Reopening must retain the existing toolbar", exitingNode.id, resumedNode.id)
                assertTrue("Reopening must not restart from zero", resumedWidth > 0f && resumedWidth < fullWidth)
                compose.mainClock.advanceTimeBy(32)
                assertTrue("The retained toolbar must grow after reversing",
                    compose.onNodeWithText("Copy").fetchSemanticsNode().boundsInRoot.width > resumedWidth)
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
                                morphSourceMaterialEffect = anchor.sourceMaterialEffect,
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
                NavigationDropShadowRenderer(useNativeProfile = true, nativeMaterial = NavigationGlassMaterial.RegularMedium)
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
            Rect(sourceLocal.center.x - 12f * density, sourceLocal.top - 16f * density, sourceLocal.center.x + 12f * density, sourceLocal.top - 8f * density),
            Rect(sourceLocal.center.x - 12f * density, sourceLocal.bottom + 8f * density, sourceLocal.center.x + 12f * density, sourceLocal.bottom + 16f * density),
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
            assertTrue("The static toolbar reference must have a visible exterior shadow: ${expected / samples}", expected / samples > 0.01)
            assertTrue("The final morph frame cannot lose its source shadow", observed > expected * 0.8)
            assertTrue("The final morph shadow must approach the static toolbar: " +
                "band=$band, observed=${observed / samples}, expected=${expected / samples}",
                abs(observed - expected) / samples < 0.015)
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
        assertTrue(
            "Updating the source frost must update the retained capture's real blur: sharp=$sharpContrast, soft=$softContrast",
            softContrast < sharpContrast * 0.4f,
        )
    }

    @Test
    fun clearMaterialUsesTheNativeBlackFloorWithoutTheLegacyShadowLift() {
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
        assertTrue("The contrast-preserving Clear source must stay dark: ${dark.red}", dark.red < .2f)
        assertEquals("Clear follows the measured UIKit black endpoint", .125f, lifted.red, .02f)
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
                { lerp(1f, 1.16f, press.floatValue) }
            }
            val labelAlpha = remember { { (1f - progress.floatValue).coerceIn(0f, 1f) } }
            val lateScale = remember { { 1f } }
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
                                        expandedTabWidthPx = with(density) { 58.dp.toPx() },
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
        val measured = mutableStateOf(false)
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
                    val tabScale = remember { { 1f } }
                    val labelAlpha = remember { { (1f - progress.floatValue).coerceIn(0f, 1f) } }
                    val lateScale = remember { { 1f } }
                    CompositionLocalProvider(LocalLiquidTabScale provides tabScale) {
                        Box(
                            Modifier.size(160.dp, 80.dp).background(Color.White)
                                .testTag("compactTab"),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (measured.value) {
                                MorphingSelectedNavigationItem(
                                    item = library,
                                    compactProgress = progress,
                                    fullWidthPx = fullWidthPx,
                                    compactWidthPx = compactWidthPx,
                                    itemCount = 3,
                                    selectedIndex = 1,
                                    isLtr = true,
                                    horizontalInsetPx = insetPx,
                                    expandedTabWidthPx = expandedWidth,
                                    selectedContentLateScale = lateScale,
                                    labelAlpha = labelAlpha,
                                    selectedIconColorFilter = null,
                                    contentColor = colors.content,
                                )
                            } else {
                                // Native-sized composition is the pixel oracle for the
                                // deferred layout, including an explicit custom 44dp target.
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
                                            fontSize = 18.sp,
                                            weight = FontWeight.Medium,
                                            fitGlyphToBounds = false,
                                            modifier = Modifier.padding(top = 3.dp * visibility)
                                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                                        )
                                        Text(
                                            text = library.label,
                                            color = colors.content,
                                            style = IosTypography.caption.copy(fontSize = 10.sp, lineHeight = 12.sp),
                                            fontWeight = FontWeight.Medium,
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
        val mismatches = mutableListOf<String>()
        listOf(1.25f, 2.75f, 3.25f).forEach { density ->
            listOf(0f, 0.35f, 0.74f, 0.98f, 1f, 0.5f, 0f).forEach { compact ->
                compose.runOnIdle {
                    pixelDensity.floatValue = density
                    progress.floatValue = compact
                    measured.value = false
                }
                // Sample both implementations at the same physical origin so the
                // GPU does not introduce different subpixel layer coverage.
                val tab = compose.onNodeWithTag("compactTab")
                val expected = tab.captureToImage().toPixelMap()
                compose.runOnIdle { measured.value = true }
                val actual = tab.captureToImage().toPixelMap()
                assertEquals(expected.width, actual.width)
                assertEquals(expected.height, actual.height)
                var maximumError = 0
                var totalError = 0L
                var overTolerancePixels = 0
                var contentPixels = 0
                var maximumPoint = Offset.Zero
                var maximumExpected = Color.Transparent
                var maximumActual = Color.Transparent
                repeat(expected.height) { y ->
                    repeat(expected.width) { x ->
                        val before = expected[x, y]
                        val after = actual[x, y]
                        val red = abs((before.red * 255f).roundToInt() - (after.red * 255f).roundToInt())
                        val green = abs((before.green * 255f).roundToInt() - (after.green * 255f).roundToInt())
                        val blue = abs((before.blue * 255f).roundToInt() - (after.blue * 255f).roundToInt())
                        val alpha = abs((before.alpha * 255f).roundToInt() - (after.alpha * 255f).roundToInt())
                        val error = maxOf(red, green, blue, alpha)
                        totalError += red + green + blue + alpha
                        if (error > maximumError) {
                            maximumError = error
                            maximumPoint = Offset(x.toFloat(), y.toFloat())
                            maximumExpected = before
                            maximumActual = after
                        }
                        if (error > 2) overTolerancePixels++
                        if (minOf(before.red, before.green, before.blue) < 247f / 255f) contentPixels++
                    }
                }
                val context = "density=$density, compact=$compact"
                val meanError = totalError.toDouble() / (expected.width * expected.height * 4)
                val diagnostic = "$context, peak=$maximumError at $maximumPoint " +
                    "expected=$maximumExpected actual=$maximumActual, mean=$meanError, " +
                    "pixelsOver2=$overTolerancePixels, contentPixels=$contentPixels"
                if (maximumError > 2 || meanError > 1.0) mismatches += diagnostic
            }
        }
        assertTrue("Compact tab pixel differences:\n${mismatches.joinToString("\n")}", mismatches.isEmpty())
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
    fun toolbarSeparationKeepsItsNativeMaterialOnTheMovingContour() {
        val progress = mutableFloatStateOf(0f)
        val drivenByStyle = mutableStateOf(false)
        val separated = mutableStateOf(false)
        val opaque = mutableStateOf(true)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val clicks = IntArray(3)
        val background = Color(0xFF235578)
        compose.setContent {
            GlasmioTheme(isDark = false, appearance = GlassAppearance(
                adaptiveColorScheme = false, reduceTransparency = opaque.value)) {
                CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                    GlasmioBackdropHost(
                        modifier = Modifier.size(300.dp, 160.dp).testTag("toolbar-separation-scene"),
                        sampledContent = { Box(Modifier.fillMaxSize().background(background)) },
                        overlayContent = {
                            GlasmioToolbarActionGroup(Modifier.align(Alignment.Center),
                                style = if (separated.value) GlasmioToolbarActionStyle.Separated
                                    else GlasmioToolbarActionStyle.Merged,
                                separationProgress = progress.floatValue.takeUnless { drivenByStyle.value }) {
                                repeat(3) { index ->
                                    GlasmioIconButton(onClick = { clicks[index]++ },
                                        modifier = Modifier.testTag("separation-action-$index"),
                                        style = GlassSurfaceStyle.Navigation) { }
                                }
                            }
                        },
                    )
                }
            }
        }
        val scene = compose.onNodeWithTag("toolbar-separation-scene")
        fun error(a: Color, b: Color) = maxOf(abs(a.red - b.red),
            abs(a.green - b.green), abs(a.blue - b.blue))
        compose.mainClock.autoAdvance = false
        try {
            for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                compose.runOnIdle { direction.value = layout; progress.floatValue = 0f; opaque.value = true }
                compose.mainClock.advanceTimeBy(64)
                compose.waitForIdle()
                val merged = scene.captureToImage().toPixelMap()
                // Include a reverse target before rest. A rounded-rect fallback hides
                // these necks until the shared run abruptly disappears.
                val probeOffsets = intArrayOf(-4, 0, 4)
                for (phase in listOf(.87f, .96f, .91f)) {
                    compose.runOnIdle { progress.floatValue = phase; opaque.value = true }
                    compose.mainClock.advanceTimeBy(64)
                    compose.waitForIdle()
                    val outline = scene.captureToImage().toPixelMap()
                    compose.runOnIdle { opaque.value = false }
                    compose.mainClock.advanceTimeBy(64)
                    compose.waitForIdle()
                    val native = scene.captureToImage().toPixelMap()
                    var samples = 0
                    var totalError = 0.0
                    var largestError = 0f
                    val guard = 4
                    for (y in guard until outline.height - guard) {
                        for (x in guard until outline.width - guard) {
                            if (error(merged[x, y], Color.White) > .02f) continue
                            if (probeOffsets.any { dy ->
                                probeOffsets.any { dx ->
                                    error(outline[x + dx, y + dy], background) > 1f / 255f
                                }
                            }) continue
                            val difference = error(native[x, y], background)
                            totalError += difference
                            largestError = maxOf(largestError, difference)
                            samples++
                        }
                    }
                    assertTrue("The intermediate toolbar must visibly retract its necks: " +
                        "layout=$layout, progress=$phase, samples=$samples", samples > 20)
                    assertTrue("Native glass must follow the retracting toolbar instead of filling its old capsule: " +
                        "layout=$layout, progress=$phase, mean=${totalError / samples}, max=$largestError",
                        totalError / samples < .035 && largestError < .12f)
                }
            }
            compose.runOnIdle {
                direction.value = LayoutDirection.Ltr; opaque.value = false
                drivenByStyle.value = true; separated.value = false
            }
            compose.mainClock.advanceTimeBy(1200)
            compose.waitForIdle()
            val initial = scene.captureToImage().toPixelMap()
            val initialBounds = (0..2).map { compose.onNodeWithTag("separation-action-$it")
                .fetchSemanticsNode().boundsInRoot }
            compose.runOnIdle { separated.value = true }
            compose.mainClock.advanceTimeBy(160)
            val intermediate = scene.captureToImage().toPixelMap()
            listOf(false, true, false, true).forEach { target ->
                compose.runOnIdle { separated.value = target }
                compose.mainClock.advanceTimeBy(48)
            }
            compose.mainClock.advanceTimeBy(1800)
            compose.waitForIdle()
            val detached = scene.captureToImage().toPixelMap()
            var differentFromInitial = 0f
            var differentFromDetached = 0f
            for (y in 0 until initial.height) for (x in 0 until initial.width) {
                differentFromInitial = maxOf(differentFromInitial, error(intermediate[x, y], initial[x, y]))
                differentFromDetached = maxOf(differentFromDetached, error(intermediate[x, y], detached[x, y]))
            }
            assertTrue("The style driver must render an intermediate contour before settlement",
                differentFromInitial > .01f && differentFromDetached > .01f)
            (0..2).forEach { index ->
                val action = compose.onNodeWithTag("separation-action-$index")
                val bounds = action.fetchSemanticsNode().boundsInRoot
                assertEquals(initialBounds[index].left, bounds.left, .5f)
                assertEquals(initialBounds[index].top, bounds.top, .5f)
                action.performTouchInput { click(center) }
            }
            compose.runOnIdle { assertTrue("Every action remains independently clickable", clicks.all { it == 1 }) }
            compose.runOnIdle { separated.value = false }
            compose.mainClock.advanceTimeBy(1800)
            compose.waitForIdle()
            val restored = scene.captureToImage().toPixelMap()
            var roundTripError = 0.0
            for (y in 0 until initial.height) for (x in 0 until initial.width) {
                roundTripError += error(initial[x, y], restored[x, y])
            }
            assertTrue("Rapid retargeting and independent clicks must return to the original merged material",
                roundTripError / (initial.width * initial.height) < 2.0 / 255.0)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun plainMenuGaussianTailDoesNotExtendItsKeyFillIntoExteriorRectangles() {
        val smoothing = mutableFloatStateOf(.6f)
        val renderScale = mutableFloatStateOf(.5f)
        val gaussian = mutableStateOf(true)
        var host: LayoutCoordinates? = null
        var menu = Rect.Zero
        compose.setContent {
            // The native half-point key strip extends beyond the two-pixel field halo at 3x.
            CompositionLocalProvider(LocalDensity provides Density(3f)) {
                GlasmioTheme(isDark = false) {
                    GlasmioBackdropHost(
                        modifier = Modifier.size(330.dp).testTag("menu-field-exterior"),
                        sampledContent = { Box(Modifier.fillMaxSize().background(Color.White)) },
                        overlayContent = { backdrop ->
                            val renderer = rememberGlassMorphRenderer(backdrop, active = true,
                                materialPreset = NavigationGlassMaterial.RegularMedium,
                                renderScale = renderScale.floatValue)
                            val field = remember { LiquidMenuFieldTexture() }
                            Box(Modifier.fillMaxSize().onGloballyPositioned { host = it }.drawWithContent {
                                val coordinates = host ?: return@drawWithContent
                                menu = Rect(70.dp.toPx(), 90.dp.toPx(), 260.dp.toPx(), 240.dp.toPx())
                                val radius = 34.dp.toPx()
                                val shape = LiquidMenuContour(Rect(Offset.Zero, menu.size),
                                    Offset(radius, radius), null, Offset.Zero, smoothing.floatValue)
                                val sampled = requireNotNull(shape.sampledField())
                                field.update(sampled, menu.topLeft)
                                renderer.draw(
                                    scope = this, coordinates = coordinates, viewport = Rect(Offset.Zero, size),
                                    origin = Offset.Zero, bounds = menu, radius = radius, shape = shape,
                                    tint = Color.White, highlightAlpha = 1f, navigationMaterial = true,
                                    lensBounds = menu, lensSmoothing = smoothing.floatValue,
                                    menuField = field.takeIf { gaussian.value },
                                    refractionHeight = 0.dp, refractionAmount = 0.dp,
                                    dropShadowAlpha = 0f,
                                )
                            })
                        },
                    )
                }
            }
        }
        val scene = compose.onNodeWithTag("menu-field-exterior")
        for ((scale, sigma) in listOf(.5f, 1f).flatMap { scale ->
            listOf(.6f, 1f, 2f, 4f, 8f).map { sigma -> scale to sigma }
        }) {
            compose.runOnIdle {
                renderScale.floatValue = scale; smoothing.floatValue = sigma; gaussian.value = true
            }
            compose.waitForIdle()
            val actual = scene.captureToImage().toPixelMap()
            compose.runOnIdle { gaussian.value = false }
            compose.waitForIdle()
            val reference = scene.captureToImage().toPixelMap()
            // Compare the same native material and geometry. Only its bounded Gaussian
            // texture is disabled in the reference; the unbounded analytic field is exact
            // along these straight exterior edges. Keep real shadows out of this key test.
            val strips = listOf(
                Rect(menu.left - 42f, menu.center.y - 30f, menu.left - 8f, menu.center.y + 30f),
                Rect(menu.center.x - 30f, menu.top - 42f, menu.center.x + 30f, menu.top - 8f),
                Rect(menu.right + 8f, menu.center.y - 30f, menu.right + 42f, menu.center.y + 30f),
                Rect(menu.center.x - 30f, menu.bottom + 8f, menu.center.x + 30f, menu.bottom + 42f),
            )
            strips.forEachIndexed { edge, strip ->
                var maximumError = 0f
                for (y in strip.top.toInt() until strip.bottom.toInt()) {
                    for (x in strip.left.toInt() until strip.right.toInt()) {
                        val a = actual[x, y]
                        val b = reference[x, y]
                        maximumError = maxOf(maximumError, abs(a.red - b.red),
                            abs(a.green - b.green), abs(a.blue - b.blue))
                    }
                }
                assertTrue("A Gaussian menu must not extend its key fill into an exterior rectangle: " +
                    "scale=$scale, sigma=$sigma, edge=$edge, error=$maximumError", maximumError <= 2f / 255f)
            }
        }
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
            assertTrue("The native reference must contain a visible $edge shadow outside the logical menu: ${referenceShade / pixels}",
                referenceShade / pixels > 2.0 / 255.0)
            assertTrue("Plain material opacity must retain the $edge shadow outside its menu-sized RenderNode: ${actualShade / pixels}",
                actualShade / pixels > 2.0 / 255.0)
            assertTrue("The $edge shadow must match native post-blur opacity: max=$maximumError, " +
                "mean=${totalError / pixels}, observed=${actualShade / pixels}, expected=${referenceShade / pixels}",
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
