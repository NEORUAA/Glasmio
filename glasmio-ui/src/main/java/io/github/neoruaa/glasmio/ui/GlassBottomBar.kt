package io.github.neoruaa.glasmio.ui


import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.*

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.ProvideGlassMaterialContext
import io.github.neoruaa.glasmio.glass.render.rememberGlassMaterialContext
import io.github.neoruaa.glasmio.glass.render.backdrops.emptyBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCombinedBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.drawPlainBackdrop
import io.github.neoruaa.glasmio.glass.render.sampleBackdropLuminance
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.glass.DampedDragAnimation
import io.github.neoruaa.glasmio.glass.InteractiveHighlight
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@androidx.compose.runtime.Immutable
data class GlasmioTabItem<T>(
    val key: T,
    val label: String,
    val symbol: SfSymbol,
    val contentDescription: String = label,
)

@androidx.compose.runtime.Immutable
private data class StableGlassTabItems<T>(
    val values: List<GlasmioTabItem<T>>,
)

private data class NavigationSurfaceGeometry(
    val layoutSize: Size,
    val visualSize: Size,
    val compactInset: Float,
)

internal val LocalLiquidTabScale = staticCompositionLocalOf { { 1f } }

/** Shared compact icon endpoint for the bottom navigation and its adjacent search control. */
val CompactBottomControlIconSize = 28.dp

/** Compact touch surface shared by the navigation material and the search control. */
val CompactBottomControlSize = 48.dp

/** Resting height of the navigation material, excluding system insets. */
val NavigationBarSurfaceHeight = 62.dp

private val ExpandedNavigationIconSize = 28.dp
private val ExpandedNavigationItemHeight = 54.dp
private val ExpandedNavigationItemMinWidth = 75.dp
private val NavigationItemIconClearance = 4.dp

private data class NavigationTabGeometry(val widthPx: Float, val stepPx: Float) {
    fun center(index: Float): Float = widthPx / 2f + index * stepPx
}

/** UIKit's vertical phone allocation when every intrinsic item width is at most 42 pt. */
internal fun nativeShortTabWidth(platterWidth: Float, itemCount: Int): Float {
    require(itemCount in 4..5)
    val inset = if (itemCount == 4) 24f else 15f
    val available = platterWidth - 2f * inset
    val gapCount = itemCount - 1
    if ((available - itemCount * 54f) / gapCount < 14f) {
        // The crowded allocation changes the content inset to 14, then outsets buttons
        // by 14 - 4. Keep this native branch instead of interpolating across its threshold.
        return (platterWidth - 28f) / itemCount + 20f
    }
    val contentOutset = inset - 4f
    val allocation = maxOf(
        minOf(54f, (available - 2f * gapCount) / itemCount),
        available / itemCount - contentOutset,
    )
    return allocation + 2f * contentOutset
}

internal fun nativeFourShortTabWidth(platterWidth: Float): Float = nativeShortTabWidth(platterWidth, 4)

private fun navigationTabGeometry(
    contentWidthPx: Float,
    itemCount: Int,
    minimumWidthPx: Float,
    protectedIconRadiusPx: Float,
    nativeShortItemWidthPx: Float? = null,
): NavigationTabGeometry {
    val availableWidth = contentWidthPx.coerceAtLeast(1f)
    // The fallback keeps width / 2 + protectedIconRadius inside the center-to-center step.
    val maximumWidth = if (itemCount > 1) {
        (2f * (availableWidth - (itemCount - 1) * protectedIconRadiusPx) / (itemCount + 1))
            .coerceIn(1f, availableWidth)
    } else availableWidth
    val width = if (nativeShortItemWidthPx != null) {
        nativeShortItemWidthPx.coerceIn(1f, availableWidth)
    } else {
        (availableWidth / itemCount)
            .coerceAtLeast(minimumWidthPx)
            .coerceAtMost(maximumWidth)
    }
    // Keep the outer edges inside the capsule; narrow bars overlap adjacent items.
    val step = if (itemCount > 1) (availableWidth - width) / (itemCount - 1) else 0f
    return NavigationTabGeometry(width, step)
}

private val NavigationLabelStyle: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = IosTypography.caption.copy(
        fontSize = 10.sp,
        lineHeight = 12.sp,
    )

@Composable
private fun <T> hasNativeShortTabs(items: List<GlasmioTabItem<T>>): Boolean {
    val symbolTypeface = LocalGlasmioSymbolTypeface.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = NavigationLabelStyle.copy(fontWeight = FontWeight.Medium)
    return remember(items, symbolTypeface, density, textMeasurer, labelStyle) {
        if (items.size !in 4..5 || symbolTypeface == null) return@remember false
        val maximumIntrinsicWidth = with(density) { 42.dp.toPx() }
        val symbolPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(symbolTypeface, FontWeight.Medium.weight, false)
            textSize = with(density) { 18.sp.toPx() }
            fontFeatureSettings = "'ss16' 1"
        }
        items.all { item ->
            val titleWidth = textMeasurer.measure(
                text = AnnotatedString(item.label),
                style = labelStyle,
                softWrap = false,
                maxLines = 1,
            ).multiParagraph.intrinsics.maxIntrinsicWidth
            val glyph = String(Character.toChars(item.symbol.codePoint))
            // The advance retains the symbol's side bearings, unlike its visible ink bounds.
            maxOf(titleWidth, symbolPaint.measureText(glyph)) <= maximumIntrinsicWidth
        }
    }
}

private fun Modifier.navigationContentPadding(vertical: () -> Dp): Modifier = layout { measurable, constraints ->
    val inset = vertical().roundToPx().coerceIn(0, constraints.maxHeight / 2)
    val placeable = measurable.measure(constraints.offset(vertical = -2 * inset))
    layout(placeable.width, constraints.maxHeight) { placeable.placeRelative(0, inset) }
}

private fun morphSurfaceWidthPx(
    fullWidthPx: Float,
    compactWidthPx: Float,
    compactProgress: Float,
): Float = lerp(fullWidthPx, compactWidthPx, compactProgress.coerceIn(0f, 1f))

private fun compactIndicatorVisibility(compactProgress: Float): Float {
    return (1f - compactProgress).coerceIn(0f, 1f)
}

internal fun compactIconLateProgress(compactProgress: Float): Float {
    return compactProgress.coerceIn(0f, 1f)
}

/** An opaque parent accent tint already supplies the selected source's RGB. */
internal fun navigationLensSourceColorFilter(
    colors: GlassColors,
    selectedFilter: State<ColorFilter>,
): State<ColorFilter>? =
    if (colors.accent.alpha == 1f && colors.content.alpha == 1f) null else selectedFilter

/**
 * iOS split-search tab bar with a separate native selected-content portal.
 *
 * The outer glass keeps the capsule-to-circle navigation morph. The indicator samples the
 * actual material separately from the raw selected row, preserving each symbol's native metrics.
 * [onItemBoundsChanged] reports each visible item's interactive bounds in window coordinates.
 */
@Composable
fun <T> GlasmioNavigationBar(
    items: List<GlasmioTabItem<T>>,
    selectedKey: T,
    onSelected: (T) -> Unit,
    onExpand: () -> Unit,
    compactProgress: State<Float>,
    compactSize: Dp,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    onItemBoundsChanged: ((T, Rect) -> Unit)? = null,
    compactItem: GlasmioTabItem<T>? = null,
    onItemIconBoundsChanged: ((T, Rect) -> Unit)? = null,
) {
    require(items.isNotEmpty())
    val compactState = remember(compactProgress) {
        derivedStateOf { compactProgress.value.coerceIn(0f, 1f) }
    }
    val morphing by remember(compactProgress) {
        derivedStateOf { compactProgress.value != 0f && compactProgress.value != 1f }
    }
    val compactMode by remember(compactState) {
        derivedStateOf { compactState.value > 0f }
    }
    val onSelectedState = rememberUpdatedState(onSelected)
    val stableOnSelected: (T) -> Unit = remember {
        { key -> onSelectedState.value(key) }
    }
    val stableItems = remember(items) { StableGlassTabItems(items) }
    val tabItems = stableItems.values
    val nativeShortItems = hasNativeShortTabs(tabItems)
    val selectedItemIndex = tabItems.indexOfFirst { it.key == selectedKey }
    val hasSelection = selectedItemIndex >= 0
    val selectedIndex = selectedItemIndex.coerceAtLeast(0)
    val selectedItem = tabItems[selectedIndex]
    val materialContext = rememberGlassMaterialContext()
    val colors = materialContext.colors
    val appearance = LocalGlassAppearance.current
    val isLight = !materialContext.isDark
    val navigationInnerShadows = rememberNavigationInnerShadows(appearance.usesBackdrop, renderScale = 0.5f)
    val containerColor = colors.container
    val tabsBackdrop = rememberLayerBackdrop()
    val ownBarBackdrop = rememberLayerBackdrop()
    val bottomContact = LocalBottomControlContact.current
    val sourceBackdrop = if (bottomContact == null) rememberCombinedBackdrop(backdrop, ownBarBackdrop)
    else rememberCombinedBackdrop(
        backdrop,
        bottomContact.materialBackdrop,
    )
    // Background and selected-content portals retain separate, unfiltered source planes.
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val viewConfiguration = LocalViewConfiguration.current
    val navigationViewConfiguration = remember(viewConfiguration) {
        object : ViewConfiguration by viewConfiguration {
            // Horizontal auto-expansion would defeat the neighbor icon clearance.
            override val minimumTouchTargetSize = DpSize(
                0.dp,
                viewConfiguration.minimumTouchTargetSize.height,
            )
        }
    }
    val animationScope = rememberCoroutineScope()
    val onExpandState = rememberUpdatedState(onExpand)
    val selectedIndexState = rememberUpdatedState(selectedIndex)
    val tabItemsState = rememberUpdatedState(tabItems)

    val dragCoordinateSpace = remember { mutableStateOf<LayoutCoordinates?>(null) }
    ProvideGlassMaterialContext(materialContext) {
    BoxWithConstraints(
        modifier.height(NavigationBarSurfaceHeight).onGloballyPositioned { dragCoordinateSpace.value = it },
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val fullWidthPx = constraints.maxWidth.toFloat()
        val compactWidthPx = with(density) { compactSize.toPx() }
        val compactInsetPx = with(density) { (NavigationBarSurfaceHeight - compactSize).toPx() / 2f }
        val expandedHeightPx = with(density) { NavigationBarSurfaceHeight.toPx() }
        val surfaceGeometry = remember(
            compactProgress, fullWidthPx, compactWidthPx, expandedHeightPx, compactInsetPx, density, compactSize,
        ) {
            derivedStateOf {
                val progress = compactProgress.value
                val layoutProgress = progress.coerceIn(0f, 1f)
                NavigationSurfaceGeometry(
                    layoutSize = Size(
                        lerp(fullWidthPx, compactWidthPx, layoutProgress),
                        with(density) {
                            androidx.compose.ui.unit.lerp(NavigationBarSurfaceHeight, compactSize, layoutProgress).toPx()
                        },
                    ),
                    visualSize = Size(
                        lerp(fullWidthPx, compactWidthPx, progress).coerceAtLeast(1f),
                        lerp(expandedHeightPx, compactWidthPx, progress).coerceAtLeast(1f),
                    ),
                    compactInset = compactInsetPx * progress,
                )
            }
        }
        val indicatorSettled by remember(compactState) {
            derivedStateOf { compactState.value >= 1f }
        }
        // Geometry changes belong to measurement. Do not rebuild the tab/source/effect
        // compositions on every spring frame just to update their width and height.
        val surfaceSizeModifier = remember(surfaceGeometry) {
            Modifier.layout { measurable, constraints ->
                val size = surfaceGeometry.value.layoutSize
                val width = constraints.constrainWidth(
                    size.width.roundToInt(),
                )
                val height = constraints.constrainHeight(
                    size.height.roundToInt(),
                )
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width, height))
                layout(width, height) { placeable.placeRelative(0, 0) }
            }
        }
        val paddingPx = with(density) { 4.dp.toPx() }
        val expandedTabs = remember(fullWidthPx, paddingPx, density, tabItems.size, nativeShortItems) {
            navigationTabGeometry(
                contentWidthPx = fullWidthPx - paddingPx * 2f,
                itemCount = tabItems.size,
                minimumWidthPx = with(density) { ExpandedNavigationItemMinWidth.toPx() },
                protectedIconRadiusPx = with(density) {
                    ExpandedNavigationIconSize.toPx() / 2f + NavigationItemIconClearance.toPx()
                },
                nativeShortItemWidthPx = if (nativeShortItems) {
                    nativeShortTabWidth(fullWidthPx / density.density, tabItems.size) * density.density
                } else null,
            )
        }
        val expandedTabWidthPx = expandedTabs.widthPx
        val compactInnerWidthPx = (compactWidthPx - paddingPx * 2f).coerceAtLeast(1f)
        val tabStepPx = rememberUpdatedState(expandedTabs.stepPx.coerceAtLeast(1f))
        val innerGlassVisibilityState = remember(compactState) {
            derivedStateOf { compactIndicatorVisibility(compactState.value) }
        }
        val indicatorSizeModifier = remember(
            compactState, expandedTabWidthPx, compactInnerWidthPx, compactWidthPx, compactSize,
        ) {
            Modifier.layout { measurable, constraints ->
                val progress = compactState.value
                val settled = progress >= 1f
                val width = constraints.constrainWidth((if (settled) compactWidthPx else
                    lerp(expandedTabWidthPx, compactInnerWidthPx, progress)).roundToInt())
                val height = constraints.constrainHeight((if (settled) compactSize else
                    androidx.compose.ui.unit.lerp(ExpandedNavigationItemHeight, compactSize - 8.dp, progress)).roundToPx())
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width, height))
                layout(width, height) { placeable.placeRelative(0, 0) }
            }
        }
        val indicatorHitBoundsModifier = remember(compactState, expandedTabs, tabItems.size) {
            Modifier.layout { measurable, constraints ->
                val overlap = (expandedTabs.widthPx - expandedTabs.stepPx).coerceAtLeast(0f) *
                    (1f - compactState.value) / 2f
                val startInset = if (selectedIndexState.value > 0) overlap.roundToInt() else 0
                val endInset = if (selectedIndexState.value < tabItems.lastIndex) overlap.roundToInt() else 0
                val width = (constraints.maxWidth - startInset - endInset).coerceAtLeast(1)
                val placeable = measurable.measure(
                    androidx.compose.ui.unit.Constraints.fixed(width, constraints.maxHeight),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.placeRelative(startInset, 0)
                }
            }
        }
        val offsetAnimation = remember { Animatable(0f) }
        val selectionPressOrigin = remember { mutableStateOf(true) }
        val dragAnimation = remember(animationScope, tabItems.size, isLtr) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndexState.value.toFloat(),
                valueRange = 0f..(tabItems.lastIndex.coerceAtLeast(1)).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                dragCoordinateSpace = { dragCoordinateSpace.value },
                onDragStarted = { selectionPressOrigin.value = compactState.value == 0f },
                onDragStopped = {
                    if (compactState.value >= 0.74f) {
                        onExpandState.value()
                        animateToValue(selectedIndexState.value.toFloat())
                        return@DampedDragAnimation
                    }
                    val target = targetValue.fastRoundToInt().fastCoerceIn(0, tabItems.lastIndex)
                    if (target != selectedIndexState.value) {
                        stableOnSelected(tabItemsState.value[target].key)
                    }
                    animateToValue(target.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    if (compactState.value >= 0.74f) return@DampedDragAnimation
                    updateValue(
                        (targetValue + dragAmount.x / tabStepPx.value * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, tabItems.lastIndex.toFloat()),
                    )
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                },
                lensStyle = NativeControlLensStyle.TabBarIndicator,
            )
        }
        val onUserSelected: (T) -> Unit = remember(dragAnimation, compactState, stableOnSelected) {
            { key ->
                selectionPressOrigin.value = compactState.value == 0f
                dragAnimation.press()
                stableOnSelected(key)
                dragAnimation.release()
            }
        }
        LaunchedEffect(selectedIndex, dragAnimation) {
            // Route and preference updates only move the indicator. Navigation callbacks
            // belong to user input, including while the bar is fading out on a detail page.
            if (dragAnimation.targetValue != selectedIndex.toFloat()) {
                selectionPressOrigin.value = compactState.value == 0f
                dragAnimation.animateToValue(selectedIndex.toFloat(), animatePress = false)
            }
        }
        // Compact input keeps panel feedback without pressing the emerging selection.
        val selectionFeedbackEnabled = remember(compactState, selectionPressOrigin) {
            derivedStateOf { selectionPressOrigin.value && compactState.value == 0f }
        }
        val selectionPressProgressState = remember(dragAnimation, selectionFeedbackEnabled) {
            derivedStateOf { if (selectionFeedbackEnabled.value) dragAnimation.pressProgress else 0f }
        }
        val interactiveHighlight = remember(
            animationScope,
            isLtr,
            fullWidthPx,
            compactWidthPx,
            paddingPx,
            expandedTabs,
            dragAnimation,
        ) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    val progress = compactState.value
                    val expandedTabCenter = expandedTabs.center(dragAnimation.value)
                    val tabCenter = if (isLtr) {
                        paddingPx + expandedTabCenter
                    } else {
                        fullWidthPx - paddingPx - expandedTabCenter
                    }
                    val morphCenter = lerp(tabCenter, compactWidthPx / 2f, progress)
                    val fraction = (offsetAnimation.value / fullWidthPx).fastCoerceIn(-1f, 1f)
                    val currentPanelOffset =
                        paddingPx * fraction.sign * EaseOut.transform(abs(fraction))
                    Offset(
                        x = morphCenter + currentPanelOffset,
                        y = size.height / 2f,
                    )
                },
            )
        }
        val commonTransform = remember(surfaceGeometry, fullWidthPx, isLtr) {
            Modifier.graphicsLayer {
                val geometry = surfaceGeometry.value
                val fraction = (offsetAnimation.value / fullWidthPx).fastCoerceIn(-1f, 1f)
                translationX = geometry.compactInset * if (isLtr) 1f else -1f
                translationX += paddingPx * fraction.sign * EaseOut.transform(abs(fraction))
                scaleX = geometry.visualSize.width / size.width.coerceAtLeast(1f)
                scaleY = geometry.visualSize.height / size.height.coerceAtLeast(1f)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    if (isLtr) 0f else 1f,
                    0.5f,
                )
            }
        }
        val tabContentAlpha = remember {
            { (1f - compactState.value).coerceIn(0f, 1f) }
        }
        val pressProgressState = dragAnimation.pressProgressState
        val platterPuff = remember { Animatable(0f, .001f) }
        val flexWidth = if (compactMode) compactWidthPx else fullWidthPx
        val flexHeight = with(density) { (if (compactMode) compactSize else NavigationBarSurfaceHeight).toPx() }
        val flexShortDimension = minOf(flexWidth, flexHeight) / density.density
        val flexLongDimension = maxOf(flexWidth, flexHeight) / density.density
        val flexSizeProgress = if (flexLongDimension < 120f) 0f else ((flexShortDimension - 44f) / 116f).coerceIn(0f, 1f)
        val flexSpringProgress = if (flexLongDimension < 120f) 0f else ((flexLongDimension - 44f) / 116f).coerceIn(0f, 1f)
        val puffLiftPoints = 16f - 12f * flexSizeProgress
        val puffPressSpring = remember(flexSpringProgress) {
            val response = .262 + (.314 - .262) * flexSpringProgress
            spring<Float>(.625f, (4.0 * PI * PI / (response * response)).toFloat(), .001f)
        }
        val puffReleaseSpring = remember(flexSpringProgress) {
            val response = .4 + (.36 - .4) * flexSpringProgress
            spring<Float>(lerp(.375f, .6f, flexSpringProgress),
                (4.0 * PI * PI / (response * response)).toFloat(), .001f)
        }
        LaunchedEffect(interactiveHighlight.isPressed, puffPressSpring, puffReleaseSpring) {
            val pressed = interactiveHighlight.isPressed
            platterPuff.animateTo(if (pressed) 1f else 0f,
                if (pressed) puffPressSpring else puffReleaseSpring)
        }
        val selectedIconColorFilterState: State<ColorFilter> = remember(
            compactState,
            selectionPressProgressState,
            colors.accent,
            colors.content,
        ) {
            derivedStateOf {
                val compactTint = androidx.compose.ui.graphics.lerp(
                    colors.accent,
                    colors.content,
                    compactState.value,
                )
                ColorFilter.tint(
                    androidx.compose.ui.graphics.lerp(
                        compactTint,
                        colors.content,
                        selectionPressProgressState.value,
                    ),
                )
            }
        }
        val hiddenSelectedIconColorFilterState = navigationLensSourceColorFilter(
            colors, selectedIconColorFilterState,
        )
        val lensSourceAccentFilter = remember(colors.accent) {
            ColorFilter.tint(colors.accent)
        }
        val lensSourceAccentModifier = remember(lensSourceAccentFilter) {
            Modifier.graphicsLayer(colorFilter = lensSourceAccentFilter)
        }
        val visibleTabContentScale = remember {
            { 1f }
        }
        val lensSourceTabContentScale = remember(selectionPressProgressState, compactState) {
            {
                lerp(1f, 1.16f, selectionPressProgressState.value)
            }
        }
        val selectedContentLateScaleProvider: () -> Float = remember {
            { 1f }
        }
        val panelPressScale = remember(platterPuff, puffLiftPoints, flexLongDimension) {
            derivedStateOf {
                // UIKit animates the platter's outermost layer independently from its lens.
                1f + puffLiftPoints * platterPuff.value / flexLongDimension.coerceAtLeast(1f)
            }
        }
        val pressLayerBlock: GraphicsLayerScope.() -> Unit = remember(panelPressScale) {
            {
                scaleX = panelPressScale.value
                scaleY = panelPressScale.value
            }
        }
        val navigationSurfaceSize = remember { mutableStateOf(Size.Zero) }
        val navigationVisualBounds: (Size) -> Rect = remember(
            panelPressScale, surfaceGeometry, fullWidthPx, isLtr, offsetAnimation, paddingPx,
        ) {
            { size ->
                val scale = panelPressScale.value
                val geometry = surfaceGeometry.value
                val physicalWidth = geometry.visualSize.width
                val physicalHeight = geometry.visualSize.height
                val fraction = (offsetAnimation.value / fullWidthPx).fastCoerceIn(-1f, 1f)
                val inset = geometry.compactInset
                val left = if (isLtr) inset else size.width - physicalWidth - inset
                val offset = paddingPx * fraction.sign * EaseOut.transform(abs(fraction))
                val centerX = left + physicalWidth / 2f + offset
                Rect(
                    centerX - physicalWidth * scale / 2f,
                    (size.height - physicalHeight * scale) / 2f,
                    centerX + physicalWidth * scale / 2f,
                    (size.height + physicalHeight * scale) / 2f,
                )
            }
        }
        val contactMember = rememberBottomControlContactMember(
            materialPressProgress = { pressProgressState.value },
            visualBounds = navigationVisualBounds,
            sourceSize = { surfaceGeometry.value.visualSize },
            highlight = { size ->
                val position = interactiveHighlight.highlightPosition(size)
                GlassPressHighlight(
                    interactiveHighlight.pressProgress,
                    Offset(position.x / size.width.coerceAtLeast(1f), position.y / size.height.coerceAtLeast(1f)),
                )
            },
        )
        val sharesContactSurface = contactMember?.sharesSurface == true
        val morphLuminanceModifier = remember(
            backdrop, materialContext.luminanceState, morphing, sharesContactSurface, navigationVisualBounds,
            navigationSurfaceSize,
        ) {
            val luminance = materialContext.luminanceState
            if (morphing && !sharesContactSurface && luminance != null) Modifier.sampleBackdropLuminance(
                backdrop = backdrop, luminanceState = luminance,
                sampleBounds = {
                    val size = navigationSurfaceSize.value
                    if (size.isEmpty()) Rect.Zero else navigationVisualBounds(size)
                },
            ) else Modifier
        }
        val indicatorPanelScale = panelPressScale
        val selectedTabPanelScale: () -> Float = remember(panelPressScale) {
            { panelPressScale.value }
        }
        val morphingNavigationGlass = rememberMorphingNavigationGlass(
            backdrop = backdrop,
            active = morphing && !sharesContactSurface,
            shape = Capsule(),
            tint = containerColor,
            navigationMaterial = appearance.usesBackdrop,
            isDark = materialContext.isDark,
            darkFraction = materialContext.darkFraction,
            pressProgress = { pressProgressState.value },
            layerBlock = pressLayerBlock,
        )
        val navigationGlassModifier = if (sharesContactSurface) Modifier.graphicsLayer(pressLayerBlock)
        else if (morphing) morphingNavigationGlass else remember(
            backdrop,
            containerColor,
            isLight,
            navigationInnerShadows,
            pressProgressState,
            pressLayerBlock,
            appearance,
            materialContext,
        ) {
            Modifier.navigationGlassBackground(
                backdrop = backdrop,
                shape = { Capsule() },
                containerColor = containerColor,
                navigationMaterial = appearance.usesBackdrop,
                innerShadows = navigationInnerShadows,
                isDark = materialContext.isDark,
                materialContext = materialContext,
                pressProgressState = pressProgressState,
                layerBlock = pressLayerBlock,
                appearance = appearance,
            )
        }
        val hiddenLayerBackdropModifier = remember(tabsBackdrop) {
            Modifier.layerBackdrop(tabsBackdrop).drawWithContent {
                // LayerBackdrop draws once normally and once into its recording. This
                // hidden source only needs the latter, with the same content and optics.
                if (drawContext.graphicsLayer === tabsBackdrop.graphicsLayer) drawContent()
            }
        }
        val hiddenSourceSizeModifier = remember(compactState, fullWidthPx, compactWidthPx, compactSize, density) {
            Modifier.layout { measurable, constraints ->
                val progress = compactState.value
                val width = constraints.constrainWidth(
                    morphSurfaceWidthPx(fullWidthPx, compactWidthPx, progress).roundToInt(),
                )
                val height = constraints.constrainHeight(lerp(
                    62.dp.toPx(),
                    compactSize.toPx(),
                    progress,
                ).roundToInt())
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width, height))
                layout(width, height) { placeable.placeRelative(0, 0) }
            }
        }
        val indicatorPositionLayerBlock: GraphicsLayerScope.() -> Unit = remember(
            compactState,
            offsetAnimation,
            dragAnimation,
            indicatorPanelScale,
            isLtr,
            fullWidthPx,
            compactWidthPx,
            paddingPx,
            expandedTabWidthPx,
            compactInnerWidthPx,
            expandedTabs,
            compactInsetPx,
            compactProgress,
        ) {
            {
                // Follow the current tab center while the capsule shrinks, then converge to
                // the compact center. This keeps the first, middle and last tabs symmetric.
                val progress = compactState.value
                val currentWidth = morphSurfaceWidthPx(fullWidthPx, compactWidthPx, progress)
                val expandedTabCenter = expandedTabs.center(dragAnimation.value)
                val tabCenter = if (isLtr) {
                    paddingPx + expandedTabCenter
                } else {
                    fullWidthPx - paddingPx - expandedTabCenter
                }
                val indicatorWidth = if (progress >= 1f) {
                    compactWidthPx
                } else {
                    lerp(expandedTabWidthPx, compactInnerWidthPx, progress)
                }
                if (progress >= 1f) {
                    // The transparent compact interaction target follows the material center.
                    val compactOffset = (currentWidth - compactWidthPx).coerceAtLeast(0f) / 2f
                    translationX = compactOffset * if (isLtr) 1f else -1f
                } else {
                    val targetIndicatorLeft = lerp(
                        tabCenter - indicatorWidth / 2f,
                        compactWidthPx / 2f - indicatorWidth / 2f,
                        progress,
                    )
                    val indicatorLeft = targetIndicatorLeft.coerceIn(
                        paddingPx,
                        currentWidth - paddingPx - indicatorWidth,
                    )
                    val surfaceBaseLeft = if (isLtr) 0f else fullWidthPx - currentWidth
                    val directionalBase = if (isLtr) 0f else fullWidthPx - indicatorWidth
                    val fraction = (offsetAnimation.value / fullWidthPx).fastCoerceIn(-1f, 1f)
                    translationX = surfaceBaseLeft + indicatorLeft - directionalBase +
                        paddingPx * fraction.sign * EaseOut.transform(abs(fraction))
                    translationX += navigationPanelCenterOffset(
                        indicatorLeft + indicatorWidth / 2f, currentWidth / 2f, indicatorPanelScale.value,
                    )
                }
                translationX += compactInsetPx * compactProgress.value * if (isLtr) 1f else -1f
            }
        }
        val indicatorBackdropLayerBlock: GraphicsLayerScope.() -> Unit = remember(
            dragAnimation,
            innerGlassVisibilityState,
            selectionFeedbackEnabled,
            panelPressScale,
        ) {
            {
                val velocity = if (selectionFeedbackEnabled.value) dragAnimation.velocity / 10f else 0f
                scaleX = panelPressScale.value / (1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f))
                scaleY = panelPressScale.value * (1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f))
                // drawBackdrop installs this block on the backdrop layer itself. Keep the
                // complete refractive surface, highlight and shadows on the same alpha curve.
                alpha = innerGlassVisibilityState.value
            }
        }
        val indicatorBackdropModifier = if (hasSelection && !indicatorSettled) {
            remember(
                sourceBackdrop,
                tabsBackdrop,
                dragAnimation,
                compactState,
                containerColor,
                isLight,
                indicatorBackdropLayerBlock,
                selectionPressProgressState,
                appearance,
                isLtr,
                tabItems.size,
            ) {
                Modifier
                    .drawPlainBackdrop(
                        backdrop = if (appearance.usesBackdrop) sourceBackdrop else emptyBackdrop(),
                        shape = { Capsule() },
                        effects = {
                            if (appearance.usesBackdrop) {
                                nativeControlLens(
                                    style = NativeControlLensStyle.TabBarIndicator,
                                    liftProgress = if (appearance.usesRefraction) selectionPressProgressState.value else 0f,
                                    isDark = !isLight,
                                    liftedContent = tabsBackdrop,
                                )
                            }
                        },
                        layerBlock = indicatorBackdropLayerBlock,
                        onDrawSurface = {
                            if (!appearance.usesBackdrop) {
                                val indicatorBaseColor = if (isLight) {
                                    Color.Black.copy(alpha = 0.10f)
                                } else {
                                    Color.White.copy(alpha = 0.10f)
                                }
                                drawRect(
                                    androidx.compose.ui.graphics.lerp(
                                        indicatorBaseColor, containerColor, compactState.value,
                                    ),
                                    alpha = 1f - compactState.value,
                                )
                            }
                        },
                    )
            }
        } else {
            Modifier
        }

        // The platter's outermost-layer puff carries both ordinary content and the lens.
        // The lifted source retains its additional per-item 1.16 transform.
        CompositionLocalProvider(
            LocalLiquidTabScale provides visibleTabContentScale,
            LocalViewConfiguration provides navigationViewConfiguration,
        ) {
            // Glass and unselected tabs follow the panel; the selected item has its own center.
            Box(
                modifier = Modifier
                    .then(surfaceSizeModifier)
                    .then(morphLuminanceModifier)
                    .onGloballyPositioned {
                        navigationSurfaceSize.value = it.size.toSize()
                        contactMember?.updateCoordinates(it)
                    },
            ) {
                // Keep registration outside both transforms so contact geometry cannot feed
                // its own press magnification back into layout bounds.
                Box(Modifier.matchParentSize()
                    .then(commonTransform)
                    .then(if (appearance.usesBackdrop && !sharesContactSurface) Modifier.layerBackdrop(ownBarBackdrop, reuseContent = true) else Modifier)
                    .then(navigationGlassModifier)
                    .then(if (sharesContactSurface) Modifier else interactiveHighlight.modifier))
            }
            Row(
                modifier = Modifier
                    .then(surfaceSizeModifier)
                    .then(commonTransform)
                    .then(interactiveHighlight.gestureModifier)
                    .then(if (compactMode) Modifier.clearAndSetSemantics {} else Modifier)
                    .padding(horizontal = 4.dp)
                    .navigationContentPadding { 4.dp }
                    .clip(Capsule()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Box(Modifier.fillMaxSize().graphicsLayer(pressLayerBlock)) {
                        FullTabContent(
                            items = stableItems,
                            selectedKey = selectedKey,
                            onSelected = onUserSelected,
                            enabled = !compactMode,
                            alpha = tabContentAlpha,
                            selectedColorFilter = selectedIconColorFilterState,
                            hideSelectedItem = true,
                            expandedTabWidthPx = expandedTabWidthPx,
                            compactTabWidthPx = compactInnerWidthPx / tabItems.size,
                            compactProgress = compactState,
                            onItemBoundsChanged = onItemBoundsChanged,
                            onItemIconBoundsChanged = onItemIconBoundsChanged,
                        )
                    }
                    if (hasSelection || compactMode) MorphingSelectedNavigationItem(
                        item = selectedItem,
                        compactProgress = compactState,
                        fullWidthPx = fullWidthPx,
                        compactWidthPx = compactWidthPx,
                        itemCount = tabItems.size,
                        selectedIndex = selectedIndex,
                        isLtr = isLtr,
                        horizontalInsetPx = paddingPx,
                        expandedTabWidthPx = expandedTabWidthPx,
                        selectedContentLateScale = selectedContentLateScaleProvider,
                        labelAlpha = tabContentAlpha,
                        selectedIconColorFilter = selectedIconColorFilterState,
                        contentColor = colors.content,
                        panelScale = selectedTabPanelScale,
                        iconAlpha = if (compactItem != null) tabContentAlpha else null,
                        onIconBoundsChanged = onItemIconBoundsChanged?.let { callback ->
                            { bounds -> callback(selectedItem.key, bounds) }
                        },
                    )
                    if (compactItem != null) MorphingSelectedNavigationItem(
                        item = compactItem,
                        compactProgress = compactState,
                        fullWidthPx = fullWidthPx,
                        compactWidthPx = compactWidthPx,
                        itemCount = tabItems.size,
                        selectedIndex = 0,
                        isLtr = isLtr,
                        horizontalInsetPx = paddingPx,
                        expandedTabWidthPx = expandedTabWidthPx,
                        selectedContentLateScale = selectedContentLateScaleProvider,
                        labelAlpha = { 0f },
                        iconAlpha = { compactState.value },
                        selectedIconColorFilter = null,
                        contentColor = colors.content,
                        panelScale = selectedTabPanelScale,
                    )
                }
            }
        }

        if (hasSelection && !indicatorSettled && appearance.usesBackdrop) {
            CompositionLocalProvider(
                LocalLiquidTabScale provides lensSourceTabContentScale,
                LocalViewConfiguration provides navigationViewConfiguration,
            ) {
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .then(hiddenLayerBackdropModifier)
                        .then(hiddenSourceSizeModifier)
                        .then(commonTransform)
                        .graphicsLayer(pressLayerBlock)
                        .padding(horizontal = 4.dp)
                        .navigationContentPadding { 4.dp },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(lensSourceAccentModifier),
                    ) {
                        FullTabContent(
                            items = stableItems,
                            selectedKey = selectedKey,
                            onSelected = onUserSelected,
                            enabled = false,
                            alpha = tabContentAlpha,
                            selectedColorFilter = selectedIconColorFilterState,
                            hideSelectedItem = true,
                            expandedTabWidthPx = expandedTabWidthPx,
                            compactTabWidthPx = compactInnerWidthPx / tabItems.size,
                            compactProgress = compactState,
                            selectedContentSource = true,
                        )
                        MorphingSelectedNavigationItem(
                            item = selectedItem,
                            compactProgress = compactState,
                            fullWidthPx = fullWidthPx,
                            compactWidthPx = compactWidthPx,
                            itemCount = tabItems.size,
                            selectedIndex = selectedIndex,
                            isLtr = isLtr,
                            horizontalInsetPx = paddingPx,
                            expandedTabWidthPx = expandedTabWidthPx,
                            selectedContentLateScale = selectedContentLateScaleProvider,
                            labelAlpha = tabContentAlpha,
                            selectedIconColorFilter = hiddenSelectedIconColorFilterState,
                            contentColor = colors.content,
                            selectedContentSource = true,
                        )
                    }
                }
            }
        }

        if (hasSelection || compactMode) CompositionLocalProvider(LocalViewConfiguration provides navigationViewConfiguration) {
            Box(
                modifier = Modifier
                    .graphicsLayer(indicatorPositionLayerBlock)
                    .then(indicatorSizeModifier),
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .nativeControlLensGeometry(
                            NativeControlLensStyle.TabBarIndicator,
                            horizontalProgress = {
                                if (appearance.usesRefraction && selectionFeedbackEnabled.value) {
                                    dragAnimation.pressProgress
                                } else 0f
                            },
                        )
                        .then(indicatorBackdropModifier),
                )
                Box(
                    Modifier.fillMaxSize()
                        .then(indicatorHitBoundsModifier)
                        .then(if (!compactMode) interactiveHighlight.gestureModifier else Modifier)
                        .then(if (!compactMode) dragAnimation.modifier else Modifier),
                )
            }
        }
        if (compactMode) CompositionLocalProvider(LocalViewConfiguration provides navigationViewConfiguration) {
            // Once compacting, the entire main platter expands navigation. Keeping this
            // target on the selected indicator would overlap the emerging auxiliary.
            Box(
                Modifier.then(surfaceSizeModifier)
                    .then(commonTransform)
                    .then(interactiveHighlight.gestureModifier)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onExpandState.value() },
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = (compactItem ?: selectedItem).contentDescription
                        selected = hasSelection && compactItem == null
                    },
            )
        }

    }
    }
}

@Composable
private fun <T> FullTabContent(
    items: StableGlassTabItems<T>,
    selectedKey: T,
    onSelected: (T) -> Unit,
    enabled: Boolean,
    alpha: () -> Float,
    selectedColorFilter: State<ColorFilter>,
    expandedTabWidthPx: Float,
    compactTabWidthPx: Float,
    compactProgress: State<Float>,
    hideSelectedItem: Boolean = false,
    selectedContentSource: Boolean = false,
    onItemBoundsChanged: ((T, Rect) -> Unit)? = null,
    onItemIconBoundsChanged: ((T, Rect) -> Unit)? = null,
) {
    val scale = LocalLiquidTabScale.current
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            items.values.forEachIndexed { index, item ->
                val selected = item.key == selectedKey
                Layout(
                    modifier = Modifier
                        .fillMaxHeight()
                        .then(
                            // A disabled clickable still participates in pointer hit testing.
                            // The invisible source plane must never cover the real tab targets.
                            if (selectedContentSource || !enabled) Modifier else Modifier
                                .clickable(
                                    interactionSource = null,
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = { onSelected(item.key) },
                                )
                                .semantics { this.selected = selected },
                        )
                        .then(
                            if (onItemBoundsChanged != null) Modifier.onGloballyPositioned { coordinates ->
                                onItemBoundsChanged(item.key, coordinates.boundsInWindow())
                            } else Modifier,
                        ),
                    content = {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(Capsule())
                                .graphicsLayer {
                                    this.alpha = if (selected && hideSelectedItem) 0f else alpha()
                                    val contentScale = scale()
                                    scaleX = contentScale
                                    scaleY = contentScale
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            NavigationTabContent(
                                item = item,
                                selected = selected,
                                // The selected placeholder stays transparent while the separate
                                // morphing item owns its pixels. Keep its layout and semantics
                                // without two unused tint layers observing every press frame.
                                selectedColorFilter = if (selected && hideSelectedItem) null else selectedColorFilter,
                                selectedContentSource = selectedContentSource,
                                onIconBoundsChanged = if (!selectedContentSource && !(selected && hideSelectedItem)) {
                                    onItemIconBoundsChanged?.let { callback -> { bounds -> callback(item.key, bounds) } }
                                } else null,
                            )
                        }
                    },
                ) { measurables, constraints ->
                    val visualWidth = lerp(expandedTabWidthPx, compactTabWidthPx, compactProgress.value)
                        .roundToInt().coerceAtLeast(1)
                    val placeable = measurables.single().measure(
                        androidx.compose.ui.unit.Constraints.fixed(visualWidth, constraints.maxHeight),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val offset = when (index) {
                            0 -> 0
                            items.values.lastIndex -> constraints.maxWidth - visualWidth
                            else -> (constraints.maxWidth - visualWidth) / 2
                        }
                        placeable.placeRelative(offset, 0)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val itemWidth = lerp(expandedTabWidthPx, compactTabWidthPx, compactProgress.value)
            .roundToInt().coerceIn(1, constraints.maxWidth.coerceAtLeast(1))
        val step = if (measurables.size > 1) {
            (constraints.maxWidth - itemWidth).toFloat() / (measurables.size - 1)
        } else 0f
        // Keep targets inside both the visual item and the neighboring center midpoints.
        val starts = IntArray(measurables.size)
        val placeables = measurables.mapIndexed { index, measurable ->
            val visualStart = (index * step).roundToInt()
            val slotStart = if (index == 0) 0 else {
                (itemWidth / 2f + (index - 0.5f) * step).roundToInt()
            }
            val slotEnd = if (index == measurables.lastIndex) constraints.maxWidth else {
                (itemWidth / 2f + (index + 0.5f) * step).roundToInt()
            }
            val hitStart = maxOf(slotStart, visualStart)
            val hitEnd = minOf(slotEnd, visualStart + itemWidth)
            starts[index] = hitStart
            measurable.measure(
                androidx.compose.ui.unit.Constraints.fixed(
                    (hitEnd - hitStart).coerceAtLeast(0), constraints.maxHeight,
                ),
            )
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(starts[index], 0)
            }
        }
    }
}

@Composable
private fun <T> NavigationTabContent(
    item: GlasmioTabItem<T>,
    selected: Boolean,
    selectedColorFilter: State<ColorFilter>? = null,
    selectedIconTint: Color? = null,
    labelAlpha: (() -> Float)? = null,
    rasterizeBeforeScale: Boolean = false,
    selectedContentSource: Boolean = false,
    iconAlpha: (() -> Float)? = null,
    onIconBoundsChanged: ((Rect) -> Unit)? = null,
) {
    val colors = LocalGlassColors.current
    SfIcon(
        symbol = item.symbol,
        contentDescription = item.contentDescription,
        tint = if (selected) selectedIconTint ?: colors.accent else colors.content,
        size = ExpandedNavigationIconSize,
        fontSize = 18.sp,
        weight = FontWeight.Medium,
        fontFeatureSettings = "'ss16' 1",
        fitGlyphToBounds = false,
        modifier = Modifier
            .then(
                if (labelAlpha != null) Modifier.navigationTabIconPadding(labelAlpha)
                else Modifier.padding(top = 3.dp),
            )
            .then(
                if (onIconBoundsChanged != null) Modifier.onGloballyPositioned {
                    onIconBoundsChanged(it.boundsInWindow())
                } else Modifier,
            )
            .then(
                if (iconAlpha != null) Modifier.graphicsLayer { alpha = iconAlpha() } else Modifier,
            )
            .then(
                if (selected && (selectedColorFilter != null || rasterizeBeforeScale)) {
                    Modifier.graphicsLayer {
                        colorFilter = selectedColorFilter?.value
                        if (rasterizeBeforeScale) compositingStrategy = CompositingStrategy.Offscreen
                    }
                } else {
                    Modifier
                },
            ),
    )
    Text(
        text = item.label,
        color = colors.content,
        style = NavigationLabelStyle,
        fontWeight = if (selectedContentSource) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .then(
                if (labelAlpha != null) {
                    Modifier.collapseVertically(labelAlpha)
                } else {
                    Modifier
                },
            )
            .padding(top = 1.dp, bottom = 4.dp)
            .then(
                if (labelAlpha != null || (selected && selectedColorFilter != null) || rasterizeBeforeScale) {
                    Modifier.graphicsLayer {
                        if (labelAlpha != null) {
                            alpha = labelAlpha().coerceIn(0f, 1f)
                        }
                        if (selected) {
                            selectedColorFilter?.let { colorFilter = it.value }
                        }
                        if (rasterizeBeforeScale) compositingStrategy = CompositingStrategy.Offscreen
                    }
                } else {
                    Modifier
                },
            ),
    )
}

private fun Modifier.navigationTabIconPadding(labelAlpha: () -> Float): Modifier = layout { measurable, constraints ->
    // Keep PaddingModifier's Dp multiplication and pixel rounding, but observe the spring
    // in measurement instead of recomposing the icon and label on every morph frame.
    val topPadding = (3.dp * labelAlpha().coerceIn(0f, 1f)).roundToPx()
    val placeable = measurable.measure(constraints.offset(vertical = -topPadding))
    layout(
        constraints.constrainWidth(placeable.width),
        constraints.constrainHeight(placeable.height + topPadding),
    ) {
        placeable.placeRelative(0, topPadding)
    }
}

private fun Modifier.collapseVertically(fraction: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height * fraction().coerceIn(0f, 1f))
        .roundToInt()
        .coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(placeable.width, height) {
        placeable.placeRelative(0, 0)
    }
}

@Composable
internal fun <T> BoxScope.MorphingSelectedNavigationItem(
    item: GlasmioTabItem<T>,
    compactProgress: State<Float>,
    fullWidthPx: Float,
    compactWidthPx: Float,
    itemCount: Int,
    selectedIndex: Int,
    isLtr: Boolean,
    horizontalInsetPx: Float,
    expandedTabWidthPx: Float,
    selectedContentLateScale: () -> Float,
    labelAlpha: () -> Float,
    selectedIconColorFilter: State<ColorFilter>?,
    contentColor: Color,
    panelScale: () -> Float = { 1f },
    selectedContentSource: Boolean = false,
    iconAlpha: (() -> Float)? = null,
    onIconBoundsChanged: ((Rect) -> Unit)? = null,
) {
    val sharedTabScale = LocalLiquidTabScale.current
    val density = LocalDensity.current
    val expandedTabs = remember(fullWidthPx, horizontalInsetPx, expandedTabWidthPx, itemCount) {
        val contentWidth = fullWidthPx - 2f * horizontalInsetPx
        NavigationTabGeometry(
            widthPx = expandedTabWidthPx,
            stepPx = if (itemCount > 1) (contentWidth - expandedTabWidthPx) / (itemCount - 1) else 0f,
        )
    }
    val selectedTabWidthModifier = remember(
        density, expandedTabs, compactWidthPx, horizontalInsetPx, itemCount, compactProgress,
    ) {
        Modifier.layout { measurable, constraints ->
            // Preserve width(Dp)'s px -> Dp -> rounded-px path at fractional densities.
            val width = with(density) {
                lerp(
                    expandedTabs.widthPx,
                    (compactWidthPx - 2f * horizontalInsetPx) / itemCount,
                    compactProgress.value.coerceIn(0f, 1f),
                )
                    .coerceAtLeast(ExpandedNavigationIconSize.toPx())
                    .toDp()
                    .roundToPx()
            }
            val constrainedWidth = constraints.constrainWidth(width)
            val placeable = measurable.measure(
                constraints.copy(minWidth = constrainedWidth, maxWidth = constrainedWidth),
            )
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }
    }
    Box(
        modifier = Modifier
            // The actual interactive row (or compact hit surface) owns accessibility.
            .clearAndSetSemantics {}
            .align(Alignment.Center)
            .fillMaxSize()
            .graphicsLayer {
                val progress = compactProgress.value.coerceIn(0f, 1f)
                val expandedContentWidth = fullWidthPx - 2f * horizontalInsetPx
                val compactContentWidth = compactWidthPx - 2f * horizontalInsetPx
                val currentSurfaceWidth = morphSurfaceWidthPx(
                    fullWidthPx,
                    compactWidthPx,
                    progress,
                )
                val currentContentWidth = currentSurfaceWidth - 2f * horizontalInsetPx
                val expandedTabCenter = expandedTabs.center(selectedIndex.toFloat())
                val expandedCenter = if (isLtr) {
                    expandedTabCenter
                } else {
                    expandedContentWidth - expandedTabCenter
                }
                val compactCenter = compactContentWidth / 2f
                val targetCenter = lerp(expandedCenter, compactCenter, progress)
                val tabScale = panelScale()
                translationX = targetCenter - currentContentWidth / 2f +
                    navigationPanelCenterOffset(targetCenter, currentContentWidth / 2f, tabScale)
                // The icon keeps its native metrics while its center and title move to the
                // compact target. Only the platter and active lens supply press scaling.
                val scale = sharedTabScale() * selectedContentLateScale() * tabScale
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .then(selectedTabWidthModifier)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            NavigationTabContent(
                item = item,
                selected = true,
                selectedColorFilter = selectedIconColorFilter,
                selectedIconTint = contentColor,
                labelAlpha = labelAlpha,
                // Match the filtered path's individual raster bounds before the tab
                // scales, even when the opaque parent makes both inner tints redundant.
                rasterizeBeforeScale = true,
                selectedContentSource = selectedContentSource,
                iconAlpha = iconAlpha,
                onIconBoundsChanged = onIconBoundsChanged,
            )
        }
    }
}
