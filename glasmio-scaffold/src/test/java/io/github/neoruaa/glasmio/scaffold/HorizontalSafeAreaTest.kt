package io.github.neoruaa.glasmio.scaffold

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class HorizontalSafeAreaTest {
    @Test
    fun pageMarginsUseTheLargestHardwareSide() {
        for ((left, right) in listOf(80 to 0, 0 to 80, 30 to 80, 80 to 30)) {
            assertEquals(HorizontalInsets(80, 80), resolvePageHorizontalInsets(left, right, true))
        }
        assertEquals(HorizontalInsets(0, 0), resolvePageHorizontalInsets(0, 0, true))
    }

    @Test
    fun sidebarReplacesTheFacingHardwareMargin() {
        assertEquals(
            HorizontalInsets(240, 80),
            resolvePageHorizontalInsets(0, 80, true, sidebarLeft = 240),
        )
        assertEquals(
            HorizontalInsets(80, 240),
            resolvePageHorizontalInsets(80, 0, true, sidebarRight = 240),
        )
    }

    @Test
    fun sidebarAnimationKeepsPageAndScrollerMarginsContinuous() {
        for (sidebar in listOf(0, 1, 40, 79, 80, 81, 240)) {
            val residual = resolvePageHorizontalInsets(0, 80, true, sidebarLeft = sidebar, includeSidebar = false)
            val total = resolvePageHorizontalInsets(0, 80, true, sidebarLeft = sidebar)
            assertEquals(HorizontalInsets(maxOf(80, sidebar), 80), total)
            assertEquals(total, HorizontalInsets(residual.left + sidebar, residual.right))
        }
    }

    @Test
    fun disablingHardwareSafetyRetainsEachSidebarSide() {
        assertEquals(
            HorizontalInsets(240, 30),
            resolvePageHorizontalInsets(80, 120, false, sidebarLeft = 240, sidebarRight = 30),
        )
        assertEquals(HorizontalInsets(0, 0), resolvePageHorizontalInsets(80, 120, false))
        assertEquals(
            HorizontalInsets(0, 0),
            resolvePageHorizontalInsets(80, 120, false, sidebarLeft = 240, sidebarRight = 30, includeSidebar = false),
        )
    }

    @Test
    fun scrollingContentPreservesPhysicalSidebarAndVerticalPaddingInBothDirections() {
        val insets = resolvePageHorizontalInsets(0, 80, true, sidebarLeft = 240)
        val original = PaddingValues(start = 16.dp, top = 3.dp, end = 20.dp, bottom = 7.dp)
        for (direction in LayoutDirection.entries) {
            val padding = horizontalContentPadding(original, insets, Density(2f), direction)
            val expectedLeft = if (direction == LayoutDirection.Ltr) 136.dp else 140.dp
            val expectedRight = if (direction == LayoutDirection.Ltr) 60.dp else 56.dp
            assertEquals(expectedLeft, padding.calculateLeftPadding(direction))
            assertEquals(expectedRight, padding.calculateRightPadding(direction))
            assertEquals(3.dp, padding.calculateTopPadding())
            assertEquals(7.dp, padding.calculateBottomPadding())
        }
    }
}
