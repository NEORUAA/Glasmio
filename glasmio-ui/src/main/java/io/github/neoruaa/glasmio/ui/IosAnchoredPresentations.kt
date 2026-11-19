package io.github.neoruaa.glasmio.ui

import android.view.WindowManager
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.round
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassAppearance
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.glasmioSafeDrawing
import io.github.neoruaa.glasmio.glass.LocalBlurBackdrop
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import io.github.neoruaa.glasmio.glass.NavigationGlassOutlineShape
import io.github.neoruaa.glasmio.glass.navigationGlassBackground
import io.github.neoruaa.glasmio.glass.navigationRegularGlassShadowPadding
import io.github.neoruaa.glasmio.glass.rememberNavigationInnerShadows
import io.github.neoruaa.glasmio.glass.rememberStationaryBackdrop
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.ProvideGlassMaterialContext
import io.github.neoruaa.glasmio.glass.render.drawBackdrop
import io.github.neoruaa.glasmio.glass.render.effects.blur
import io.github.neoruaa.glasmio.glass.render.isRuntimeShaderSupported
import io.github.neoruaa.glasmio.glass.render.rememberGlassMaterialContext
import io.github.neoruaa.glasmio.glass.render.shadow.Shadow
import io.github.neoruaa.glasmio.icons.SfIcon
import kotlin.math.ceil
import kotlinx.coroutines.delay

private val TooltipSides = listOf(AnchoredSide.Above, AnchoredSide.Below)
private val PopoverSides = listOf(AnchoredSide.Above, AnchoredSide.Right, AnchoredSide.Below, AnchoredSide.Left)
private val RtlPopoverSides = listOf(AnchoredSide.Above, AnchoredSide.Left, AnchoredSide.Below, AnchoredSide.Right)
private val TooltipShape = RoundedCornerShape(9.dp)
private val PopoverPointerDepth = 9.dp
private val PopoverVerticalPadding = 14.dp
private val PopoverShadowSpace = 28.dp
private val PassivePopupProperties = PopupProperties(
    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    dismissOnBackPress = false,
    dismissOnClickOutside = false,
    excludeFromSystemGesture = false,
)

/** Attaches a short, noninteractive hint to [anchor]. Hover or touch and hold to show it. */
@Composable
fun GlasmioTooltip(
    text: String,
    modifier: Modifier = Modifier,
    anchor: @Composable () -> Unit,
) {
    if (text.isBlank()) {
        Box(modifier, propagateMinConstraints = true) { anchor() }
        return
    }
    var hovered by remember { mutableStateOf(false) }
    var hoverVisible by remember { mutableStateOf(false) }
    var touchRequest by remember { mutableIntStateOf(0) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(hovered, windowFocused) {
        hoverVisible = false
        if (hovered && windowFocused) {
            delay(650)
            hoverVisible = true
            delay(3_000)
            hoverVisible = false
        }
    }
    LaunchedEffect(touchRequest, windowFocused) {
        if (windowFocused && touchRequest != 0) delay(2_500)
        touchRequest = 0
    }
    val colors = LocalGlassColors.current
    Box(
        modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.type == PointerType.Mouse }) continue
                        when (event.type) {
                            PointerEventType.Enter -> hovered = true
                            PointerEventType.Exit -> hovered = false
                            PointerEventType.Press -> hovered = false
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (down.type != PointerType.Touch && down.type != PointerType.Stylus) return@awaitEachGesture
                    touchRequest = 0
                    val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val pointer = event.changes.firstOrNull { it.id == down.id }
                            if (pointer == null || !pointer.pressed || event.changes.count { it.pressed } != 1 ||
                                (pointer.previousPressed && pointer.isConsumed) ||
                                (pointer.position - down.position).getDistance() > viewConfiguration.touchSlop
                            ) break
                        }
                        true
                    } ?: false
                    if (!cancelled) {
                        touchRequest++
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
            }
            .semantics(mergeDescendants = true) {
                customActions = listOf(CustomAccessibilityAction("Show tooltip") {
                    touchRequest++
                    true
                })
            },
        propagateMinConstraints = true,
    ) {
        anchor()
        AnchoredPopup(visible = windowFocused && (touchRequest != 0 || hoverVisible), tooltip = true) { _, bounds, _ ->
            Text(
                text = text,
                modifier = Modifier
                    .widthIn(max = minOf(232.dp, bounds.width))
                    .heightIn(max = bounds.height)
                    .dropShadow(TooltipShape) {
                        radius = 8.dp.toPx()
                        offset = Offset(0f, 3.dp.toPx())
                        color = Color.Black.copy(alpha = 0.22f)
                    }
                    .background(colors.elevatedBackground.copy(alpha = 1f), TooltipShape)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = colors.content,
                style = IosTypography.footnote,
            )
        }
    }
}

/**
 * Presents interactive [content] next to its [anchor]. Compact windows use a sheet by default.
 * Set [adaptToSheet] to false to keep the arrow in compact windows.
 * Content is size constrained to the available window and can supply its own scrolling.
 */
@Composable
fun GlasmioPopover(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    adaptToSheet: Boolean = true,
    maxWidth: Dp = 296.dp,
    title: String = "Popover",
    backdrop: Backdrop = LocalBlurBackdrop.current,
    anchor: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    require(maxWidth.value.isFinite() && maxWidth > 0.dp) { "Popover width must be finite and positive." }
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val compactWidth = with(density) { window.width.toDp() < 600.dp }
    val compactHeight = with(density) { window.height.toDp() < 480.dp }
    Box(modifier, propagateMinConstraints = true) {
        anchor()
        if (adaptToSheet && (compactWidth || compactHeight)) {
            GlasmioBottomSheet(
                visible = visible,
                onDismissRequest = onDismissRequest,
                title = title,
                backdrop = backdrop,
                topBar = if (compactHeight) {
                    {
                        GlasmioSheetTopToolbar(title, actions = {
                            GlasmioSheetToolbarButton(onClick = onDismissRequest) { SfIcon("xmark", "Close") }
                        })
                    }
                } else null,
            ) {
                Column(Modifier.padding(LocalGlasmioSheetContentPadding.current).padding(16.dp)) { content() }
            }
        } else {
            val appearance = LocalGlassAppearance.current
            val adaptiveAppearance = remember(appearance) { appearance.copy(adaptiveColorScheme = true) }
            CompositionLocalProvider(LocalGlassAppearance provides adaptiveAppearance) {
                GlasmioPopoverPopup(visible, onDismissRequest, maxWidth, title, backdrop, content)
            }
        }
    }
}

@Composable
private fun GlasmioPopoverPopup(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    maxWidth: Dp,
    title: String,
    backdrop: Backdrop,
    content: @Composable ColumnScope.() -> Unit,
) {
    val appearance = LocalGlassAppearance.current
    val usesRegularGlass = appearance.usesBackdrop && isRuntimeShaderSupported()
    AnchoredPopup(
        visible = visible,
        tooltip = false,
        onDismissRequest = onDismissRequest,
        usesRegularShadow = usesRegularGlass,
    ) { placement, bounds, transform ->
        val material = rememberGlassMaterialContext(inheritLuminance = false)
        val dark = material.isDark
        val bodyStyle = IosTypography.body
        val capsuleHeight = with(LocalDensity.current) {
            val textHeight = if (bodyStyle.lineHeight.isSp) bodyStyle.lineHeight.toPx() else 22.dp.toPx()
            ceil(textHeight).toInt() + PopoverVerticalPadding.roundToPx() * 2
        }
        val shape = remember(placement?.side, placement?.pointerOffset, capsuleHeight) {
            PopoverShape(placement, radius = 24.dp, pointerDepth = PopoverPointerDepth, capsuleHeight = capsuleHeight)
        }
        var samplingCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val sampling = rememberStationaryBackdrop(backdrop, remember { { samplingCoordinates } })
        val fill = if (dark) Color(0xFF202126) else Color(0xFFF6F7FA)
        val shadowColor = Color.Black.copy(alpha = if (dark) 0.35f else 0.15f)
        val surface = if (placement == null) {
            Modifier
        } else if (usesRegularGlass) {
            val opticalShape = remember(shape) { NavigationGlassOutlineShape(shape, bodyInset = PopoverPointerDepth) }
            Modifier.navigationGlassBackground(
                backdrop = sampling,
                shape = { opticalShape },
                containerColor = fill,
                navigationMaterial = true,
                innerShadows = rememberNavigationInnerShadows(enabled = true),
                layerBlock = transform,
                appearance = appearance,
                materialContext = material,
            )
        } else if (appearance.usesBackdrop) {
            Modifier.drawBackdrop(
                backdrop = sampling,
                shape = { shape },
                layerBlock = transform,
                luminanceState = material.luminanceState,
                effects = { blur(NavigationGlassMaterial.RegularMedium.frostRadius.toPx()) },
                shadow = { Shadow(radius = 18.dp, offset = DpOffset(0.dp, 6.dp), color = shadowColor) },
                highlight = null,
            )
        } else {
            Modifier.graphicsLayer(transform)
                .dropShadow(shape) {
                    radius = 18.dp.toPx()
                    offset = Offset(0f, 6.dp.toPx())
                    color = shadowColor
                }
                .background(fill, shape)
        }
        Box(Modifier.onGloballyPositioned { samplingCoordinates = it }) {
            Column(
                surface
                    .then(if (usesRegularGlass) Modifier else Modifier.popoverFallbackRim(shape, dark))
                    .padding(PopoverPointerDepth)
                    .widthIn(max = minOf(maxWidth, bounds.width))
                    .heightIn(max = bounds.height)
                    .padding(PaddingValues(horizontal = 16.dp, vertical = PopoverVerticalPadding))
                    .semantics { paneTitle = title },
            ) {
                ProvideGlassMaterialContext(material) {
                    CompositionLocalProvider(LocalContentColor provides material.colors.content) {
                        ProvideTextStyle(bodyStyle) { content() }
                    }
                }
            }
        }
    }
}

private fun Modifier.popoverFallbackRim(shape: PopoverShape, dark: Boolean): Modifier = drawWithCache {
    val path = (shape.createOutline(size, layoutDirection, this) as? Outline.Generic)?.path
    val edge = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = if (dark) 0.14f else 0.10f),
        1f to Color.Black.copy(alpha = if (dark) 0.22f else 0.17f),
    )
    val highlight = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.50f else 0.87f),
        0.52f to Color.White.copy(alpha = if (dark) 0.20f else 0.32f),
        1f to Color.White.copy(alpha = if (dark) 0.46f else 0.64f),
    )
    val edgeStroke = Stroke(width = 1.dp.toPx())
    val highlightStroke = Stroke(width = 1.5.dp.toPx())
    onDrawWithContent {
        drawContent()
        if (path != null) {
            drawPath(path, edge, style = edgeStroke)
            clipPath(path) { drawPath(path, highlight, style = highlightStroke) }
        }
    }
}

@Composable
private fun AnchoredPopup(
    visible: Boolean,
    tooltip: Boolean,
    onDismissRequest: (() -> Unit)? = null,
    usesRegularShadow: Boolean = false,
    content: @Composable (AnchoredPlacement?, DpSize, GraphicsLayerScope.() -> Unit) -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }.apply { targetState = visible }
    val transition = rememberTransition(visibility, label = if (tooltip) "Tooltip" else "Popover")
    val progress by transition.animateFloat(
        transitionSpec = {
            if (tooltip) tween(durationMillis = if (targetState) 150 else 100)
            else spring(
                dampingRatio = if (targetState) 0.8f else 0.85f,
                stiffness = if (targetState) 332f else 284f,
                visibilityThreshold = 0.002f,
            )
        },
        label = "Anchor scale",
    ) { if (it) 1f else 0f }
    if (!transition.currentState && !transition.targetState) return

    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val window = LocalWindowInfo.current.containerSize
    val view = LocalView.current
    val safe = WindowInsets.glasmioSafeDrawing
    val pointerSpace = if (tooltip) 0.dp else PopoverPointerDepth
    val pointerPx = with(density) { pointerSpace.roundToPx() }
    val baseShadowPx = with(density) { (if (tooltip) 16.dp else PopoverShadowSpace).roundToPx() }
    val baseGutter = baseShadowPx + pointerPx
    val edge = with(density) { 8.dp.roundToPx() }
    val left = safe.getLeft(density, direction) + edge
    val right = safe.getRight(density, direction) + edge
    val top = safe.getTop(density) + edge
    val bottom = safe.getBottom(density) + edge
    val bounds = with(density) {
        DpSize(
            (window.width - left - right - baseGutter * 2).coerceAtLeast(1).toDp(),
            (window.height - top - bottom - baseGutter * 2).coerceAtLeast(1).toDp(),
        )
    }
    val separation = with(density) { (if (tooltip) 7.dp else pointerSpace).roundToPx() }
    val pointerInset = with(density) { if (tooltip) 0 else 16.dp.roundToPx() }
    var anchorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Read the same parent coordinates as Popup so its position provider can identify the space used.
    Layout(
        content = {},
        modifier = Modifier.onGloballyPositioned { anchorCoordinates = it.parentLayoutCoordinates },
    ) { _, _ -> layout(0, 0) {} }
    var placement by remember { mutableStateOf<AnchoredPlacement?>(null) }
    var shadowPadding by remember(baseShadowPx) { mutableStateOf(IntOffset(baseShadowPx, baseShadowPx)) }
    val provider = remember(view, window, tooltip, left, top, right, bottom, baseShadowPx, pointerPx, separation, pointerInset) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val gutter = shadowPadding + IntOffset(pointerPx, pointerPx)
                val bodySize = IntSize(
                    (popupContentSize.width - gutter.x * 2).coerceAtLeast(1),
                    (popupContentSize.height - gutter.y * 2).coerceAtLeast(1),
                )
                val sides = when {
                    tooltip -> TooltipSides
                    layoutDirection == LayoutDirection.Ltr -> PopoverSides
                    else -> RtlPopoverSides
                }
                val coordinates = anchorCoordinates?.takeIf { it.isAttached }
                // Compose uses screen coordinates for nested popups and window coordinates otherwise.
                val origin = if (coordinates != null && anchorBounds.topLeft != coordinates.positionInWindow().round()) {
                    val screenPosition = coordinates.positionOnScreen()
                    if (screenPosition.isFinite && anchorBounds.topLeft == screenPosition.round()) {
                        val frame = view.context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
                        IntOffset(frame.left, frame.top)
                    } else IntOffset.Zero
                } else IntOffset.Zero
                val viewport = IntRect(left, top, windowSize.width - right, windowSize.height - bottom)
                val next = placeAnchoredSurface(
                    anchorBounds,
                    viewport.translate(origin),
                    bodySize, sides, separation, pointerInset, preferMaximumVisibleArea = !tooltip,
                )
                placement = next
                return next.body.topLeft - gutter
            }
        }
    }
    val currentDismissRequest by rememberUpdatedState(onDismissRequest?.takeIf { visible })
    // Android's platform Back callback can outlive recompositions of the popup.
    val dismissPopup: () -> Unit = remember { { currentDismissRequest?.invoke() } }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = if (tooltip) null else dismissPopup,
        properties = if (tooltip || !visible) PassivePopupProperties else PopupProperties(
            focusable = true,
            excludeFromSystemGesture = false,
            clippingEnabled = false,
        ),
    ) {
        val dismissGutter = if (visible && onDismissRequest != null) {
            Modifier.pointerInput(onDismissRequest, baseShadowPx, pointerPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val gutter = shadowPadding + IntOffset(pointerPx, pointerPx)
                    if (down.position.x < gutter.x || down.position.x >= size.width - gutter.x ||
                        down.position.y < gutter.y || down.position.y >= size.height - gutter.y
                    ) {
                        down.consume()
                        onDismissRequest()
                    }
                }
            }
        } else Modifier
        val transform: GraphicsLayerScope.() -> Unit = {
            val frame = placement
            val fraction = progress
            val startScale = if (tooltip) 0.96f else 0.02f
            scaleX = startScale + (1f - startScale) * fraction
            scaleY = scaleX
            if (!tooltip && frame != null && size.width > 0f && size.height > 0f) {
                val pivot = frame.tip - frame.body.topLeft + IntOffset(pointerPx, pointerPx)
                transformOrigin = TransformOrigin(pivot.x / size.width, pivot.y / size.height)
            }
        }
        Box(
            dismissGutter.graphicsLayer {
                alpha = if (placement == null) 0f else progress.coerceIn(0f, 1f)
                if (tooltip) transform(this)
            }.layout { measurable, constraints ->
                // Reserve extra space for the shadow only after measuring the original content.
                val child = measurable.measure(constraints.offset(-2 * baseShadowPx, -2 * baseShadowPx))
                val desired = if (usesRegularShadow) {
                    val bodySize = minOf(child.width, child.height) - 2 * pointerPx
                    maxOf(baseShadowPx, navigationRegularGlassShadowPadding(bodySize, this.density))
                } else baseShadowPx
                val horizontal = minOf(desired, (constraints.maxWidth - child.width) / 2)
                val vertical = minOf(desired, (constraints.maxHeight - child.height) / 2)
                shadowPadding = IntOffset(horizontal, vertical)
                layout(child.width + 2 * horizontal, child.height + 2 * vertical) {
                    child.place(horizontal, vertical)
                }
            },
        ) { content(placement, bounds, transform) }
    }
}

private val AnchoredPlacement.pointerOffset: Int
    get() = when (side) {
        AnchoredSide.Above, AnchoredSide.Below -> tip.x - body.left
        AnchoredSide.Left, AnchoredSide.Right -> tip.y - body.top
    }
