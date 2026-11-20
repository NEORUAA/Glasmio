package io.github.neoruaa.glasmio.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuPanelMotionTest {
    private val bounds = Rect(63f, 109f, 313f, 297f)

    @Test
    fun horizontalDragKeepsGrowingOutsideThePanelAtAnyDensity() {
        val input = MenuPanelInput(Offset(188f, 140f), Offset(353f, 140f), bounds)
        assertEquals(Offset(125f, 0f), input.internalTranslation)
        val unit = menuPanelTarget(input.translation, input.internalTranslation, bounds.size, 0f, 0f, 1f)
        assertEquals(1.01f, unit.scale.x, 0.0001f)
        assertEquals(0.99f, unit.scale.y, 0.0001f)
        assertEquals(2.525f, unit.translation.x * unit.scale.x, 0.0001f)
        val dense = menuPanelTarget(input.translation * 3f, input.internalTranslation * 3f, bounds.size * 3f, 0f, 0f, 3f)
        assertEquals(unit.scale, dense.scale)
        assertEquals(unit.translation.x * 3f, dense.translation.x, 0.0001f)
    }

    @Test
    fun verticalPulseMirrorsTheAttachmentEdgeAndRetainsDriftDirection() {
        val size = Size(250f, 188f)
        val below = menuPanelTarget(Offset.Zero, Offset.Zero, size, 800f, 0f, 1f)
        val above = menuPanelTarget(Offset.Zero, Offset.Zero, size, 800f, 1f, 1f)
        assertEquals(0.98f, below.scale.x, 0.0001f)
        assertEquals(1.02f, above.scale.x, 0.0001f)
        assertEquals(2.24f, below.translation.y, 0.0001f)
        assertEquals(below.translation.y, above.translation.y, 0.0001f)
    }

    @Test
    fun movingAwayFromAnExternalTriggerDoesNotInvertThePanelFlex() {
        val input = MenuPanelInput(Offset(188f, 80f), Offset(188f, 60f), bounds)
        assertEquals(Offset.Zero, input.internalTranslation)
        val target = menuPanelTarget(input.translation, input.internalTranslation, bounds.size, 0f, 0f, 1f)
        assertEquals(-188f * 20f / 6000f, target.translation.y, 0.0001f)
    }
}
