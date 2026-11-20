package io.github.neoruaa.glasmio.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.GlasmioMenuDragSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.PI

private val LocalMenuDragTargets = staticCompositionLocalOf<MenuDragTargets?> { null }
private val LocalMenuDragSurface = staticCompositionLocalOf<MenuDragSelectionLayer?> { null }
internal val LocalMenuPanelMotion = staticCompositionLocalOf<MenuPanelMotion?> { null }
internal val LocalMenuItemAutoNavigationDelayMillis = compositionLocalOf<Long?> { null }
internal val LocalMenuItemDragSelectionEnabled = compositionLocalOf { true }

@Composable
private fun menuSelectionColor(): Color =
    (if (LocalGlassColors.current.isDark) Color.White else Color(0xFF767680)).copy(alpha = 0.12f)

private val MenuSelectionGeometryStiffness = (2f * PI.toFloat() / 0.12f).let { it * it }
private val MenuSelectionFadeStiffness = (2f * PI.toFloat() / 0.30f).let { it * it }

private val SelectionBoundsSpring = spring<Rect>(
    dampingRatio = 1f,
    stiffness = MenuSelectionGeometryStiffness,
    visibilityThreshold = Rect(0.25f, 0.25f, 0.25f, 0.25f),
)
private val SelectionRadiusSpring = spring<Float>(
    dampingRatio = 1f,
    stiffness = MenuSelectionGeometryStiffness,
    visibilityThreshold = 0.25f,
)
private val SelectionFadeSpring = spring<Float>(
    dampingRatio = 1f,
    stiffness = MenuSelectionFadeStiffness,
    visibilityThreshold = 0.001f,
)

private class MenuDragTargets(
    private val drag: GlasmioMenuDragSelection,
    private val dwellMovementThreshold: Float,
    private val isReady: () -> Boolean,
) {
    private data class Dwell(val row: MenuDragRow, val position: Offset)

    val rows = mutableStateListOf<MenuDragRow>()
    var layoutRevision by mutableIntStateOf(0)
        private set
    val hovered = derivedStateOf {
        drag.position?.let { position ->
            // Coordinates remain live. Observe layout changes only during an active drag.
            layoutRevision
            hitTest(position)
        }
    }
    val highlight = MenuDragHighlight()
    private var dwell by mutableStateOf<Dwell?>(null)

    fun onLayoutChanged() {
        if (Snapshot.withoutReadObservation { drag.position != null }) layoutRevision++
    }

    fun releaseAt(position: Offset) {
        val row = hitTest(position)
        clearDwell()
        row?.select?.invoke()
    }

    fun navigate(row: MenuDragRow) {
        val point = drag.position ?: return
        if (hitTest(point) !== row) return
        // The next list can appear under a stationary finger. Require fresh movement
        // before allowing its rows to replace or commit the action just opened.
        dwell = Dwell(row, point)
        row.select()
    }

    fun clearDwell() {
        dwell = null
    }

    private fun hitTest(position: Offset): MenuDragRow? {
        if (!isReady() || !position.isFinite) return null
        val completed = dwell
        if (completed != null && (position - completed.position).getDistance() <= dwellMovementThreshold) return null
        // Register only enabled actions. The topmost submenu is composed above the parent.
        return rows.asReversed().firstOrNull { it.contains(position) }?.takeUnless { it === completed?.row }
    }
}

internal class MenuDragSelectionLayer(
    onLayoutChanged: () -> Unit,
    val logicalOrigin: () -> Offset?,
    drawModifier: (MenuDragSelectionLayer) -> Modifier,
) {
    var coordinates: LayoutCoordinates? = null
        private set

    val modifier = Modifier.onGloballyPositioned {
        coordinates = it
        onLayoutChanged()
    }.then(drawModifier(this))
}

private data class MenuDragGeometry(
    val owner: MenuDragSelectionLayer,
    val bounds: Rect,
    val clipping: Rect?,
    val radius: Float,
)

private class MenuDragRow(
    val owner: MenuDragSelectionLayer,
    val select: () -> Unit,
    val navigationDelayMillis: () -> Long?,
    val cornerRadius: (Size) -> Float,
) {
    var coordinates: LayoutCoordinates? = null

    fun contains(position: Offset): Boolean {
        val layout = coordinates?.takeIf { it.isAttached } ?: return false
        val origin = owner.logicalOrigin()
        val container = owner.coordinates?.takeIf { it.isAttached }
        if (origin != null && container != null) {
            // The list's model geometry drives scrubbing even while its surface flexes.
            return container.localBoundingBoxOf(layout, clipBounds = true).contains(position - origin)
        }
        val local = layout.screenToLocal(position)
        if (local.x < 0f || local.y < 0f ||
            local.x >= layout.size.width || local.y >= layout.size.height
        ) return false
        // The row can remain measured outside a menu's scroll viewport.
        return local.isFinite && layout.boundsInWindow().contains(layout.localToWindow(local))
    }

    fun geometry(): MenuDragGeometry? {
        val container = owner.coordinates?.takeIf { it.isAttached } ?: return null
        val row = coordinates?.takeIf { it.isAttached } ?: return null
        val bounds = container.localBoundingBoxOf(row, clipBounds = false)
        val visible = container.localBoundingBoxOf(row, clipBounds = true)
        if (bounds.isEmpty || visible.isEmpty || !bounds.topLeft.isFinite || !bounds.bottomRight.isFinite) return null
        val clip = if (visible != bounds) {
            // Clip at the list viewport's edge, not to the current row on all four sides.
            Rect(
                if (visible.left > bounds.left) visible.left else 0f,
                if (visible.top > bounds.top) visible.top else 0f,
                if (visible.right < bounds.right) visible.right else container.size.width.toFloat(),
                if (visible.bottom < bounds.bottom) visible.bottom else container.size.height.toFloat(),
            )
        } else null
        return MenuDragGeometry(owner, bounds, clip, cornerRadius(bounds.size))
    }
}

private class MenuDragHighlight {
    var owner: MenuDragSelectionLayer? by mutableStateOf(null)
        private set
    var clipping: Rect? by mutableStateOf(null)
        private set
    val bounds = Animatable(Rect.Zero, Rect.VectorConverter)
    val radius = Animatable(0f)
    val alpha = Animatable(0f)

    suspend fun retarget(geometry: MenuDragGeometry?, scope: CoroutineScope) {
        if (geometry == null) {
            if (alpha.targetValue != 0f) scope.launch(start = CoroutineStart.UNDISPATCHED) {
                alpha.animateTo(0f, SelectionFadeSpring)
                owner = null
            }
            return
        }
        // A menu has one selection view. Reposition it immediately when the owning
        // list changes, then animate its next move within that list.
        if (owner !== geometry.owner || (alpha.value < 0.05f && alpha.targetValue == 0f)) {
            alpha.snapTo(0f)
            bounds.snapTo(geometry.bounds)
            radius.snapTo(geometry.radius)
            owner = geometry.owner
        }
        clipping = geometry.clipping
        // Animatable takes the running velocity before it cancels the previous target.
        if (bounds.targetValue != geometry.bounds) scope.launch(start = CoroutineStart.UNDISPATCHED) {
            bounds.animateTo(geometry.bounds, SelectionBoundsSpring)
        }
        if (radius.targetValue != geometry.radius) scope.launch(start = CoroutineStart.UNDISPATCHED) {
            radius.animateTo(geometry.radius, SelectionRadiusSpring)
        }
        scope.updateAlpha(1f)
    }

    private fun CoroutineScope.updateAlpha(target: Float) {
        if (alpha.targetValue != target) launch(start = CoroutineStart.UNDISPATCHED) {
            alpha.animateTo(target, SelectionFadeSpring)
        }
    }
}

@Composable
internal fun ProvideMenuDragTargets(
    drag: GlasmioMenuDragSelection,
    isReady: () -> Boolean,
    content: @Composable () -> Unit,
) {
    val latestReady = rememberUpdatedState(isReady)
    val dwellMovementThreshold = with(LocalDensity.current) { 4.dp.toPx() }
    val panelMotion = remember(drag) { MenuPanelMotion(drag) }
    val targets = remember(drag, dwellMovementThreshold) {
        MenuDragTargets(drag, dwellMovementThreshold) { latestReady.value() }
    }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(targets, haptics) {
        snapshotFlow { (drag.position != null) to targets.hovered.value }.collectLatest { (dragging, row) ->
            if (!dragging) targets.clearDwell()
            if (row != null) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                snapshotFlow(row.navigationDelayMillis).collectLatest { timeout ->
                    if (timeout != null) {
                        delay(timeout)
                        targets.navigate(row)
                    }
                }
            }
        }
    }
    LaunchedEffect(targets) {
        snapshotFlow {
            targets.hovered.value?.let { row ->
                targets.layoutRevision
                row.geometry()
            }
        }.collect { geometry ->
            targets.highlight.retarget(geometry, this)
        }
    }
    DisposableEffect(drag, targets) {
        val release = targets::releaseAt
        drag.onRelease = release
        onDispose {
            if (drag.onRelease === release) drag.onRelease = null
        }
    }
    CompositionLocalProvider(
        LocalMenuDragTargets provides targets,
        LocalMenuPanelMotion provides panelMotion,
        content = content,
    )
}

@Composable
internal fun rememberMenuDragSelectionLayer(logicalOrigin: () -> Offset?): MenuDragSelectionLayer? {
    val targets = LocalMenuDragTargets.current ?: return null
    val color = rememberUpdatedState(menuSelectionColor())
    val latestOrigin = rememberUpdatedState(logicalOrigin)
    return remember(targets) {
        val highlight = targets.highlight
        MenuDragSelectionLayer(targets::onLayoutChanged, { latestOrigin.value() }) { layer ->
            val outlineGeometry = derivedStateOf {
                if (highlight.owner === layer) highlight.bounds.value.size to highlight.radius.value else null
            }
            Modifier.drawWithCache {
                val outline = outlineGeometry.value?.let { (size, radius) ->
                    if (size.isEmpty()) null
                    else ContinuousRoundedRectangle(radius.coerceIn(0f, size.minDimension / 2f))
                        .createOutline(size, layoutDirection, this)
                }
                onDrawBehind {
                    if (outline == null || highlight.owner !== layer) return@onDrawBehind
                    val tint = color.value
                    val alpha = highlight.alpha.value * tint.alpha
                    if (alpha <= 0f) return@onDrawBehind
                    val offset = highlight.bounds.value.topLeft
                    val clip = highlight.clipping ?: Rect(Offset.Zero, size)
                    clipRect(clip.left, clip.top, clip.right, clip.bottom) {
                        translate(offset.x, offset.y) { drawOutline(outline, tint.copy(alpha = alpha)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MenuDragSelectionContent(layer: MenuDragSelectionLayer?, content: @Composable () -> Unit) {
    if (layer == null) content()
    else CompositionLocalProvider(LocalMenuDragSurface provides layer, content = content)
}

@Composable
internal fun Modifier.menuPanelDrag(motion: MenuPanelMotion): Modifier {
    val drag = motion.drag
    val coordinates = remember { MenuPanelDragCoordinates() }
    return onGloballyPositioned { coordinates.layout = it }.pointerInput(drag) {
        val hysteresis = 10.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type != PointerType.Touch && down.type != PointerType.Stylus) return@awaitEachGesture
            val origin = coordinates.screenPosition(down.position) ?: return@awaitEachGesture
            val token = Any()
            if (!drag.acquire(token, origin)) return@awaitEachGesture
            var tracking = false
            try {
                while (true) {
                    // Let an overflowing list win before starting action scrubbing.
                    val changes = awaitPointerEvent(
                        if (tracking) PointerEventPass.Initial else PointerEventPass.Final,
                    ).changes
                    val pointer = changes.firstOrNull { it.id == down.id } ?: break
                    if (changes.any { it.id != down.id && it.pressed }) break
                    val point = coordinates.screenPosition(pointer.position) ?: break
                    if (!pointer.pressed) {
                        if (tracking && !pointer.isConsumed) {
                            pointer.consume()
                            drag.finish(token, point)
                        }
                        break
                    }
                    if (pointer.previousPressed && pointer.isConsumed) break
                    if (tracking || (point - origin).getDistance() > hysteresis) {
                        pointer.consume()
                        tracking = true
                        drag.move(token, point)
                    }
                }
            } finally {
                drag.finish(token)
            }
        }
    }
}

private class MenuPanelDragCoordinates {
    var layout: LayoutCoordinates? = null

    fun screenPosition(offset: Offset): Offset? =
        layout?.takeIf { it.isAttached }?.localToScreen(offset)?.takeIf { it.isFinite }
}

@Composable
internal fun Modifier.menuDragTarget(
    enabled: Boolean,
    onClick: () -> Unit,
    shape: Shape,
    pressProgress: () -> Float,
): Modifier {
    val targets = LocalMenuDragTargets.current
    val surface = LocalMenuDragSurface.current
    val positioned = if (targets != null && surface != null && enabled && LocalMenuItemDragSelectionEnabled.current) {
        val latestClick = rememberUpdatedState(onClick)
        val latestDelay = rememberUpdatedState(LocalMenuItemAutoNavigationDelayMillis.current)
        val density = LocalDensity.current
        val target = remember(targets, surface, shape, density) {
            MenuDragRow(surface, { latestClick.value() }, { latestDelay.value }) { size ->
                shape.selectionRadius(size, density)
            }
        }
        DisposableEffect(targets, target) {
            targets.rows.add(target)
            onDispose { targets.rows.remove(target) }
        }
        onGloballyPositioned {
            target.coordinates = it
            targets.onLayoutChanged()
        }
    } else this
    val color = menuSelectionColor()
    return positioned.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind {
            val progress = pressProgress()
            if (progress > 0f) drawOutline(outline, color.copy(alpha = color.alpha * progress))
        }
    }
}

private fun Shape.selectionRadius(size: Size, density: Density): Float = when (this) {
    is Capsule -> size.minDimension / 2f
    is CornerBasedShape -> topStart.toPx(size, density).coerceIn(0f, size.minDimension / 2f)
    else -> 0f
}
