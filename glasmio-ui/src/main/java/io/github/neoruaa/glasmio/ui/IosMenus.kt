package io.github.neoruaa.glasmio.ui

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.*

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.glass.InteractiveHighlight
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


private val LocalIosPopupMenuInteractive = staticCompositionLocalOf { true }

/** Overflow room around the menu so spring overshoot, velocity deformation, blur bleed and the
 *  drop shadow can draw past the resting bounds without being clipped by the popup host. The
 *  visible menu is offset back onto the anchor edge, so its position and physics are unchanged. */
private val PopupMenuOvershootMarginStart = 64.dp
private val PopupMenuOvershootMarginVertical = 32.dp
private val PopupMenuWidth = 238.dp

/** The stable shell is larger than the resting menu so the spring overshoot (up to 1.12x)
 *  fits inside the shell's constraints and actually renders. */
private const val PopupMenuOvershootScale = 1.15f

private class IosPopupPositionProvider(
    private val targetMenuHeightPx: Int,
    private val targetMenuWidthPx: Int,
    private val horizontalInsetPx: Int,
    private val forceBelowAnchor: Boolean,
    private val centerHorizontally: Boolean,
    private val alignToAnchorStart: Boolean,
    private val anchorAlignment: Alignment.Horizontal,
    private val horizontalOvershootMarginPx: Int,
    private val visualHostWidthPx: Int,
    private val visualHostHeightPx: Int,
    private val anchorOvershootVerticalPx: Int,
    private val onAnchorOriginResolved: (Float) -> Unit,
    private val onDirectionResolved: (opensAbove: Boolean) -> Unit,
) : PopupPositionProvider {
    private var opensAbove: Boolean? = null

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val resolvedDirection = if (forceBelowAnchor) {
            false
        } else {
            opensAbove ?: run {
                val roomBelow = windowSize.height - anchorBounds.bottom
                val roomAbove = anchorBounds.top
                (roomBelow < targetMenuHeightPx && roomAbove > roomBelow).also {
                    opensAbove = it
                    onDirectionResolved(it)
                }
            }
        }
        // Constrain the visible menu, not its transparent animation shell. The shell may
        // start offscreen; clamping it to zero pushes wider menus away from their anchor.
        val inset = horizontalInsetPx.coerceAtMost(
            ((windowSize.width - targetMenuWidthPx) / 2).coerceAtLeast(0),
        )
        val requestedLeft = if (centerHorizontally) {
            (windowSize.width - targetMenuWidthPx) / 2
        } else if (alignToAnchorStart) {
            if (layoutDirection == LayoutDirection.Rtl) anchorBounds.right - targetMenuWidthPx else anchorBounds.left
        } else {
            anchorBounds.right - targetMenuWidthPx
        }
        val menuLeft = requestedLeft.coerceIn(
            inset,
            (windowSize.width - targetMenuWidthPx - inset).coerceAtLeast(inset),
        )
        val anchorX = anchorBounds.left + anchorAlignment.align(0, anchorBounds.width, layoutDirection)
        onAnchorOriginResolved((anchorX - menuLeft).toFloat() / targetMenuWidthPx.coerceAtLeast(1))
        val menuLeftInHost = if (layoutDirection == LayoutDirection.Rtl) {
            horizontalOvershootMarginPx
        } else {
            visualHostWidthPx - targetMenuWidthPx
        }
        val x = menuLeft - menuLeftInHost
        // The bottom-aligned inner host leaves one fixed margin above the resting menu.
        // The spring overshoot space sits below it and must not shift the menu over its anchor.
        val menuTopInHost = anchorOvershootVerticalPx
        val visualY = if (forceBelowAnchor) {
            anchorBounds.bottom - menuTopInHost
        } else if (resolvedDirection) {
            anchorBounds.bottom - visualHostHeightPx
        } else {
            anchorBounds.top
        }.coerceIn(0, (windowSize.height - visualHostHeightPx).coerceAtLeast(0))
        val y = if (forceBelowAnchor || resolvedDirection) {
            visualY
        } else {
            visualY - anchorOvershootVerticalPx
        }
        return IntOffset(x, y)
    }
}

/** Scrollable iOS capsule tabs for sets that cannot fit a segmented control. */

@Composable
fun GlasmioContextMenu(
    visible: Boolean,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalBlurBackdrop.current,
    animationProgress: Float = 1f,
    transformOriginProgress: Float = animationProgress,
    menuAlpha: Float = animationProgress,
    contentAlpha: Float = animationProgress,
    shadowAlpha: Float = animationProgress,
    highlightAlpha: Float? = null,
    opensAbove: Boolean = false,
    itemCount: Int = 1,
    compact: Boolean = false,
    heightOverride: androidx.compose.ui.unit.Dp? = null,
    menuWidth: androidx.compose.ui.unit.Dp = PopupMenuWidth,
    menuScale: () -> Float = { 1f },
    exportChildBackdrop: Boolean = true,
    initialTransformOrigin: TransformOrigin? = null,
    onMenuBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    morphMotion: LiquidMenuMorphMotion? = null,
    morphSourceAcquired: Boolean = true,
    morphSourcePortal: ToolbarMenuPortal? = null,
    morphShaderPrograms: LiquidMenuShaderPrograms? = null,
    onMorphReady: ((Rect) -> Unit)? = null,
    content: @Composable ColumnScope.(LayerBackdrop) -> Unit,
) {
    if (!visible) return
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val progress = animationProgress.coerceIn(0f, 1f)
    val menuHeight = heightOverride ?: (20.dp + 44.dp * itemCount)
    val radius = 34.dp
    var menuScreenOrigin by remember { mutableStateOf<Offset?>(null) }
    val menuShape = remember(radius) { ContinuousRoundedRectangle(radius) }
    val transitionScale = 0.24f + 0.76f * animationProgress
    val startOrigin = initialTransformOrigin ?: TransformOrigin(1f, if (opensAbove) 1f else 0f)
    val originProgress = transformOriginProgress.coerceIn(0f, 1f)
    val transitionOrigin = TransformOrigin(
        pivotFractionX = startOrigin.pivotFractionX +
            (0.5f - startOrigin.pivotFractionX) * originProgress,
        pivotFractionY = startOrigin.pivotFractionY +
            (0.5f - startOrigin.pivotFractionY) * originProgress,
    )
    val appearance = LocalGlassAppearance.current
    val innerShadows = rememberNavigationInnerShadows(true)
    val pressProgress = remember(interactiveHighlight) {
        derivedStateOf { interactiveHighlight.pressProgress }
    }
    val childBackdrop = rememberLayerBackdrop()
    var samplingCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val samplingCoordinatesProvider = remember { { samplingCoordinates } }
    // Sample from the stable host, then compensate the live menu transform explicitly.
    // The animated graphics layer's coordinates can lag behind its current draw transform.
    val samplingBackdrop = if (appearance.usesBackdrop) {
        rememberStationaryBackdrop(backdrop, samplingCoordinatesProvider)
    } else {
        emptyBackdrop()
    }
    val colors = LocalGlassColors.current
    val isLight = !colors.isDark
    val menuLayerBlock: GraphicsLayerScope.() -> Unit = {
        val resolvedMenuScale = menuScale()
        transformOrigin = startOrigin
        applyGlassDragScale(
            pressProgress = interactiveHighlight.pressProgress,
            offset = interactiveHighlight.offset,
        )
        val dragScaleX = scaleX
        val dragScaleY = scaleY
        val dragTranslationX = translationX
        val dragTranslationY = translationY
        val startPivotX = startOrigin.pivotFractionX * size.width
        val startPivotY = startOrigin.pivotFractionY * size.height
        val transitionPivotX = transitionOrigin.pivotFractionX * size.width
        val transitionPivotY = transitionOrigin.pivotFractionY * size.height

        // Share the complete transition and drag transform with the stationary sampler.
        transformOrigin = transitionOrigin
        scaleX = transitionScale * dragScaleX * resolvedMenuScale
        scaleY = transitionScale * dragScaleY * resolvedMenuScale
        // Apply the rear-menu scale around the opening corner while preserving the
        // existing entrance animation's moving pivot when menuScale is 1.
        translationX = resolvedMenuScale * transitionScale * (
            dragTranslationX + (1f - dragScaleX) * (startPivotX - transitionPivotX)
        ) + (1f - resolvedMenuScale) * (startPivotX - transitionPivotX)
        translationY = resolvedMenuScale * transitionScale * (
            dragTranslationY + (1f - dragScaleY) * (startPivotY - transitionPivotY)
        ) + (1f - resolvedMenuScale) * (startPivotY - transitionPivotY)
    }
    // Stable full-size host: keep overshoot margins on both the growth and anchor sides in the
    // measured Popup bounds. The original visual host is nested inside it, so this only enlarges
    // the RenderEffect backing area and does not move the visible menu.
    val popupHostWidth = if (compact) menuWidth else PopupMenuOvershootMarginStart * 2 + menuWidth * PopupMenuOvershootScale
    val popupHostHeight = if (compact) menuHeight else PopupMenuOvershootMarginVertical * 2 + menuHeight * PopupMenuOvershootScale
    Box(
        modifier
            // Keep the shell's intended width even when Android constrains the popup window.
            // Start alignment prevents implicit centering from changing the visible offset.
            .wrapContentWidth(androidx.compose.ui.AbsoluteAlignment.Left, unbounded = true)
            .requiredWidth(popupHostWidth)
            // Keep the transition RenderEffect on the stable Popup host. A blur modifier
            // creates a bounded layer even with Unbounded edge treatment, so it must stay
            // outside the spring-sized menu to avoid exposing a menu-sized rectangle.
            .blur(
                if (morphMotion == null) (8.dp * (1f - progress)).coerceAtLeast(0.dp) else 0.dp,
                BlurredEdgeTreatment.Unbounded,
            )
            .graphicsLayer {
                alpha = if (morphMotion != null && (!morphMotion.initialized || !morphSourceAcquired)) 0f else menuAlpha.coerceIn(0f, 1f)
            }
            .size(width = popupHostWidth, height = popupHostHeight),
    ) {
        Box(
            Modifier
                .align(if (opensAbove) Alignment.TopStart else Alignment.BottomStart)
                .size(
                    width = if (compact) menuWidth else PopupMenuOvershootMarginStart + menuWidth * PopupMenuOvershootScale,
                    height = if (compact) menuHeight else PopupMenuOvershootMarginVertical + menuHeight * PopupMenuOvershootScale,
                ),
        ) {
            Box(
                Modifier
                    .align(if (opensAbove) Alignment.BottomEnd else Alignment.TopEnd)
                    .size(width = menuWidth, height = menuHeight)
                    .onGloballyPositioned { coordinates ->
                        samplingCoordinates = coordinates
                        menuScreenOrigin = coordinates.localToScreen(Offset.Zero)
                        onMorphReady?.invoke(Rect(coordinates.localToScreen(Offset.Zero), Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())))
                        // Measure outside the animated glass layer. Capturing a pressed row
                        // includes transient drag scale and gives the child a stale endpoint.
                        val origin = coordinates.localToScreen(androidx.compose.ui.geometry.Offset.Zero)
                        onMenuBoundsChanged?.invoke(
                            androidx.compose.ui.geometry.Rect(
                                origin,
                                Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
                            ),
                        )
                    },
            ) {
                if (morphMotion != null && morphMotion.initialized && menuScreenOrigin != null) {
                    LiquidMenuMorphSurface(
                        motion = morphMotion,
                        screenOrigin = menuScreenOrigin!!,
                        backdrop = backdrop,
                        exportChildBackdrop = exportChildBackdrop,
                        sourcePortal = morphSourcePortal,
                        shaderPrograms = morphShaderPrograms,
                        menuScale = menuScale,
                        transformOrigin = startOrigin,
                        highlight = interactiveHighlight,
                        content = content,
                    )
                } else Column(
                    Modifier
                        .fillMaxSize()
                        .navigationGlassBackground(
                            backdrop = samplingBackdrop,
                            exportedBackdrop = childBackdrop.takeIf { exportChildBackdrop && appearance.usesBackdrop },
                            shape = { menuShape },
                            containerColor = colors.container,
                            navigationMaterial = true,
                            innerShadows = innerShadows,
                            isDark = colors.isDark,
                            blurRadius = if (isLight) 16.dp else 12.dp,
                            appearance = appearance.copy(liquidGlassEnabled = false),
                            pressProgressState = pressProgress,
                            highlightAlphaMultiplier = highlightAlpha ?: progress,
                            shadowAlpha = shadowAlpha,
                            dropShadow = Shadow(
                                radius = 32.dp,
                                offset = androidx.compose.ui.unit.DpOffset(0.dp, 10.dp),
                                color = Color.Black,
                                alpha = 0.4f * GlassBoxShadowAlpha,
                            ),
                            layerBlock = menuLayerBlock,
                        )
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                        .clip(menuShape)
                        .padding(10.dp)
                        .graphicsLayer { alpha = contentAlpha.coerceIn(0f, 1f) },
                ) {
                    content(childBackdrop)
                }
            }
        }
    }
}

/**
 * Keeps one Popup and one connected source through opening, retargeting and collapse.
 * Toolbar menus use independent liquid geometry and optical channels; other anchors retain
 * the compact popup transition. The source returns only after the physical surface settles.
 */
@Composable
fun GlasmioMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    itemCount: Int,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalBlurBackdrop.current,
    keepAnchorVisible: Boolean = false,
    forceBelowAnchor: Boolean = false,
    fullScreen: Boolean = false,
    centerHorizontally: Boolean = false,
    alignToAnchorStart: Boolean = false,
    expandFromAnchor: Alignment.Horizontal? = null,
    externalAnchorBounds: androidx.compose.ui.geometry.Rect? = null,
    menuWidth: androidx.compose.ui.unit.Dp = PopupMenuWidth,
    menuScale: () -> Float = { 1f },
    exportChildBackdrop: Boolean = true,
    heightOverride: androidx.compose.ui.unit.Dp? = null,
    onMenuBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    onMenuAlphaChanged: ((Float) -> Unit)? = null,
    menuAnchorState: GlasmioMenuAnchorState? = null,
    onClosed: (() -> Unit)? = null,
    overlayContent: @Composable BoxScope.(() -> Float) -> Unit = {},
    anchor: @Composable (onClick: () -> Unit) -> Unit,
    content: @Composable ColumnScope.(LayerBackdrop, close: () -> Unit) -> Unit,
) {
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    var popupAlive by remember { mutableStateOf(false) }
    var transitionStarted by remember { mutableStateOf(false) }
    var opensAbove by remember { mutableStateOf(false) }
    var anchorFractionX by remember { mutableFloatStateOf(1f) }
    val progress = remember { Animatable(0f) }
    val transformOriginProgress = remember { Animatable(0f) }
    val menuAlpha = remember { Animatable(0f) }
    val shadowAlpha = remember { Animatable(0f) }
    val localMenuAnchor = rememberGlasmioMenuAnchorState()
    val sourceAnchor = menuAnchorState ?: localMenuAnchor
    val toolbarGroup = LocalIosToolbarActionGroup.current
    LaunchedEffect(sourceAnchor, toolbarGroup) {
        if (toolbarGroup != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            sourceAnchor.prewarmShaders()
        }
    }
    val hostView = LocalView.current
    val density = LocalDensity.current
    val liquidMotion = remember { LiquidMenuMorphMotion() }
    val morphsToolbar = menuAnchorState != null || toolbarGroup != null
    var windowScreenOffset by remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(sourceAnchor) { onDispose { sourceAnchor.release() } }
    var morphSourceBounds by remember { mutableStateOf<Rect?>(null) }
    var surfaceReady by remember { mutableStateOf(false) }
    var measuredMorphTarget by remember { mutableStateOf<Rect?>(null) }
    var positionAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    val usesMorph = morphSourceBounds != null
    val resolvedFullScreen = fullScreen || usesMorph
    val contentAlpha = if (usesMorph) {
        ((progress.value - 0.12f) / 0.64f).coerceIn(0f, 1f)
    } else progress.value.coerceIn(0f, 1f)

    LaunchedEffect(expanded) {
        if (expanded) {
            transitionStarted = true
            if (!popupAlive) {
                surfaceReady = false
                measuredMorphTarget = null
                positionAnchorBounds = externalAnchorBounds
                if (morphsToolbar) {
                    val source = snapshotFlow { sourceAnchor.bounds?.takeUnless { it.isEmpty } }.first { it != null }!!
                    val screen = IntArray(2)
                    val window = IntArray(2)
                    hostView.getLocationOnScreen(screen)
                    hostView.getLocationInWindow(window)
                    windowScreenOffset = Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat())
                    morphSourceBounds = source.translate(windowScreenOffset)
                }
            }
            popupAlive = true
            if (morphSourceBounds != null) {
                snapshotFlow { surfaceReady }.first { it }
                if (!sourceAnchor.isAcquired || !liquidMotion.initialized) {
                    morphSourceBounds = sourceAnchor.acquire()?.translate(windowScreenOffset) ?: morphSourceBounds
                    liquidMotion.initialize(
                        morphSourceBounds!!,
                        requireNotNull(measuredMorphTarget),
                        with(density) { 34.dp.toPx() },
                        density.density,
                        through = sourceAnchor.previewBounds?.translate(windowScreenOffset) ?: morphSourceBounds!!,
                    )
                }
                // Reopening during collapse retains the live contour and every velocity.
            }
            coroutineScope {
                if (morphSourceBounds != null) launch {
                    progress.snapTo(1f)
                    transformOriginProgress.snapTo(1f)
                    shadowAlpha.snapTo(1f)
                    liquidMotion.animateTo(true)
                } else launch {
                    progress.animateTo(1f, spring(
                        // AnimationKit liquidMorph: response 0.3 s, mass normalized to 1.
                        dampingRatio = 0.8f,
                        stiffness = 438.65f,
                        visibilityThreshold = 0.001f,
                    ))
                }
                if (morphSourceBounds == null) {
                    launch { transformOriginProgress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 500f)) }
                    launch { shadowAlpha.animateTo(1f, tween(200)) }
                }
                launch { menuAlpha.animateTo(1f, tween(120)) }
            }
        } else {
            if (popupAlive) coroutineScope {
                if (morphSourceBounds != null && liquidMotion.initialized) launch {
                    sourceAnchor.liveBounds?.let {
                        liquidMotion.updateSource(
                            it.translate(windowScreenOffset),
                            through = sourceAnchor.previewBounds?.translate(windowScreenOffset) ?: it.translate(windowScreenOffset),
                        )
                    }
                    liquidMotion.animateTo(false)
                }
                if (morphSourceBounds == null) {
                    launch { progress.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 438.65f, visibilityThreshold = 0.001f)) }
                    launch { transformOriginProgress.animateTo(0f, tween(300)) }
                    launch { shadowAlpha.animateTo(0f, tween(160)) }
                }
                launch { menuAlpha.animateTo(0f, tween(300)) }
            }
            sourceAnchor.release()
            popupAlive = false
            morphSourceBounds = null
            surfaceReady = false
            if (transitionStarted) {
                transitionStarted = false
                onClosed?.invoke()
            }
        }
    }

    val currentAlphaCallback by androidx.compose.runtime.rememberUpdatedState(onMenuAlphaChanged)
    LaunchedEffect(menuAlpha) {
        snapshotFlow { menuAlpha.value.coerceIn(0f, 1f) }
            .collect { currentAlphaCallback?.invoke(it) }
    }

    val anchorFadePaint = remember { Paint() }

    Box(modifier.onSizeChanged { anchorSize = it }.onGloballyPositioned {
        if (menuAnchorState == null) localMenuAnchor.bind(toolbarGroup, it.boundsInWindow())
    }) {
        Box(
            Modifier
                .drawWithContent {
                    val alpha = if (sourceAnchor.isAcquired && !sourceAnchor.isGroupedSource) {
                        0f
                    } else if (keepAnchorVisible || usesMorph) {
                        1f
                    } else {
                        1f - menuAlpha.value.coerceIn(0f, 1f)
                    }
                    when {
                        alpha <= 0f -> Unit
                        alpha >= 1f -> drawContent()
                        else -> {
                            anchorFadePaint.alpha = alpha
                            drawContext.canvas.saveLayer(
                                androidx.compose.ui.geometry.Rect(
                                    left = 0f,
                                    top = 0f,
                                    right = size.width,
                                    bottom = size.height,
                                ),
                                anchorFadePaint,
                            )
                            drawContent()
                            drawContext.canvas.restore()
                        }
                    }
                },
        ) {
            CompositionLocalProvider(
                LocalToolbarActionSeparation provides { 0f },
            ) {
                anchor { onExpandedChange(!expanded) }
            }
        }
        if (popupAlive && (anchorSize != IntSize.Zero || externalAnchorBounds != null)) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val menuHeight = heightOverride ?: (20.dp + 44.dp * itemCount)
            val targetMenuHeightPx = with(density) { menuHeight.roundToPx() }
            val targetMenuWidthPx = with(density) { menuWidth.roundToPx() }
            val horizontalInsetPx = with(density) { 12.dp.roundToPx() }
            val visualHostWidthPx = with(density) {
                (PopupMenuOvershootMarginStart + menuWidth * PopupMenuOvershootScale).roundToPx()
            }
            val visualHostHeightPx = with(density) {
                (
                    PopupMenuOvershootMarginVertical +
                        menuHeight * PopupMenuOvershootScale
                ).roundToPx()
            }
            // Popup tracks live anchor bounds; caption resizing must not reset its
            // resolved offset and briefly dispose the content during collapse.
            val positionProvider = remember(
                targetMenuHeightPx,
                targetMenuWidthPx,
                horizontalInsetPx,
                forceBelowAnchor,
                centerHorizontally,
                alignToAnchorStart,
                expandFromAnchor,
                visualHostWidthPx,
                visualHostHeightPx,
            ) {
                IosPopupPositionProvider(
                    targetMenuHeightPx = targetMenuHeightPx,
                    targetMenuWidthPx = targetMenuWidthPx,
                    horizontalInsetPx = horizontalInsetPx,
                    forceBelowAnchor = forceBelowAnchor,
                    centerHorizontally = centerHorizontally,
                    alignToAnchorStart = alignToAnchorStart,
                    anchorAlignment = expandFromAnchor ?: Alignment.Start,
                    horizontalOvershootMarginPx = with(density) { PopupMenuOvershootMarginStart.roundToPx() },
                    visualHostWidthPx = visualHostWidthPx,
                    visualHostHeightPx = visualHostHeightPx,
                    anchorOvershootVerticalPx = with(density) {
                        PopupMenuOvershootMarginVertical.roundToPx()
                    },
                    onAnchorOriginResolved = { anchorFractionX = it },
                ) { resolved ->
                    opensAbove = resolved
                }
            }
            var resolvedFullScreenMenuOffset by remember(positionProvider, positionAnchorBounds, resolvedFullScreen) {
                mutableStateOf<IntOffset?>(null)
            }
            val popupPositionProvider = remember(positionProvider, positionAnchorBounds, resolvedFullScreen) {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        val menuOffset = positionProvider.calculatePosition(
                            positionAnchorBounds?.let {
                                IntRect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
                            } ?: anchorBounds,
                            windowSize,
                            layoutDirection,
                            popupContentSize,
                        )
                        return if (resolvedFullScreen) {
                            resolvedFullScreenMenuOffset = menuOffset
                            IntOffset.Zero
                        } else {
                            menuOffset
                        }
                    }
                }
            }
            Popup(
                popupPositionProvider = popupPositionProvider,
                onDismissRequest = { onExpandedChange(false) },
                properties = PopupProperties(
                    focusable = expanded,
                    dismissOnBackPress = expanded,
                    dismissOnClickOutside = expanded,
                    clippingEnabled = false,
                ),
            ) {
                // The overshoot margin is invisible window area; treat taps there like
                // outside taps (dismiss) instead of letting them vanish into the window.
                Box(
                    Modifier
                        .then(if (resolvedFullScreen) Modifier.fillMaxSize() else Modifier),
                    contentAlignment = androidx.compose.ui.AbsoluteAlignment.TopLeft,
                ) {
                    // The dismiss target is a sibling so it never merges submenu pane semantics.
                    Box(
                        Modifier.matchParentSize().clickable(
                            interactionSource = null,
                            indication = null,
                        ) { onExpandedChange(false) },
                    )
                    CompositionLocalProvider(LocalIosPopupMenuInteractive provides expanded) {
                        // Wide cascading menus exceed the platform Popup's measured width.
                        // Keep their window at screen size and position the existing animation
                        // shell inside it so its right-hand shadow is not cut off by the window.
                        if (resolvedFullScreen && resolvedFullScreenMenuOffset == null) return@CompositionLocalProvider
                        GlasmioContextMenu(
                            visible = true,
                            modifier = if (resolvedFullScreen) {
                                Modifier.absoluteOffset { resolvedFullScreenMenuOffset ?: IntOffset.Zero }
                            } else {
                                Modifier
                            },
                            backdrop = backdrop,
                            animationProgress = progress.value,
                            transformOriginProgress = transformOriginProgress.value,
                            menuAlpha = if (usesMorph) 1f else menuAlpha.value,
                            contentAlpha = contentAlpha,
                            shadowAlpha = shadowAlpha.value,
                            opensAbove = opensAbove,
                            itemCount = itemCount,
                            heightOverride = heightOverride,
                            menuScale = menuScale,
                            exportChildBackdrop = exportChildBackdrop,
                            initialTransformOrigin = if (expandFromAnchor != null) {
                                TransformOrigin(anchorFractionX, if (opensAbove) 1f else 0f)
                            } else null,
                            menuWidth = menuWidth,
                            onMenuBoundsChanged = onMenuBoundsChanged,
                            morphMotion = liquidMotion.takeIf { usesMorph },
                            morphSourceAcquired = sourceAnchor.isAcquired,
                            morphSourcePortal = sourceAnchor.portal,
                            morphShaderPrograms = sourceAnchor.shaderPrograms,
                            onMorphReady = { target ->
                                if (usesMorph && !surfaceReady) measuredMorphTarget = target
                                surfaceReady = true
                            },
                        ) { childBackdrop ->
                            content(childBackdrop) { onExpandedChange(false) }
                        }
                    }
                    overlayContent { menuAlpha.value.coerceIn(0f, 1f) }
                }
            }
        }
    }
}

@Composable
fun <T> GlasmioPopUpButton(
    selected: T,
    items: List<T>,
    onSelected: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: Backdrop = LocalBlurBackdrop.current,
) {
    var expanded by remember { mutableStateOf(false) }
    GlasmioMenu(
        expanded = expanded,
        onExpandedChange = { if (enabled || !it) expanded = it },
        itemCount = items.size,
        modifier = modifier,
        backdrop = backdrop,
        anchor = { openMenu ->
            Row(
                Modifier
                    .clickable(
                        enabled = enabled,
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = openMenu,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label(selected),
                    style = IosTypography.body,
                    color = if (enabled) LocalGlassColors.current.accent
                    else LocalGlassColors.current.secondaryContent,
                )
                SfIcon(
                    "chevron.up.chevron.down",
                    contentDescription = null,
                    size = 15.dp,
                    tint = if (enabled) LocalGlassColors.current.accent
                    else LocalGlassColors.current.secondaryContent,
                    modifier = Modifier.padding(start = 7.dp),
                    weight = FontWeight.Bold
                )
            }
        },
    ) { childBackdrop, close ->
        items.forEach { item ->
            GlasmioMenuItem(
                title = label(item),
                onClick = {
                    onSelected(item)
                    close()
                },
                systemName = if (item == selected) "checkmark" else null,
                backdrop = childBackdrop,
            )
        }
    }
}

@Composable
fun GlasmioMenuItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    systemName: String? = null,
    destructive: Boolean = false,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    enabled: Boolean = true,
    iconTint: Color? = null,
    fontWeight: FontWeight? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val interactive = LocalIosPopupMenuInteractive.current
    val highlight = remember(scope) { InteractiveHighlight(scope) }
    Row(
        modifier
            .fillMaxWidth()
            .height(if (subtitle == null) 44.dp else 56.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .semantics(mergeDescendants = true) {
                if (!enabled) disabled()
            }
            .background(
                color = Color.Black.copy(alpha = 0.15f * highlight.pressProgress),
                shape = Capsule(),
            )
            .then(
                if (interactive && enabled) {
                    Modifier
                        .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
                        .then(highlight.gestureModifier)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Fixed-width gutter keeps every title's left edge aligned, whether or not the
        // row shows a checkmark (iOS context-menu alignment).
        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            if (systemName != null) {
                SfIcon(systemName, null, size = 20.dp, tint = iconTint ?: if (destructive) LocalGlassColors.current.destructive else LocalGlassColors.current.content)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, style = IosTypography.body,
                fontWeight = fontWeight, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (destructive) LocalGlassColors.current.destructive else LocalGlassColors.current.content,
            )
            subtitle?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = LocalGlassColors.current.secondaryContent,
                )
            }
        }
        trailing?.invoke()
    }
}

