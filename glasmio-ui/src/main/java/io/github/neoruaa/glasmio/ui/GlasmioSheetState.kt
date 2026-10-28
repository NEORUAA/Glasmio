package io.github.neoruaa.glasmio.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlin.math.abs

/** A modal presentation's semantic detents, independent of its window and content. */
enum class GlasmioSheetValue { Hidden, Medium, Large }

/** Window-relative top offsets in physical pixels, increasing towards the bottom edge. */
@Immutable
data class GlasmioSheetAnchors(
    val hidden: Float,
    val large: Float,
    val medium: Float? = null,
) {
    init {
        require(hidden.isFinite() && large.isFinite() && hidden > large) {
            "The hidden offset must be finite and below the large detent."
        }
        require(medium == null || medium.isFinite() && medium > large && medium < hidden) {
            "The medium detent must be strictly between large and hidden."
        }
    }

    internal fun offsetOf(value: GlasmioSheetValue): Float = when (value) {
        GlasmioSheetValue.Hidden -> hidden
        GlasmioSheetValue.Medium -> medium ?: large
        GlasmioSheetValue.Large -> large
    }

    internal fun values(includeHidden: Boolean): List<GlasmioSheetValue> = buildList {
        add(GlasmioSheetValue.Large)
        if (medium != null) add(GlasmioSheetValue.Medium)
        if (includeHidden) add(GlasmioSheetValue.Hidden)
    }
}

/**
 * State for an iOS modal sheet. The presenter resolves geometry; this state owns only detent
 * selection, interruption, drag resistance and the spring that follows a release.
 *
 * [currentValue] changes after settling. In particular, a closing sheet stays visible until its
 * spring reaches [GlasmioSheetValue.Hidden], allowing its host to retain the window and backdrop.
 */
@Stable
class GlasmioSheetState(
    val skipPartiallyExpanded: Boolean = true,
    val initialValue: GlasmioSheetValue =
        if (skipPartiallyExpanded) GlasmioSheetValue.Large else GlasmioSheetValue.Medium,
    private val confirmValueChange: (GlasmioSheetValue) -> Boolean = { true },
) {
    init {
        require(!skipPartiallyExpanded || initialValue != GlasmioSheetValue.Medium) {
            "A medium initial value requires skipPartiallyExpanded = false."
        }
    }

    var currentValue by mutableStateOf(GlasmioSheetValue.Hidden)
        private set
    var targetValue by mutableStateOf(GlasmioSheetValue.Hidden)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    var velocity by mutableFloatStateOf(0f)
        private set
    var isDragging by mutableStateOf(false)
        private set
    var isAnimationRunning by mutableStateOf(false)
        private set
    /**
     * A presentation controller translates the chosen platter without traversing other detents.
     * A finished dismissal retains this value until the presenter removes its window.
     */
    var presentationDetent by mutableStateOf<GlasmioSheetValue?>(null)
        private set

    private var anchors by mutableStateOf<GlasmioSheetAnchors?>(null)
    private var density = 1f
    private var safeAreaTop = 0f
    private var anchorsRevision = 0
    private var generation = 0
    private var animationJob: Job? = null
    private var rawOffset = 0f
    private var dragOrigin = GlasmioSheetValue.Large
    private var dragDismissible = true
    private var controllerClosing = false
    private var controllerDimmingActive by mutableStateOf(false)
    private var controllerDimming by mutableFloatStateOf(0f)
    private var controllerDimmingVelocity = 0f

    val hasAnchors: Boolean get() = anchors != null
    val hasMediumDetent: Boolean get() = anchors?.medium != null && !skipPartiallyExpanded
    val isVisible: Boolean get() = anchors?.let {
        targetValue != GlasmioSheetValue.Hidden || currentValue != GlasmioSheetValue.Hidden ||
            offset < it.hidden
    } ?: false
    val progress: Float get() = anchors?.let {
        ((it.hidden - offset) / (it.hidden - it.large)).coerceIn(0f, 1f)
    } ?: 0f
    /** Controller dimming has its own spring; interactive dimming is full at the medium detent. */
    val presentationProgress: Float get() = if (controllerDimmingActive) {
        controllerDimming.coerceIn(0f, 1f)
    } else interactivePresentationProgress

    private val interactivePresentationProgress: Float get() = anchors?.let {
        ((it.hidden - offset) / (it.hidden - (it.medium ?: it.large))).coerceIn(0f, 1f)
    } ?: 0f
    val expansionProgress: Float get() = anchors?.let {
        val medium = it.medium ?: return@let progress
        ((medium - offset) / (medium - it.large)).coerceIn(0f, 1f)
    } ?: 0f

    /**
     * Relocate settled detents and preserve the presentation position and velocity of active motion.
     * [safeAreaTop] and [density] convert UIKit's geometry-dependent rubber-band extents and speeds.
     */
    fun updateAnchors(anchors: GlasmioSheetAnchors, density: Float = 1f, safeAreaTop: Float = 0f) {
        require(density.isFinite() && density > 0f) { "Density must be finite and positive." }
        require(safeAreaTop.isFinite()) { "The top safe area must be finite." }
        val resolved = if (skipPartiallyExpanded) anchors.copy(medium = null) else anchors
        val previous = this.anchors
        this.density = density
        this.safeAreaTop = safeAreaTop
        if (previous == resolved) return
        this.anchors = resolved
        anchorsRevision++
        targetValue = resolve(targetValue, resolved)
        currentValue = resolve(currentValue, resolved)
        presentationDetent = presentationDetent?.let { resolve(it, resolved) }
        when {
            previous == null -> offset = resolved.hidden
            isDragging -> {
                // Preserve the finger's position through an inset/layout change. Extents are
                // re-evaluated on the next delta rather than mapping a gesture to a new fraction.
                Unit
            }
            isAnimationRunning -> Unit // The animation observes the new target next frame.
            else -> offset = resolved.offsetOf(currentValue)
        }
        rawOffset = if (isDragging) unconstrainedOffset(resolved) else offset
    }

    suspend fun show() {
        val resting = presentationDetent?.takeIf { controllerClosing && isVisible } ?: if (initialValue == GlasmioSheetValue.Hidden) {
            if (skipPartiallyExpanded) GlasmioSheetValue.Large else GlasmioSheetValue.Medium
        } else initialValue
        animateRequested(resting, velocity, presentationMotion = true, restingDetent = resting)
    }

    suspend fun hide() = animateRequested(
        GlasmioSheetValue.Hidden, velocity, presentationMotion = true,
        restingDetent = visibleRestingDetent(),
    )
    suspend fun expand() = animateTo(GlasmioSheetValue.Large)
    suspend fun partialExpand() {
        check(!skipPartiallyExpanded) { "The medium detent is disabled." }
        animateTo(GlasmioSheetValue.Medium)
    }

    /** Programmatic retargeting starts with the current presentation velocity by default. */
    suspend fun animateTo(value: GlasmioSheetValue, initialVelocity: Float = velocity) =
        animateRequested(value, initialVelocity, presentationMotion = false)

    private suspend fun animateRequested(
        value: GlasmioSheetValue,
        initialVelocity: Float,
        presentationMotion: Boolean,
        restingDetent: GlasmioSheetValue? = null,
    ) {
        val geometry = anchors ?: snapshotFlow { anchors }.filterNotNull().first()
        val destination = resolve(value, geometry)
        if (!confirmValueChange(destination)) return
        animateConfirmed(
            destination, initialVelocity.takeIf { it.isFinite() } ?: 0f,
            presentationMotion = presentationMotion,
            restingDetent = restingDetent?.let { resolve(it, geometry) },
        )
    }

    fun beginDrag() {
        val geometry = anchors ?: return
        if (!isVisible || isDragging) return
        dragOrigin = presentationDetent ?: if (targetValue == GlasmioSheetValue.Hidden) {
            IosSheetMotion.closestValue(offset, 0f, geometry, false, density)
        } else targetValue
        dragDismissible = confirmValueChange(GlasmioSheetValue.Hidden)
        generation++
        animationJob?.cancel()
        animationJob = null
        isAnimationRunning = false
        isDragging = true
        presentationDetent = null
        controllerDimmingActive = false
        controllerClosing = false
        controllerDimmingVelocity = 0f
        rawOffset = unconstrainedOffset(geometry)
        velocity = 0f
    }

    /**
     * Apply a downward-positive finger delta. Scroll arbitration can disable [allowOverdrag] so
     * remaining upward movement passes directly into the child when the large detent is reached.
     */
    fun dragBy(deltaPx: Float, allowOverdrag: Boolean = true): Float {
        if (!deltaPx.isFinite() || deltaPx == 0f) return 0f
        if (!isDragging) beginDrag()
        if (!isDragging) return 0f
        val geometry = anchors ?: return 0f
        val previousOffset = offset
        val lower = if (dragDismissible) geometry.hidden else geometry.medium ?: geometry.large
        rawOffset += deltaPx
        offset = if (!allowOverdrag) {
            rawOffset = rawOffset.coerceIn(geometry.large, lower)
            rawOffset
        } else when {
            rawOffset < geometry.large -> geometry.large + IosSheetMotion.rubberBand(
                rawOffset - geometry.large,
                IosSheetMotion.upperRubberBandExtent(geometry, safeAreaTop, density),
            )
            rawOffset > lower -> lower + IosSheetMotion.rubberBand(
                rawOffset - lower, IosSheetMotion.lowerRubberBandExtent(geometry, density),
            )
            else -> rawOffset
        }
        return offset - previousOffset
    }

    suspend fun endDrag(velocityPxPerSecond: Float) {
        if (!isDragging) return
        val geometry = anchors ?: return
        val releaseVelocity = velocityPxPerSecond.takeIf { it.isFinite() } ?: 0f
        val candidate = IosSheetMotion.closestValue(rawOffset, releaseVelocity, geometry, dragDismissible, density)
        val destination = if (confirmValueChange(candidate)) candidate else resolve(dragOrigin, geometry)
        val lower = if (dragDismissible) geometry.hidden else geometry.medium ?: geometry.large
        val constrainedVelocity = IosSheetMotion.constrainedReleaseVelocity(
            releaseVelocity, rawOffset, geometry.large, lower,
            IosSheetMotion.upperRubberBandExtent(geometry, safeAreaTop, density),
            IosSheetMotion.lowerRubberBandExtent(geometry, density), density,
        )
        animateConfirmed(
            destination, constrainedVelocity,
            highSpeed = abs(releaseVelocity) >= IosSheetMotion.HighSpeedThreshold * density,
            releaseNudge = true,
        )
    }

    suspend fun cancelDrag() {
        if (!isDragging) return
        val geometry = anchors ?: return
        animateConfirmed(resolve(dragOrigin, geometry), 0f)
    }

    private fun resolve(value: GlasmioSheetValue, anchors: GlasmioSheetAnchors): GlasmioSheetValue =
        if (value == GlasmioSheetValue.Medium && anchors.medium == null) GlasmioSheetValue.Large else value

    private fun visibleRestingDetent(): GlasmioSheetValue = presentationDetent
        ?: targetValue.takeIf { it != GlasmioSheetValue.Hidden }
        ?: currentValue.takeIf { it != GlasmioSheetValue.Hidden }
        ?: initialValue.takeIf { it != GlasmioSheetValue.Hidden }
        ?: if (skipPartiallyExpanded) GlasmioSheetValue.Large else GlasmioSheetValue.Medium

    private fun presentationVelocity(): Float {
        if (controllerDimmingActive) return controllerDimmingVelocity
        val geometry = anchors ?: return 0f
        val firstDetent = geometry.medium ?: geometry.large
        return if (offset > firstDetent && offset < geometry.hidden) {
            -velocity / (geometry.hidden - firstDetent)
        } else 0f
    }

    private fun unconstrainedOffset(geometry: GlasmioSheetAnchors): Float {
        val lower = if (dragDismissible) geometry.hidden else geometry.medium ?: geometry.large
        return when {
            offset < geometry.large -> geometry.large + IosSheetMotion.unconstrainedExcess(
                offset - geometry.large,
                IosSheetMotion.upperRubberBandExtent(geometry, safeAreaTop, density),
            )
            offset > lower -> lower + IosSheetMotion.unconstrainedExcess(
                offset - lower, IosSheetMotion.lowerRubberBandExtent(geometry, density),
            )
            else -> offset
        }
    }

    private suspend fun animateConfirmed(
        value: GlasmioSheetValue,
        initialVelocity: Float,
        highSpeed: Boolean = abs(initialVelocity) >= IosSheetMotion.HighSpeedThreshold * density,
        presentationMotion: Boolean = false,
        restingDetent: GlasmioSheetValue? = null,
        releaseNudge: Boolean = false,
    ) = coroutineScope {
        val initialDimming = presentationProgress
        val initialDimmingVelocity = presentationVelocity()
        val token = ++generation
        animationJob?.cancel()
        animationJob = coroutineContext.job
        isDragging = false
        isAnimationRunning = true
        targetValue = value
        velocity = initialVelocity
        if (releaseNudge && velocity != 0f && offset == requireNotNull(anchors).offsetOf(value)) {
            // HyperAnimator disambiguates a zero-distance release by one millisecond of motion.
            offset += velocity * 0.001f
        }
        if (presentationMotion) {
            presentationDetent = restingDetent ?: visibleRestingDetent()
            controllerClosing = value == GlasmioSheetValue.Hidden
            controllerDimming = initialDimming
            controllerDimmingVelocity = initialDimmingVelocity
            controllerDimmingActive = true
        } else {
            presentationDetent = null
            controllerClosing = false
            controllerDimmingActive = false
            controllerDimmingVelocity = 0f
        }
        val dimmingTarget = if (value == GlasmioSheetValue.Hidden) 0f else 1f
        val motionDurationScale = coroutineContext[MotionDurationScale]
        try {
            do {
                val revision = anchorsRevision
                val target = requireNotNull(anchors).offsetOf(targetValue)
                val dimmingStart = controllerDimming
                val dimmingStartVelocity = controllerDimmingVelocity
                if (offset == target && velocity == 0f && (!presentationMotion ||
                        dimmingStart == dimmingTarget && dimmingStartVelocity == 0f)) break
                val spec = if (presentationMotion) IosSheetPresentationMotion else IosSheetMotion.springSpec(highSpeed)
                AnimationState(offset, velocity).animateTo(target, spec) {
                    if (revision != anchorsRevision) {
                        cancelAnimation()
                    } else {
                        this@GlasmioSheetState.offset = this.value
                        this@GlasmioSheetState.velocity = this.velocity
                        if (presentationMotion) {
                            val durationScale = motionDurationScale?.scaleFactor ?: 1f
                            val playTime = if (durationScale == 0f) {
                                IosSheetPresentationMotion.DurationMillis * 1_000_000L
                            } else ((lastFrameTimeNanos - startTimeNanos) / durationScale).toLong()
                            controllerDimming = IosSheetPresentationMotion.getValueFromNanos(
                                playTime, dimmingStart, dimmingTarget, dimmingStartVelocity,
                            )
                            controllerDimmingVelocity = IosSheetPresentationMotion.getVelocityFromNanos(
                                playTime, dimmingStart, dimmingTarget, dimmingStartVelocity,
                            )
                        }
                    }
                }
            } while (token == generation && revision != anchorsRevision)
            if (token == generation) {
                offset = requireNotNull(anchors).offsetOf(targetValue)
                rawOffset = offset
                currentValue = targetValue
                velocity = 0f
                if (presentationMotion) {
                    controllerDimming = dimmingTarget
                    controllerDimmingVelocity = 0f
                    if (targetValue != GlasmioSheetValue.Hidden) {
                        presentationDetent = null
                        controllerClosing = false
                        controllerDimmingActive = false
                    }
                }
            }
        } finally {
            if (token == generation) {
                isAnimationRunning = false
                animationJob = null
            }
        }
    }
}

@Composable
fun rememberGlasmioSheetState(
    skipPartiallyExpanded: Boolean = true,
    initialValue: GlasmioSheetValue =
        if (skipPartiallyExpanded) GlasmioSheetValue.Large else GlasmioSheetValue.Medium,
    confirmValueChange: (GlasmioSheetValue) -> Boolean = { true },
): GlasmioSheetState {
    val confirm by rememberUpdatedState(confirmValueChange)
    return rememberSaveable(
        skipPartiallyExpanded, initialValue,
        saver = Saver(
            save = { state: GlasmioSheetState ->
                (if (state.targetValue == GlasmioSheetValue.Hidden) initialValue else state.targetValue).name
            },
            restore = { saved: String ->
                GlasmioSheetState(skipPartiallyExpanded, GlasmioSheetValue.valueOf(saved)) { confirm(it) }
            },
        ),
    ) {
        GlasmioSheetState(skipPartiallyExpanded, initialValue) { confirm(it) }
    }
}
