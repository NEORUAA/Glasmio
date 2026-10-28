package io.github.neoruaa.glasmio.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.shapes.Capsule
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationIndicatorLensTest {
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

    private class QueuedDragDispatcher : kotlinx.coroutines.CoroutineDispatcher() {
        private val pending = ArrayDeque<Runnable>()
        override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { pending.addLast(block) }
        fun drain() { while (pending.isNotEmpty()) pending.removeFirst().run() }
    }

    private class DragFrameClock : androidx.compose.runtime.MonotonicFrameClock {
        private val waiting = ArrayList<(Long) -> Unit>()
        private var time = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                waiting.add { nanos -> if (continuation.isActive) continuation.resumeWith(runCatching { onFrame(nanos) }) }
            }
        fun settle(dispatcher: QueuedDragDispatcher) {
            repeat(180) {
                time += 16_666_667L
                val ready = waiting.toList()
                waiting.clear()
                ready.forEach { it(time) }
                dispatcher.drain()
            }
        }
    }

    private fun withQueuedDrag(block: (DampedDragAnimation, QueuedDragDispatcher, DragFrameClock) -> Unit) {
        val dispatcher = QueuedDragDispatcher()
        val clock = DragFrameClock()
        val scope = kotlinx.coroutines.CoroutineScope(dispatcher + clock)
        val drag = DampedDragAnimation(scope, 0f, 0f..3f, 0.001f, 1f, 78f / 56f,
            onDragStarted = {}, onDragStopped = {}, onDrag = { _, _ -> })
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
                val actual = FloatArray(geometry.rgba.size)
                assertTrue("Native raster must execute instead of silently falling back",
                    LiquidMenuNativeField.buildContact(contour.edges, geometry.origin.x, geometry.origin.y,
                        geometry.step, geometry.width, geometry.height, band, actual))
                assertContactFieldBits("Native scene $scene, band $band",
                    referenceContactField(contour.edges, geometry), actual)
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
