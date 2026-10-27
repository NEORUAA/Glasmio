/*
 * Magnifier optics adapted from AndroidLiquidGlass MagnifierContent,
 * Copyright 2025 Kyant. Licensed under the Apache License, Version 2.0.
 * https://www.apache.org/licenses/LICENSE-2.0
 * https://github.com/Kyant0/AndroidLiquidGlass
 */
package io.github.neoruaa.glasmio.ui

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.LocalBottomControlContact
import io.github.neoruaa.glasmio.glass.LocalIosToolbarActionGroup
import io.github.neoruaa.glasmio.glass.LocalToolbarActionSeparation
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import io.github.neoruaa.glasmio.core.LocalGlassColors
import kotlin.math.roundToInt

/**
 * A visual magnifier for caller-owned selection, cursor or inspection gestures.
 *
 * Place this in a bounded overlay that shares the source content's coordinate space.
 * [sourcePosition] and optional [viewportBounds] use physical pixels relative to this
 * overlay's top-left, including in RTL. [backdrop] must record the source content without
 * this magnifier or its other overlay siblings, preventing a recursive sample graph.
 *
 * The lens prefers [verticalOffset] above the source, flipping below when space is limited.
 * System bar and IME insets constrain the viewport; a damped spring follows position changes.
 * Clamping moves the lens without changing the sampled source point. An undersized viewport
 * scales the lens down uniformly. The primitive owns no cursor, gesture, focus or IME state.
 */
@Composable
fun GlasmioMagnifier(
    visible: Boolean,
    sourcePosition: Offset,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    viewportBounds: Rect? = null,
    lensSize: DpSize = DpSize(128.dp, 96.dp),
    verticalOffset: Dp = 80.dp,
    magnification: Float = 1.5f,
    backgroundColor: Color = if (LocalGlassColors.current.isDark) Color.Black else Color.White,
) {
    require(magnification.isFinite() && magnification > 0f) { "Magnification must be finite and positive." }
    require(lensSize.width.value.isFinite() && lensSize.width > 0.dp &&
        lensSize.height.value.isFinite() && lensSize.height > 0.dp) { "Lens size must be finite and positive." }
    require(verticalOffset.value.isFinite()) { "Vertical offset must be finite." }
    if (!visible || !sourcePosition.x.isFinite() || !sourcePosition.y.isFinite()) return

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val windowSize = LocalWindowInfo.current.containerSize
    val safeInsets = WindowInsets.safeDrawing.union(WindowInsets.ime)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy()) }
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { coordinates = it }, contentAlignment = AbsoluteAlignment.TopLeft) {
        if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) return@BoxWithConstraints
        val root = coordinates?.takeIf { it.isAttached } ?: return@BoxWithConstraints
        val fullViewport = Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val safeViewport = Rect(
            root.windowToLocal(Offset(safeInsets.getLeft(density, layoutDirection).toFloat(), safeInsets.getTop(density).toFloat())),
            root.windowToLocal(Offset((windowSize.width - safeInsets.getRight(density, layoutDirection)).toFloat(),
                (windowSize.height - safeInsets.getBottom(density)).toFloat())),
        ).intersect(fullViewport)
        val requestedViewport = viewportBounds ?: fullViewport
        if (!requestedViewport.left.isFinite() || !requestedViewport.top.isFinite() ||
            !requestedViewport.right.isFinite() || !requestedViewport.bottom.isFinite()) return@BoxWithConstraints
        val viewport = requestedViewport.intersect(safeViewport)
        if (viewport.width < 1f || viewport.height < 1f) return@BoxWithConstraints

        val desiredWidth = with(density) { lensSize.width.toPx() }
        val desiredHeight = with(density) { lensSize.height.toPx() }
        val fit = minOf(1f, viewport.width / desiredWidth, viewport.height / desiredHeight)
        val width = (desiredWidth * fit).roundToInt().coerceIn(1, viewport.width.toInt())
        val height = (desiredHeight * fit).roundToInt().coerceIn(1, viewport.height.toInt())
        val gap = with(density) { verticalOffset.toPx() } - height / 2f
        val target = Offset(
            (sourcePosition.x - width / 2f).coerceIn(viewport.left, viewport.right - width),
            editingOverlayTop(Rect(sourcePosition, sourcePosition), viewport, height.toFloat(), gap.coerceAtLeast(0f), preferAbove = true),
        )
        val animatedOrigin by animateOffsetAsState(target,
            spring(dampingRatio = 0.85f, stiffness = 420f), label = "EditingMagnifierPosition")
        val origin = IntOffset(
            animatedOrigin.x.coerceIn(viewport.left, viewport.right - width).roundToInt(),
            animatedOrigin.y.coerceIn(viewport.top, viewport.bottom - height).roundToInt(),
        )
        val actualCenter = Offset(origin.x + width / 2f, origin.y + height / 2f)
        val currentSource = rememberUpdatedState(sourcePosition)
        val currentCenter = rememberUpdatedState(actualCenter)
        val currentBackground = rememberUpdatedState(backgroundColor)
        val transform: DrawScope.(DrawScope.() -> Unit) -> Unit = remember(magnification) {
            { drawSource ->
                // The magnified region can extend beyond the source window near an edge.
                // Fill that transparent part before transforming the recorded scene.
                drawRect(currentBackground.value)
                val translation = currentCenter.value - currentSource.value
                withTransform(
                    {
                        scale(magnification, magnification)
                        translate(left = translation.x, top = translation.y)
                    },
                    drawSource,
                )
            }
        }
        val magnifiedBackdrop = rememberBackdrop(backdrop, transform)
        // A selection lens never participates in a toolbar's connected glass geometry.
        CompositionLocalProvider(
            LocalIosToolbarActionGroup provides null,
            LocalToolbarActionSeparation provides null,
            LocalBottomControlContact provides null,
        ) {
            GlassSurface(
                modifier = Modifier.absoluteOffset { origin }
                    .size(with(density) { width.toDp() }, with(density) { height.toDp() }),
                backdrop = magnifiedBackdrop,
                shape = Capsule(),
                // Keep the Clear edge layers without frosting the magnified glyphs.
                material = NavigationGlassMaterial.Clear,
                frostRadius = 0.dp,
                preserveBackdropColors = true,
                refractionHeight = 8.dp,
                refractionAmount = 24.dp,
            ) {}
        }
    }
}
