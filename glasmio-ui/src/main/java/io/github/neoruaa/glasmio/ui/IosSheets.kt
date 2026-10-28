package io.github.neoruaa.glasmio.ui

import android.graphics.Color as AndroidColor
import android.view.RoundedCorner
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.LocalGlassAppearance
import io.github.neoruaa.glasmio.glass.LocalBlurBackdrop
import io.github.neoruaa.glasmio.glass.glassBackdropSource
import io.github.neoruaa.glasmio.glass.progressiveBlurBackdrop
import io.github.neoruaa.glasmio.glass.scrollEdgeContentFade
import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Safe content spacing for a sheet with a pinned [GlasmioBottomSheet] top bar.
 * Apply the top inset to scroll-content padding, rather than the scroll viewport, so rows
 * can pass behind the progressive header. Apply the bottom inset inside the footer's background.
 * Without a top-bar slot the modal continues to apply these insets itself.
 */
val LocalGlasmioSheetContentPadding = staticCompositionLocalOf { PaddingValues() }

/** Draggable reports zero velocity for cancellation; retain its actual interaction kind. */
private class SheetDragInteractions : MutableInteractionSource {
    private val delegate = MutableInteractionSource()
    var cancelled = false
        private set
    override val interactions: Flow<Interaction> get() = delegate.interactions
    private fun track(interaction: Interaction) {
        if (interaction is DragInteraction.Start) cancelled = false
        if (interaction is DragInteraction.Cancel) cancelled = true
    }
    override suspend fun emit(interaction: Interaction) { track(interaction); delegate.emit(interaction) }
    override fun tryEmit(interaction: Interaction): Boolean { track(interaction); return delegate.tryEmit(interaction) }
}

/**
 * UIKit-style modal presentation with its own detents, scroll arbitration and window lifetime.
 * Keep the call mounted and change [visible] for an animated programmatic dismissal.
 * [fitContent] is for short action panels; medium/large sheets lay out content at the live height.
 * With [topBar], content extends behind all chrome. Read [LocalGlasmioSheetContentPadding]
 * to keep initial scroll content and footer controls clear of the pinned bar and system insets.
 * [topBarBlurProgress] defaults to hidden; drive it from the content's scroll-edge progress.
 * [contentBackground] stays visible while scrollable foreground fades beneath [topBar].
 */
@Composable
fun GlasmioBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = true,
    backdrop: Backdrop = LocalBlurBackdrop.current,
    contentWindowInsets: @Composable () -> WindowInsets = { WindowInsets.statusBars.union(WindowInsets.ime) },
    sheetState: GlasmioSheetState = rememberGlasmioSheetState(skipPartiallyExpanded),
    contentNavigationBarInsets: @Composable () -> WindowInsets = { WindowInsets.navigationBars },
    visible: Boolean = true,
    fitContent: Boolean = false,
    dismissOnClickOutside: Boolean = true,
    dismissOnBackPress: Boolean = true,
    prefersScrollingExpandsWhenScrolledToEdge: Boolean = true,
    title: String = "Sheet",
    topBar: (@Composable () -> Unit)? = null,
    topBarBlurProgress: Float = 0f,
    contentBackground: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentDismiss by rememberUpdatedState(onDismissRequest)
    val currentVisible by rememberUpdatedState(visible)
    var presented by remember { mutableStateOf(visible) }
    var shown by remember(sheetState) { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            if (!presented) shown = false
            presented = true
        } else if (presented) {
            sheetState.hide()
            if (sheetState.currentValue == GlasmioSheetValue.Hidden) presented = false
        }
    }
    LaunchedEffect(sheetState) {
        snapshotFlow {
            shown && !sheetState.isDragging && !sheetState.isAnimationRunning &&
                sheetState.currentValue == GlasmioSheetValue.Hidden &&
                sheetState.targetValue == GlasmioSheetValue.Hidden
        }.distinctUntilChanged().collect { dismissed ->
            if (dismissed && presented) {
                presented = false
                if (currentVisible) currentDismiss()
            }
        }
    }
    if (!presented) return
    val dismiss = { scope.launch { sheetState.hide() }; Unit }
    Dialog(
        onDismissRequest = { if (dismissOnBackPress) dismiss() },
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val colors = LocalGlassColors.current
        val focus = LocalFocusManager.current
        LaunchedEffect(visible) { if (!visible) focus.clearFocus(force = true) }
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.apply {
                setWindowAnimations(0)
                setDimAmount(0f)
                @Suppress("DEPRECATION")
                run {
                    statusBarColor = AndroidColor.TRANSPARENT
                    navigationBarColor = AndroidColor.TRANSPARENT
                    navigationBarDividerColor = AndroidColor.TRANSPARENT
                    isNavigationBarContrastEnforced = false
                }
                WindowCompat.getInsetsController(this, decorView).apply {
                    isAppearanceLightStatusBars = !colors.isDark
                    isAppearanceLightNavigationBars = !colors.isDark
                }
            }
        }
        GlasmioTextEditingWindow(backdrop) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val heightPx = constraints.maxHeight.toFloat()
                val widthPx = constraints.maxWidth.toFloat()
                val insets = contentWindowInsets()
                val topInset = insets.getTop(density).toFloat()
                val lowerInset = insets.getBottom(density).toFloat()
                val navigationInsets = contentNavigationBarInsets()
                val safeBottom = navigationInsets.getBottom(density).toFloat()
                var measuredContentHeight by remember { mutableStateOf(0) }
                val contentSized = fitContent && sheetState.skipPartiallyExpanded
                val effectiveHeight = max(1f, heightPx - lowerInset)
                val layout = IosSheetLayout(
                    containerWidth = widthPx,
                    containerHeight = effectiveHeight,
                    fullTop = min(topInset, effectiveHeight - 1f),
                    safeBottom = safeBottom,
                    density = density.density,
                    compactHeight = heightPx <= widthPx,
                )
                val anchors = layout.anchors(
                    sheetState.skipPartiallyExpanded,
                    measuredContentHeight.takeIf { contentSized && it > 0 }?.toFloat(),
                ).copy(hidden = heightPx)
                SideEffect { sheetState.updateAnchors(anchors, density.density, topInset) }
                val ready = sheetState.hasAnchors && (!contentSized || measuredContentHeight > 0)
                LaunchedEffect(visible, sheetState, ready) {
                    if (visible && ready && (!shown || sheetState.targetValue == GlasmioSheetValue.Hidden)) {
                        shown = true
                        sheetState.show()
                    }
                }
                val currentLarge by rememberUpdatedState(anchors.large)
                val nestedScroll = remember(sheetState, prefersScrollingExpandsWhenScrolledToEdge) {
                    object : NestedScrollConnection {
                        private var contentAtTop = true
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (!currentVisible || source != NestedScrollSource.UserInput || available.y >= 0f ||
                                !prefersScrollingExpandsWhenScrolledToEdge || sheetState.offset <= currentLarge
                            ) return Offset.Zero
                            return Offset(0f, sheetState.dragBy(available.y, allowOverdrag = false))
                        }

                        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                            if (!currentVisible || source != NestedScrollSource.UserInput) return Offset.Zero
                            if (consumed.y != 0f && available.y == 0f) contentAtTop = false
                            if (available.y > 0f) contentAtTop = true
                            if (available.y == 0f) return Offset.Zero
                            if (available.y < 0f && (!prefersScrollingExpandsWhenScrolledToEdge ||
                                sheetState.offset <= currentLarge)) return Offset.Zero
                            val consumed = sheetState.dragBy(available.y, allowOverdrag = available.y > 0f)
                            // The interactor owns resisted input as well as displayed travel;
                            // forwarding the difference would also stretch the child scroller.
                            return Offset(0f, if (available.y > 0f && sheetState.isDragging) available.y else consumed)
                        }

                        override suspend fun onPreFling(available: Velocity): Velocity {
                            if (!sheetState.isDragging) return Velocity.Zero
                            val passesIntoContent = sheetState.offset <= currentLarge && (available.y < 0f || !contentAtTop)
                            sheetState.endDrag(if (passesIntoContent) 0f else available.y)
                            return if (passesIntoContent) Velocity.Zero else available
                        }

                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                            if (!sheetState.isDragging) return Velocity.Zero
                            sheetState.endDrag(available.y)
                            return available
                        }
                    }
                }
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer { alpha = sheetState.presentationProgress }
                        .background(Color.Black.copy(alpha = 0.2f))
                        .then(if (dismissOnClickOutside) Modifier.clickable(
                            interactionSource = null, indication = null, onClick = dismiss,
                        ) else Modifier),
                )
                val controllerTop = sheetState.presentationDetent?.let { anchors.offsetOf(it) }
                val frame = layout.frame(
                    controllerTop ?: if (sheetState.hasAnchors) sheetState.offset else anchors.hidden,
                    anchors.medium ?: anchors.large,
                    fullHeightDetentAvailable = !contentSized || anchors.large <= layout.fullTop,
                )
                val expansion = frame.expansionProgress
                val glass = remember(sheetState, contentSized) { Animatable(if (contentSized || !sheetState.skipPartiallyExpanded) 1f else 0f) }
                val largeBackground = expansion > 0.5f
                val closing = controllerTop != null && sheetState.targetValue == GlasmioSheetValue.Hidden
                LaunchedEffect(largeBackground, closing) {
                    glass.animateTo(
                        if (largeBackground && !closing) 0f else 1f,
                        if (controllerTop != null) IosSheetPresentationMotion else IosSheetMotion.materialSpringSpec(),
                    )
                }
                val surfaceHeight = if (contentSized) max(1f, effectiveHeight - layout.fullTop) else frame.naturalHeight
                val systemInsets = LocalView.current.rootWindowInsets
                val minimumBottom = with(density) { 20.dp.toPx() }
                val bottomLeft = max(minimumBottom,
                    (systemInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0) - frame.sideInset)
                val bottomRight = max(minimumBottom,
                    (systemInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0) - frame.sideInset)
                val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
                val shape = if (frame.capsularProgress > 0f) {
                    ContinuousRoundedRectangle(with(density) { (frame.naturalHeight / 2f).toDp() })
                } else ContinuousRoundedRectangle(
                    topStart = 38.dp,
                    topEnd = 38.dp,
                    bottomStart = with(density) { (if (isLtr) bottomLeft else bottomRight).toDp() },
                    bottomEnd = with(density) { (if (isLtr) bottomRight else bottomLeft).toDp() },
                )
                val dragInteractions = remember { SheetDragInteractions() }
                val contentMaterial = IosSheetMaterialContext(
                    isDark = colors.isDark,
                    vibrancy = when {
                        largeBackground && closing -> 1f - sheetState.presentationProgress
                        largeBackground -> 0f
                        else -> 1f
                    },
                )
                CompositionLocalProvider(LocalIosSheetMaterialContext provides contentMaterial) {
                GlasmioSheetSurface(
                    modifier = Modifier
                        .offset {
                            IntOffset(frame.sideInset.roundToInt(),
                                (frame.visibleTop + if (controllerTop != null) sheetState.offset - controllerTop else 0f).roundToInt())
                        }
                        .width(with(density) { widthPx.toDp() })
                        .graphicsLayer {
                            alpha = if (!sheetState.hasAnchors || contentSized && measuredContentHeight == 0) 0f else 1f
                            scaleX = frame.scale
                            scaleY = frame.scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                        .then(modifier)
                        .draggable(
                            state = rememberDraggableState { sheetState.dragBy(it) },
                            orientation = Orientation.Vertical,
                            enabled = visible,
                            interactionSource = dragInteractions,
                            startDragImmediately = sheetState.isAnimationRunning,
                            onDragStarted = { sheetState.beginDrag() },
                            onDragStopped = {
                                if (dragInteractions.cancelled) sheetState.cancelDrag() else sheetState.endDrag(it)
                            },
                        )
                        .nestedScroll(nestedScroll)
                        .semantics {
                            paneTitle = title
                            stateDescription = sheetState.currentValue.name
                            dismiss { dismiss(); true }
                            expand { scope.launch { sheetState.expand() }; true }
                            if (sheetState.hasMediumDetent) collapse {
                                scope.launch { sheetState.partialExpand() }; true
                            }
                        },
                    backdrop = backdrop,
                    shape = shape,
                    applyDragScale = false,
                    interactive = false,
                    glassFraction = glass.value,
                ) {
                    val chromeBackdrop = rememberLayerBackdrop()
                    val chromeColors = LocalGlassColors.current
                    val sheetBackplate = LocalIosSheetBackdrop.current
                    val appearance = LocalGlassAppearance.current
                    var toolbarHeight by remember(density.density) { mutableStateOf(with(density) { 54.dp.roundToPx() }) }
                    val handleHeight = 20.dp
                    val headerHeight = handleHeight + with(density) { toolbarHeight.toDp() }
                    val chromePadding = if (topBar != null) PaddingValues(
                        top = headerHeight,
                        bottom = with(density) { safeBottom.toDp() },
                    ) else PaddingValues()
                    Layout(
                        modifier = Modifier.clip(shape),
                        content = {
                            if (topBar == null) {
                                Column(Modifier.fillMaxWidth().windowInsetsPadding(navigationInsets)) {
                                    Box(Modifier.fillMaxWidth().height(handleHeight), contentAlignment = Alignment.TopCenter) {
                                        Box(
                                            Modifier.padding(top = 6.dp).size(60.dp, 4.dp)
                                                .background(colors.tertiaryContent.copy(alpha = 0.55f), Capsule()),
                                        )
                                    }
                                    CompositionLocalProvider(LocalGlasmioSheetContentPadding provides chromePadding) { content() }
                                }
                            } else {
                                Box(Modifier.fillMaxWidth()) {
                                    Box(
                                        (if (contentSized) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                                            .glassBackdropSource(chromeBackdrop)
                                            .trackBackdropPosition(chromeBackdrop),
                                    ) {
                                        Box(
                                            Modifier.matchParentSize()
                                                .then(if (sheetBackplate != null) Modifier.drawPlainBackdrop(
                                                    sheetBackplate, shape = { RectangleShape }, effects = {},
                                                ) else Modifier)
                                                .background(chromeColors.groupedBackground),
                                        ) { contentBackground?.invoke(this) }
                                        Column(
                                            (if (contentSized) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                                                .scrollEdgeContentFade(topBarBlurProgress, headerHeight),
                                        ) {
                                            CompositionLocalProvider(LocalGlasmioSheetContentPadding provides chromePadding) { content() }
                                        }
                                    }
                                    // Record the body only: the bar and grabber must never sample themselves.
                                    Box(
                                        Modifier.fillMaxWidth()
                                            .height(headerHeight + if (appearance.usesBackdrop) 34.dp else 0.dp)
                                            .then(if (appearance.usesBackdrop) Modifier.progressiveBlurBackdrop(
                                                backdrop = chromeBackdrop,
                                                progress = topBarBlurProgress,
                                            ) else Modifier.graphicsLayer {
                                                alpha = topBarBlurProgress.coerceIn(0f, 1f)
                                            }.background(chromeColors.groupedBackground.copy(alpha = 1f))),
                                    )
                                    Box(Modifier.fillMaxWidth().padding(top = handleHeight).onSizeChanged { toolbarHeight = it.height }) {
                                        topBar()
                                    }
                                    Box(Modifier.fillMaxWidth().height(handleHeight), contentAlignment = Alignment.TopCenter) {
                                        Box(Modifier.padding(top = 6.dp).size(60.dp, 4.dp)
                                            .background(colors.tertiaryContent.copy(alpha = 0.55f), Capsule()))
                                    }
                                }
                            }
                        },
                    ) { measurables, constraints ->
                        val height = surfaceHeight.roundToInt().coerceAtLeast(1)
                        val child = measurables.single().measure(Constraints(
                            minWidth = constraints.maxWidth,
                            maxWidth = constraints.maxWidth,
                            minHeight = if (contentSized) 0 else height,
                            maxHeight = height,
                        ))
                        if (contentSized && measuredContentHeight != child.height) measuredContentHeight = child.height
                        layout(child.width, child.height) { child.place(0, 0) }
                    }
                }
                }
            }
        }
    }
}
