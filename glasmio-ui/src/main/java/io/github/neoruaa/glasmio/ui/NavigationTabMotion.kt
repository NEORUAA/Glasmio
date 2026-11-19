package io.github.neoruaa.glasmio.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.util.lerp
import io.github.neoruaa.glasmio.glass.LiquidMenuSpring
import io.github.neoruaa.glasmio.glass.LiquidMenuSpringSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Advances the tab capsule, lens lift and flex on the same clock with independent springs. */
internal class NavigationTabMotion(
    private val animationScope: CoroutineScope,
    private val isTopBar: Boolean,
    private val density: Float,
    initialCenterPx: Float,
    initialWidthPx: Float,
    private val heightPx: Float,
    private val dragCoordinateSpace: () -> LayoutCoordinates?,
    private val onDragStarted: NavigationTabMotion.(position: Offset) -> Unit,
    private val onDragStopped: NavigationTabMotion.(position: Offset) -> Unit,
    private val onDragCancelled: NavigationTabMotion.() -> Unit,
    private val onDrag: NavigationTabMotion.(position: Offset, amount: Offset) -> Unit,
) {
    private val center = LiquidMenuSpring(.05f * density, .5f * density).apply { reset(initialCenterPx) }
    private val width = LiquidMenuSpring(.05f * density, .5f * density).apply { reset(initialWidthPx) }
    private val lift = LiquidMenuSpring(.001f, .015f)
    private val horizontalFlex = LiquidMenuSpring(.001f, .015f).apply { reset(1f) }
    private val verticalFlex = LiquidMenuSpring(.001f, .015f).apply { reset(1f) }
    private val flexDrift = LiquidMenuSpring(.05f * density, .5f * density)
    private var targetCenter = initialCenterPx
    private var targetWidth = initialWidthPx
    private var tracking = false
    private var liftRequested = false
    private var releasePending = false
    private var liftedFor = 0f
    private var renderJob: Job? = null

    private var filteredPosition = initialCenterPx / density
    private var filteredVelocity = 0f
    private var filteredAcceleration = 0f
    private var movementPending = false
    private var flexSizeFraction = -1f
    private var flexSpec = GlasmioLoupeFlexMotion()
    private var trackingFlexSpring = LiquidMenuSpringSpec(.90f, .50f)
    private var restingFlexSpring = LiquidMenuSpringSpec(1f, .50f)

    var centerPx by mutableFloatStateOf(initialCenterPx)
        private set
    var widthPx by mutableFloatStateOf(initialWidthPx)
        private set
    private val liftProgress = mutableFloatStateOf(0f)
    val pressProgressState: State<Float> get() = liftProgress
    val pressProgress: Float get() = liftProgress.floatValue
    private var flexScale by mutableStateOf(Offset(1f, 1f))
    val scaleX: Float get() = flexScale.x
    val scaleY: Float get() = flexScale.y
    var flexOffsetPx by mutableFloatStateOf(0f)
        private set
    var isDragging = false
        private set
    val isMoving: Boolean get() = !center.isSettled || !width.isSettled

    private var inputCoordinates: LayoutCoordinates? = null
    val modifier: Modifier = Modifier
        .onGloballyPositioned { inputCoordinates = it }
        .pointerInput(this) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val downPosition = inTabCoordinates(down.position)
                var previousX = downPosition.x
                isDragging = true
                try {
                    press()
                    onDragStarted(downPosition)
                    while (isDragging) {
                        // The original finger owns the position and the release target.
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            if (change.isConsumed) break
                            isDragging = false
                            onDragStopped(inTabCoordinates(change.position))
                            release()
                            break
                        }
                        if (change.position == change.previousPosition) continue
                        if (change.isConsumed) break
                        val position = inTabCoordinates(change.position)
                        if (position.x != previousX) {
                            onDrag(position, position - downPosition)
                            previousX = position.x
                        }
                    }
                } finally {
                    cancelDrag()
                }
            }
        }

    private fun inTabCoordinates(position: Offset): Offset {
        val source = inputCoordinates
        val destination = dragCoordinateSpace()
        return if (source?.isAttached == true && destination?.isAttached == true) {
            destination.localPositionOf(source, position)
        } else position
    }

    private fun cancelDrag() {
        if (!isDragging) return
        isDragging = false
        onDragCancelled()
        release()
    }

    fun moveTo(centerPx: Float, widthPx: Float, tracking: Boolean = false) {
        if (targetCenter == centerPx && targetWidth == widthPx && this.tracking == tracking) return
        targetCenter = centerPx
        targetWidth = widthPx
        this.tracking = tracking
        // Retarget the same oscillators so the release and reversals inherit their momentum.
        center.target(centerPx, when {
            tracking -> TrackingCenter
            isTopBar -> TopRestingCenter
            else -> BottomRestingCenter
        })
        width.target(widthPx, when {
            !tracking -> RestingWidth
            isTopBar -> TopTrackingWidth
            else -> BottomTrackingWidth
        })
        requestFrames()
    }

    fun snapTo(centerPx: Float, widthPx: Float) {
        targetCenter = centerPx
        targetWidth = widthPx
        tracking = false
        center.reset(centerPx)
        width.reset(widthPx)
        resetFlex()
        publish()
    }

    fun press() {
        releasePending = false
        if (!liftRequested) liftedFor = 0f
        liftRequested = true
        lift.target(1f, LensLift)
        requestFrames()
    }

    fun release() {
        if (!liftRequested) return
        releasePending = true
        requestFrames()
    }

    fun cancelPress() {
        isDragging = false
        liftRequested = false
        releasePending = false
        liftedFor = 0f
        lift.reset(0f)
        resetFlex()
        publish()
    }

    private val needsFrame: Boolean
        get() = isMoving || !lift.isSettled || releasePending ||
            (liftRequested && liftedFor < MinimumLiftSeconds) ||
            movementPending || !horizontalFlex.isSettled || !verticalFlex.isSettled || !flexDrift.isSettled

    private fun requestFrames() {
        if (renderJob?.isActive == true || !needsFrame) return
        renderJob = animationScope.launch {
            var previousFrame = 0L
            val motion = coroutineContext[MotionDurationScale]
            while (needsFrame) withFrameNanos { now ->
                val scale = motion?.scaleFactor ?: 1f
                if (scale <= 0f) {
                    center.finish()
                    width.finish()
                    if (releasePending) liftRequested = false
                    releasePending = false
                    liftedFor = MinimumLiftSeconds
                    lift.reset(if (liftRequested) 1f else 0f)
                    resetFlex()
                    publish()
                } else if (previousFrame != 0L) {
                    advance((now - previousFrame) / 1_000_000_000f / scale)
                }
                previousFrame = now
            }
        }
    }

    internal fun advance(seconds: Float) {
        if (seconds <= 0f) return
        val dt = seconds.coerceAtMost(.1f)
        val positionWasMoving = !center.isSettled
        center.advance(dt)
        width.advance(dt)
        lift.advance(dt)
        if (liftRequested) liftedFor = (liftedFor + dt).coerceAtMost(MinimumLiftSeconds)
        if (releasePending && liftedFor >= MinimumLiftSeconds &&
            abs(center.value - targetCenter) <= 8f * density
        ) {
            releasePending = false
            liftRequested = false
            lift.target(0f, LensLift)
        }
        if (positionWasMoving || movementPending) updateFlex(dt)
        horizontalFlex.advance(dt)
        verticalFlex.advance(dt)
        flexDrift.advance(dt)
        publish()
    }

    private fun updateFlex(dt: Float) {
        val position = center.value / density
        val alpha = loupeLowPassAlpha(.3f, dt)
        val nextPosition = lerp(filteredPosition, position, alpha)
        val nextVelocity = lerp(filteredVelocity, (nextPosition - filteredPosition) / dt, alpha)
        val acceleration = (abs(nextVelocity) - abs(filteredVelocity)) / dt
        filteredAcceleration = lerp(filteredAcceleration, acceleration, alpha)
        filteredPosition = nextPosition
        filteredVelocity = nextVelocity
        movementPending = !center.isSettled || abs(filteredPosition - position) > .01f ||
            abs(filteredVelocity) > .1f || abs(filteredAcceleration) > 2f
        val liveWidth = (width.value / density + 16f * lift.value).coerceAtLeast(1f)
        val liveHeight = heightPx / density + 16f * lift.value
        val sizeFraction = ((minOf(liveWidth, liveHeight) - 37f) / 33f).coerceIn(0f, 1f)
        if (sizeFraction != flexSizeFraction) {
            flexSizeFraction = sizeFraction
            flexSpec = GlasmioLoupeFlexMotion(
                minScale = lerp(.90f, .75f, sizeFraction),
                maxScale = lerp(1.10f, 1.15f, sizeFraction),
                scalePoints = lerp(10f, 100f, sizeFraction),
                normalization = lerp(2000f, 2500f, sizeFraction),
            )
            val response = lerp(.444f, .50f, sizeFraction)
            trackingFlexSpring = LiquidMenuSpringSpec(lerp(.56f, .90f, sizeFraction), response)
            restingFlexSpring = LiquidMenuSpringSpec(lerp(.56f, 1f, sizeFraction), response)
        }
        val spring = if (tracking) trackingFlexSpring else restingFlexSpring
        val flex = if (movementPending) loupeFlex(
            Offset(filteredAcceleration, 0f), Offset(filteredVelocity, 0f),
            liveWidth, liveHeight, flexSpec,
        ) else GlasmioLoupeFlex()
        horizontalFlex.target(flex.scale.x, spring)
        verticalFlex.target(flex.scale.y, spring)
        flexDrift.target(flex.drift.x * density, spring)
    }

    private fun resetFlex() {
        filteredPosition = center.value / density
        filteredVelocity = 0f
        filteredAcceleration = 0f
        movementPending = false
        horizontalFlex.reset(1f)
        verticalFlex.reset(1f)
        flexDrift.reset(0f)
    }

    private fun publish() {
        centerPx = center.value
        widthPx = width.value
        liftProgress.floatValue = lift.value
        flexScale = Offset(horizontalFlex.value, verticalFlex.value)
        flexOffsetPx = flexDrift.value
    }

    private companion object {
        const val MinimumLiftSeconds = .22f
        val TrackingCenter = LiquidMenuSpringSpec(.85f, .20f)
        val TopRestingCenter = LiquidMenuSpringSpec(.90f, .40f)
        val BottomRestingCenter = LiquidMenuSpringSpec(.85f, .40f)
        val TopTrackingWidth = LiquidMenuSpringSpec(.90f, .30f)
        val BottomTrackingWidth = LiquidMenuSpringSpec(.85f, .30f)
        val RestingWidth = LiquidMenuSpringSpec(.85f, .60f)
        val LensLift = LiquidMenuSpringSpec(1f, .25f)
    }
}
