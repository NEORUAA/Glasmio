package io.github.neoruaa.glasmio.glass

import io.github.neoruaa.glasmio.glass.render.GlassLuminanceHysteresis
import io.github.neoruaa.glasmio.glass.render.BackdropLuminance
import io.github.neoruaa.glasmio.glass.render.GlassMaterialContext
import io.github.neoruaa.glasmio.glass.render.resolveBackdropSamplingScale
import io.github.neoruaa.glasmio.core.defaultGlassColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.shapes.Capsule
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.nanoseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationIndicatorLensTest {
    @Test
    fun opticalHeadroomRetainsEveryNativeChooserEndpointWithoutDisplayJitter() {
        val ratios = listOf(Float.NaN, Float.NEGATIVE_INFINITY, 0f, 1f, 1.05f, 1.2f, 4.9f, 5f, 8f)
        for (ratio in ratios) {
            val effective = io.github.neoruaa.glasmio.glass.render.nativeGlassOpticalHeadroom(ratio)
            assertTrue(effective in 1f..1.2f)
            for (dark in listOf(false, true)) for (clear in listOf(0f, .5f, 1f)) {
                assertEquals(navigationGlassKeyThreshold(dark, clear, ratio),
                    navigationGlassKeyThreshold(dark, clear, effective), .000001f)
            }
        }
        assertEquals(1.2f, io.github.neoruaa.glasmio.glass.render.nativeGlassOpticalHeadroom(4.9f), 0f)
        assertEquals(1.2f, io.github.neoruaa.glasmio.glass.render.nativeGlassOpticalHeadroom(5.1f), 0f)
    }

    @Test
    fun nativeNavigationUnionUsesTheRecoveredGapAndGradientTransfer() {
        for (density in listOf(1f, 3f)) for (gap in listOf(8f, 3f, .931f, -2f)) {
            val main = LiquidContactCapsule(Size(263f, 62f) * density,
                Rect(0f, 0f, 263f * density, 62f * density))
            val trailing = LiquidContactCapsule(Size(62f, 62f) * density,
                Rect((263f + gap) * density, 0f, (325f + gap) * density, 62f * density))
            val union = requireNotNull(NavigationNativeUnion.create(main, trailing, 6f * density))
            val x = (263f + gap * .5f) * density
            val field = union.field(x, 31f * density)
            // Facing normals give k=6, h=.5, hence d=gap/2-1.5 in native points.
            assertEquals((gap * .5f - 1.5f) * density, field[0], .0001f)
            assertEquals(0f, field[1], .0001f)
            assertEquals(0f, field[2], .0001f)
            assertEquals(gap <= 3f, union.isConnected)
            val reversed = requireNotNull(NavigationNativeUnion.create(trailing, main, 6f * density))
                .field(x, 31f * density)
            repeat(3) { assertEquals(field[it], reversed[it], 0f) }
        }
    }

    @Test
    fun nativeNavigationSingleElementPreservesItsAffineDistanceAndNormal() {
        val size = Size(100f, 40f)
        val center = Offset(60f, 40f)
        val plain = requireNotNull(NavigationNativeUnion.create(
            LiquidContactCapsule(size, Rect(10f, 20f, 110f, 60f)), null, 6f))
        val scale = 1.2f
        val grown = requireNotNull(NavigationNativeUnion.create(
            LiquidContactCapsule(size, Rect(0f, 16f, 120f, 64f)), null, 6f))
        for (offset in listOf(Offset(0f, -25f), Offset(45f, 0f), Offset(48f, -15f), Offset(60f, 15f))) {
            val point = center + offset
            val target = center + offset * scale
            val sourceField = plain.field(point.x, point.y)
            val grownField = grown.field(target.x, target.y)
            assertEquals(sourceField[0] * scale, grownField[0], .0001f)
            assertEquals(sourceField[1], grownField[1], .0001f)
            assertEquals(sourceField[2], grownField[2], .0001f)
        }
        assertTrue(!plain.isConnected && !grown.isConnected)
        val stretched = requireNotNull(NavigationNativeUnion.create(
            LiquidContactCapsule(size, Rect(10f, 20f, 210f, 60f)), null, 6f))
        val right = stretched.field(220f, 40f)
        assertEquals(5f, right[0], .0001f)
        assertEquals(.5f, right[1], .0001f)
        assertEquals(0f, right[2], .0001f)
        val top = stretched.field(110f, 10f)
        assertEquals(10f, top[0], .0001f)
        assertEquals(0f, top[1], .0001f)
        assertEquals(-1f, top[2], .0001f)
        assertTrue(NavigationNativeUnion.create(
            LiquidContactCapsule(size, Rect(10f, 20f, 110f, 60f)),
            LiquidContactCapsule(Size(40f, 40f), Rect(118f, 22f, 158f, 62f)), 6f) == null)
    }

    @Test
    fun nativeToolbarUnionKeepsThreeNodeMergedAndDetachedZeroFields() {
        for (density in listOf(1f, 3f)) {
            val merged = LiquidContactCapsule(Size(132f, 44f) * density,
                Rect(0f, 0f, 132f * density, 44f * density))
            val mergedUnion = requireNotNull(NavigationNativeUnion.create(List(3) { merged }, 12f * density))
            val singleUnion = requireNotNull(NavigationNativeUnion.create(merged, null, 12f * density))
            assertTrue(mergedUnion.isConnected)
            for (point in listOf(Offset(0f, 22f), Offset(66f, 0f), Offset(66f, 22f), Offset(132f, 44f))) {
                val physical = point * density
                val actual = mergedUnion.field(physical.x, physical.y)
                val expected = singleUnion.field(physical.x, physical.y)
                repeat(3) { assertEquals(expected[it], actual[it], .0001f) }
            }
            for (gap in listOf(0f, 4f, 5.99f, 6f, 6.01f, 8f, 16f)) {
                val nodes = List(3) { i ->
                    val left = i * (44f + gap) * density
                    LiquidContactCapsule(Size(44f, 44f) * density,
                        Rect(left, 0f, left + 44f * density, 44f * density))
                }
                val union = requireNotNull(NavigationNativeUnion.create(nodes, 12f * density))
                val rtl = requireNotNull(NavigationNativeUnion.create(nodes.reversed(), 12f * density))
                // Tangency has no stable topology after native Float/half quantization.
                // Preserve the actual zero field instead of widening it to join a gap.
                if (gap != 6f) assertEquals("density=$density gap=$gap", gap < 6f, union.isConnected)
                assertEquals(union.isConnected, rtl.isConnected)
                repeat(2) { i ->
                    val neck = (nodes[i].bounds.right + nodes[i + 1].bounds.left) * .5f
                    val field = union.field(neck, 22f * density)
                    val reversed = rtl.field(neck, 22f * density)
                    assertEquals((gap * .5f - 3f) * density, field[0], .0001f)
                    assertEquals(0f, field[1], .0001f)
                    assertEquals(0f, field[2], .0001f)
                    repeat(3) { assertEquals(field[it], reversed[it], 0f) }
                }
                for (x in -8..(132f + gap * 2f + 8f).toInt() step 4) for (y in -8..52 step 4) {
                    assertTrue(union.field(x * density, y * density).all { it.isFinite() })
                }
            }
        }
    }

    @Test
    fun nativeToolbarUnionUsesPhysicalOrderAndPreservesIndependentClusters() {
        val nodes = listOf(Rect(0f, 0f, 44f, 44f), Rect(48f, 0f, 92f, 44f), Rect(112f, 0f, 156f, 44f))
            .map { LiquidContactCapsule(it.size, it) }
        val union = requireNotNull(NavigationNativeUnion.create(nodes, 12f))
        val rtl = requireNotNull(NavigationNativeUnion.create(nodes.reversed(), 12f))
        assertEquals(nodes.first(), rtl.first)
        assertEquals(nodes[1], rtl.second)
        assertTrue(!union.isConnected && !rtl.isConnected)
        assertTrue(union.field(46f, 22f)[0] < 0f)
        assertTrue(union.field(102f, 22f)[0] > 0f)
        for (x in -8..164 step 2) for (y in -8..52 step 4) {
            val expected = union.field(x.toFloat(), y.toFloat())
            val actual = rtl.field(x.toFloat(), y.toFloat())
            repeat(3) { assertEquals(expected[it], actual[it], 0f) }
        }
        val legacy = requireNotNull(NavigationNativeUnion.create(nodes[1], nodes[0], 12f))
        val pair = requireNotNull(NavigationNativeUnion.create(nodes.take(2).reversed(), 12f))
        repeat(3) { assertEquals(legacy.field(46f, 12f)[it], pair.field(46f, 12f)[it], 0f) }
        assertEquals(legacy.isConnected, pair.isConnected)
        assertTrue(NavigationNativeUnion.create(emptyList(), 12f) == null)
        assertTrue(requireNotNull(NavigationNativeUnion.create(List(16) { nodes.first() }, 12f)).isConnected)
        assertTrue(NavigationNativeUnion.create(List(17) { nodes.first() }, 12f) == null)
        assertTrue(NavigationNativeUnion.create(nodes.dropLast(1) + LiquidContactCapsule(Size(44f, 44f),
            Rect(112f, 2f, 156f, 46f)), 12f) == null)
        assertTrue(NavigationNativeUnion.create(nodes, 0f) == null)
    }

    @Test
    fun nativeTintMatricesMatchThePhysicalUIKitCoefficients() {
        val weights = floatArrayOf(.2126f, .7152f, .0722f)
        data class TintCase(val color: Color, val dark: Boolean, val slopes: FloatArray, val biases: FloatArray)
        val cases = listOf(
            TintCase(Color(0xFF0088FF), false,
                floatArrayOf(0f, .24627456f, .4f), floatArrayOf(0f, .28705877f, .6f)),
            TintCase(Color(0xFFFF383C), false,
                floatArrayOf(.229481f, .258362f, .276817f), floatArrayOf(.770519f, -.038754f, -.041522f)),
            TintCase(Color(0xFF0088FF), true,
                floatArrayOf(.20416664f, .07527768f, -.03749996f),
                floatArrayOf(-.030625f, .52204168f, 1.005625f)),
            TintCase(Color(0xFFFF4245), true,
                floatArrayOf(-.02563725f, .23544118f, .23129707f),
                floatArrayOf(1.00384559f, .22350735f, .23589367f)),
        )
        cases.forEach { case ->
            val matrix = navigationTintMatrix(case.color, case.dark)
            repeat(3) { row ->
                repeat(3) { column -> assertEquals(case.slopes[row] * weights[column], matrix[row * 4 + column], .000002f) }
                assertEquals(case.biases[row], matrix[row * 4 + 3], .000002f)
            }
        }
        listOf(Color.Black, Color.White, Color.Gray, Color.Yellow).forEach { tint ->
            listOf(false, true).forEach { dark -> assertTrue(navigationTintMatrix(tint, dark).all { it.isFinite() }) }
        }
    }

    @Test
    fun adaptiveMaterialKeepsCompleteEndpointRecipesAndTheirMeasuredSizeClass() {
        fun uniforms(recipe: NavigationMaterialRecipe): Map<String, FloatArray> = buildMap {
            updateNavigationMaterialUniforms(recipe, 1f) { name, value -> put(name, value) }
        }
        for (material in listOf(null, NavigationGlassMaterial.Clear, NavigationGlassMaterial.RegularMedium)) {
            val light = NavigationMaterialRecipe.resolve(false, material, Color(0xFF0088FF), Color(0x33665544))
            val dark = NavigationMaterialRecipe.resolve(true, material, Color(0xFF0088FF), Color(0x33665544))
            assertTrue(light.matches(NavigationMaterialRecipe.resolve(0f, material, Color(0xFF0088FF), Color(0x33665544))))
            assertTrue(dark.matches(NavigationMaterialRecipe.resolve(1f, material, Color(0xFF0088FF), Color(0x33665544))))
            val mixed = NavigationMaterialRecipe.resolve(.37f, material, Color(0xFF0088FF), Color(0x33665544))
            assertTrue(dark.matches(mixed.appearanceAlternate))
            val actual = uniforms(mixed)
            val lightUniforms = uniforms(light)
            val darkUniforms = uniforms(dark)
            assertEquals(.37f, actual.getValue("materialAppearanceMix")[0], 0f)
            assertEquals(1f, actual.getValue("materialOpacity")[0], 0f)
            assertEquals(1f, actual.getValue("appearance_materialOpacity")[0], 0f)
            lightUniforms.filterKeys { !it.startsWith("appearance_") && it != "materialAppearanceMix" }.forEach { (name, expected) ->
                expected.indices.forEach { i ->
                    assertEquals(expected[i], actual.getValue(name)[i], 0f)
                    assertEquals(darkUniforms.getValue(name)[i], actual.getValue("appearance_$name")[i], 0f)
                }
            }
        }
        val automatic = NavigationMaterialRecipe.resolve(.37f, null)
        for ((dimension, rolloff) in listOf(44f to .4f, 160f to .65f)) {
            val measured = automatic.forMinimumDimension(dimension)
            assertEquals(.37f, measured.darkFraction, 0f)
            assertEquals(0f, measured.luminanceRollOff, 0f)
            assertEquals(rolloff, requireNotNull(measured.appearanceAlternate).luminanceRollOff, 0f)
            assertTrue(!requireNotNull(measured.appearanceAlternate).automaticSize)
        }
        for (clear in listOf(0f, .4f, 1f)) for (headroom in listOf(1f, 1.1f, 1.2f)) {
            val light = navigationGlassKeyThreshold(false, clear, headroom)
            val dark = navigationGlassKeyThreshold(true, clear, headroom)
            assertEquals(light, navigationGlassKeyThreshold(0f, clear, headroom), 0f)
            assertEquals(dark, navigationGlassKeyThreshold(1f, clear, headroom), 0f)
            val midpoint = navigationGlassKeyThreshold(.5f, clear, headroom)
            assertTrue(midpoint >= minOf(light, dark) - .000001f && midpoint <= maxOf(light, dark) + .000001f)
        }
    }

    @Test
    fun adaptiveGlassUsesEnvironmentRangesAndRetainsItsPreviousAppearance() {
        assertEquals(1f, resolveBackdropSamplingScale(Float.NaN), 0f)
        assertEquals(1f, resolveBackdropSamplingScale(Float.POSITIVE_INFINITY), 0f)
        assertEquals(.25f, resolveBackdropSamplingScale(.1f), 0f)
        assertEquals(.5f, resolveBackdropSamplingScale(.5f), 0f)
        assertEquals(1f, resolveBackdropSamplingScale(2f), 0f)
        val light = GlassLuminanceHysteresis(false)
        assertTrue(!light.resolve(null, false))
        assertTrue(light.resolve(.1f, false))
        assertTrue(light.resolve(.35f, false))
        assertTrue(light.resolve(Float.NaN, false))
        assertTrue(!light.resolve(.7f, false))
        assertTrue(!light.resolve(.35f, false))
        val dark = GlassLuminanceHysteresis(true)
        assertTrue(dark.resolve(.7f, true))
        assertTrue(!dark.resolve(.95f, true))
        assertTrue(!dark.resolve(.7f, true))
        assertTrue(dark.resolve(.5f, true))
        // The host environment picks the range even after the resolved appearance changed.
        assertTrue(!dark.resolve(.55f, false))
        assertTrue(!dark.resolve(null, false))
        assertTrue(dark.resolve(.5f, true, enabled = false))
    }

    @Test
    fun adaptiveLuminancePublishesSmallThresholdCrossingsAndRejectsInvalidSamples() {
        val luminance = BackdropLuminance()
        val invalid = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -.01f)
        invalid.forEach { luminance.publish(it) }
        assertTrue(luminance.value == null)
        val chooser = GlassLuminanceHysteresis(false)

        luminance.publish(.205f)
        assertTrue(!chooser.resolve(luminance.value, false))
        luminance.publish(.199f)
        assertEquals(.199f, requireNotNull(luminance.value), 0f)
        assertTrue(chooser.resolve(luminance.value, false))
        luminance.publish(.495f)
        assertTrue(chooser.resolve(luminance.value, false))
        luminance.publish(.501f)
        assertEquals(.501f, requireNotNull(luminance.value), 0f)
        assertTrue(!chooser.resolve(luminance.value, false))

        invalid.forEach { luminance.publish(it) }
        assertEquals(.501f, requireNotNull(luminance.value), 0f)
        luminance.publish(.50101f)
        assertEquals(.50101f, requireNotNull(luminance.value), 0f)
    }

    @Test
    fun adaptiveForegroundInterpolatesRolesWithoutChangingTheEnvironmentOrExplicitInk() {
        val light = defaultGlassColors(false, Color.Blue)
        val dark = defaultGlassColors(true, light.accent)
        val halfway = adaptiveGlassContentColors(light, true, true, .5f)
        assertTrue(!halfway.isDark)
        assertTrue(halfway.content != light.content && halfway.content != dark.content)
        assertTrue(halfway.secondaryContent != light.secondaryContent && halfway.secondaryContent != dark.secondaryContent)
        assertEquals(light.accent, halfway.accent)
        assertEquals(light.container, halfway.container)
        assertEquals(light.content, adaptiveGlassContentColors(light, true, true, 0f).content)
        assertEquals(dark.content, adaptiveGlassContentColors(light, false, true, 1f).content)
        assertEquals(light, adaptiveGlassContentColors(light, true, false, .5f))
        assertEquals(dark.content, adaptiveGlassContentColors(light, true, true, Float.NaN).content)
        val custom = light.copy(content = Color.Magenta, secondaryContent = Color.Cyan, tertiaryContent = Color.Yellow)
        assertEquals(custom, adaptiveGlassContentColors(custom, true, true, .5f))

        val probe = BackdropLuminance()
        val parent = GlassMaterialContext(false, true, halfway, light, probe, true, true, .5f)
        val childColors = adaptiveGlassContentColors(light, false, true, .25f)
        val child = GlassMaterialContext(false, false, childColors, light, probe, true, false, .25f)
        assertEquals(childColors.content, child.foreground(halfway.content, parent))
        assertEquals(childColors.secondaryContent, child.foreground(halfway.secondaryContent, parent))
        assertEquals(Color.Magenta, child.foreground(Color.Magenta, parent))
        val unsampled = GlassMaterialContext(false, false, light, light, probe, false, false)
        assertEquals(light.content, unsampled.foreground(halfway.content, parent))
        assertTrue(child.luminanceState == null && unsampled.luminanceState == null)
    }

    @Test
    fun queuedDragDeltasKeepTheirFullTargetAndSettledDistance() {
        listOf(false, true).forEach { batched ->
            withQueuedDrag { drag, dispatcher, clock ->
                repeat(3) { batch ->
                    repeat(4) {
                        drag.updateValue(drag.targetValue + 0.25f)
                        if (!batched) dispatcher.drain()
                    }
                    assertEquals((batch + 1).toFloat(), drag.targetValue, 0f)
                    dispatcher.drain()
                }
                clock.settle(dispatcher)
                assertEquals(3f, drag.value, 0.001f)
            }
        }
    }

    @Test
    fun queuedDragReversalAndBoundsDoNotReuseAnOldAnimationTarget() {
        withQueuedDrag { drag, dispatcher, clock ->
            listOf(1f, 1f, 2f, -0.25f, -0.25f, -0.5f).forEach {
                drag.updateValue(drag.targetValue + it)
            }
            assertEquals(2f, drag.targetValue, 0f)
            dispatcher.drain()
            clock.settle(dispatcher)
            assertEquals(2f, drag.value, 0.001f)
            drag.updateValue(-4f)
            assertEquals(0f, drag.targetValue, 0f)
            drag.updateValue(drag.targetValue + 0.5f)
            dispatcher.drain()
            clock.settle(dispatcher)
            assertEquals(0.5f, drag.value, 0.001f)
        }
    }

    @Test
    fun immediateReleaseAndProgrammaticRetargetUseTheLatestRequestedPosition() {
        withQueuedDrag { drag, dispatcher, clock ->
            repeat(7) { drag.updateValue(drag.targetValue + 0.25f) }
            val selected = kotlin.math.round(drag.targetValue).toInt()
            assertEquals(2, selected)
            drag.animateToValue(selected.toFloat(), animatePress = false)
            drag.release()
            assertEquals(2f, drag.targetValue, 0f)
            dispatcher.drain()
            clock.settle(dispatcher)
            assertEquals(2f, drag.value, 0.001f)
            drag.animateToValue(1f, animatePress = false)
            drag.updateValue(drag.targetValue + 0.5f)
            assertEquals(1.5f, drag.targetValue, 0f)
            dispatcher.drain()
            clock.settle(dispatcher)
            assertEquals(1.5f, drag.value, 0.001f)
        }
    }

    @Test
    fun nativeLensLiftKeepsItsSpringFamilyAndReleasesWhileTheValueSettles() {
        NativeControlLensStyle.entries.forEach { style ->
            withQueuedDrag(lensStyle = style) { drag, dispatcher, clock ->
                drag.press()
                dispatcher.drain()
                clock.advance(dispatcher, 12)
                if (style == NativeControlLensStyle.SwitchThumb || style == NativeControlLensStyle.SliderThumb)
                    assertTrue("The measured small lens spring must retain its overshoot", drag.pressProgress > 1.02f)
                else assertTrue("The native large lens spring approaches its target without overshoot",
                    drag.pressProgress in .9f..1f)
                clock.settle(dispatcher)
                drag.updateValue(3f)
                dispatcher.drain()
                drag.release()
                dispatcher.drain()
                clock.advance(dispatcher, 3)
                assertTrue("The thumb value is still moving in this release case", drag.value < 2.9f)
                assertTrue("Native unlift must not wait for the selected value to settle", drag.pressProgress < .99f)
                drag.press()
                dispatcher.drain()
                clock.settle(dispatcher)
                assertEquals("A new touch cancels the previous unlift", 1f, drag.pressProgress, .001f)
            }
        }
    }

    @Test
    fun nativeQuickTapCompletesItsMinimumLiftBeforeUnlifting() {
        listOf(NativeControlLensStyle.SliderThumb, NativeControlLensStyle.SegmentedIndicator).forEach { style ->
            withQueuedDrag(lensStyle = style) { drag, dispatcher, clock ->
                drag.press()
                dispatcher.drain()
                clock.advance(dispatcher, 4)
                val onUp = drag.pressProgress
                drag.release()
                dispatcher.drain()
                clock.advance(dispatcher, 5)
                val duringMinimumLift = drag.pressProgress
                assertTrue("A short tap must finish lifting before the native dwell ends",
                    duringMinimumLift > onUp + .1f)
                clock.advance(dispatcher, 10)
                assertTrue("Unlift must begin after the minimum lifted lifetime",
                    drag.pressProgress < duringMinimumLift)
                clock.settle(dispatcher)
                assertEquals(0f, drag.pressProgress, .001f)
            }
        }
    }

    private class QueuedDragDispatcher : kotlinx.coroutines.CoroutineDispatcher() {
        private val pending = ArrayDeque<Runnable>()
        override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { pending.addLast(block) }
        fun drain() { while (pending.isNotEmpty()) pending.removeFirst().run() }
    }

    @OptIn(kotlin.time.ExperimentalTime::class)
    private class DragFrameClock : androidx.compose.runtime.MonotonicFrameClock {
        private val waiting = ArrayList<(Long) -> Unit>()
        private val testTime = kotlin.time.TestTimeSource()
        val timeSource: kotlin.time.TimeSource get() = testTime
        private var time = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                waiting.add { nanos -> if (continuation.isActive) continuation.resumeWith(runCatching { onFrame(nanos) }) }
            }
        fun advance(dispatcher: QueuedDragDispatcher, frames: Int) {
            repeat(frames) {
                time += 16_666_667L
                testTime += 16_666_667L.nanoseconds
                val ready = waiting.toList()
                waiting.clear()
                ready.forEach { it(time) }
                dispatcher.drain()
            }
        }
        fun settle(dispatcher: QueuedDragDispatcher) = advance(dispatcher, 180)
    }

    private fun withQueuedDrag(
        lensStyle: NativeControlLensStyle? = null,
        block: (DampedDragAnimation, QueuedDragDispatcher, DragFrameClock) -> Unit,
    ) {
        val dispatcher = QueuedDragDispatcher()
        val clock = DragFrameClock()
        val scope = kotlinx.coroutines.CoroutineScope(dispatcher + clock)
        val drag = DampedDragAnimation(scope, 0f, 0f..3f, 0.001f, 1f, 78f / 56f,
            onDragStarted = {}, onDragStopped = {}, onDrag = { _, _ -> }, lensStyle = lensStyle,
            timeSource = clock.timeSource)
        try { block(drag, dispatcher, clock) } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            dispatcher.drain()
        }
    }

    @Test
    fun contactFieldUsesOnlyTheExposedNeckAndKeepsBothDetachedContours() {
        val left = listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 80f), Offset(0f, 80f))
        val right = left.map { it + Offset(120f, 0f) }
        val detached = NavigationContactField().grid(NavigationContactContour(pathApproximation(left, right)), 24f)
        assertField(detached, Offset(98f, 40f), -2f, Offset(1f, 0f))
        assertField(detached, Offset(122f, 40f), -2f, Offset(-1f, 0f))
        val separated = NavigationContactField().grid(
            NavigationContactContour(pathApproximation(left, left.map { it + Offset(320f, 0f) })), 24f)
        val gap = Offset(210f, 40f)
        val gapIndex = (((gap.y - separated.origin.y) / separated.step).toInt() * separated.width +
            ((gap.x - separated.origin.x) / separated.step).toInt()) * 4
        assertTrue("A distant gap between detached shapes must remain exterior", separated.rgba[gapIndex] > 0f)
        assertTrue("The visible field must include its exterior support", separated.origin.x <= -24f &&
            separated.origin.y <= -24f && separated.origin.x + (separated.width - 1) * separated.step >= 444f)
        // These are the exposed upper/lower necks after the two facing sides vanish.
        val joined = listOf(
            Offset(0f, 0f), Offset(80f, 0f), Offset(100f, 20f), Offset(120f, 20f),
            Offset(140f, 0f), Offset(220f, 0f), Offset(220f, 80f), Offset(140f, 80f),
            Offset(120f, 60f), Offset(100f, 60f), Offset(80f, 80f), Offset(0f, 80f),
        )
        listOf(joined, joined.asReversed()).forEach { contour ->
            val field = NavigationContactField()
            val snapshot = NavigationContactContour(pathApproximation(contour))
            val grid = field.grid(snapshot, 24f)
            assertField(grid, Offset(110f, 22f), -2f, Offset(0f, -1f))
            assertField(grid, Offset(110f, 58f), -2f, Offset(0f, 1f))
            assertField(grid, Offset(100f, 38f), -18f, Offset(0f, -1f))
            assertTrue("A smaller optical band must reuse the same geometry", field.grid(snapshot, 10f) === grid)
        }
    }

    @Test
    fun contactFieldKeepsSubpixelMotionAndExcludesBuriedEdgesAtTheOldSeam() {
        val silhouette = listOf(Offset(0f, 0f), Offset(220f, 0f), Offset(220f, 80f), Offset(0f, 80f))
        listOf(0f, 0.25f, 0.5f, 1f).forEach { shift ->
            val field = NavigationContactField()
            val grid = field.grid(NavigationContactContour(pathApproximation(silhouette.map { it + Offset(shift, shift) })), 12f)
            assertField(grid, Offset(100f, 2f), -(2f - shift), Offset(0f, -1f))
            val x = ((100f - grid.origin.x) / grid.step).toInt()
            val y = ((40f - grid.origin.y) / grid.step).toInt()
            assertTrue("The old facing edge must not create a refraction band",
                grid.rgba[(y * grid.width + x) * 4] < -12f)
        }
    }

    @Test
    fun contactFieldReusesLargeStorageAcrossDragFramesAndBuildsEachSnapshotOnce() {
        val renderer = NavigationContactField()
        val rectangle = listOf(Offset(0f, 0f), Offset(220f, 0f), Offset(220f, 80f), Offset(0f, 80f))
        val arrays = HashSet<FloatArray>()
        repeat(120) { frame ->
            val contour = NavigationContactContour(pathApproximation(rectangle.map { it + Offset(frame / 120f, 0f) }))
            val sharedBand = 32f
            val visible = renderer.grid(contour, sharedBand)
            arrays.add(visible.rgba)
            val hidden = renderer.grid(contour, 29f)
            assertTrue("Both material passes must use one field generation", visible === hidden)
            assertEquals(frame + 1, renderer.buildCount)
            assertField(visible, Offset(100f, 2f), -2f, Offset(0f, -1f))
        }
        assertEquals("Stable-sized animation frames must rotate only three arrays", 3, arrays.size)
    }

    @Test
    fun contactFieldReusesIdenticalGeometryAcrossFreshSnapshotsWithoutUploadingAgain() {
        val renderer = NavigationContactField()
        val rectangle = listOf(Offset(0f, 0f), Offset(220f, 0f), Offset(220f, 80f), Offset(0f, 80f))
        val first = renderer.grid(NavigationContactContour(pathApproximation(rectangle)), 32f)
        val values = first.rgba.copyOf()
        val generation = first.generation
        first.uploadedGeneration = generation
        repeat(120) {
            val repeated = renderer.grid(NavigationContactContour(pathApproximation(rectangle)), 32f)
            assertTrue("Equal geometry must retain the same raster and upload", repeated === first)
            assertEquals(generation, repeated.generation)
            assertEquals(generation, repeated.uploadedGeneration)
            assertTrue(values.contentEquals(repeated.rgba))
        }
        assertEquals(1, renderer.buildCount)
        renderer.grid(NavigationContactContour(pathApproximation(rectangle.map { it + Offset(0.25f, 0f) })), 32f)
        assertEquals("Subpixel contour changes must still rebuild", 2, renderer.buildCount)
    }

    @Test
    fun contactFieldPruningMatchesUnprunedExposedEdges() {
        val left = listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 80f), Offset(0f, 80f))
        val neck = listOf(Offset(0f, 0f), Offset(80f, 0f), Offset(100f, 20f), Offset(120f, 20f),
            Offset(140f, 0f), Offset(220f, 0f), Offset(220f, 80f), Offset(140f, 80f),
            Offset(120f, 60f), Offset(100f, 60f), Offset(80f, 80f), Offset(0f, 80f))
        listOf(pathApproximation(left, left.map { it + Offset(120f, 0f) }),
            pathApproximation(neck), pathApproximation(neck.asReversed())).forEach { path ->
            val contour = NavigationContactContour(path)
            val grid = NavigationContactField().grid(contour, 32f)
            for (y in 0 until grid.height) for (x in 0 until grid.width) {
                val point = grid.origin + Offset(x * grid.step, y * grid.step)
                var nearest = grid.band + 2f * grid.step
                for (edge in contour.edges.indices step 6) {
                    val a = Offset(contour.edges[edge], contour.edges[edge + 1])
                    val b = Offset(contour.edges[edge + 2], contour.edges[edge + 3])
                    val delta = b - a
                    val relative = point - a
                    val t = ((relative.x * delta.x + relative.y * delta.y) / delta.getDistanceSquared()).coerceIn(0f, 1f)
                    nearest = minOf(nearest, (point - (a + delta * t)).getDistance())
                }
                assertEquals(nearest, kotlin.math.abs(grid.rgba[(y * grid.width + x) * 4]), 0.0001f)
            }
        }
    }

    @Test
    fun contactFieldAxisHoistingMatchesEveryOriginalFloatBit() {
        contactOracleContours().forEachIndexed { scene, polygons ->
            val contour = NavigationContactContour(pathApproximation(*polygons.toTypedArray()))
            listOf(0f, 7.5f, 32f, 120f).forEach { band ->
                val actual = NavigationContactField().grid(contour, band)
                val expected = referenceContactField(contour.edges, actual)
                // The visible grid changes only the old unresolved negative-distance sentinel.
                // Keep every solved distance, normal and alpha bit checked against the old raster.
                expected.indices.step(4).forEach { index ->
                    if (expected[index + 1] == 0f && expected[index + 2] == 0f)
                        expected[index] = kotlin.math.abs(expected[index])
                }
                assertContactFieldBits("Scene $scene, band $band", expected, actual.rgba)
            }
        }
    }

    @Test
    fun nativeContactFieldMatchesEveryOriginalFloatBit() {
        org.junit.Assume.assumeTrue("Host native library is optional", LiquidMenuNativeField.warmUp())
        contactOracleContours().forEachIndexed { scene, polygons ->
            val contour = NavigationContactContour(pathApproximation(*polygons.toTypedArray()))
            listOf(0f, 7.5f, 32f, 120f).forEach { band ->
                val geometry = NavigationContactField().grid(contour, band)
                val rawGeometry = NavigationContactField.Grid(geometry.width, geometry.height).apply {
                    origin = geometry.origin
                    this.band = band
                }
                val actual = FloatArray(geometry.rgba.size)
                assertTrue("Native raster must execute instead of silently falling back",
                    LiquidMenuNativeField.buildContact(contour.edges, geometry.origin.x, geometry.origin.y,
                        geometry.step, geometry.width, geometry.height, band, actual))
                assertContactFieldBits("Native scene $scene, band $band",
                    referenceContactField(contour.edges, rawGeometry), actual)
            }
        }
    }

    @Test
    fun nativeContactFieldRejectsInvalidGeometryBeforeMutatingOutput() {
        org.junit.Assume.assumeTrue("Host native library is optional", LiquidMenuNativeField.warmUp())
        val contour = NavigationContactContour(pathApproximation(
            listOf(Offset(0f, 0f), Offset(32f, 0f), Offset(32f, 16f), Offset(0f, 16f))))
        val output = FloatArray(32 * 16 * 4) { 0.25f }
        assertTrue(!LiquidMenuNativeField.buildContact(contour.edges, 0f, 0f, 2f,
            Int.MAX_VALUE, Int.MAX_VALUE, 24f, output))
        assertTrue(!LiquidMenuNativeField.buildContact(contour.edges, 0f, 0f, 0f, 32, 16, 24f, output))
        assertTrue(!LiquidMenuNativeField.buildContact(contour.edges, Float.NaN, 0f, 2f, 32, 16, 24f, output))
        assertTrue(!LiquidMenuNativeField.buildContact(contour.edges, 0f, 0f, 2f, 32, 16, -1f, output))
        assertTrue(!LiquidMenuNativeField.buildContact(FloatArray(6), 0f, 0f, 2f, 32, 16, 24f, output))
        assertTrue(output.all { it.toRawBits() == 0.25f.toRawBits() })
    }

    private fun assertContactFieldBits(label: String, expected: FloatArray, actual: FloatArray) {
        expected.indices.forEach { index ->
            assertEquals("$label, RGBA offset $index", expected[index].toRawBits(), actual[index].toRawBits())
        }
    }

    private fun contactOracleContours(): List<List<List<Offset>>> {
        fun capsule(x: Float, y: Float, width: Float, height: Float): List<Offset> = List(64) { index ->
            val angle = 2.0 * Math.PI * index / 64.0
            val radius = height / 2f
            val centerX = x + if (kotlin.math.cos(angle) >= 0.0) width - radius else radius
            Offset((centerX + radius * kotlin.math.cos(angle)).toFloat(),
                (y + radius + radius * kotlin.math.sin(angle)).toFloat())
        }
        val neck = listOf(Offset(0f, 0f), Offset(80f, 0f), Offset(100f, 20f), Offset(120f, 20f),
            Offset(140f, 0f), Offset(220f, 0f), Offset(220f, 80f), Offset(140f, 80f),
            Offset(120f, 60f), Offset(100f, 60f), Offset(80f, 80f), Offset(0f, 80f))
        return listOf(
            listOf(capsule(0f, 0f, 270f, 56f), capsule(278f, 0f, 56f, 56f)),
            listOf(neck), listOf(neck.asReversed()),
            listOf(capsule(-17.25f, 0.125f, 334f, 56f)),
            listOf(listOf(Offset(0f, 0f), Offset(220f, 80f), Offset(0f, 80f), Offset(220f, 0f))),
            listOf(listOf(Offset(0f, 0f), Offset(0.000001f, 0f), Offset(220f, 0f),
                Offset(220f, 80f), Offset(0f, 80f), Offset(0f, 0f))),
            listOf(listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(220f, 0f))),
            listOf(listOf(Offset(-0.0f, 0.0f), Offset(0.0f, 80f),
                Offset(220f, 80f), Offset(220f, -0.0f))),
        )
    }

    @Test
    fun nativeFieldUploadPreservesSignedHalfFloatChannels() {
        val packed = java.nio.ByteBuffer.allocateDirect(16).order(java.nio.ByteOrder.nativeOrder())
        org.junit.Assume.assumeTrue("Host native library is optional",
            LiquidMenuNativeField.pack(floatArrayOf(-2f, 0f, -1f, 1f), 1, 1, 16, packed))
        assertEquals(0xc000, packed.getShort(0).toInt() and 0xffff)
        assertEquals(0, packed.getShort(2).toInt())
        assertEquals(0xbc00, packed.getShort(4).toInt() and 0xffff)
        assertEquals(0x3c00, packed.getShort(6).toInt() and 0xffff)
    }

    private fun referenceContactField(edges: FloatArray, grid: NavigationContactField.Grid): FloatArray {
        val width = grid.width
        val height = grid.height
        val step = grid.step
        val origin = grid.origin
        val rgba = FloatArray(grid.rgba.size)
        val reach = grid.band + step * 2f
        for (index in 0 until width * height) {
            val output = index * 4
            rgba[output] = reach * reach
            rgba[output + 1] = 0f
            rgba[output + 2] = 0f
            rgba[output + 3] = -1f
        }
        for (edge in edges.indices step 6) {
            val ax = edges[edge]
            val ay = edges[edge + 1]
            val bx = edges[edge + 2]
            val by = edges[edge + 3]
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            val inverseLengthSquared = 1f / lengthSquared
            val minimumX = minOf(ax, bx)
            val maximumX = maxOf(ax, bx)
            val minimumY = minOf(ay, by)
            val maximumY = maxOf(ay, by)
            val left = floor((minOf(ax, bx) - reach - origin.x) / step).toInt().coerceAtLeast(0)
            val right = ceil((maxOf(ax, bx) + reach - origin.x) / step).toInt().coerceAtMost(width - 1)
            val top = floor((minOf(ay, by) - reach - origin.y) / step).toInt().coerceAtLeast(0)
            val bottom = ceil((maxOf(ay, by) + reach - origin.y) / step).toInt().coerceAtMost(height - 1)
            for (y in top..bottom) for (x in left..right) {
                val pointX = origin.x + x * step
                val pointY = origin.y + y * step
                val output = (y * width + x) * 4
                val outsideX = maxOf(minimumX - pointX, pointX - maximumX, 0f)
                val outsideY = maxOf(minimumY - pointY, pointY - maximumY, 0f)
                if (outsideX * outsideX + outsideY * outsideY >= rgba[output]) continue
                val px = pointX - ax
                val py = pointY - ay
                val t = ((px * dx + py * dy) * inverseLengthSquared).coerceIn(0f, 1f)
                val nx = px - t * dx
                val ny = py - t * dy
                val squared = nx * nx + ny * ny
                if (squared >= rgba[output]) continue
                val direction = if (nx * edges[edge + 4] + ny * edges[edge + 5] >= 0f) 1f else -1f
                rgba[output] = squared
                rgba[output + 1] = if (squared > 0.00000001f) nx * direction else edges[edge + 4]
                rgba[output + 2] = if (squared > 0.00000001f) ny * direction else edges[edge + 5]
                rgba[output + 3] = direction
            }
        }
        for (index in 0 until width * height) {
            val output = index * 4
            val distance = sqrt(rgba[output])
            rgba[output] = distance * rgba[output + 3]
            if (distance > 0.0001f) {
                rgba[output + 1] /= distance
                rgba[output + 2] /= distance
            }
            rgba[output + 3] = 1f
        }
        return rgba
    }

    private fun pathApproximation(vararg contours: List<Offset>): FloatArray {
        val result = ArrayList<Float>()
        var fraction = 0f
        contours.forEach { contour ->
            (contour + contour.first()).forEachIndexed { i, point ->
                if (i > 0) fraction += 1f
                result.add(fraction); result.add(point.x); result.add(point.y)
            }
        }
        return result.toFloatArray()
    }

    private fun assertField(grid: NavigationContactField.Grid, point: Offset, distance: Float, normal: Offset) {
        val x = ((point.x - grid.origin.x) / grid.step).toInt()
        val y = ((point.y - grid.origin.y) / grid.step).toInt()
        val index = (y * grid.width + x) * 4
        assertEquals(distance, grid.rgba[index], 0.001f)
        assertEquals(normal.x, grid.rgba[index + 1], 0.001f)
        assertEquals(normal.y, grid.rgba[index + 2], 0.001f)
    }

    @Test
    fun sharedPanelTransformAlignsTheIndicatorAndIconsWithAndWithoutLensAnchoring() {
        listOf(1f, 2.75f, 3.5f).forEach { density ->
            val width = 280f * density
            listOf(0f, 0.3f, 1f).forEach { press ->
                val panelScale = navigationPressScale(width, 16f * density, press)
                listOf(0f, 1f).forEach { anchoring ->
                    listOf(41.5f, 140f, 238.5f).forEachIndexed { index, centerDp ->
                        listOf(centerDp, 280f - centerDp).forEach { directionalCenter ->
                            val center = directionalCenter * density
                            listOf(0f, 0.5f, 1f, 1.5f, 2f).forEach { lensPosition ->
                                val tabScale = navigationTabPanelScale(index, lensPosition, panelScale, anchoring)
                                val correction = navigationTabPressOffset(center, width / 2f, panelScale, tabScale)
                                val rowCenter = width / 2f + (center + correction - width / 2f) * panelScale
                                val sharedCenter = center + navigationPanelCenterOffset(center, width / 2f, tabScale)
                                assertEquals(rowCenter, sharedCenter, 0.001f)
                                if (anchoring == 0f) assertEquals(panelScale, tabScale, 0f)
                            }
                            val alignedScale = navigationTabPanelScale(index, index.toFloat(), panelScale, anchoring)
                            val indicatorScale = navigationTabPanelScale(0, 0f, panelScale, anchoring)
                            assertEquals(
                                center + navigationPanelCenterOffset(center, width / 2f, alignedScale),
                                center + navigationPanelCenterOffset(center, width / 2f, indicatorScale),
                                0.001f,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun departingTabFollowsThePanelBeforeTheRouteCommitsAndReanchorsOnReturn() {
        val panelScale = navigationPressScale(280f, 16f, 1f)
        val forward = (0..100).map { step -> navigationTabPanelScale(0, step / 100f, panelScale) }
        assertEquals(1f, forward.first(), 0f)
        assertEquals(panelScale, forward.last(), 0f)
        assertTrue(forward.zipWithNext().all { (before, after) -> after >= before })
        assertTrue(forward[50] > 1f && forward[50] < panelScale)
        val reverse = (100 downTo 0).map { step -> navigationTabPanelScale(0, step / 100f, panelScale) }
        assertEquals(forward, reverse.reversed())
        assertEquals(1f, navigationTabPanelScale(2, 2f, panelScale), 0f)
        assertEquals(panelScale, navigationTabPanelScale(0, 2f, panelScale), 0f)
    }

    @Test
    fun swappingBetweenRowAndSelectedOverlayPreservesCenterAndSizeDuringDrag() {
        listOf(1f, 2.75f, 3.5f).forEach { density ->
            val width = 280f * density
            val panelScale = navigationPressScale(width, 16f * density, 1f)
            listOf(41.5f, 140f, 238.5f).forEachIndexed { index, centerDp ->
                listOf(centerDp, 280f - centerDp).forEach { directionalCenter ->
                    val center = directionalCenter * density
                    listOf(0f, 0.2f, 0.5f, 1f, 1.8f, 2f).forEach { lensPosition ->
                        val tabScale = navigationTabPanelScale(index, lensPosition, panelScale)
                        val offset = navigationTabPressOffset(center, width / 2f, panelScale, tabScale)
                        val rowCenter = width / 2f + (center + offset - width / 2f) * panelScale
                        val overlayCenter = width / 2f + (center - width / 2f) * tabScale
                        assertEquals(overlayCenter, rowCenter, 0.001f)
                        listOf(1f, 1.2f).forEach { contentScale ->
                            val rowScale = contentScale * (tabScale / panelScale) * panelScale
                            assertEquals(contentScale * tabScale, rowScale, 0.0001f)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun incomingTabIsAnchoredIndependentlyOfThePreviouslySelectedKey() {
        val panelScale = 1.08f
        listOf(0, 1, 2).forEach { incoming ->
            assertEquals(1f, navigationTabPanelScale(incoming, incoming.toFloat(), panelScale), 0f)
            assertEquals(
                navigationTabPanelScale(incoming, incoming - 0.3f, panelScale),
                navigationTabPanelScale(incoming, incoming + 0.3f, panelScale), 0.0001f,
            )
        }
    }

    @Test
    fun hiddenMaterialUsesTheInnerHeightThroughoutTheCompactTransition() {
        listOf(1f, 2.75f, 3.5f).forEach { density ->
            listOf(0f, 0.3f, 0.74f, 1f).forEach { compact ->
                val height = navigationLensSourceHeight((64f - 16f * compact) * density, 4f * density)
                assertEquals((56f - 16f * compact) * density, height, 0.0001f)
            }
        }
    }

    @Test
    fun demoSamplingBandCrossesTheMaterialBoundaryOnAllFourSides() {
        listOf(1f, 2.75f, 3.5f).forEach { density ->
            val scale = 78f / 56f
            val height = NavigationIndicatorRefractionHeight.value * density
            val amount = NavigationIndicatorRefractionAmount.value * density
            val sourceHeight = navigationLensSourceHeight(64f * density, 4f * density)
            val firstCenter = 41.5f * density
            val lastCenter = 238.5f * density
            val insets = sampleInsets(height, amount).map { it * scale }
            val topSamples = insets.map { -39f * density + it }
            val bottomSamples = topSamples.map { -it }
            val leftSamples = insets.map { firstCenter - 75f * density * scale / 2f + it }
            val rightSamples = insets.map { lastCenter + 75f * density * scale / 2f - it }
            listOf(
                topSamples to -sourceHeight / 2f,
                bottomSamples to sourceHeight / 2f,
                leftSamples to 0f,
                rightSamples to 280f * density,
            ).forEach { (samples, boundary) ->
                assertTrue("The refractive band must include pixels outside the source", samples.min() < boundary - 0.5f * density)
                assertTrue("The refractive band must also include pixels inside the source", samples.max() > boundary + 0.5f * density)
            }
        }
    }

    @Test
    fun previousScaledFullHeightSourceKeepsItsTopEdgeOutsideTheSamplingBand() {
        val panelScale = navigationPressScale(280f, 16f, 1f)
        val oldSourceTop = -64f * panelScale / 2f
        val oldSamples = sampleInsets(14f, 22f).map { -39f + it * (78f / 56f) }
        assertTrue(oldSamples.min() > oldSourceTop + 8f)
    }

    @Test
    fun segmentedSamplingCrossesTheInnerSourceEdgesForNarrowAndWideSegments() {
        listOf(1f, 2.75f, 3.5f).forEach { density ->
            val height = SegmentedIndicatorHeight.value * density
            val insets = sampleInsets(
                SegmentedIndicatorRefractionHeight.value * density,
                SegmentedIndicatorRefractionAmount.value * density,
            )
            val pressedHeight = height * SegmentedIndicatorPressedScale
            val topSamples = insets.map { -pressedHeight / 2f + it }
            assertStraddles(topSamples, -height / 2f, density)
            assertStraddles(topSamples.map { -it }, height / 2f, density)
            listOf(180f, 280f, 360f, 600f).forEach { widthDp ->
                listOf(2, 3, 6).forEach { count ->
                    val sourceWidth = widthDp * density
                    val inset = 2f * density
                    val tabWidth = (sourceWidth - 2f * inset) / count
                    val visualSize = segmentedIndicatorSize(
                        Size(tabWidth, height), SegmentedIndicatorPressedScale, SegmentedIndicatorPressedScale, 0f,
                        SegmentedIndicatorHorizontalExpansion.value * density,
                    )
                    val leftSamples = insets.map { inset + tabWidth / 2f - visualSize.width / 2f + it }
                    assertStraddles(leftSamples, 0f, density)
                    assertStraddles(leftSamples.map { sourceWidth - it }, sourceWidth, density)
                    val corners = Capsule().corners(visualSize, LayoutDirection.Ltr, Density(density))
                    val radius = visualSize.minDimension / 2f
                    assertEquals(radius, corners.topLeft, 0.001f)
                    assertEquals(radius, corners.bottomRight, 0.001f)
                }
            }
        }
    }

    @Test
    fun previousSegmentedLensSamplesInsideTheOpaquePillInsteadOfItsEdge() {
        val scale = 36f / 28f
        val topSamples = sampleInsets(7f, 18f).map { -18f + it * scale }
        assertTrue(topSamples.min() > -14f + 3f)
    }

    private fun assertStraddles(samples: List<Float>, boundary: Float, density: Float) {
        assertTrue("Sampling must reach outside the source", samples.min() < boundary - 0.25f * density)
        assertTrue("Sampling must also reach inside the source", samples.max() > boundary + 0.25f * density)
    }

    /** Trace Backdrop's stock circular edge profile along a capsule's symmetry axes. */
    private fun sampleInsets(height: Float, amount: Float): List<Float> = (0..1000).map { step ->
        val depth = height * step / 1000f
        val x = 1f - depth / height
        depth + amount * (1f - kotlin.math.sqrt((1f - x * x).coerceAtLeast(0f)))
    }
}
