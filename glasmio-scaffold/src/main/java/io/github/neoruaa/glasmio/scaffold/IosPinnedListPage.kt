package io.github.neoruaa.glasmio.scaffold

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.ui.*
import io.github.neoruaa.glasmio.icons.*

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * A collapsed large title is a state, not a scroll position. Past the enter breakpoint the header
 * plays one spring to its collapsed state instead of tracking the finger; it only expands again
 * below the exit breakpoint, so a scroll resting near the boundary cannot retrigger the motion.
 */
object GlasmioHeaderCollapseDefaults {
    /** Scroll offset at which the large title starts collapsing. */
    val EnterBreakpoint: Dp = 56.dp

    /** Scroll offset the header must return above before expanding again. */
    val ExitBreakpoint: Dp = 24.dp

    /**
     * SwiftUI `Spring(duration: 0.416, bounce: 0.25)` expressed as a Compose spring:
     * damping ratio 0.75 and stiffness `(2π / 0.416)²`. It matches HeaderSprings.geometry.
     */
    val Spring: AnimationSpec<Float> = spring(dampingRatio = .75f, stiffness = 228.125f)
}

/**
 * Breakpoint state machine for the collapsing large title. Pure so the hysteresis is testable
 * without a Compose runtime: collapsing needs a deeper scroll than expanding does.
 */
internal fun nextCollapsed(
    collapsed: Boolean,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    enterBreakpoint: Float,
    exitBreakpoint: Float,
): Boolean = when {
    firstVisibleItemIndex > 0 -> true
    collapsed -> firstVisibleItemScrollOffset > exitBreakpoint
    else -> firstVisibleItemScrollOffset >= enterBreakpoint
}

/**
 * Scroll-linked edge progress for the top bar material. Unlike the title collapse this stays
 * continuous, so the blur fades in with the finger instead of pulsing on a breakpoint.
 */
@Composable
fun rememberGlasmioHeaderScrollEdge(
    state: LazyListState,
    distance: Dp = GlasmioHeaderCollapseDefaults.EnterBreakpoint,
): Float {
    val distancePx = with(LocalDensity.current) { distance.toPx().coerceAtLeast(1f) }
    val progress by remember(state, distancePx) {
        derivedStateOf { scrollEdge(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, distancePx) }
    }
    return progress
}

/** Grid counterpart of [rememberGlasmioHeaderScrollEdge]. */
@Composable
fun rememberGlasmioHeaderScrollEdge(
    state: LazyGridState,
    distance: Dp = GlasmioHeaderCollapseDefaults.EnterBreakpoint,
): Float {
    val distancePx = with(LocalDensity.current) { distance.toPx().coerceAtLeast(1f) }
    val progress by remember(state, distancePx) {
        derivedStateOf { scrollEdge(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, distancePx) }
    }
    return progress
}

internal fun scrollEdge(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int, distancePx: Float): Float =
    if (firstVisibleItemIndex > 0) 1f
    else (firstVisibleItemScrollOffset / distancePx.coerceAtLeast(1f)).coerceIn(0f, 1f)

/**
 * Collapse progress driven by breakpoints instead of the scroll position. The returned value only
 * animates between 0 and 1; consumers keep their existing visual mapping unchanged.
 */
@Composable
fun rememberGlasmioHeaderCollapse(
    state: LazyListState,
    enterBreakpoint: Dp = GlasmioHeaderCollapseDefaults.EnterBreakpoint,
    exitBreakpoint: Dp = GlasmioHeaderCollapseDefaults.ExitBreakpoint,
    animationSpec: AnimationSpec<Float> = GlasmioHeaderCollapseDefaults.Spring,
): Float = rememberBreakpointCollapse(
    stateKey = state,
    firstVisibleItemIndex = { state.firstVisibleItemIndex },
    firstVisibleItemScrollOffset = { state.firstVisibleItemScrollOffset },
    enterBreakpoint = enterBreakpoint,
    exitBreakpoint = exitBreakpoint,
    animationSpec = animationSpec,
)

/** Grid counterpart of [rememberGlasmioHeaderCollapse]. */
@Composable
fun rememberGlasmioHeaderCollapse(
    state: LazyGridState,
    enterBreakpoint: Dp = GlasmioHeaderCollapseDefaults.EnterBreakpoint,
    exitBreakpoint: Dp = GlasmioHeaderCollapseDefaults.ExitBreakpoint,
    animationSpec: AnimationSpec<Float> = GlasmioHeaderCollapseDefaults.Spring,
): Float = rememberBreakpointCollapse(
    stateKey = state,
    firstVisibleItemIndex = { state.firstVisibleItemIndex },
    firstVisibleItemScrollOffset = { state.firstVisibleItemScrollOffset },
    enterBreakpoint = enterBreakpoint,
    exitBreakpoint = exitBreakpoint,
    animationSpec = animationSpec,
)

@Composable
private fun rememberBreakpointCollapse(
    stateKey: Any,
    firstVisibleItemIndex: () -> Int,
    firstVisibleItemScrollOffset: () -> Int,
    enterBreakpoint: Dp,
    exitBreakpoint: Dp,
    animationSpec: AnimationSpec<Float>,
): Float {
    val density = LocalDensity.current
    val enterPx = with(density) { enterBreakpoint.toPx().coerceAtLeast(1f) }
    // A wider exit threshold would make the state flip back and forth around one offset.
    val exitPx = with(density) { exitBreakpoint.toPx() }.coerceIn(0f, enterPx)
    var collapsed by remember(stateKey, enterPx, exitPx) {
        mutableStateOf(nextCollapsed(false, firstVisibleItemIndex(), firstVisibleItemScrollOffset(), enterPx, exitPx))
    }
    LaunchedEffect(stateKey, enterPx, exitPx) {
        snapshotFlow { firstVisibleItemIndex() to firstVisibleItemScrollOffset() }
            .collect { (index, offset) ->
                val next = nextCollapsed(collapsed, index, offset, enterPx, exitPx)
                if (next != collapsed) collapsed = next
            }
    }
    val progress by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = animationSpec,
        label = "GlasmioHeaderCollapse",
    )
    return progress
}

/**
 * Grid collapse progress. This keeps the historical name but now plays a breakpoint animation
 * rather than mapping the scroll offset; [collapseDistance] is the enter breakpoint.
 */
@Composable
fun rememberGlasmioGridCollapseProgress(
    gridState: LazyGridState,
    collapseDistance: Dp = GlasmioHeaderCollapseDefaults.EnterBreakpoint,
    exitBreakpoint: Dp = GlasmioHeaderCollapseDefaults.ExitBreakpoint,
): Float = rememberGlasmioHeaderCollapse(
    state = gridState,
    enterBreakpoint = collapseDistance,
    exitBreakpoint = exitBreakpoint,
)

/**
 * Fixed iOS navigation bar over a progressively blurred scroll source.
 *
 * The scrolling layer exports its rendered result to a dedicated backdrop. The toolbar is kept
 * outside that layer, so the backdrop graph cannot recursively sample the toolbar itself.
 */
@Composable
fun GlasmioPinnedListPage(
    title: String,
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    horizontalContentPadding: Dp = IosPageHorizontalPadding,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    listState: LazyListState = rememberLazyListState(),
    showsLargeTitle: Boolean = true,
    collapseEnterBreakpoint: Dp = GlasmioHeaderCollapseDefaults.EnterBreakpoint,
    collapseExitBreakpoint: Dp = GlasmioHeaderCollapseDefaults.ExitBreakpoint,
    collapseAnimationSpec: AnimationSpec<Float> = GlasmioHeaderCollapseDefaults.Spring,
    largeTitleHorizontalPadding: Dp = 4.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
    onNavigateBack: (() -> Unit)? = null,
    navigateBackContentDescription: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
    actionsStyle: GlasmioToolbarActionStyle = GlasmioToolbarActionStyle.Merged,
    actionsSeparationProgress: Float? = null,
    backgroundColor: Color? = null,
    backgroundBrush: Brush? = null,
    bottomPaddingValues: PaddingValues? = null,
    content: LazyListScope.() -> Unit,
) {
    // The title collapses on a breakpoint, while the material keeps fading with the scroll offset.
    val animatedCollapse = rememberGlasmioHeaderCollapse(
        state = listState,
        enterBreakpoint = collapseEnterBreakpoint,
        exitBreakpoint = collapseExitBreakpoint,
        animationSpec = collapseAnimationSpec,
    )
    val collapseProgress = if (showsLargeTitle) animatedCollapse else 1f
    val scrollEdgeProgress = rememberGlasmioHeaderScrollEdge(listState, collapseEnterBreakpoint)
    GlasmioPinnedPage(
        title = title,
        subtitle = subtitle,
        bottomPadding = bottomPadding,
        modifier = modifier,
        onNavigateBack = onNavigateBack,
        navigateBackContentDescription = navigateBackContentDescription,
        actions = actions,
        actionsStyle = actionsStyle,
        actionsSeparationProgress = actionsSeparationProgress,
        collapseProgress = collapseProgress,
        topBarBlurProgress = scrollEdgeProgress,
        backgroundColor = backgroundColor,
        backgroundBrush = backgroundBrush,
    ) { contentPadding ->
        val listContentPadding = remember(contentPadding, horizontalContentPadding, bottomPaddingValues) {
            // Keep animated bottom insets in LazyColumn measurement, not page composition.
            object : PaddingValues by contentPadding {
                override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = horizontalContentPadding

                override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = horizontalContentPadding

                override fun calculateBottomPadding(): Dp =
                    contentPadding.calculateBottomPadding() +
                        (bottomPaddingValues?.calculateBottomPadding() ?: 0.dp)
            }
        }
        // A different scroll state owns a different measurement node and item provider.
        key(listState) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = listContentPadding,
                verticalArrangement = verticalArrangement,
            ) {
                if (showsLargeTitle) {
                    item(key = "ios-large-title:$title") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = 1f - collapseProgress
                                    val scale = 1f - 0.04f * collapseProgress
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                                }
                                .blur(6.dp * collapseProgress)
                                .offset(y = (-10).dp)
                                .padding(horizontal = largeTitleHorizontalPadding, vertical = 6.dp),
                        ) {
                            Text(
                                text = title,
                                style = IosTypography.largeTitle,
                                fontWeight = FontWeight.Bold,
                                color = LocalGlassColors.current.content,
                            )
                            subtitle?.let {
                                Text(
                                    text = it,
                                    style = IosTypography.subheadline,
                                    color = LocalGlassColors.current.secondaryContent,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                        }
                    }
                }
                content()
            }
        }
    }
}

/**
 * Fixed iOS navigation bar for pages whose body is not a single [LazyColumn].
 *
 * [content] is the only exported sample layer. All glass controls inside it continue to read
 * [LocalGlassBackdrop], while the toolbar reads this dedicated page layer, preventing feedback.
 */
@Composable
fun GlasmioPinnedPage(
    title: String,
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    navigateBackContentDescription: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
    actionsStyle: GlasmioToolbarActionStyle = GlasmioToolbarActionStyle.Merged,
    actionsSeparationProgress: Float? = null,
    collapseProgress: Float = 1f,
    backgroundColor: Color? = null,
    topBarBlurProgress: Float = 0f,
    backgroundBrush: Brush? = null,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    val pageBackdrop = rememberLayerBackdrop()
    val colors = LocalGlassColors.current
    val pageBackground = backgroundColor ?: colors.groupedBackground
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val toolbarHeight = statusBarHeight + 62.dp
    val contentPadding = PaddingValues(
        start = IosPageHorizontalPadding,
        top = toolbarHeight + 10.dp,
        end = IosPageHorizontalPadding,
        bottom = bottomPadding + 24.dp,
    )

    CompositionLocalProvider(LocalContentColor provides colors.content) {
        Box(modifier.fillMaxSize()) {
            GlasmioPageBody(
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
                backgroundColor = pageBackground,
                backgroundBrush = backgroundBrush,
                topBarBlurProgress = topBarBlurProgress,
                backdrop = pageBackdrop,
                content = content,
            )
            CompositionLocalProvider(LocalGlassBackdrop provides pageBackdrop) {
                GlasmioTopBar(
                    title = title,
                    subtitle = subtitle,
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().align(Alignment.TopCenter),
                    collapseProgress = collapseProgress,
                    navigation = onNavigateBack?.let { navigateBack ->
                        {
                            GlasmioIconButton(navigateBack) {
                                SfIcon(
                                    SfSymbol.ChevronBack,
                                    navigateBackContentDescription,
                                    mirrored = true,
                                )
                            }
                        }
                    },
                    actions = actions,
                    actionsStyle = actionsStyle,
                    actionsSeparationProgress = actionsSeparationProgress,
                )
            }
        }
    }
}
