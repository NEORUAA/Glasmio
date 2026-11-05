package io.github.neoruaa.glasmio.scaffold

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FloatAnimationSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.LocalGlassAppearance
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import io.github.neoruaa.glasmio.glass.rememberRasterizedBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberSidebarBackgroundExtensionBackdrop
import io.github.neoruaa.glasmio.ui.GlasmioSidebar
import io.github.neoruaa.glasmio.ui.GlasmioSidebarMorphSurface
import io.github.neoruaa.glasmio.ui.GlasmioToolbarDefaults
import kotlin.math.roundToInt
import kotlin.math.exp
import kotlin.math.abs
import kotlinx.coroutines.currentCoroutineContext

/** UIKit's 500 ms critical spring, including its velocity-dependent duration conversion. */
internal object IosSidebarSpring : FloatAnimationSpec {
    private const val Duration = .5
    private const val DurationNanos = 500_000_000L
    private const val RestingOmega = 18.46682744880656

    private fun omega(start: Float, end: Float, velocity: Float): Double {
        if (velocity == 0f || start == end) return RestingOmega
        val normalized = velocity.toDouble() / (end - start)
        // UIViewSpringAnimationState's two Newton attempts use expf and epsilon .001.
        for (attempt in 0..1) {
            val initial = if (attempt == 0) 5.0 else 1.0
            val count = if (attempt == 0) 12 else 20
            var w = initial / Duration
            repeat(count) { index ->
                val value = (1.0 + (w - normalized) * Duration) * exp((-w * Duration).toFloat()).toDouble() - .001
                val derivative = Duration * Duration * (normalized - w) / exp((w * Duration).toFloat()).toDouble()
                val updated = w - value / derivative
                if (!updated.isFinite() || updated <= 0.0) return@repeat
                if (index >= 2 && abs(updated - w) <= .001) return updated
                w = updated
            }
        }
        return RestingOmega
    }

    override fun getDurationNanos(initialValue: Float, targetValue: Float, initialVelocity: Float): Long =
        if (initialValue == targetValue && initialVelocity == 0f) 0L else DurationNanos

    override fun getValueFromNanos(playTimeNanos: Long, initialValue: Float, targetValue: Float, initialVelocity: Float): Float {
        if (playTimeNanos <= 0) return initialValue
        if (playTimeNanos >= DurationNanos) return targetValue
        val t = playTimeNanos / 1e9
        val w = omega(initialValue, targetValue, initialVelocity)
        val x = initialValue.toDouble() - targetValue
        return (targetValue + (x + (initialVelocity + w * x) * t) * exp(-w * t)).toFloat()
    }

    override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: Float, targetValue: Float, initialVelocity: Float): Float {
        if (playTimeNanos <= 0) return initialVelocity
        if (playTimeNanos >= DurationNanos) return 0f
        val t = playTimeNanos / 1e9
        val w = omega(initialValue, targetValue, initialVelocity)
        val x = initialValue.toDouble() - targetValue
        return ((initialVelocity - w * (initialVelocity + w * x) * t) * exp(-w * t)).toFloat()
    }
}

private class SidebarPlacement { var width = 0; var overlays = false }
private class SidebarConsumedInsets(private val occupied: () -> Int) : WindowInsets {
    override fun getLeft(density: Density, layoutDirection: LayoutDirection) = if (layoutDirection == LayoutDirection.Ltr) occupied() else 0
    override fun getRight(density: Density, layoutDirection: LayoutDirection) = if (layoutDirection == LayoutDirection.Rtl) occupied() else 0
    override fun getTop(density: Density) = 0
    override fun getBottom(density: Density) = 0
}

enum class GlasmioNavigationLayout { BottomBar, TopTabs }
enum class GlasmioSidebarTransition { Automatic, SplitView }

/** MeiloX's orientation policy; hosts can explicitly select TopTabs for regular-width portrait. */
@Composable
fun glasmioNavigationLayout(): GlasmioNavigationLayout {
    val window = LocalConfiguration.current
    return if (window.screenWidthDp > window.screenHeightDp)
        GlasmioNavigationLayout.TopTabs else GlasmioNavigationLayout.BottomBar
}

@Stable
class GlasmioAdaptiveNavigationState internal constructor(expanded: Boolean) {
    var sidebarExpanded by mutableStateOf(expanded)
    val tabBarBounds = mutableStateOf<Rect?>(null)
    fun toggleSidebar() { sidebarExpanded = !sidebarExpanded }
}

@Composable
fun rememberGlasmioAdaptiveNavigationState(initiallyExpanded: Boolean = false): GlasmioAdaptiveNavigationState =
    rememberSaveable(saver = Saver(
        save = { it.sidebarExpanded },
        restore = { GlasmioAdaptiveNavigationState(it) },
    )) { GlasmioAdaptiveNavigationState(initiallyExpanded) }

val LocalGlasmioNavigationLayout = staticCompositionLocalOf { GlasmioNavigationLayout.BottomBar }
val LocalGlasmioTabBarBounds = staticCompositionLocalOf<State<Rect?>> { mutableStateOf(null) }
val LocalGlasmioSidebarInsets = staticCompositionLocalOf<WindowInsets> { WindowInsets(0, 0, 0, 0) }
val LocalGlasmioSidebarExpanded = compositionLocalOf { false }

/** Foreground-only safe area. Page backgrounds deliberately remain outside this modifier. */
@Composable
fun Modifier.glasmioAdaptiveContent(): Modifier = windowInsetsPadding(LocalGlasmioSidebarInsets.current)

object GlasmioAdaptiveNavigationDefaults {
    val SidebarWidth = 320.dp
    val SearchTopPadding = 65.dp
    val TopBarHeight = GlasmioToolbarDefaults.Height
}

/**
 * Owns only the regular-width split. Navigation entries remain mounted when its primary column
 * changes width, and the saved sidebar preference survives rotations without entering the back stack.
 * [content] contains the existing navigation scaffold, floating search and application overlays.
 * [overlay] covers both columns without changing their presentation or foreground insets.
 */
@Composable
fun GlasmioAdaptiveNavigationScaffold(
    state: GlasmioAdaptiveNavigationState,
    sidebar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    layout: GlasmioNavigationLayout = glasmioNavigationLayout(),
    sidebarEnabled: Boolean = true,
    sidebarWidth: Dp = GlasmioAdaptiveNavigationDefaults.SidebarWidth,
    sidebarTransition: GlasmioSidebarTransition = GlasmioSidebarTransition.Automatic,
    extendContentUnderSidebar: Boolean = false,
    contentBackdrop: LayerBackdrop? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val open = layout == GlasmioNavigationLayout.TopTabs && sidebarEnabled && state.sidebarExpanded
    val coordinator = remember(state) { AdaptiveSidebarCoordinator(open) }
    val native = coordinator.motion
    val usesTabMorph = layout == GlasmioNavigationLayout.TopTabs &&
        sidebarTransition == GlasmioSidebarTransition.Automatic && coordinator.tabHosts > 0
    coordinator.usesTabMorph = usesTabMorph
    val progress = remember { Animatable(if (open) 1f else 0f) }
    LaunchedEffect(open, layout, sidebarEnabled, usesTabMorph) {
        if (usesTabMorph || layout == GlasmioNavigationLayout.BottomBar || !sidebarEnabled) progress.snapTo(0f)
        else progress.animateTo(if (open) 1f else 0f, IosSidebarSpring)
    }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val panePixels = with(density) { sidebarWidth.roundToPx() }.coerceIn(0, viewport.width)
    val sideFrame = if (direction == LayoutDirection.Ltr) Rect(0f, 0f, panePixels.toFloat(), viewport.height.toFloat())
        else Rect((viewport.width - panePixels).toFloat(), 0f, viewport.width.toFloat(), viewport.height.toFloat())
    val tabsFrame = coordinator.tabsBounds
    LaunchedEffect(native.running, coordinator.measuredTabsBounds) {
        if (!native.running) coordinator.tabsBounds = coordinator.measuredTabsBounds
    }
    LaunchedEffect(open, usesTabMorph, tabsFrame, sideFrame, layout, sidebarEnabled, density.density) {
        if (!usesTabMorph || layout == GlasmioNavigationLayout.BottomBar || !sidebarEnabled) {
            native.snapTo(false)
        } else if (tabsFrame != null && sideFrame.width > 0f && sideFrame.height > 0f) {
            native.updateGeometry(tabsFrame, sideFrame, density.density, open)
            native.retarget(open)
            val scale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
            var previous = 0L
            while (native.running) withFrameNanos { now ->
                val dt = if (previous == 0L) 0f else (now - previous) / 1e9f / scale.coerceAtLeast(.01f)
                previous = now
                native.advance(dt, scale == 0f)
            }
        }
    }
    val backdrop = contentBackdrop ?: rememberLayerBackdrop()
    val placement = remember { SidebarPlacement() }
    val consumed = remember(progress, coordinator) { SidebarConsumedInsets {
        val p = if (coordinator.usesTabMorph) native.contentInsetProgress else progress.value.coerceIn(0f, 1f)
        if (placement.overlays) 0 else (placement.width * p).roundToInt()
    } }
    val contentBoundsInWindow = {
        val origin = coordinator.hostOrigin
        Rect(origin.x + consumed.getLeft(density, direction), origin.y,
            origin.x + viewport.width - consumed.getRight(density, direction), origin.y + viewport.height)
    }
    // Flatten only the sampling source; visible text and controls retain native resolution.
    // Both endpoints share this live plane instead of replaying nested glass filter trees.
    val extensionSource = if (extendContentUnderSidebar) rememberRasterizedBackdrop(backdrop) else backdrop
    val sidebarBackdrop = if (extendContentUnderSidebar) rememberSidebarBackgroundExtensionBackdrop(
        source = extensionSource,
        contentBoundsInWindow = contentBoundsInWindow,
        consumerBoundsInWindow = { sideFrame.translate(coordinator.hostOrigin) },
        isLeftEdge = direction == LayoutDirection.Ltr,
    ) else backdrop
    val morphSidebarBackdrop = if (extendContentUnderSidebar) rememberSidebarBackgroundExtensionBackdrop(
        source = extensionSource,
        contentBoundsInWindow = contentBoundsInWindow,
        consumerBoundsInWindow = { native.bounds.translate(coordinator.hostOrigin) },
        isLeftEdge = direction == LayoutDirection.Ltr,
    ) else backdrop
    val visibleTabBounds = remember(state, coordinator) { derivedStateOf {
        if (coordinator.usesTabMorph && (native.expanded || native.running)) null else state.tabBarBounds.value
    } }
    var sidebarLayer by remember { mutableStateOf<GraphicsLayer?>(null) }
    var sidebarLayerSize by remember { mutableStateOf(IntSize.Zero) }
    val colors = LocalGlassColors.current
    val focusManager = LocalFocusManager.current
    var sidebarHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(open) { if (!open && sidebarHasFocus) focusManager.clearFocus() }
    DisposableEffect(state, layout) {
        onDispose { state.tabBarBounds.value = null }
    }
    CompositionLocalProvider(
        LocalGlasmioNavigationLayout provides layout,
        LocalGlasmioTabBarBounds provides visibleTabBounds,
        LocalGlasmioSidebarInsets provides consumed,
        LocalGlasmioSidebarExpanded provides (if (usesTabMorph) native.expanded else open),
        LocalAdaptiveSidebarCoordinator provides coordinator,
    ) {
        Layout(modifier = modifier.clipToBounds().testTag("GlasmioAdaptiveNavigation")
            .onSizeChanged { viewport = it }
            .onGloballyPositioned { coordinator.hostOrigin = it.positionInWindow() }, content = {
            Box(Modifier.fillMaxSize()
                .then(if (contentBackdrop == null) Modifier.layerBackdrop(backdrop, reuseContent = true)
                    .trackBackdropPosition(backdrop) else Modifier)
                .background(colors.groupedBackground)) {
                content()
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .2f))
                .graphicsLayer { alpha = if (usesTabMorph) native.contentInsetProgress else progress.value.coerceIn(0f, 1f) }
                .then(if (open) Modifier.clickable(interactionSource = null, indication = null) {
                    state.sidebarExpanded = false
                } else Modifier.clearAndSetSemantics {}))
            val blocked = usesTabMorph && (native.running || native.expanded != open)
            Box(Modifier.fillMaxSize().testTag("GlasmioSidebar")
                .pointerInput(blocked) {
                    if (blocked) awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
                .focusProperties { onEnter = { if (blocked) cancelFocusChange() } }
                .onFocusChanged { sidebarHasFocus = it.hasFocus }.focusGroup()
                .then(if ((if (usesTabMorph) native.expanded else open) && !blocked) Modifier else Modifier.clearAndSetSemantics {})) {
                GlasmioSidebar(
                    backdrop = sidebarBackdrop,
                    backgroundAlpha = { if (usesTabMorph && native.running) 0f else 1f },
                    onContentPreview = if (usesTabMorph && native.running) ({ layer, size ->
                        sidebarLayer = layer
                        sidebarLayerSize = size
                    }) else null,
                    drawContentInPlace = !usesTabMorph || !native.running,
                ) {
                    sidebar()
                }
            }
            Box(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                if (usesTabMorph && native.running) {
                    GlasmioSidebarMorphSurface(backdrop, { native.bounds }, { native.cornerRadius }, { native.materialProgress },
                        sidebarBackdrop = morphSidebarBackdrop,
                        tabsColors = coordinator.tabsColors ?: LocalGlassColors.current,
                        tabsAppearance = coordinator.tabsAppearance ?: LocalGlassAppearance.current)
                    TabSidebarContentPreview({ coordinator.tabsLayer }, { coordinator.tabsLayerSize },
                        { native.bounds }, { native.cornerRadius }, { native.tabsPreviewProgress })
                    TabSidebarContentPreview({ sidebarLayer }, { sidebarLayerSize },
                        { native.bounds }, { native.cornerRadius }, { native.sidebarPreviewProgress })
                }
            }
            // Keep page < sidebar < overlay priority without dismissing the pane beneath a player.
            BackHandler(open) { state.sidebarExpanded = false }
            Box(Modifier.fillMaxSize(), content = overlay)
        }) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val paneWidth = sidebarWidth.roundToPx().coerceIn(0, width)
            val overlay = width - paneWidth < 320.dp.roundToPx()
            placement.width = paneWidth
            placement.overlays = overlay
            val p = if (usesTabMorph) native.contentInsetProgress else progress.value.coerceIn(0f, 1f)
            val body = measurables[0].measure(Constraints.fixed(width, height))
            val scrim = measurables[1].measure(Constraints.fixed(if (overlay && p > 0f) width else 0, height))
            val primary = measurables[2].measure(Constraints.fixed(paneWidth, height))
            val morph = measurables[3].measure(Constraints.fixed(width, height))
            val foreground = measurables[4].measure(Constraints.fixed(width, height))
            layout(width, height) {
                body.placeRelative(0, 0)
                if (overlay && p > 0f) scrim.placeRelative(0, 0)
                if (usesTabMorph) {
                    if (native.expanded || native.running) primary.placeRelative(0, 0)
                } else if (p > 0f) primary.placeRelative(((p - 1f) * paneWidth).roundToInt(), 0)
                morph.place(0, 0)
                foreground.place(0, 0)
            }
        }
    }
}
