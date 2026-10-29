package io.github.neoruaa.glasmio.scaffold

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.floor

/**
 * UIKit's scroll-away driver and its two independent presentation clocks. The driver supplies a
 * five-percent hint while scrolling; only its endpoints change the capsule/circle target.
 *
 * The ordinary target spring uses the recovered .8 damping/.3 response. Compose preserves its
 * current velocity when retargeting. AnimationKit's conditional projection and impulse integrator
 * are not reproduced by that adapter; they must not be applied as unconditional scalar offsets.
 */
@Stable
class GlasmioNavigationScrollState internal constructor(initialCompact: Boolean) {
    var isCompact by mutableStateOf(initialCompact)
        private set

    /** Physical target spring, including its small overshoot. Clamp only opacity or hit geometry. */
    var compactProgress: State<Float> = mutableFloatStateOf(if (initialCompact) 1f else 0f)
        internal set

    /** Independent hint clock. A value of one corresponds to five-percent platter shrinkage. */
    var scrollHintProgress: State<Float> = mutableFloatStateOf(0f)
        internal set

    internal var progress by mutableFloatStateOf(if (initialCompact) 1f else 0f)
        private set
    internal var isTracking by mutableStateOf(false)
        private set
    private var phase = Inactive
    private var initialProgress = progress
    private var userSession = false
    private var decelerating = false
    private var crossesStart = false
    private var enabled = true
    private var activeDistancePx = 64f
    private var minimumRangePx = 96f
    private var scrollableRangePx: () -> Float = { 0f }
    private var distanceToStartPx: () -> Float = { Float.POSITIVE_INFINITY }
    private var atStart: () -> Boolean = { true }
    private var atEnd: () -> Boolean = { true }

    /** Observe the scrolling host without consuming any drag or fling distance. */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!enabled || available.y == 0f) return Offset.Zero
            if (source == NestedScrollSource.UserInput && !isTracking) beginDrag()
            if (!accepts(source)) return Offset.Zero
            // This exact offset is available while item zero is visible. Record a strict crossing
            // before a page's bounce effect can consume the unconsumed edge distance.
            val startDistance = distanceToStartPx()
            crossesStart = available.y > 0f && startDistance.isFinite() &&
                available.y > startDistance
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!enabled || !accepts(source)) return Offset.Zero
            if ((crossesStart || available.y > 0f) && atStart() && isCompact) {
                // Post-scroll remainder is authoritative when one update crosses several lazy
                // items, whose initial distance to the start was not yet measurable.
                setEndpoint(compact = false)
                phase = Inactive
            } else if (phase == AwaitingActivation && progress != 0f && atEnd() && available.y < 0f) {
                // An edge bounce supplies no consumed list distance. It is boundary evidence,
                // not additional distance for the interior hint or its eligibility check.
                setEndpoint(compact = false)
                phase = Inactive
            } else {
                advance(-consumed.y)
            }
            crossesStart = false
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (userSession) {
                isTracking = false
                decelerating = available.y != 0f
                if (!decelerating) finishGesture()
            }
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            finishGesture()
            return Velocity.Zero
        }
    }

    /** A compact navigation tap reveals the existing items without changing selection or routes. */
    fun expand() {
        setEndpoint(compact = false)
        phase = Inactive
    }

    internal fun bind(
        enabled: Boolean,
        activeDistancePx: Float,
        scrollableRangePx: () -> Float,
        distanceToStartPx: () -> Float,
        atStart: () -> Boolean,
        atEnd: () -> Boolean,
    ) {
        this.enabled = enabled
        this.activeDistancePx = activeDistancePx
        minimumRangePx = activeDistancePx * 1.5f
        this.scrollableRangePx = scrollableRangePx
        this.distanceToStartPx = distanceToStartPx
        this.atStart = atStart
        this.atEnd = atEnd
        if (!enabled) finishGesture()
    }

    private fun accepts(source: NestedScrollSource): Boolean = userSession &&
        (source == NestedScrollSource.UserInput || decelerating)

    private fun beginDrag() {
        userSession = true
        isTracking = true
        decelerating = false
        phase = AwaitingActivation
        initialProgress = progress
        crossesStart = false
    }

    internal fun finishGesture() {
        if (!userSession) return
        userSession = false
        isTracking = false
        decelerating = false
        phase = Inactive
        crossesStart = false
        setEndpoint(floor(progress) == 1f)
    }

    private fun setEndpoint(compact: Boolean) {
        progress = if (compact) 1f else 0f
        isCompact = compact
    }

    private fun advance(signedDelta: Float) {
        if (signedDelta == 0f || phase == Inactive) return
        if (phase == AwaitingActivation) {
            if (signedDelta > 0f && atEnd() && progress != 0f) {
                setEndpoint(compact = false)
                phase = Inactive
                return
            }
            // The native edge reset precedes eligibility. The activation threshold is zero;
            // reverse interior motion never activates a new scroll-away interaction.
            val range = scrollableRangePx()
            if (signedDelta < 0f || !range.isFinite() || range < minimumRangePx) return
            phase = Active
            initialProgress = progress
        }
        if (atStart() && signedDelta < 0f && initialProgress != 0f) {
            setEndpoint(compact = false)
            phase = Inactive
        } else if (progress != 1f) {
            progress = (progress + signedDelta / activeDistancePx).coerceIn(.01f, 1f)
            if (progress == 1f) {
                isCompact = true
                phase = Inactive
            }
        }
    }

    internal companion object {
        private const val Inactive = 0
        private const val AwaitingActivation = 1
        private const val Active = 2
        val Saver = Saver<GlasmioNavigationScrollState, Boolean>(
            save = { it.isCompact },
            restore = { GlasmioNavigationScrollState(it) },
        )
    }
}

/**
 * Bind navigation minimization to an existing list and place [nestedScrollConnection][GlasmioNavigationScrollState.nestedScrollConnection]
 * above that list. Changing the list preserves the compact target and cancels only the old drag.
 *
 * [scrollableRangePx] may supply an exact content extent. By default the list's official scroll
 * indicator range is used; Compose estimates unmeasured lazy rows in that platform metric.
 * Programmatic jumps cannot begin a scroll-away interaction. The Android motion-duration policy
 * remains in force, including immediate completion when animations are disabled.
 */
@Composable
fun rememberGlasmioNavigationScrollState(
    listState: LazyListState,
    enabled: Boolean = true,
    scrollableRangePx: (() -> Float)? = null,
): GlasmioNavigationScrollState {
    val state = rememberSaveable(saver = GlasmioNavigationScrollState.Saver) {
        GlasmioNavigationScrollState(initialCompact = false)
    }
    val activeDistancePx = with(LocalDensity.current) { 64.dp.toPx() }
    SideEffect {
        state.bind(
            enabled = enabled,
            activeDistancePx = activeDistancePx,
            scrollableRangePx = scrollableRangePx ?: {
                listState.scrollIndicatorState?.let { (it.contentSize - it.viewportSize).coerceAtLeast(0).toFloat() } ?: 0f
            },
            distanceToStartPx = {
                if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat()
                else Float.POSITIVE_INFINITY
            },
            atStart = { !listState.canScrollBackward },
            atEnd = { !listState.canScrollForward },
        )
    }
    LaunchedEffect(state, listState, enabled) {
        state.finishGesture()
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) state.finishGesture()
        }
    }
    state.compactProgress = animateFloatAsState(
        targetValue = if (state.isCompact) 1f else 0f,
        animationSpec = nativeNavigationSpring(response = .3f),
        label = "GlasmioNavigationCompact",
    )
    val interior = state.progress > 0f && state.progress < 1f
    state.scrollHintProgress = animateFloatAsState(
        targetValue = if (interior) state.progress else 0f,
        animationSpec = nativeNavigationSpring(
            response = if (!interior) .3f else if (state.isTracking) .1f else .5f,
        ),
        label = "GlasmioNavigationScrollHint",
    )
    return state
}

private fun nativeNavigationSpring(response: Float) = spring<Float>(
    dampingRatio = .8f,
    stiffness = ((2.0 * PI / response) * (2.0 * PI / response)).toFloat(),
    // Normalized Android adapter precision, not a recovered UIKit termination threshold.
    visibilityThreshold = .0001f,
)

/**
 * Scale the shared navigation content once, including its detached item and foreground glyphs.
 * Place this before the bar's bottom window-inset padding so the pivot uses its full allocation;
 * keep any application margin outside this modifier. It does not change layout or consume input.
 */
fun Modifier.glasmioNavigationScrollHint(progress: State<Float>): Modifier =
    glasmioNavigationScrollHint(progress) { TransformOrigin.Center }

/** Share UIKit's wrapper pivot when an accessory is hosted in a separate layout layer. */
fun Modifier.glasmioNavigationScrollHint(
    progress: State<Float>,
    pivot: GraphicsLayerScope.() -> TransformOrigin,
): Modifier = graphicsLayer {
    val scale = 1f - .05f * progress.value
    scaleX = scale
    scaleY = scale
    transformOrigin = pivot()
}
