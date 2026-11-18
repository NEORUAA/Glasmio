package io.github.neoruaa.glasmio.scaffold

import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.neoruaa.glasmio.core.LocalGlasmioHorizontalSafeAreaEnabled

/** Inset shared by page headers and the scroll edge in fullscreen and freeform windows. */
internal val WindowInsets.Companion.glasmioPageHeaderInsets: WindowInsets
    @Composable
    get() = statusBars.union(captionBar.only(WindowInsetsSides.Top))

/** Total body insets; parents that reserve the sidebar have already consumed that space. */
internal val WindowInsets.Companion.glasmioPageSafeDrawing: WindowInsets
    @Composable
    get() = safeDrawing.only(WindowInsetsSides.Vertical)
        .union(glasmioPageHorizontalInsets(includeSidebar = true))

/** Sidebar space replaces the facing hardware-safe margin without enlarging the opposite side. */
@Composable
internal fun glasmioPageHorizontalInsets(
    insets: WindowInsets = WindowInsets.safeDrawing,
    includeSidebar: Boolean = false,
): WindowInsets {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val (left, right) = resolvePageHorizontalInsets(insets, density, layoutDirection, includeSidebar)
    return WindowInsets(left = left, top = 0, right = right, bottom = 0)
}

internal data class HorizontalInsets(val left: Int, val right: Int)

internal fun resolvePageHorizontalInsets(
    hardwareLeft: Int,
    hardwareRight: Int,
    horizontalSafeAreaEnabled: Boolean,
    sidebarLeft: Int = 0,
    sidebarRight: Int = 0,
    includeSidebar: Boolean = true,
): HorizontalInsets {
    val safeZone = if (horizontalSafeAreaEnabled) maxOf(hardwareLeft, hardwareRight) else 0
    // Let the sidebar absorb the facing margin gradually as its occupied width animates.
    val left = (safeZone - sidebarLeft).coerceAtLeast(0)
    val right = (safeZone - sidebarRight).coerceAtLeast(0)
    return HorizontalInsets(
        left + if (includeSidebar) sidebarLeft else 0,
        right + if (includeSidebar) sidebarRight else 0,
    )
}

@Composable
internal fun resolvePageHorizontalInsets(
    insets: WindowInsets,
    density: Density,
    layoutDirection: LayoutDirection,
    includeSidebar: Boolean = false,
): HorizontalInsets {
    val sidebar = LocalGlasmioSidebarInsets.current
    val sidebarLeft = sidebar.getLeft(density, layoutDirection)
    val sidebarRight = sidebar.getRight(density, layoutDirection)
    val enabled = LocalGlasmioHorizontalSafeAreaEnabled.current
    if (!enabled) return resolvePageHorizontalInsets(0, 0, false, sidebarLeft, sidebarRight, includeSidebar)

    val insetLeft = insets.getLeft(density, layoutDirection)
    val insetRight = insets.getRight(density, layoutDirection)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return resolvePageHorizontalInsets(insetLeft, insetRight, true, sidebarLeft, sidebarRight, includeSidebar)
    }

    // A page consumes its Compose insets before nested scrollers expand through that area.
    // Retain the physical root values so both consumers resolve the same page margin.
    val rootInsets = LocalView.current.rootWindowInsets
    val systemInsets = rootInsets?.getInsetsIgnoringVisibility(
        android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout(),
    )
    val cutout = rootInsets?.displayCutout
    return resolvePageHorizontalInsets(
        hardwareLeft = maxOf(insetLeft, systemInsets?.left ?: 0, cutout?.safeInsetLeft ?: 0),
        hardwareRight = maxOf(insetRight, systemInsets?.right ?: 0, cutout?.safeInsetRight ?: 0),
        horizontalSafeAreaEnabled = true,
        sidebarLeft = sidebarLeft,
        sidebarRight = sidebarRight,
        includeSidebar = includeSidebar,
    )
}
