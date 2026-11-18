package io.github.neoruaa.glasmio.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class AnchoredPlacementTest {
    @Test fun popoverPrefersTheCandidateWithTheLargestVisibleBody() {
        val placement = placeAnchoredSurface(
            anchor = IntRect(280, 30, 306, 54),
            viewport = IntRect(10, 20, 310, 220),
            body = IntSize(120, 80),
            sides = listOf(AnchoredSide.Above, AnchoredSide.Right, AnchoredSide.Below, AnchoredSide.Left),
            separation = 13,
            pointerInset = 16,
            preferMaximumVisibleArea = true,
        )
        assertEquals(AnchoredSide.Left, placement.side)
        assertEquals(IntOffset(280, 42), placement.tip)
    }

    @Test fun flipsAtTopAndKeepsTipClearOfTheWindowCorner() {
        val placement = placeAnchoredSurface(
            anchor = IntRect(280, 30, 306, 54),
            viewport = IntRect(10, 20, 310, 220),
            body = IntSize(120, 80),
            sides = listOf(AnchoredSide.Above, AnchoredSide.Below),
            separation = 8,
            pointerInset = 20,
        )
        assertEquals(AnchoredSide.Below, placement.side)
        assertEquals(IntRect(190, 62, 310, 142), placement.body)
        assertEquals(IntOffset(290, 54), placement.tip)
    }

    @Test fun usesHorizontalDirectionWhenBothVerticalDirectionsAreBlocked() {
        val placement = placeAnchoredSurface(
            anchor = IntRect(35, 64, 65, 112),
            viewport = IntRect(10, 20, 310, 160),
            body = IntSize(100, 80),
            sides = listOf(AnchoredSide.Below, AnchoredSide.Above, AnchoredSide.Right),
            separation = 8,
            pointerInset = 20,
        )
        assertEquals(AnchoredSide.Right, placement.side)
        assertEquals(IntRect(73, 48, 173, 128), placement.body)
        assertEquals(IntOffset(65, 88), placement.tip)
    }

    @Test fun oversizedBodyClampsToViewportAndUsesDirectionWithMostRoom() {
        val viewport = IntRect(10, 20, 210, 120)
        val placement = placeAnchoredSurface(
            anchor = IntRect(94, 80, 110, 100),
            viewport = viewport,
            body = IntSize(500, 250),
            sides = listOf(AnchoredSide.Below, AnchoredSide.Above),
            separation = 8,
            pointerInset = 120,
        )
        assertEquals(AnchoredSide.Above, placement.side)
        assertEquals(viewport, placement.body)
        assertEquals(IntOffset(110, 128), placement.tip)
    }
}
