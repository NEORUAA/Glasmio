package io.github.neoruaa.glasmio.ui

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.*

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.emptyBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCombinedBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.drawPlainBackdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.glass.DampedDragAnimation

internal val LocalMergedGlassCards = staticCompositionLocalOf { false }

/** Resize the rendered lens while retaining its resting layout and input bounds. */
internal fun Modifier.nativeControlLensGeometry(
    style: NativeControlLensStyle,
    horizontalProgress: () -> Float,
    verticalProgress: () -> Float = horizontalProgress,
): Modifier = layout { measurable, constraints ->
    val restingWidth = constraints.maxWidth
    val restingHeight = constraints.maxHeight
    val horizontal = horizontalProgress()
    val vertical = verticalProgress()
    val width = when (style) {
        NativeControlLensStyle.SwitchThumb -> restingWidth * (1f + (58f / 37f - 1f) * horizontal)
        NativeControlLensStyle.SliderThumb -> restingWidth * (1f + .55f * horizontal)
        NativeControlLensStyle.SegmentedIndicator -> restingWidth + 24.dp.toPx() * horizontal
        NativeControlLensStyle.TabBarIndicator -> restingWidth + 16.dp.toPx() * horizontal
    }.fastRoundToInt().coerceAtLeast(1)
    val height = when (style) {
        NativeControlLensStyle.SwitchThumb -> restingHeight * (1f + (38.333333f / 24f - 1f) * vertical)
        NativeControlLensStyle.SliderThumb -> restingHeight * (1f + .55f * vertical)
        NativeControlLensStyle.SegmentedIndicator,
        NativeControlLensStyle.TabBarIndicator -> restingHeight + 16.dp.toPx() * vertical
    }.fastRoundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(restingWidth, restingHeight) {
        placeable.place((restingWidth - width) / 2, (restingHeight - height) / 2)
    }
}

/**
 * iOS 27 switch: 63 x 28 dp track and 37 x 24 dp elastic liquid thumb.
 * The checked track defaults to system green; [onTintColor] provides an explicit override.
 */
@Composable
fun GlasmioSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    onTintColor: Color = Color.Unspecified,
) {
    val colors = LocalGlassColors.current
    val appearance = LocalGlassAppearance.current
    val light = !colors.isDark
    val onColor = if (onTintColor == Color.Unspecified) {
        if (colors.isDark) Color(0xFF30D158) else Color(0xFF34C759)
    } else onTintColor
    val track = (if (light) {
        Color(0xFF787878).copy(alpha = 0.20f)
    } else {
        Color(0xFF787880).copy(alpha = 0.36f)
    }).let {
        if (appearance.reduceTransparency) it.compositeOver(colors.elevatedBackground) else it
    }
    val density = LocalDensity.current
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val travelPx = with(density) { 22.dp.toPx() }
    val scope = rememberCoroutineScope()
    val currentEnabled by rememberUpdatedState(enabled)
    val currentChecked by rememberUpdatedState(checked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val animation = remember(scope, enabled, travelPx, ltr) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (!currentEnabled) return@DampedDragAnimation
                fraction = if (didDrag) {
                    if (fraction >= 0.5f) 1f else 0f
                } else if (currentChecked) 0f else 1f
                didDrag = false
                updateValue(fraction)
                currentOnCheckedChange(fraction == 1f)
            },
            onDrag = { _, dragAmount ->
                if (!currentEnabled) return@DampedDragAnimation
                didDrag = didDrag || dragAmount.x != 0f
                val delta = dragAmount.x / travelPx
                fraction = if (ltr) (fraction + delta).fastCoerceIn(0f, 1f)
                else (fraction - delta).fastCoerceIn(0f, 1f)
                updateValue(fraction)
            },
            consumeGestures = true,
            onDragCancelled = {
                didDrag = false
                fraction = if (currentChecked) 1f else 0f
                updateValue(fraction)
            },
            lensStyle = NativeControlLensStyle.SwitchThumb,
        )
    }
    LaunchedEffect(checked, enabled, animation) {
        didDrag = false
        val target = if (checked) 1f else 0f
        if (target != fraction || target != animation.targetValue) {
            fraction = target
            // Restored preferences and parent-row changes are not thumb presses.
            animation.animateToValue(target, animatePress = false)
        }
    }

    val trackBackdrop = rememberLayerBackdrop()
    val thumbBackdrop = rememberCombinedBackdrop(backdrop, trackBackdrop)
    val thumbGlassModifier = remember(thumbBackdrop, animation, appearance, colors.isDark, onColor) {
        Modifier.drawPlainBackdrop(
            backdrop = if (appearance.usesBackdrop) thumbBackdrop else emptyBackdrop(),
            shape = { Capsule() },
            effects = {
                if (appearance.usesBackdrop) {
                    nativeControlLens(
                        style = NativeControlLensStyle.SwitchThumb,
                        liftProgress = if (appearance.usesRefraction) animation.pressProgress else 0f,
                        isDark = colors.isDark,
                        tint = onColor,
                    )
                }
            },
            layerBlock = {
                // Velocity deformation is independent of the native lens's measured lift.
                val velocity = animation.velocity / 50f
                scaleX = 1f / (1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f))
                scaleY = 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
            },
            onDrawSurface = {
                if (appearance.reduceTransparency) drawRect(Color.White)
            },
        )
    }
    Box(
        modifier = modifier
            .swipeBackExcluded(enabled)
            .alpha(if (enabled) 1f else 0.5f)
            .size(width = 63.dp, height = 28.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                if (!enabled) disabled()
                onClick {
                    if (currentEnabled) currentOnCheckedChange(!currentChecked)
                    currentEnabled
                }
            }
            .then(if (enabled) animation.modifier else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .then(if (appearance.usesBackdrop) Modifier.layerBackdrop(trackBackdrop, reuseContent = true) else Modifier)
                .clip(Capsule())
                .drawBehind { drawRect(colorLerp(track, onColor, animation.value)) }
                .size(width = 63.dp, height = 28.dp),
        )
        Box(
            Modifier
                .graphicsLayer {
                    val padding = 2.dp.toPx()
                    translationX = if (ltr) lerp(padding, padding + travelPx, animation.value)
                    else lerp(-padding, -(padding + travelPx), animation.value)
                }
                .size(width = 37.dp, height = 24.dp)
                .nativeControlLensGeometry(
                    NativeControlLensStyle.SwitchThumb,
                    horizontalProgress = { if (appearance.usesRefraction) animation.pressProgress else 0f },
                )
                .then(thumbGlassModifier),
        )
    }
}

/**
 * iOS 27 liquid slider with immediate local drag feedback.
 * The caller owns [value]; use local preview state when persisting changes asynchronously.
 */
@Composable
fun GlasmioSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    require(value.isFinite()) { "Slider value must be finite." }
    require(valueRange.start.isFinite() && valueRange.endInclusive.isFinite() &&
        valueRange.start < valueRange.endInclusive &&
        (valueRange.endInclusive - valueRange.start).isFinite()) {
        "Slider range must have finite, increasing bounds and a finite span."
    }
    val colors = LocalGlassColors.current
    val appearance = LocalGlassAppearance.current
    val light = !colors.isDark
    val accent = colors.accent
    val track = (if (light) {
        Color(0xFF787878).copy(alpha = 0.20f)
    } else {
        Color(0xFF787880).copy(alpha = 0.36f)
    }).let {
        if (appearance.reduceTransparency) it.compositeOver(colors.elevatedBackground) else it
    }
    val trackBackdrop = rememberLayerBackdrop()
    val currentEnabled by rememberUpdatedState(enabled)
    val currentValue by rememberUpdatedState(value.coerceIn(valueRange))
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    var dragValue by remember(valueRange, enabled) {
        mutableFloatStateOf(value.coerceIn(valueRange))
    }
    var dragging by remember(valueRange, enabled) { mutableStateOf(false) }
    val displayedValue = if (dragging) dragValue else currentValue
    val updateValue by rememberUpdatedState<(Float) -> Unit> { next ->
        val target = next.coerceIn(valueRange)
        val previous = if (dragging) dragValue else currentValue
        if (dragging) dragValue = target
        if (target != previous) currentOnValueChange(target)
    }
    val progress = {
        (displayedValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    }

    BoxWithConstraints(
        modifier
            .swipeBackExcluded(enabled)
            .fillMaxWidth()
            .height(40.dp)
            .progressSemantics(displayedValue, valueRange)
            .semantics {
                if (!enabled) disabled()
                setProgress { requested ->
                    if (!enabled || !requested.isFinite()) {
                        false
                    } else {
                        val target = requested.coerceIn(valueRange)
                        if (target == displayedValue) {
                            false
                        } else {
                            updateValue(target)
                            currentOnValueChangeFinished?.invoke()
                            true
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidth = constraints.maxWidth
        val thumbWidthPx = with(LocalDensity.current) { 37.dp.toPx() }
        val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val currentTrackWidth by rememberUpdatedState(trackWidth)
        val currentThumbWidth by rememberUpdatedState(thumbWidthPx)
        val currentLtr by rememberUpdatedState(ltr)
        val scope = rememberCoroutineScope()
        val animation = remember(scope, valueRange, enabled) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = value.fastCoerceIn(valueRange.start, valueRange.endInclusive),
                valueRange = valueRange,
                visibilityThreshold = (valueRange.endInclusive - valueRange.start) / 1000f,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = {},
                onDrag = { _, _ -> },
                lensStyle = NativeControlLensStyle.SliderThumb,
            )
        }
        val updateFromPosition by rememberUpdatedState<(Float) -> Unit> { x ->
            val fraction = ((x - currentThumbWidth / 2f) /
                (currentTrackWidth - currentThumbWidth).coerceAtLeast(1f)).coerceIn(0f, 1f)
            updateValue(
                if (currentLtr) lerp(valueRange.start, valueRange.endInclusive, fraction)
                else lerp(valueRange.endInclusive, valueRange.start, fraction),
            )
        }
        val finishGesture by rememberUpdatedState {
            if (currentEnabled) currentOnValueChangeFinished?.invoke()
            dragging = false
            animation.release()
        }
        val thumbBackdrop = rememberCombinedBackdrop(backdrop, trackBackdrop)
        val thumbGlassModifier = remember(thumbBackdrop, animation, appearance, colors.isDark, accent) {
            Modifier.drawPlainBackdrop(
                backdrop = if (appearance.usesBackdrop) thumbBackdrop else emptyBackdrop(),
                shape = { Capsule() },
                effects = {
                    if (appearance.usesBackdrop) {
                        nativeControlLens(
                            style = NativeControlLensStyle.SliderThumb,
                            liftProgress = if (appearance.usesRefraction) animation.pressProgress else 0f,
                            isDark = colors.isDark,
                            tint = accent,
                        )
                    }
                },
                onDrawSurface = {
                    if (appearance.reduceTransparency) drawRect(Color.White)
                },
            )
        }
        Box(
            Modifier.then(if (appearance.usesBackdrop) Modifier.layerBackdrop(trackBackdrop, reuseContent = true) else Modifier)
                .fillMaxWidth(),
        ) {
            Box(
                Modifier
                    .clip(Capsule())
                    .background(track)
                    .height(6.dp)
                    .fillMaxWidth(),
            )
            Box(
                Modifier
                    .clip(Capsule())
                    .background(accent)
                    .height(6.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * progress()).fastRoundToInt()
                        layout(width, placeable.height) { placeable.place(0, 0) }
                    },
            )
        }
        Box(
            Modifier
                .graphicsLayer {
                    // Position follows the pointer directly; springs only deform the glass.
                    translationX = (trackWidth - size.width).coerceAtLeast(0f) * progress() * if (ltr) 1f else -1f
                    alpha = if (enabled) 1f else 0.45f
                }
                .size(width = 37.dp, height = 24.dp)
                .nativeControlLensGeometry(
                    NativeControlLensStyle.SliderThumb,
                    horizontalProgress = { if (appearance.usesRefraction) animation.pressProgress else 0f },
                )
                .then(thumbGlassModifier),
        )
        // Use a stationary input surface. Measuring deltas in the translated thumb's
        // coordinates subtracts its own movement and makes dragging feel damped.
        Box(
            Modifier.fillMaxSize()
                .pointerInput(enabled, valueRange, animation) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            animation.press()
                            tryAwaitRelease()
                            if (!dragging) animation.release()
                        },
                        onTap = { position ->
                            if (currentEnabled) {
                                updateFromPosition(position.x)
                                currentOnValueChangeFinished?.invoke()
                            }
                        },
                    )
                }
                .pointerInput(enabled, valueRange, animation) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { position ->
                            if (currentEnabled) {
                                dragValue = currentValue
                                dragging = true
                                animation.press()
                                updateFromPosition(position.x)
                            }
                        },
                        onDragEnd = { finishGesture() },
                        onDragCancel = { finishGesture() },
                        onHorizontalDrag = { change, _ ->
                            if (currentEnabled) updateFromPosition(change.position.x)
                        },
                    )
                },
        )
    }
}

@Composable
fun GlasmioCard(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") backdrop: Backdrop = LocalGlassBackdrop.current,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalGlassColors.current
    val merged = LocalMergedGlassCards.current
    Box(
        modifier = modifier
            .then(
                if (merged) {
                    Modifier.drawBehind {
                        drawLine(
                            colors.separator,
                            Offset(16.dp.toPx(), 0f),
                            Offset(size.width - 16.dp.toPx(), 0f),
                            1.dp.toPx(),
                        )
                    }
                } else {
                    Modifier
                        .clip(ContinuousRoundedRectangle(LocalGlassDimensions.current.regularCornerRadius))
                        .background(colors.elevatedBackground)
                },
            )
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ) else Modifier,
            ),
        content = {
            CompositionLocalProvider(
                LocalContentColor provides colors.content,
                LocalGlassContentColor provides colors.content,
            ) {
                content()
            }
        },
    )
}

/**
 * A compact glass selector. Labels remain the accessibility names when [itemContent] supplies icons.
 *
 * A null or unmatched [selected] leaves every item unselected. With [deferSelection], a drag submits
 * [onSelected] and returns to the confirmed [selected] until the caller updates it. Custom content
 * is composed in the visible track, exported optical source, and fading resting-surface overlay.
 */
@Composable
fun <T> GlasmioSegmentedControl(
    items: List<Pair<T, String>>,
    selected: T?,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    positionAnimation: Animatable<Float, AnimationVector1D>? = null,
    height: Dp = 32.dp,
    enabled: Boolean = true,
    deferSelection: Boolean = false,
    itemContent: (@Composable BoxScope.(value: T, selected: Boolean) -> Unit)? = null,
) {
    require(items.isNotEmpty())
    require(height.value.isFinite() && height > 4.dp) { "Segment height must be finite and greater than 4 dp." }
    val colors = LocalGlassColors.current
    val appearance = LocalGlassAppearance.current
    val isLight = !colors.isDark
    val selectedIndex = if (selected == null) -1 else items.indexOfFirst { it.first == selected }
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentDeferSelection by rememberUpdatedState(deferSelection)
    val currentItems by rememberUpdatedState(items)
    val currentOnSelected by rememberUpdatedState(onSelected)
    val scope = rememberCoroutineScope()
    val tabsBackdrop = rememberLayerBackdrop()
    val trackBackdrop = rememberLayerBackdrop()
    val indicatorBackdrop = rememberCombinedBackdrop(backdrop, trackBackdrop)
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val trackColor = colors.segmentedControlBackground.let {
        if (appearance.reduceTransparency) it.compositeOver(colors.elevatedBackground) else it
    }
    val indicatorHeight = height - 4.dp
    val indicatorColor = if (isLight) Color.White else Color(0xFF636366)

    // The native background and lifted label planes have independent recordings.
    val dragCoordinateSpace = remember { mutableStateOf<LayoutCoordinates?>(null) }
    BoxWithConstraints(
        modifier.swipeBackExcluded(enabled).height(height).selectableGroup().alpha(if (enabled) 1f else 0.45f)
            .onGloballyPositioned { dragCoordinateSpace.value = it },
    ) {
        val density = LocalDensity.current
        val contentWidthPx = constraints.maxWidth.coerceAtLeast(items.size).toFloat()
        val tabWidthPx = contentWidthPx / items.size
        val indicatorWidthPx = (tabWidthPx - with(density) { 4.dp.toPx() }).coerceAtLeast(1f)
        val currentTabWidthPx by rememberUpdatedState(tabWidthPx)
        val trackPaddingPx = with(density) { 2.dp.toPx() }
        val animation = remember(scope, items.size, isLtr, positionAnimation) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = selectedIndex.coerceAtLeast(0).toFloat(),
                valueRange = 0f..items.lastIndex.coerceAtLeast(1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = SegmentedIndicatorPressedScale,
                dragCoordinateSpace = { dragCoordinateSpace.value },
                sharedValueAnimation = positionAnimation,
                horizontalDragOnly = true,
                onDragStarted = {},
                onDragStopped = {
                    val target = targetValue.fastRoundToInt().fastCoerceIn(0, currentItems.lastIndex)
                    if (currentEnabled) currentOnSelected(currentItems[target].first)
                    val restingIndex = if (currentEnabled && !currentDeferSelection) target else currentSelectedIndex
                    if (restingIndex >= 0) animateToValue(restingIndex.toFloat())
                },
                onDragCancelled = {
                    if (currentSelectedIndex >= 0) {
                        animateToValue(currentSelectedIndex.toFloat(), animatePress = false)
                    }
                },
                onDrag = { _, dragAmount ->
                    if (!currentEnabled) return@DampedDragAnimation
                    updateValue(
                        (targetValue + dragAmount.x / currentTabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, currentItems.lastIndex.toFloat()),
                    )
                },
                lensStyle = NativeControlLensStyle.SegmentedIndicator,
            )
        }
        LaunchedEffect(selectedIndex, enabled, animation) {
            if (selectedIndex >= 0) animation.animateToValue(selectedIndex.toFloat(), animatePress = false)
        }
        val onUserSelected: (T) -> Unit = remember(animation) {
            { value ->
                if (currentEnabled) {
                    animation.press()
                    currentOnSelected(value)
                    animation.release()
                }
            }
        }
        val indicatorGlassModifier = remember(indicatorBackdrop, tabsBackdrop, animation, appearance, isLight, indicatorColor) {
            if (!appearance.usesBackdrop) Modifier else Modifier.drawPlainBackdrop(
                backdrop = indicatorBackdrop,
                shape = { Capsule() },
                effects = {
                    nativeControlLens(
                        style = NativeControlLensStyle.SegmentedIndicator,
                        liftProgress = if (appearance.usesRefraction) animation.pressProgress else 0f,
                        isDark = !isLight,
                        liftedContent = tabsBackdrop,
                        restingColor = indicatorColor,
                    )
                },
                layerBlock = {
                    val velocity = animation.velocity / 10f
                    scaleX = 1f / (1f - (velocity * .75f).fastCoerceIn(-.18f, .18f))
                    scaleY = 1f - (velocity * .25f).fastCoerceIn(-.14f, .14f)
                },
            )
        }

        // 1. Record the actual track independently from both label planes.
        Box(
            Modifier.fillMaxSize()
                .then(if (appearance.usesBackdrop) Modifier.layerBackdrop(trackBackdrop, reuseContent = true) else Modifier)
                .clip(Capsule())
                .background(trackColor),
        )
        Row(
            Modifier
                .fillMaxSize()
                .clip(Capsule())
                .drawBehind {
                    if (appearance.reduceTransparency && selectedIndex >= 0) {
                        val visualIndex = if (isLtr) animation.value else items.lastIndex - animation.value
                        val pillHeight = indicatorHeight.toPx()
                        drawRoundRect(
                            color = indicatorColor,
                            topLeft = Offset(trackPaddingPx + visualIndex * tabWidthPx, trackPaddingPx),
                            size = Size(indicatorWidthPx, pillHeight),
                            cornerRadius = CornerRadius(pillHeight / 2f),
                        )
                    }
                }
                .padding(vertical = 2.dp),
        ) {
            SegmentedTabContent(
                items = items,
                selectedIndex = selectedIndex,
                onSelected = onUserSelected,
                enabled = enabled,
                itemContent = itemContent,
            )
        }

        // 2. Keep the original labels in a separate, unfiltered portal recording.
        if (appearance.usesBackdrop) Row(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(height)
                .clearAndSetSemantics { }
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .padding(vertical = 2.dp),
        ) {
            SegmentedTabContent(
                items = items,
                selectedIndex = selectedIndex,
                onSelected = onUserSelected,
                enabled = false,
                itemContent = itemContent,
                interactive = false,
            )
        }

        // 3. The only drag surface. It sits over the selected cell, samples layer 2 and
        // becomes larger only while pressed; unselected cells remain tappable through layer 2.
        if (selectedIndex >= 0) Box(
            Modifier
                .align(AbsoluteAlignment.TopLeft)
                .graphicsLayer {
                    val visualIndex = if (isLtr) animation.value else items.lastIndex - animation.value
                    translationX = 2.dp.toPx() + visualIndex * tabWidthPx
                    translationY = 2.dp.toPx()
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                }
                .then(if (enabled) animation.modifier else Modifier)
                .height(indicatorHeight)
                .clearAndSetSemantics { }
                .layout { measurable, constraints ->
                    val width = indicatorWidthPx.fastRoundToInt()
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = width, maxWidth = width),
                    )
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .nativeControlLensGeometry(
                        NativeControlLensStyle.SegmentedIndicator,
                        horizontalProgress = {
                            if (appearance.usesRefraction) animation.pressProgress else 0f
                        },
                    )
                    .then(indicatorGlassModifier),
            )
        }
    }
}

@Composable
private fun <T> androidx.compose.foundation.layout.RowScope.SegmentedTabContent(
    items: List<Pair<T, String>>,
    selectedIndex: Int,
    onSelected: (T) -> Unit,
    enabled: Boolean,
    itemContent: (@Composable BoxScope.(value: T, selected: Boolean) -> Unit)?,
    interactive: Boolean = true,
) {
    val colors = LocalGlassColors.current
    items.forEachIndexed { index, (key, label) ->
        val isSelected = index == selectedIndex
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(Capsule())
                .then(if (interactive) Modifier.selectable(
                    selected = isSelected,
                    enabled = enabled,
                    interactionSource = null,
                    indication = null,
                    role = Role.Tab,
                    onClick = { if (enabled) onSelected(key) },
                ) else Modifier)
                .then(if (itemContent != null) Modifier.semantics { contentDescription = label } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (itemContent == null) Text(
                label,
                style = IosTypography.caption.copy(fontSize = 13.sp, lineHeight = 15.513672.sp),
                color = colors.content,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
            ) else CompositionLocalProvider(
                LocalContentColor provides colors.content,
                LocalGlassContentColor provides colors.content,
            ) {
                Box(Modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                    itemContent(key, isSelected)
                }
            }
        }
    }
}
