package io.github.neoruaa.glasmio.scaffold

import org.junit.Assert.*
import org.junit.Test

class SheetMotionTest {
    @Test fun releaseDirectionRespectsVelocityThenLastDragThenOrigin() {
        assertTrue(sheetDragOpens(-100f, 30f, false))
        assertFalse(sheetDragOpens(100f, -30f, true))
        assertTrue(sheetDragOpens(0f, -1f, false))
        assertFalse(sheetDragOpens(0f, 1f, true))
        assertTrue(sheetDragOpens(0f, 0f, true))
        assertFalse(sheetDragOpens(0f, 0f, false))
    }

    @Test fun dragConvertsPixelsAndClampsToOrderedAnchors() {
        assertEquals(150f, sheetDragValue(100f, -100f, 2f, 50f, 300f), 0f)
        assertEquals(50f, sheetDragValue(100f, 1000f, 2f, 50f, 300f), 0f)
        assertEquals(300f, sheetDragValue(100f, -1000f, 2f, 50f, 300f), 0f)
        assertEquals(50f, sheetDragValue(100f, -1000f, 2f, 50f, 10f), 0f)
    }
}
