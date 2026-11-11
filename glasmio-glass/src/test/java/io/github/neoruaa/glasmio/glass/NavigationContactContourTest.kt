package io.github.neoruaa.glasmio.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationContactContourTest {
    private val rectangle = listOf(Offset(0f, 0f), Offset(80f, 0f), Offset(80f, 32f), Offset(0f, 32f))

    @Test
    fun degenerateContoursHaveNoEdgesOrBounds() {
        for (points in listOf(emptyList(), listOf(Offset.Zero), rectangle.take(2), List(4) { Offset.Zero })) {
            val contour = NavigationContactContour(approximate(points))
            assertTrue(contour.edges.isEmpty())
            assertEquals(Rect.Zero, contour.bounds)
        }
    }

    @Test
    fun oppositeWindingsBothPointOutward() {
        for (points in listOf(rectangle, rectangle.asReversed())) {
            val contour = NavigationContactContour(approximate(points))
            assertEquals(Rect(0f, 0f, 80f, 32f), contour.bounds)
            assertEquals(4 * 6, contour.edges.size)
            for (i in contour.edges.indices step 6) {
                val x = (contour.edges[i] + contour.edges[i + 2]) * .5f - 40f
                val y = (contour.edges[i + 1] + contour.edges[i + 3]) * .5f - 16f
                assertTrue(x * contour.edges[i + 4] + y * contour.edges[i + 5] > 0f)
            }
        }
    }

    @Test
    fun repeatedFractionsSeparateContoursWithoutBridgeEdges() {
        val other = rectangle.map { it + Offset(200f, 40f) }.asReversed()
        val contour = NavigationContactContour(approximate(rectangle, other))
        val first = NavigationContactContour(approximate(rectangle)).edges
        val second = NavigationContactContour(approximate(other)).edges
        assertTrue((first + second).contentEquals(contour.edges))
        assertEquals(Rect(0f, 0f, 280f, 72f), contour.bounds)
    }

    @Test
    fun duplicatePointsAndExplicitClosingPointKeepTheSameEdges() {
        val expected = NavigationContactContour(approximate(rectangle))
        val actual = NavigationContactContour(approximate(rectangle.flatMap { listOf(it, it) } + rectangle.first()))
        assertTrue(expected.edges.contentEquals(actual.edges))
        assertEquals(expected.bounds, actual.bounds)
        assertEquals(expected.fingerprint, actual.fingerprint)
    }

    @Test
    fun discardedContoursDoNotExpandBoundsOrConnectToTheNextContour() {
        val contour = NavigationContactContour(approximate(
            listOf(Offset(-1000f, -1000f), Offset(-900f, -900f)), rectangle,
            listOf(Offset(1000f, 1000f)),
        ))
        val expected = NavigationContactContour(approximate(rectangle))
        assertTrue(expected.edges.contentEquals(contour.edges))
        assertEquals(expected.bounds, contour.bounds)
    }

    @Test
    fun snapshotOwnsItsPackedEdges() {
        val approximation = approximate(rectangle)
        val contour = NavigationContactContour(approximation)
        val original = contour.edges.copyOf()
        approximation.fill(1000f)
        assertTrue(original.contentEquals(contour.edges))
        assertEquals(Rect(0f, 0f, 80f, 32f), contour.bounds)
    }

    private fun approximate(vararg contours: List<Offset>): FloatArray {
        val result = ArrayList<Float>()
        var fraction = 0f
        for (contour in contours) {
            for ((i, point) in contour.withIndex()) {
                if (i > 0) fraction += 1f
                result += fraction
                result += point.x
                result += point.y
            }
        }
        return result.toFloatArray()
    }
}
