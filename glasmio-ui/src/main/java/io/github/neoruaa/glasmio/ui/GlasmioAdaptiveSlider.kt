package io.github.neoruaa.glasmio.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCombinedBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.SfIcon
import kotlin.math.abs
import kotlin.math.roundToInt
import java.text.NumberFormat

/** A continuous adaptive slider with local gesture preview and explicit release ownership. */
@Composable
fun GlasmioAdaptiveSlider(
    value: Float?,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    deferSelection: Boolean = false,
    onValueChangeFinished: (() -> Unit)? = null,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    showLabels: Boolean = false,
    labels: List<String> = listOf("0%", "50%", "100%"),
    captionOverride: String? = null,
    discrete: Boolean = false,
    thumbContent: (@Composable () -> Unit)? = null,
) = AdaptiveSegmentedRenderer(
    items = listOf(Unit to ""), selected = Unit, onSelected = {}, adaptiveItem = Unit,
    adaptiveValue = value, onAdaptiveValueChange = onValueChange, modifier = modifier,
    enabled = enabled, adaptiveEnabled = enabled, deferSelection = deferSelection,
    hiddenItems = emptySet(), onAdaptiveValueChangeFinished = onValueChangeFinished,
    backdrop = backdrop, showLabels = showLabels, captionOverride = captionOverride,
    adaptiveLabels = labels, itemContent = null, thumbContent = thumbContent, standalone = true,
    discrete = discrete,
)

/**
 * A segmented control whose confirmed adaptive item expands into a three-cell slider.
 * Main-item and adaptive-value callbacks are independent. [hiddenItems] is a separate capability
 * input; selecting the adaptive item never hides another item implicitly.
 */
@Composable
fun <T> GlasmioSegmentedControl(
    items: List<Pair<T, String>>,
    selected: T?,
    onSelected: (T) -> Unit,
    adaptiveItem: T,
    adaptiveValue: Float?,
    onAdaptiveValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    adaptiveEnabled: Boolean = enabled,
    deferSelection: Boolean = false,
    hiddenItems: Set<T> = emptySet(),
    onAdaptiveValueChangeFinished: (() -> Unit)? = null,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    showLabels: Boolean = false,
    captionOverride: String? = null,
    adaptiveLabels: List<String> = listOf("0%", "50%", "100%"),
    itemContent: (@Composable BoxScope.(value: T, selected: Boolean) -> Unit)? = null,
    adaptiveDiscrete: Boolean = false,
    thumbContent: (@Composable () -> Unit)? = null,
) = AdaptiveSegmentedRenderer(
    items, selected, onSelected, adaptiveItem, adaptiveValue, onAdaptiveValueChange, modifier,
    enabled, adaptiveEnabled, deferSelection, hiddenItems, onAdaptiveValueChangeFinished,
    backdrop, showLabels, captionOverride, adaptiveLabels, itemContent, thumbContent, false, adaptiveDiscrete,
)

private fun adaptiveInteractionValue(value: Float, discrete: Boolean): Float = when {
    !discrete -> value
    value < .31f -> 0f
    value > .7f -> 1f
    else -> .5f
}

private val AdaptiveExpansionSpring = LiquidMenuSpringSpec(.7f, .5f)
private val AdaptiveVisibilitySpring = LiquidMenuSpringSpec(.7f, .7f)
private val AdaptiveSnapSpring = LiquidMenuSpringSpec(.7f, .3f)
private val AdaptiveScaleSpring = LiquidMenuSpringSpec(.5f, .2f)
// The native interactiveSpring uses blendDuration .25. This oscillator preserves velocity;
// it does not emulate SwiftUI's parameter-blending transaction.
private val AdaptiveReleaseSpring = LiquidMenuSpringSpec(.86f, .15f)

private class AdaptiveSpring(initial: Float, private val defaultSpec: LiquidMenuSpringSpec) {
    private val oscillator = LiquidMenuSpring(.0005f, .005f).apply { reset(initial) }
    var value by mutableFloatStateOf(initial)
        private set
    var target by mutableFloatStateOf(initial)
        private set
    val running: Boolean get() = value != target || !oscillator.isSettled
    fun target(next: Float, spec: LiquidMenuSpringSpec = defaultSpec) {
        if (target == next) return
        target = next
        oscillator.target(next, spec)
    }
    fun snap(next: Float) {
        target = next
        oscillator.reset(next)
        value = next
    }
    fun advance(seconds: Float) { oscillator.advance(seconds); value = oscillator.value }
}

private data class AdaptiveCell(val left: Float, val width: Float) { val center get() = left + width / 2f }
private data class AdaptiveGeometry(val width: Float, val unit: Float, val cells: List<AdaptiveCell>)

private fun adaptiveGeometry(width: Float, weights: List<Float>): AdaptiveGeometry {
    val unit = width / weights.sum().coerceAtLeast(.001f)
    var left = 0f
    return AdaptiveGeometry(width, unit, weights.map { weight ->
        AdaptiveCell(left, unit * weight).also { left += it.width }
    })
}

private suspend fun AwaitPointerEventScope.awaitAdaptiveSlop(
    down: PointerInputChange,
    distance: Float,
    onSlop: (PointerInputChange, Offset) -> Unit,
): PointerInputChange? {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return null
        if (change.isConsumed || !change.pressed) return null
        val travel = change.position - down.position
        if (travel.getDistance() >= distance) {
            onSlop(change, travel)
            return change.takeIf { it.isConsumed }
        }
    }
}

@Composable
private fun <T> AdaptiveSegmentedRenderer(
    items: List<Pair<T, String>>, selected: T?, onSelected: (T) -> Unit,
    adaptiveItem: T, adaptiveValue: Float?, onAdaptiveValueChange: (Float) -> Unit,
    modifier: Modifier, enabled: Boolean, adaptiveEnabled: Boolean, deferSelection: Boolean,
    hiddenItems: Set<T>, onAdaptiveValueChangeFinished: (() -> Unit)?, backdrop: Backdrop,
    showLabels: Boolean, captionOverride: String?, adaptiveLabels: List<String>,
    itemContent: (@Composable BoxScope.(T, Boolean) -> Unit)?, thumbContent: (@Composable () -> Unit)?,
    standalone: Boolean, discrete: Boolean,
) {
    require(items.isNotEmpty() && items.map { it.first }.distinct().size == items.size)
    require(items.any { it.first == adaptiveItem } && items.any { it.first !in hiddenItems })
    require(adaptiveValue == null || adaptiveValue.isFinite()) { "Adaptive value must be finite or null." }
    require(adaptiveLabels.size == 3) { "Adaptive labels must name all three detents." }
    val actualValue = adaptiveValue?.coerceIn(0f, 1f)
    val adaptiveIndex = items.indexOfFirst { it.first == adaptiveItem }
    val selectedIndex = if (selected == null || selected in hiddenItems) -1 else items.indexOfFirst { it.first == selected }
    val expanded = standalone || selectedIndex == adaptiveIndex
    val colors = GlasmioTheme.colors
    val density = LocalDensity.current
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val expansion = remember { AdaptiveSpring(if (expanded) 1f else 0f, AdaptiveExpansionSpring) }
    val position = remember { AdaptiveSpring(selectedIndex.coerceAtLeast(0).toFloat(), AdaptiveSnapSpring) }
    val ordinaryOpacity = remember { AdaptiveSpring(if (selectedIndex >= 0 && !expanded) 1f else 0f, AdaptiveExpansionSpring) }
    var lastOrdinaryIndex by remember { mutableIntStateOf(if (selectedIndex >= 0 && !expanded) selectedIndex else -1) }
    val child = remember { AdaptiveSpring(actualValue ?: .5f, AdaptiveSnapSpring) }
    val scale = remember { AdaptiveSpring(1f, AdaptiveScaleSpring) }
    val restingFill = remember { AdaptiveSpring(.8f, AdaptiveSnapSpring) }
    val visibility = remember { mutableMapOf<T, AdaptiveSpring>() }
    items.forEach { (key, _) -> visibility.getOrPut(key) { AdaptiveSpring(if (key in hiddenItems) 0f else 1f, AdaptiveVisibilitySpring) } }
    var dragKind by remember { mutableIntStateOf(0) }
    var draft by remember { mutableFloatStateOf(actualValue ?: .5f) }
    var childPreview by remember { mutableStateOf<Float?>(null) }
    var lastPreview by remember { mutableStateOf<Float?>(null) }
    val childVisible = actualValue != null || dragKind == 1 || childPreview != null
    val displayedValue = if (dragKind == 1) draft else childPreview ?: actualValue
    val currentValue by rememberUpdatedState(actualValue)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentAdaptiveEnabled by rememberUpdatedState(enabled && adaptiveEnabled)
    val currentExpanded by rememberUpdatedState(expanded)
    val currentDefer by rememberUpdatedState(deferSelection)
    val currentDiscrete by rememberUpdatedState(discrete)
    val currentOnSelected by rememberUpdatedState(onSelected)
    val currentOnAdaptive by rememberUpdatedState(onAdaptiveValueChange)
    val currentOnFinished by rememberUpdatedState(onAdaptiveValueChangeFinished)
    val currentItems by rememberUpdatedState(items)
    val currentHidden by rememberUpdatedState(hiddenItems)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    var previousValue by remember { mutableStateOf(actualValue) }
    LaunchedEffect(actualValue) {
        childPreview = null
        lastPreview = null
        if (dragKind != 1 && actualValue != null) {
            if (previousValue == null) child.snap(actualValue) else child.target(actualValue)
        }
        previousValue = actualValue
    }
    LaunchedEffect(selectedIndex, enabled, hiddenItems) {
        if (!enabled) { childPreview = null; dragKind = 0; scale.target(1f) }
        if (selectedIndex >= 0 && selectedIndex != adaptiveIndex) {
            if (ordinaryOpacity.value < .001f) position.snap(selectedIndex.toFloat())
            else position.target(selectedIndex.toFloat())
        }
    }
    SideEffect {
        expansion.target(if (expanded) 1f else 0f)
        items.forEach { (key, _) -> visibility.getValue(key).target(if (key in hiddenItems) 0f else 1f) }
        if (selectedIndex >= 0 && selectedIndex != adaptiveIndex) {
            lastOrdinaryIndex = selectedIndex
        }
        ordinaryOpacity.target(if (selectedIndex >= 0 && selectedIndex != adaptiveIndex) 1f else 0f)
        if (dragKind != 1) (childPreview ?: actualValue)?.let { child.target(it) }
        restingFill.target(if (dragKind != 0) 0f else .8f)
    }
    val source = rememberLayerBackdrop()
    val combined = rememberCombinedBackdrop(backdrop, source)
    val trackColor = colors.systemGray5
    BoxWithConstraints(modifier.swipeBackExcluded(enabled).fillMaxWidth().then(if (standalone) Modifier.semantics(mergeDescendants = true) { } else Modifier)) {
        val weights = items.map { (key, _) ->
            if (key in hiddenItems) 0f else if (key == adaptiveItem && expanded) 3f else 1f
        }
        val targetGeometry = adaptiveGeometry(constraints.maxWidth.toFloat(), weights)
        val unit = remember { AdaptiveSpring(targetGeometry.unit, AdaptiveExpansionSpring) }
        val bounds = remember { mutableMapOf<T, Pair<AdaptiveSpring, AdaptiveSpring>>() }
        items.forEachIndexed { index, (key, _) -> bounds.getOrPut(key) {
            AdaptiveSpring(targetGeometry.cells[index].left, AdaptiveVisibilitySpring) to
                AdaptiveSpring(targetGeometry.cells[index].width, if (key == adaptiveItem) AdaptiveExpansionSpring else AdaptiveVisibilitySpring)
        } }
        SideEffect {
            unit.target(targetGeometry.unit)
            items.forEachIndexed { index, (key, _) ->
                bounds.getValue(key).first.target(targetGeometry.cells[index].left)
                bounds.getValue(key).second.target(targetGeometry.cells[index].width)
            }
        }
        val tracks = listOf(expansion, ordinaryOpacity, position, child, scale, restingFill, unit) +
            items.flatMap { (key, _) -> bounds.getValue(key).let { listOf(visibility.getValue(key), it.first, it.second) } }
        val running = tracks.any { it.running }
        LaunchedEffect(running, tracks) {
            if (!running) return@LaunchedEffect
            var previous = withFrameNanos { it }
            while (tracks.any { it.running }) {
                val frame = withFrameNanos { it }
                val seconds = (frame - previous) / 1_000_000_000f
                tracks.forEach { it.advance(seconds) }
                previous = frame
            }
        }
        val geometry = AdaptiveGeometry(targetGeometry.width, unit.value,
            items.map { (key, _) -> bounds.getValue(key).let { AdaptiveCell(it.first.value, it.second.value.coerceAtLeast(0f)) } })
        val currentGeometry by rememberUpdatedState(geometry)
        val currentAdaptiveIndex by rememberUpdatedState(adaptiveIndex)
        val currentLtr by rememberUpdatedState(ltr)
        fun childCenter(): Float = geometry.cells[adaptiveIndex].left + geometry.unit * (.5f + 2f * child.value)
        val centers = items.indices.filter { it != adaptiveIndex && items[it].first !in hiddenItems }
            .map { it to geometry.cells[it].center }.ifEmpty { listOf(adaptiveIndex to geometry.cells[adaptiveIndex].center) }
        val before = centers.lastOrNull { it.first <= position.value } ?: centers.first()
        val after = centers.firstOrNull { it.first >= position.value } ?: centers.last()
        val center = if (before.first == after.first) before.second else lerp(before.second, after.second,
            ((position.value - before.first) / (after.first - before.first)).coerceIn(0f, 1f))
        val thumbWidth = (geometry.unit - with(density) { 4.dp.toPx() }).coerceAtLeast(1f)
        val ordinaryLeft = center - thumbWidth / 2f
        val childLeft = childCenter() - thumbWidth / 2f
        val physicalLeft = if (ltr) childLeft else geometry.width - childLeft - thumbWidth
        val ordinaryPhysicalLeft = if (ltr) ordinaryLeft else geometry.width - ordinaryLeft - thumbWidth
        val showChildThumb = expansion.value > .0001f && childVisible && adaptiveItem !in hiddenItems
        val submitChild by rememberUpdatedState<(Float) -> Boolean> { requested ->
            if (!currentAdaptiveEnabled || !currentExpanded || !requested.isFinite()) false else {
                val next = adaptiveInteractionValue(requested.coerceIn(0f, 1f), currentDiscrete)
                childPreview = if (currentDefer) null else next
                val previous = if (currentDiscrete) lastPreview ?: currentValue else lastPreview
                lastPreview = next
                if (next != previous) currentOnAdaptive(next)
                currentOnFinished?.invoke()
                val resting = if (currentDefer) currentValue else next
                resting?.let { child.target(it, if (currentDefer) AdaptiveReleaseSpring else AdaptiveSnapSpring) }
                true
            }
        }
        fun submitMode(index: Int) {
            val item = currentItems.getOrNull(index) ?: return
            if (currentEnabled && item.first !in currentHidden) currentOnSelected(item.first)
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                Modifier.fillMaxWidth().height(56.dp).alpha(if (enabled) 1f else .45f)
                    .pointerInput(enabled, adaptiveEnabled, ltr, adaptiveItem, discrete) {
                        if (!enabled) return@pointerInput
                        fun logical(x: Float) = if (currentLtr) x else size.width - x
                        fun indexAt(x: Float): Int {
                            val visible = currentItems.indices.filter { currentItems[it].first !in currentHidden }
                            return visible.firstOrNull { index ->
                                val cell = currentGeometry.cells[index]
                                x >= cell.left && x < cell.left + cell.width
                            } ?: if (x <= currentGeometry.cells[visible.first()].left) visible.first() else visible.last()
                        }
                        fun updateChild(x: Float, emit: Boolean = true) {
                            val g = currentGeometry
                            val cell = g.cells[currentAdaptiveIndex]
                            draft = adaptiveInteractionValue((logical(x) - cell.left) / cell.width.coerceAtLeast(1f), currentDiscrete)
                            child.target(draft, if (currentDiscrete) AdaptiveSnapSpring else AdaptiveReleaseSpring)
                            val bounded = draft.coerceIn(0f, 1f)
                            if (emit) {
                                val previous = if (currentDiscrete) lastPreview ?: currentValue else lastPreview
                                lastPreview = bounded
                                if (bounded != previous) currentOnAdaptive(bounded)
                            }
                        }
                        fun neighborAt(x: Float): Int? = when {
                            standalone -> null
                            x < with(density) { 20.dp.toPx() } -> currentItems.indices.lastOrNull { it < currentAdaptiveIndex && currentItems[it].first !in currentHidden }
                            x > currentGeometry.width - with(density) { 20.dp.toPx() } -> currentItems.indices.firstOrNull { it > currentAdaptiveIndex && currentItems[it].first !in currentHidden }
                            else -> null
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val initialIndex = indexAt(logical(down.position.x))
                            val childGesture = initialIndex == currentAdaptiveIndex && currentExpanded
                            val modeGesture = !standalone && !currentExpanded && initialIndex == currentSelectedIndex
                            val modeStartCenter = currentGeometry.cells[initialIndex].center
                            var modeTarget = initialIndex
                            fun updateMode(x: Float) {
                                val center = modeStartCenter + logical(x) - logical(down.position.x)
                                val visible = currentItems.indices.filter { currentItems[it].first !in currentHidden }
                                val before = visible.lastOrNull { currentGeometry.cells[it].center <= center } ?: visible.first()
                                val after = visible.firstOrNull { currentGeometry.cells[it].center >= center } ?: visible.last()
                                val startCenter = currentGeometry.cells[before].center
                                val endCenter = currentGeometry.cells[after].center
                                val progress = if (before == after) 0f else ((center - startCenter) / (endCenter - startCenter)).coerceIn(0f, 1f)
                                position.snap(lerp(before.toFloat(), after.toFloat(), progress))
                                modeTarget = indexAt(center)
                            }
                            var lastX = down.position.x
                            var vertical = false
                            var exceededSlop = false
                            var completed = false
                            var routedMode: Int? = null
                            lastPreview = null
                            try {
                                val start = awaitAdaptiveSlop(down, with(density) { 10.dp.toPx() }) { change, over ->
                                    exceededSlop = true
                                    if (!vertical && abs(over.x) > abs(over.y)) {
                                        if (currentEnabled && childGesture && currentAdaptiveEnabled) {
                                            change.consume()
                                            dragKind = 1
                                            lastX = change.position.x
                                            scale.target(1.3f)
                                            val target = neighborAt(logical(change.position.x))
                                            if (target == null) updateChild(change.position.x)
                                            else { routedMode = target; submitMode(target) }
                                        } else if (currentEnabled && modeGesture) {
                                            change.consume()
                                            dragKind = 2
                                            lastX = change.position.x
                                            updateMode(lastX)
                                        }
                                    } else vertical = true
                                }
                                if (start != null && dragKind != 0) {
                                    val ended = drag(start.id) { change ->
                                        change.consume()
                                        lastX = change.position.x
                                        if (dragKind == 2) updateMode(lastX)
                                        else if (routedMode == null && currentExpanded && currentAdaptiveEnabled) {
                                            val target = neighborAt(logical(change.position.x))
                                            if (target == null) updateChild(change.position.x)
                                            else if (routedMode != target) { routedMode = target; submitMode(target) }
                                        }
                                    }
                                    if (ended) {
                                        lastX = currentEvent.changes.firstOrNull { !it.pressed }?.position?.x ?: lastX
                                        currentEvent.changes.forEach { if (!it.pressed) it.consume() }
                                        if (dragKind == 2) {
                                            updateMode(lastX)
                                            submitMode(modeTarget)
                                        } else if (routedMode == null && currentExpanded && currentAdaptiveEnabled) {
                                            updateChild(lastX)
                                            submitChild(draft.coerceIn(0f, 1f))
                                        }
                                        completed = true
                                    }
                                } else if (!vertical && !exceededSlop && currentEnabled) {
                                    val up = currentEvent.changes.firstOrNull { it.id == down.id }
                                    if (up?.changedToUpIgnoreConsumed() == true && !up.isConsumed &&
                                        (!childGesture || currentAdaptiveEnabled)) {
                                        up.consume()
                                        if (childGesture) {
                                            updateChild(up.position.x, emit = false)
                                            submitChild(draft)
                                        }
                                        else submitMode(indexAt(logical(up.position.x)))
                                        completed = true
                                    }
                                }
                            } finally {
                                val modeDrag = dragKind == 2
                                dragKind = 0
                                scale.target(1f)
                                if (modeDrag) {
                                    val resting = if (completed && !currentDefer) modeTarget else currentSelectedIndex
                                    if (resting >= 0) position.target(resting.toFloat(), AdaptiveReleaseSpring)
                                }
                                if (!currentDefer && completed && childGesture && routedMode == null && currentAdaptiveEnabled)
                                    child.target(draft.coerceIn(0f, 1f), AdaptiveReleaseSpring)
                                if (!completed && childGesture && routedMode == null && lastPreview != null && currentAdaptiveEnabled) currentValue?.let {
                                    if (it != lastPreview && (!currentDiscrete || it in listOf(0f, .5f, 1f))) currentOnAdaptive(it)
                                }
                                if (currentDefer || !completed || !currentAdaptiveEnabled) {
                                    currentValue?.let { child.target(it, AdaptiveReleaseSpring) }
                                    if (!modeDrag && currentSelectedIndex >= 0 && currentSelectedIndex != currentAdaptiveIndex)
                                        position.target(currentSelectedIndex.toFloat())
                                }
                            }
                        }
                    },
            ) {
                Box(Modifier.fillMaxSize().clip(Capsule()).background(trackColor))
                Box(
                    Modifier.fillMaxSize().clearAndSetSemantics { }.alpha(0f).layerBackdrop(source)
                        .clip(Capsule()).background(trackColor),
                ) {
                    AdaptiveContents(items, selectedIndex, adaptiveIndex, geometry, expansion.value,
                        itemContent, thumbContent, Modifier.fillMaxSize())
                }
                // Ordinary cells share a matched background pill. The adaptive glass thumb is separate.
                if (lastOrdinaryIndex >= 0 && items.getOrNull(lastOrdinaryIndex)?.first !in hiddenItems && ordinaryOpacity.value > .0001f) {
                    Box(Modifier.align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset { androidx.compose.ui.unit.IntOffset(ordinaryPhysicalLeft.roundToInt(), with(density) { 2.dp.roundToPx() }) }
                        .width(with(density) { thumbWidth.toDp() }).height(52.dp)
                        .alpha(ordinaryOpacity.value.coerceIn(0f, 1f)).clip(Capsule()).background(colors.elevatedBackground))
                }
                AdaptiveContents(items, selectedIndex, adaptiveIndex, geometry, expansion.value,
                    itemContent, thumbContent, Modifier.fillMaxSize().clearAndSetSemantics { })
                AdaptiveCells(geometry, Modifier.fillMaxSize().clearAndSetSemantics { }) { index ->
                    if (index == selectedIndex && index != adaptiveIndex) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (itemContent != null) itemContent(items[index].first, true)
                        else Text(items[index].second, style = GlasmioTheme.textStyles.caption1, color = colors.content)
                    }
                }
                if (expanded) AdaptiveCells(geometry, Modifier.fillMaxSize().clearAndSetSemantics { }) { index ->
                    if (index == adaptiveIndex) Box(Modifier.fillMaxSize().alpha((1f - expansion.value).coerceIn(0f, 1f)),
                        contentAlignment = Alignment.Center) { AdaptiveThumbGlyph(thumbContent) }
                }
                if (showChildThumb) {
                    val thumbModifier = Modifier.align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset { androidx.compose.ui.unit.IntOffset(physicalLeft.roundToInt(), with(density) { 2.dp.roundToPx() }) }
                        .width(with(density) { thumbWidth.toDp() }).height(52.dp)
                        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                        .clearAndSetSemantics { }
                    GlassSurface(
                        modifier = thumbModifier.alpha(expansion.value.coerceIn(0f, 1f)),
                        backdrop = combined, shape = Capsule(), material = NavigationGlassMaterial.Clear,
                        navigationSurfaceColor = null, onClick = null,
                        pressHighlight = null,
                    ) {
                        Box(Modifier.fillMaxSize().clip(Capsule()).background(Color.White.copy(alpha = restingFill.value.coerceIn(0f, 1f))))
                        AdaptiveThumbGlyph(thumbContent, Color.Black)
                    }
                }
                AdaptiveCells(geometry, Modifier.fillMaxSize()) { index ->
                    val item = items[index]
                    if (item.first !in hiddenItems) {
                        val isAdaptive = index == adaptiveIndex && expanded
                        Box(Modifier.fillMaxSize()
                            .semantics(mergeDescendants = true) {
                                if (!standalone) { role = Role.Tab; this.selected = index == selectedIndex }
                                if (item.second.isNotBlank()) contentDescription = item.second
                                if (!enabled || isAdaptive && !adaptiveEnabled) disabled()
                                if (isAdaptive) {
                                    progressBarRangeInfo = if (displayedValue == null) ProgressBarRangeInfo.Indeterminate
                                        else ProgressBarRangeInfo(displayedValue.coerceIn(0f, 1f), 0f..1f, if (discrete) 1 else 0)
                                    displayedValue?.let { stateDescription = NumberFormat.getPercentInstance().format(it.coerceIn(0f, 1f).toDouble()) }
                                    setProgress { submitChild(it) }
                                } else onClick { if (enabled) { submitMode(index); true } else false }
                            }
                            .onKeyEvent { event ->
                                if (!enabled || event.type != KeyEventType.KeyDown) false
                                else if (isAdaptive && adaptiveEnabled) {
                                    val current = displayedValue ?: .5f
                                    fun adjusted(increase: Boolean): Float = if (discrete) {
                                        if (increase) { if (current < .5f) .5f else 1f }
                                        else if (current > .5f) .5f else 0f
                                    } else current + if (increase) .1f else -.1f
                                    val next = when (event.key) {
                                        Key.DirectionLeft -> adjusted(!ltr)
                                        Key.DirectionRight -> adjusted(ltr)
                                        Key.DirectionUp -> adjusted(true)
                                        Key.DirectionDown -> adjusted(false)
                                        Key.MoveHome -> 0f
                                        Key.MoveEnd -> 1f
                                        else -> return@onKeyEvent false
                                    }
                                    submitChild(next)
                                } else if (event.key == Key.Enter || event.key == Key.Spacebar) {
                                    submitMode(index); true
                                } else false
                            }
                            .focusable(enabled && (!isAdaptive || adaptiveEnabled)))
                    }
                }
            }
            if (!captionOverride.isNullOrBlank()) {
                Text(captionOverride, style = GlasmioTheme.textStyles[IosTextStyle.Footnote, IosTextVariant.Emphasized],
                    color = colors.content, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
            } else if (showLabels) {
                if (standalone) Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
                    adaptiveLabels.forEach { Text(it, style = GlasmioTheme.textStyles.caption1, color = colors.content,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
                } else AdaptiveCaptions(geometry, targetGeometry, Modifier.fillMaxWidth().clearAndSetSemantics { }) { index ->
                    Text(items[index].second, style = GlasmioTheme.textStyles.caption1, color = colors.content,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().alpha(visibility.getValue(items[index].first).value.coerceIn(0f, 1f)))
                }
            }
        }
    }
}

@Composable
private fun AdaptiveCaptions(
    geometry: AdaptiveGeometry,
    targetGeometry: AdaptiveGeometry,
    modifier: Modifier,
    content: @Composable (Int) -> Unit,
) {
    Layout(modifier = modifier, content = { geometry.cells.indices.forEach { Box { content(it) } } }) { measurables, constraints ->
        val children = measurables.mapIndexed { index, child ->
            val targetWidth = targetGeometry.cells[index].width
            // Only placement animates: a disappearing caption must never wrap into a zero-width cell.
            val width = (if (targetWidth > 0f) targetWidth else targetGeometry.unit).roundToInt().coerceAtLeast(0)
            child.measure(constraints.copy(minWidth = width, maxWidth = width))
        }
        val height = children.indices.filter { targetGeometry.cells[it].width > 0f }
            .maxOfOrNull { children[it].height } ?: constraints.minHeight
        layout(constraints.maxWidth, height) {
            children.forEachIndexed { index, child ->
                child.placeRelative((geometry.cells[index].center - child.width / 2f).roundToInt(), 0)
            }
        }
    }
}

@Composable
private fun AdaptiveCells(geometry: AdaptiveGeometry, modifier: Modifier, content: @Composable (Int) -> Unit) {
    Layout(modifier = modifier, content = { geometry.cells.indices.forEach { Box { content(it) } } }) { measurables, constraints ->
        val children = measurables.mapIndexed { index, child ->
            child.measure(constraints.copy(minWidth = geometry.cells[index].width.roundToInt().coerceAtLeast(0),
                maxWidth = geometry.cells[index].width.roundToInt().coerceAtLeast(0)))
        }
        val height = children.maxOfOrNull { it.height } ?: constraints.minHeight
        layout(constraints.maxWidth, height) {
            children.forEachIndexed { index, child -> child.placeRelative(geometry.cells[index].left.roundToInt(), 0) }
        }
    }
}

@Composable
private fun <T> AdaptiveContents(
    items: List<Pair<T, String>>, selectedIndex: Int, adaptiveIndex: Int, geometry: AdaptiveGeometry,
    expansion: Float,
    itemContent: (@Composable BoxScope.(T, Boolean) -> Unit)?, thumbContent: (@Composable () -> Unit)?,
    modifier: Modifier,
) = AdaptiveCells(geometry, modifier) { index ->
    val colors = GlasmioTheme.colors
    Box(Modifier.fillMaxSize().alpha(if (geometry.cells[index].width > 0f) 1f else 0f), contentAlignment = Alignment.Center) {
        if (index == adaptiveIndex) {
            val alpha = expansion.coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize().alpha(alpha).clip(Capsule()).background(colors.systemGray4))
            if (index != selectedIndex) Box(Modifier.alpha((1f - expansion).coerceIn(0f, 1f))) { AdaptiveThumbGlyph(thumbContent) }
            AdaptiveGlyphAt(geometry.unit / 2f, Modifier.alpha(alpha)) { SfIcon("circle.dotted", null, size = 22.dp, tint = Color(0xFF8E8E93)) }
            AdaptiveGlyphAt(geometry.cells[index].width - geometry.unit / 2f, Modifier.alpha(alpha)) { SfIcon("circle", null, size = 22.dp, tint = Color(0xFF8E8E93)) }
        } else if (index != selectedIndex) {
            if (itemContent != null) itemContent(items[index].first, false)
            else Text(items[index].second, style = GlasmioTheme.textStyles.caption1, color = colors.content, maxLines = 1)
        }
    }
}

@Composable
private fun AdaptiveGlyphAt(center: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(modifier = modifier.fillMaxSize(), content = { Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) { content() } }) { measurables, constraints ->
        val frame = 58.dp.roundToPx()
        val child = measurables.single().measure(Constraints.fixed(frame, frame))
        layout(constraints.maxWidth, constraints.maxHeight) { child.placeRelative((center - frame / 2f).roundToInt(), (constraints.maxHeight - frame) / 2) }
    }
}

@Composable
private fun AdaptiveThumbGlyph(content: (@Composable () -> Unit)?, tint: Color = GlasmioTheme.colors.content) {
    if (content != null) content() else Image(
        painterResource(io.github.neoruaa.glasmio.icons.R.drawable.glasmio_adaptive_audio),
        // Native 22 pt symbol metrics produce 25 pt of ink; the padded asset needs 30 dp.
        contentDescription = null, modifier = Modifier.size(30.dp),
        colorFilter = ColorFilter.tint(tint),
    )
}
