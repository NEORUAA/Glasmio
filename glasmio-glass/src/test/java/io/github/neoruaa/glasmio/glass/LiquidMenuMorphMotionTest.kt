package io.github.neoruaa.glasmio.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot
import kotlin.math.ceil
import kotlin.math.atan2

class LiquidMenuMorphMotionTest {
    private val source = Rect(676f, 183f, 996f, 321f)
    private val destination = Rect(242f, 185f, 996f, 687f)

    @Test
    fun parallelEnvelopesContractBeforeTheyFormTheMenu() {
        val motion = motion()
        motion.retarget(true)
        motion.advanceBy(0.05f)
        val early = motion.frame
        val lobe = requireNotNull(early.sourceLobe)
        assertTrue("The fused source contracts rather than detaching", lobe.width < source.width * 0.75f)
        assertTrue("The incoming menu starts as a rounded droplet", early.radiusX > early.bounds.width * 0.3f)
        assertTrue("The optical field joins both lobes", early.smoothing > 0f)
        assertTrue("The physical kick bends the path downward", early.bounds.center.y > source.center.y + 40f)
        motion.advanceBy(3f)
        assertRectEquals(destination, motion.frame.bounds)
        assertEquals(102f, motion.frame.radiusX, 0.001f)
        assertEquals(102f, motion.frame.radiusY, 0.001f)
        assertEquals(0f, motion.frame.smoothing, 0.001f)
        assertEquals(1f, motion.frame.contentProgress, 0.001f)
    }

    @Test
    fun retargetingPreservesTheLiveGeometryAndMomentum() {
        val motion = motion()
        motion.retarget(true)
        motion.advanceBy(0.08f)
        val before = motion.frame
        motion.retarget(false)
        assertEquals("Changing direction cannot replace the shape",
            before.copy(lensHeight = motion.frame.lensHeight, lensAmount = motion.frame.lensAmount), motion.frame)
        motion.advanceBy(0.004f)
        assertTrue("Momentum carries the existing eject through the reversal", motion.frame.bounds.width > before.bounds.width)
        val closing = motion.frame
        motion.retarget(true)
        assertEquals(closing.copy(lensHeight = motion.frame.lensHeight, lensAmount = motion.frame.lensAmount), motion.frame)
        motion.advanceBy(0.02f)
        assertTrue(motion.frame.bounds.width.isFinite())
        motion.retarget(false)
        motion.advanceBy(3f)
        assertRectEquals(source, requireNotNull(motion.frame.sourceLobe))
        assertEquals(0f, motion.frame.contentProgress, 0.001f)
    }

    @Test
    fun contentResizingRetargetsLiveAndSettledMenusWithoutReplayingOptics() {
        for (opensAbove in listOf(false, true)) {
            for (elapsed in listOf(0.04f, 0.15f, 0.4f, 3f)) {
                val motion = motion()
                val control = motion()
                motion.retarget(true)
                control.retarget(true)
                motion.advanceBy(elapsed)
                control.advanceBy(elapsed)
                val shorter = if (opensAbove) Rect(destination.left + 60f, destination.bottom - 248f,
                    destination.right, destination.bottom)
                else Rect(destination.left + 60f, destination.top, destination.right, destination.top + 248f)
                val before = motion.frame
                assertTrue(motion.updateTarget(shorter, 102f))
                assertEquals("A new content size retains every live presentation channel", before, motion.frame)
                assertTrue("A repeated layout report is not a new retarget", !motion.updateTarget(shorter, 102f))
                motion.advanceBy(0.025f)
                control.advanceBy(0.025f)
                assertEquals("Resizing must not replay the content reveal",
                    control.frame.contentProgress, motion.frame.contentProgress, 0.00001f)
                assertEquals("Resizing must retain the lens release timeline",
                    control.frame.lensProgress, motion.frame.lensProgress, 0.00001f)
                assertEquals("The opening transaction keeps its portal optics",
                    control.frame.contentZoom, motion.frame.contentZoom, 0.00001f)
                assertEquals(control.frame.portalBlur, motion.frame.portalBlur, 0.00001f)
                motion.advanceBy(4f)
                assertRectEquals(shorter, motion.frame.bounds)
                assertEquals(102f, motion.frame.radiusY, 0.001f)

                val taller = Rect(shorter.left - 36f, shorter.top - 44f, shorter.right + 12f, shorter.top + 660f)
                val settled = motion.frame
                assertTrue(motion.updateTarget(taller, 102f))
                assertEquals("A settled menu also retains its presentation before resizing", settled, motion.frame)
                motion.advanceBy(4f)
                assertRectEquals(taller, motion.frame.bounds)
                motion.retarget(false)
                motion.advanceBy(4f)
                assertRectEquals(source, requireNotNull(motion.frame.sourceLobe))
            }
        }

        val moving = motion()
        moving.retarget(true)
        moving.advanceBy(.08f)
        val before = moving.frame
        moving.updateTarget(Rect(destination.left, destination.top, destination.right, destination.top + 180f), 90f)
        moving.advanceBy(.001f)
        assertTrue("A shorter target must not discard the menu's existing upward size velocity",
            moving.frame.bounds.height > before.bounds.height)
    }

    @Test
    fun distanceFieldKeepsOneContinuousSkinThroughOpeningAndReversal() {
        val scenes = buildList {
            add(Triple(source, destination, source))
            listOf(4, 5).forEach { itemCount ->
                listOf(132f, 320f).forEach { sourceWidth ->
                    listOf(false, true).forEach { opensAbove ->
                        val top = if (opensAbove) 1900f else 185f
                        val source = Rect(1072f - sourceWidth, top, 1072f, top + 132f)
                        val height = (20f + 44f * itemCount) * 3f
                        val menuTop = if (opensAbove) source.bottom - height else source.top
                        val through = Rect(source.right - source.height, source.top, source.right, source.bottom)
                        add(Triple(source, Rect(358f, menuTop, 1072f, menuTop + height), through))
                    }
                }
            }
        }
        scenes.forEachIndexed { scene, (source, destination, through) ->
            val motion = LiquidMenuMorphMotion().apply {
                initialize(source, destination, 102f, 3f, through = through)
            }
            val workspace = LiquidMenuContourWorkspace()
            val initial = contour(motion.frame)
            listOf(
                Offset(source.left, source.center.y), Offset(source.right, source.center.y),
                Offset(source.center.x, source.top), Offset(source.center.x, source.bottom),
            ).forEach { edge ->
                assertEquals("Scene $scene starts at the original capsule", 0f, initial.distance(edge), 0.001f)
            }
            assertTrue(initial.distance(source.center) < 0f)
            assertTrue("Scene $scene keeps the capsule corner outside the skin", initial.distance(source.topLeft) > 0f)

            motion.retarget(true)
            repeat(720) { index ->
                // Cover the complete initial eject before testing a mid-close reversal.
                if (index == 144 || index == 288) motion.retarget(false)
                if (index == 168) motion.retarget(true)
                motion.advanceBy(1f / 240f)
                val frame = motion.frame
                val skin = contour(frame, workspace)
                val sourceCenter = requireNotNull(frame.sourceLobe).center
                repeat(65) { sample ->
                    val point = sourceCenter + (frame.bounds.center - sourceCenter) * (sample / 64f)
                    val distance = skin.distance(point)
                    assertTrue("Scene $scene has a finite field at frame $index", distance.isFinite())
                }
                // Gaussian smoothing can absorb the tiny raw source orb completely.
                // Test the physical negative region instead of requiring that erased orb's
                // center and its straight line to the menu remain inside the final skin.
                assertSingleSkin(skin, source.height, "Scene $scene, frame $index")
                listOf(frame.bounds.topLeft, frame.bounds.bottomRight, sourceCenter).forEach {
                    assertTrue("Scene $scene has a finite exterior at frame $index", skin.distance(it).isFinite())
                }
            }
        }
    }

    @Test
    fun absorptionRoundsTheMenuBeforeItReturnsToTheCapsule() {
        val motion = motion()
        motion.retarget(true)
        motion.advanceBy(3f)
        val openRoundness = motion.frame.radiusX / motion.frame.bounds.width
        motion.retarget(false)
        motion.advanceBy(0.1f)
        assertTrue("The closing surface becomes a droplet as its area shrinks",
            motion.frame.radiusX / motion.frame.bounds.width > openRoundness + 0.1f)
        motion.advanceBy(3f)
        assertRectEquals(source, requireNotNull(motion.frame.sourceLobe))
    }

    @Test
    fun nativeThroughPreviewRemainsIndependentOfTheFusedSource() {
        val through = Rect(source.right - source.height, source.top, source.right, source.bottom)
        val motion = LiquidMenuMorphMotion().apply {
            initialize(source, destination, 102f, 3f, through = through)
        }
        assertEquals(Offset(926f, 251f), motion.frame.bounds.center)
        assertEquals(destination.width * 0.5f * through.height / maxOf(destination.width, destination.height),
            motion.frame.bounds.width, 0.001f)
        assertRectEquals(source, requireNotNull(motion.frame.sourceLobe))
        motion.retarget(true)
        motion.advanceBy(3f)
        assertEquals(Offset(849f, 297f), requireNotNull(motion.frame.sourceLobe).center)
        assertEquals(source.size.minDimension * 0.25f, requireNotNull(motion.frame.sourceLobe).width, 0.001f)
        motion.retarget(false)
        motion.advanceBy(3f)
        assertEquals(source.center, motion.frame.bounds.center)
        assertRectEquals(source, requireNotNull(motion.frame.sourceLobe))
    }

    @Test
    fun morphCentersTolerateIndependentRoundingAndOversizedAnchors() {
        val source = Rect(160f, 180f, 260f, 280f)
        val destination = Rect(0f, 0f, 400f, 400f)
        val anchors = listOf(
            Rect(180f, 200f, 220f, 240f) to Offset(200f, 220f),
            source to source.center,
            Rect(160f, 180f, 260.5f, 280.5f) to source.center,
            Rect(120f, 150f, 300f, 330f) to source.center,
            Rect(120f, 200f, 300f, 240f) to Offset(210f, 220f),
            Rect(180f, 150f, 220f, 330f) to Offset(200f, 230f),
            Rect(260f, 280f, 300f, 320f) to Offset(240f, 260f),
        )
        listOf(1f, 1.25f, 2.625f, 3f).forEach { density ->
            fun Rect.scaled() = Rect(left * density, top * density, right * density, bottom * density)
            anchors.forEach { (through, expectedCenter) ->
                val motion = LiquidMenuMorphMotion().apply {
                    initialize(source.scaled(), destination.scaled(), 32f * density, density, through.scaled())
                }
                val label = "Through $through at density $density"
                assertEquals("$label keeps the nearest contained X center",
                    expectedCenter.x * density, motion.frame.bounds.center.x, 0.001f)
                assertEquals("$label keeps the nearest contained Y center",
                    expectedCenter.y * density, motion.frame.bounds.center.y, 0.001f)
                motion.retarget(true)
                motion.advanceBy(3f)
                assertRectEquals(destination.scaled(), motion.frame.bounds)
                motion.retarget(false)
                motion.advanceBy(3f)
                assertRectEquals(source.scaled(), requireNotNull(motion.frame.sourceLobe))
            }

            // A compact destination can also be smaller than the source's pivot inset.
            val compact = Rect(204f, 224f, 216f, 236f).scaled()
            val motion = LiquidMenuMorphMotion().apply {
                initialize(source.scaled(), compact, 6f * density, density)
            }
            motion.retarget(true)
            motion.advanceBy(3f)
            assertRectEquals(compact, motion.frame.bounds)
            val pivot = requireNotNull(motion.frame.sourceLobe).center
            assertEquals(compact.center.x, pivot.x, 0.001f)
            assertEquals(compact.center.y, pivot.y, 0.001f)
        }
    }

    @Test
    fun nativeUIKitGeometryKeepsTheThroughPreviewAndLargestAxisScale() {
        val source = Rect(284.33334f, 62f, 386f, 106f)
        val through = Rect(344f, 66f, 382f, 102f)
        val destination = Rect(136f, 62f, 386f, 250f)
        val motion = LiquidMenuMorphMotion().apply {
            initialize(source, destination, 32f, 1f, through = through)
        }
        // Live ARM64 register capture at 0x1de40b6ac / b754: center (363,84), scale .072.
        assertEquals(Offset(363f, 84f), motion.frame.bounds.center)
        assertEquals(18f, motion.frame.bounds.width, 0.001f)
        motion.retarget(true)
        motion.advanceBy(0.3f)
        val springFrame = motion.frame
        val sourceScale = requireNotNull(springFrame.sourceLobe).height / source.height
        val ejectProgress = (1f - sourceScale) / 0.75f
        assertTrue("Native source transform retains the main eject spring's overshoot", sourceScale < 0.25f)
        assertEquals(84f + (102f - 84f) * ejectProgress,
            requireNotNull(springFrame.sourceLobe).center.y, 0.001f)
        assertTrue("The source's explicit bounds spring remains critically damped",
            requireNotNull(springFrame.sourceLobe).width / sourceScale >= source.height)
        motion.advanceBy(3f)
        assertEquals(Offset(338f, 102f), requireNotNull(motion.frame.sourceLobe).center)
        assertEquals(11f, requireNotNull(motion.frame.sourceLobe).height, 0.001f)
        motion.retarget(false)
        motion.advanceBy(3f)
        // The toolbar group coordinator's measured final model rect is (326.1667,75,18,18).
        assertRectEquals(Rect(326.1667f, 75f, 344.1667f, 93f), motion.frame.bounds, 0.001f)
    }

    @Test
    fun nativeUnionKeepsParallelEdgesAndBlendsFacingNormals() {
        val circle = Rect(0f, 0f, 100f, 100f)
        val radii = Offset(50f, 50f)
        listOf(0f, 20f, 80f).forEach { smoothness ->
            val identical = LiquidMenuContour(circle, radii, circle, radii, smoothness)
            assertEquals("Parallel unit normals do not inflate an already unified edge",
                0f, identical.rawDistance(Offset(100f, 50f)), 0.0001f)
            val tangent = LiquidMenuContour(circle, radii, circle.translate(Offset(100f, 0f)), radii, smoothness)
            assertEquals("CASDF's opposing-normal contact receives smoothness / 4 lift",
                -smoothness / 4f, tangent.rawDistance(Offset(100f, 50f)), 0.0001f)
            assertEquals("Outside the effective blend band, union is the nearer field",
                0f, tangent.rawDistance(Offset(0f, 50f)), 0.0001f)
        }
    }

    @Test
    fun nativeRoundedSamplerPreservesRawOverrounding() {
        val bounds = Rect(0f, 0f, 240f, 200f)
        val resting = LiquidMenuContour(bounds, Offset(34f, 34f), null, Offset.Zero, 0f)
        // Samples executed from QuartzCore's original ARM64 sampler, including its 1/127 quantization.
        assertEquals(0.48031496f, resting.distance(Offset(240f, 166f)), 0.02f)
        val overrounded = LiquidMenuContour(bounds, Offset(240f, 240f), null, Offset.Zero, 0f)
        assertEquals(37.8582677f, overrounded.distance(Offset(240f, 100f)), 0.06f)
        assertEquals(28.3307087f, overrounded.distance(Offset(120f, 200f)), 0.06f)
    }

    @Test
    fun nativeAbsorptionRestoresTheSourceWithTheMainAbsorbSpring() {
        val motion = motion()
        motion.retarget(true)
        motion.advanceBy(3f)
        motion.retarget(false)
        motion.advanceBy(0.05f)
        val source = requireNotNull(motion.frame.sourceLobe)
        // AnimationKit mainAbsorbSpring: damping .8, response .7, withSpeed(.7).
        // Its 50 ms solution is 0.14542210, shared by bounds and transform scale.
        val restored = 0.14542210f
        val expectedScale = 0.25f + 0.75f * restored
        assertEquals(138f * expectedScale, source.height, 0.002f)
        assertEquals((138f + (320f - 138f) * restored) * expectedScale, source.width, 0.002f)
        assertTrue("The native absorb turn enables the liquid union before releasing it", motion.frame.smoothing > 40f)
        assertTrue("Portal blur is a separate native track, not content opacity", motion.frame.portalBlur > 1f)
        assertEquals(restored, motion.frame.sourceOpacity, 0.0001f)
        assertEquals(1f - restored, motion.frame.contentProgress, 0.0001f)
        assertEquals(12f * (1f - restored), motion.frame.sourceBlur, 0.002f)
        assertEquals(12f * restored, motion.frame.contentBlur, 0.002f)
        assertEquals(motion.frame.sourceZoom, motion.frame.contentZoom, 0f)
        assertEquals(75f * 3f * (502f / (250f * 3f)) * motion.frame.lensProgress,
            motion.frame.lensHeight, 0.001f)
        assertEquals(-motion.frame.lensHeight, motion.frame.lensAmount, 0.001f)
        var recoiledBelowIdentity = false
        repeat(120) {
            motion.updateSource(this.source)
            motion.advanceBy(1f / 240f)
            if (motion.frame.contentZoom < 1f) recoiledBelowIdentity = true
            assertTrue("Blur radius remains valid during transform recoil", motion.frame.portalBlur >= 0f)
        }
        assertTrue("Native transform recoil is not clamped at identity", recoiledBelowIdentity)
        assertTrue("Geometry tracking does not restart the released lens", motion.frame.lensProgress < 0.02f)
    }

    @Test
    fun oscillatorAndTimelineAreIndependentOfFrameSubdivision() {
        val whole = motion()
        val split = motion()
        whole.retarget(true)
        split.retarget(true)
        whole.advanceBy(0.7f)
        repeat(70) { split.advanceBy(0.01f) }
        assertRectEquals(whole.frame.bounds, split.frame.bounds, 0.01f)
        assertEquals(whole.frame.radiusX, split.frame.radiusX, 0.01f)
        assertEquals(whole.frame.radiusY, split.frame.radiusY, 0.01f)
        assertEquals(whole.frame.smoothing, split.frame.smoothing, 0.01f)
        whole.retarget(false)
        split.retarget(false)
        whole.advanceBy(0.24f)
        repeat(24) { split.advanceBy(0.01f) }
        assertRectEquals(whole.frame.bounds, split.frame.bounds, 0.01f)
        assertRectEquals(requireNotNull(whole.frame.sourceLobe), requireNotNull(split.frame.sourceLobe), 0.01f)
        assertEquals(whole.frame.smoothing, split.frame.smoothing, 0.01f)
        assertEquals(whole.frame.contentBlur, split.frame.contentBlur, 0.01f)
        assertEquals(whole.frame.lensProgress, split.frame.lensProgress, 0.01f)
        val spring = LiquidMenuSpring()
        spring.reset(0f)
        spring.target(100f, LiquidMenuSpringSpec(0.75f, 0.5f))
        spring.advance(0.1f)
        val value = spring.value
        val velocity = spring.velocity
        spring.target(0f, LiquidMenuSpringSpec(0.9f, 0.3f))
        assertEquals(value, spring.value, 0f)
        assertEquals(velocity, spring.velocity, 0f)
        spring.advance(10f)
        assertTrue(abs(spring.value) < 0.001f)
        assertTrue(abs(spring.velocity) < 0.001f)
    }

    @Test
    fun liquidCoverageRemainsStableAcrossRightAndBottomTextureGridBoundaries() {
        listOf(101.4f, 101.7f, 101.9f, 101.99f, 102.01f, 102.2f).forEach { extent ->
            listOf(false, true).forEach { vertical ->
                val bounds = if (vertical) Rect(0f, 0f, 81.9f, extent) else Rect(0f, 0f, extent, 81.9f)
                val field = requireNotNull(LiquidMenuContour(
                    bounds, Offset(10f, 10f), null, Offset.Zero, 1f,
                ).sampledField())
                val point = if (vertical) Offset(40f, extent - 0.1f) else Offset(extent - 0.1f, 40f)
                val distance = field.distance(point.x, point.y)
                // Match the portal's forward-difference coverage at a straight edge.
                val aa = abs(field.distance(point.x + 1f, point.y) - distance) +
                    abs(field.distance(point.x, point.y + 1f) - distance)
                val label = "extent=$extent vertical=$vertical"
                assertEquals("$label keeps its outward derivative", 1f, aa, 0.002f)
                assertEquals("$label keeps subpixel coverage", 0.6f,
                    (-distance / aa + 0.5f).coerceIn(0f, 1f), 0.002f)
            }
        }
    }

    @Test
    fun cachedContourPreservesSubpixelRootsAcrossOvershootAndReversal() {
        var evaluations = 0L
        var sampledFrames = 0
        var maximumError = 0f
        listOf(
            source to destination,
            Rect(784f, 155f, 1072f, 287f) to Rect(358f, 155f, 1072f, 779f),
        ).forEach { (source, destination) ->
            val motion = motion(source, destination)
            val workspace = LiquidMenuContourWorkspace()
            motion.retarget(true)
            repeat(180) { index ->
                if (index == 72 || index == 120) motion.retarget(false)
                if (index == 84) motion.retarget(true)
                motion.advanceBy(1f / 120f)
                if (index % 3 != 0) return@repeat
                val frame = motion.frame
                val skin = contour(frame)
                val outgoing = requireNotNull(frame.sourceLobe)
                val size = Size(
                    maxOf(frame.bounds.right, outgoing.right) - minOf(frame.bounds.left, outgoing.left),
                    maxOf(frame.bounds.bottom, outgoing.bottom) - minOf(frame.bounds.top, outgoing.top),
                )
                val beforeEvaluations = workspace.totalFieldEvaluations
                val vertices = workspace.vertices(skin, size)
                evaluations += workspace.totalFieldEvaluations - beforeEvaluations
                sampledFrames++
                val unchangedEvaluations = workspace.totalFieldEvaluations
                assertSame("Stable geometry reuses the numeric contour", vertices, workspace.vertices(skin.copy(), size))
                assertEquals(unchangedEvaluations, workspace.totalFieldEvaluations)
                val sourceArea = outgoing.width * outgoing.height
                val incomingArea = frame.bounds.width * frame.bounds.height
                val center = (outgoing.center * sourceArea + frame.bounds.center * incomingArea) / (sourceArea + incomingArea)
                repeat(96) { ray ->
                    val angle = ray * (2.0 * PI / 96.0)
                    val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                    var low = 0f
                    var high = hypot(size.width, size.height)
                    repeat(25) {
                        val radius = (low + high) / 2f
                        if (skin.distance(center + direction * radius) <= 0f) low = radius else high = radius
                    }
                    val expected = center + direction * ((low + high) / 2f)
                    val actual = Offset(vertices[ray * 2], vertices[ray * 2 + 1])
                    val error = (actual - expected).getDistance()
                    maximumError = maxOf(maximumError, error)
                    assertTrue("A certified root stays subpixel at frame $index, ray $ray: $error", error <= 0.08f)
                }
            }
        }
        val previousEvaluations = sampledFrames.toLong() * 96 * 14
        assertTrue("Root certification uses fewer field evaluations than blind bisection",
            evaluations < previousEvaluations * 0.8)
        println("Liquid contour: $evaluations / $previousEvaluations field evaluations; max error $maximumError px")
    }

    @Test
    fun gaussianFieldReusesGrowthCapacityAndReleasesInactiveBuffers() {
        val field = LiquidMenuGaussianField()
        fun activeMenu(width: Float) = LiquidMenuContour(
            Rect(0f, 0f, width, 548f), Offset(34f, 34f), null, Offset.Zero, 1f,
        )
        // Exercise an actual growth before measuring retained capacity, as a menu does while
        // expanding. Exact-size allocation would still replace the backing on the next +2px.
        assertSame(field, field.update(activeMenu(236f)))
        assertSame(field, field.update(activeMenu(238f)))
        val backing = field.rgba
        val grown = activeMenu(240f)
        assertSame(field, field.update(grown))
        assertSame("A small extent increase reuses the amortized backing", backing, field.rgba)
        assertEquals("Gaussian convolution preserves a linear straight-edge distance",
            -1f, field.distance(grown.incoming.center.x, 1f), 0.002f)

        // Zero sigma and a positive sigma whose discrete kernel becomes identity are separate
        // early-return paths. Both must release the dense transition grid while the menu rests.
        listOf(0f, 0.01f).forEach { inactiveSigma ->
            assertNull("Sigma $inactiveSigma has no active discrete Gaussian",
                field.update(grown.copy(smoothing = inactiveSigma)))
            assertTrue("Sigma $inactiveSigma releases the CPU backing", field.rgba.isEmpty())
            assertSame("Reopening rebuilds the reusable field", field, field.update(grown))
            assertEquals("Reopening after sigma $inactiveSigma keeps the physical field",
                -1f, field.distance(grown.incoming.center.x, 1f), 0.002f)
        }
    }

    @Test
    fun restingMenuCornerTangentsAndCurvatureDoNotDependOnMenuHeight() {
        val workspace = LiquidMenuContourWorkspace()
        listOf(1f, 3f).forEach { density ->
            listOf(152f, 437f, 532f, 548f, 578f).forEach { height ->
                val bounds = Rect(0f, 0f, 238f * density, height * density)
                val skin = LiquidMenuContour(bounds, Offset(34f * density, 34f * density), null, Offset.Zero, 0f)
                val vertices = workspace.vertices(skin, bounds.size)
                val controls = workspace.controlPoints(skin, bounds.size)
                fun diagonalDifference(segment: Int, t: Double): Double {
                    val (x, y) = cubicVector(vertices, controls, segment, t)
                    return (bounds.right - x) - (bounds.bottom - y)
                }
                // The scalar corner is symmetric under exchanging its physical X/Y insets.
                // Find that geometric diagonal independently of the polar sample locations.
                val segment = (0 until 24).first {
                    diagonalDifference(it, 0.0) <= 0.0 && diagonalDifference(it, 1.0) >= 0.0
                }
                var low = 0.0
                var high = 1.0
                repeat(48) {
                    val t = (low + high) / 2.0
                    if (diagonalDifference(segment, t) < 0.0) low = t else high = t
                }
                val t = (low + high) / 2.0
                val (dx, dy) = cubicVector(vertices, controls, segment, t, derivative = 1)
                val (ddx, ddy) = cubicVector(vertices, controls, segment, t, derivative = 2)
                val angle = atan2(abs(dy), abs(dx)) * 180.0 / PI
                val speed = hypot(dx, dy)
                val curvature = abs(dx * ddy - dy * ddx) / (speed * speed * speed)
                val label = "238x$height at density $density"
                assertEquals("$label keeps the diagonal tangent symmetric", 45.0, angle, 0.75)
                // Analytic one-sided curvature of the native scalar polynomial at r=34.
                // It is not 1/r; tiny coefficient-fit error makes a central Hessian at the
                // exact diagonal an unstable oracle, so use this independent analytic value.
                val nativeCurvature = 0.03305453160755543 / density
                assertEquals("$label keeps the native physical corner curvature",
                    nativeCurvature, curvature, nativeCurvature * 0.15)
            }
        }
    }

    @Test
    fun restingMenuStraightSegmentsStayInsideTheirOwnUnequallySpacedChords() {
        val workspace = LiquidMenuContourWorkspace()
        listOf(1f, 3f).forEach { density ->
            listOf(152f, 437f, 532f, 548f, 578f).forEach { height ->
                val bounds = Rect(0f, 0f, 238f * density, height * density)
                val skin = LiquidMenuContour(bounds, Offset(34f * density, 34f * density), null, Offset.Zero, 0f)
                val vertices = workspace.vertices(skin, bounds.size)
                val controls = workspace.controlPoints(skin, bounds.size)
                val cornerBand = 34f * density * 1.5286649465560913f + 0.125f
                var straightSegments = 0
                var shortestChord = Double.POSITIVE_INFINITY
                var longestChord = 0.0
                repeat(96) { segment ->
                    val start = segment * 2
                    val end = (segment + 1) % 96 * 2
                    val x0 = vertices[start].toDouble()
                    val y0 = vertices[start + 1].toDouble()
                    val x1 = vertices[end].toDouble()
                    val y1 = vertices[end + 1].toDouble()
                    val vertical = abs(x0 - x1) < 0.08 &&
                        (abs(x0) < 0.08 || abs(x0 - bounds.right) < 0.08) &&
                        minOf(y0, y1) > cornerBand && maxOf(y0, y1) < bounds.bottom - cornerBand
                    val horizontal = abs(y0 - y1) < 0.08 &&
                        (abs(y0) < 0.08 || abs(y0 - bounds.bottom) < 0.08) &&
                        minOf(x0, x1) > cornerBand && maxOf(x0, x1) < bounds.right - cornerBand
                    if (!vertical && !horizontal) return@repeat
                    straightSegments++
                    val chord = hypot(x1 - x0, y1 - y0)
                    shortestChord = minOf(shortestChord, chord)
                    longestChord = maxOf(longestChord, chord)
                    val label = "238x$height density $density segment $segment"
                    val control = segment * 4
                    listOf(controls[control] to controls[control + 1], controls[control + 2] to controls[control + 3]).forEach { (x, y) ->
                        assertTrue("$label has finite controls", x.isFinite() && y.isFinite())
                        assertTrue("$label keeps each control in its own X interval",
                            x.toDouble() in (minOf(x0, x1) - 0.08)..(maxOf(x0, x1) + 0.08))
                        assertTrue("$label keeps each control in its own Y interval",
                            y.toDouble() in (minOf(y0, y1) - 0.08)..(maxOf(y0, y1) + 0.08))
                    }
                    repeat(9) { sample ->
                        val (x, y) = cubicVector(vertices, controls, segment, sample / 8.0)
                        assertTrue("$label does not overshoot its straight X interval",
                            x in (minOf(x0, x1) - 0.08)..(maxOf(x0, x1) + 0.08))
                        assertTrue("$label does not overshoot its straight Y interval",
                            y in (minOf(y0, y1) - 0.08)..(maxOf(y0, y1) + 0.08))
                    }
                }
                assertTrue("The fixture must exercise straight contour segments", straightSegments > 0)
                assertTrue("Polar rays must exercise unequal physical chord lengths", longestChord > shortestChord * 1.2)
            }
        }
    }

    private fun cubicVector(
        vertices: FloatArray,
        controls: FloatArray,
        segment: Int,
        t: Double,
        derivative: Int = 0,
    ): Pair<Double, Double> {
        val start = segment * 2
        val end = (segment + 1) % (vertices.size / 2) * 2
        val control = segment * 4
        fun coordinate(axis: Int): Double {
            val a = vertices[start + axis].toDouble()
            val b = controls[control + axis].toDouble()
            val c = controls[control + 2 + axis].toDouble()
            val d = vertices[end + axis].toDouble()
            return when (derivative) {
                1 -> 3 * (1 - t) * (1 - t) * (b - a) + 6 * (1 - t) * t * (c - b) + 3 * t * t * (d - c)
                2 -> 6 * (1 - t) * (c - 2 * b + a) + 6 * t * (d - 2 * c + b)
                else -> (1 - t) * (1 - t) * (1 - t) * a + 3 * (1 - t) * (1 - t) * t * b +
                    3 * (1 - t) * t * t * c + t * t * t * d
            }
        }
        return coordinate(0) to coordinate(1)
    }

    private fun assertSingleSkin(skin: LiquidMenuContour, sourceHeight: Float, label: String) {
        val bounds = skin.exteriorBounds()
        val step = minOf(sourceHeight / 12f, maxOf(bounds.width, bounds.height) / 96f).coerceAtLeast(1f)
        val columns = ceil(bounds.width / step).toInt() + 1
        val rows = ceil(bounds.height / step).toInt() + 1
        val inside = BooleanArray(columns * rows)
        var seed = -1
        var count = 0
        repeat(rows) { row ->
            repeat(columns) { column ->
                val distance = skin.distance(bounds.left + column * step, bounds.top + row * step)
                assertTrue("$label has a finite physical field", distance.isFinite())
                if (distance < 0f) {
                    val index = row * columns + column
                    inside[index] = true
                    if (seed < 0) seed = index
                    count++
                }
            }
        }
        assertTrue("$label retains a visible skin", seed >= 0)
        val queue = IntArray(count)
        var head = 0
        var tail = 1
        queue[0] = seed
        inside[seed] = false
        while (head < tail) {
            val index = queue[head++]
            val row = index / columns
            val column = index % columns
            for (dy in -1..1) for (dx in -1..1) {
                val y = row + dy
                val x = column + dx
                if (x !in 0 until columns || y !in 0 until rows) continue
                val neighbor = y * columns + x
                if (inside[neighbor]) {
                    inside[neighbor] = false
                    queue[tail++] = neighbor
                }
            }
        }
        assertEquals("$label has one connected physical skin", count, tail)
    }

    private fun contour(frame: LiquidMenuMorphFrame, workspace: LiquidMenuContourWorkspace? = null) = LiquidMenuContour(
        frame.bounds, Offset(frame.radiusX, frame.radiusY), frame.sourceLobe,
        Offset(frame.sourceRadiusX, frame.sourceRadiusY), frame.smoothing, workspace,
    )

    private fun motion(
        source: Rect = this.source,
        destination: Rect = this.destination,
    ) = LiquidMenuMorphMotion().also {
        it.initialize(source, destination, targetRadius = 102f, density = 3f)
    }

    private fun assertRectEquals(expected: Rect, actual: Rect, tolerance: Float = 0.001f) {
        assertEquals(expected.left, actual.left, tolerance)
        assertEquals(expected.top, actual.top, tolerance)
        assertEquals(expected.right, actual.right, tolerance)
        assertEquals(expected.bottom, actual.bottom, tolerance)
    }
}
