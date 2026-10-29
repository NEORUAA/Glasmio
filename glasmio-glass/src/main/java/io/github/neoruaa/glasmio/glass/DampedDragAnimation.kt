package io.github.neoruaa.glasmio.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
    consumeGestures: Boolean = false,
    val onDragCancelled: DampedDragAnimation.() -> Unit = onDragStopped,
    private val dragCoordinateSpace: (() -> LayoutCoordinates?)? = null,
    sharedValueAnimation: Animatable<Float, AnimationVector1D>? = null,
    horizontalDragOnly: Boolean = false,
    private val lensStyle: NativeControlLensStyle? = null,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {

    private val valueAnimationSpec =
        spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec =
        spring(0.5f, 300f, visibilityThreshold * 10f)
    // UIKit's lens spec supplies distinct lift/unlift springs for its small variant.
    private val nativeLiftSpec = lensStyle?.let {
        when (it) {
            NativeControlLensStyle.SwitchThumb, NativeControlLensStyle.SliderThumb ->
                spring(.625f, 541.54208f, .001f)
            NativeControlLensStyle.SegmentedIndicator, NativeControlLensStyle.TabBarIndicator ->
                spring(1f, 631.65468f, .001f)
        }
    }
    private val nativeUnliftSpec = lensStyle?.let {
        when (it) {
            NativeControlLensStyle.SwitchThumb, NativeControlLensStyle.SliderThumb ->
                spring(.7f, 157.91367f, .001f)
            NativeControlLensStyle.SegmentedIndicator, NativeControlLensStyle.TabBarIndicator ->
                spring(1f, 631.65468f, .001f)
        }
    }
    private val pressProgressAnimationSpec = nativeLiftSpec ?: spring(1f, 1000f, 0.001f)
    private val releaseProgressAnimationSpec = nativeUnliftSpec ?: pressProgressAnimationSpec
    private val scaleXAnimationSpec =
        nativeLiftSpec ?: spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec =
        nativeLiftSpec ?: spring(0.7f, 250f, 0.001f)
    private val releaseScaleXAnimationSpec = nativeUnliftSpec ?: scaleXAnimationSpec
    private val releaseScaleYAnimationSpec = nativeUnliftSpec ?: scaleYAnimationSpec

    private val valueAnimation =
        sharedValueAnimation ?: Animatable(initialValue, visibilityThreshold)
    // Input must accumulate before the animation coroutine gets a chance to run.
    private var requestedTarget by mutableFloatStateOf(valueAnimation.targetValue)
    private var targetRequest = 0L
    private val velocityAnimation =
        Animatable(0f, 5f)
    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val scaleXAnimation =
        Animatable(initialScale, 0.001f)
    private val scaleYAnimation =
        Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private var nativeRelease: Job? = null
    private var nativeLiftStarted: TimeMark? = null
    private var nativeWantsLifted = false

    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = requestedTarget
    val pressProgressState: State<Float> = pressProgressAnimation.asState()
    val pressProgress: Float get() = pressProgressState.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    private var dragCoordinates: LayoutCoordinates? = null

    val modifier: Modifier = Modifier
        .then(if (dragCoordinateSpace != null) {
            Modifier.onGloballyPositioned { dragCoordinates = it }
        } else Modifier)
        .pointerInput(this) {
            inspectDragGestures(
                consumeGestures = consumeGestures,
                horizontalDragOnly = horizontalDragOnly,
                onDragStart = { down ->
                    onDragStarted(down.position)
                    press()
                },
                onDragEnd = {
                    onDragStopped()
                    release()
                },
                onDragCancel = {
                    onDragCancelled()
                    release()
                }
            ) { change, dragAmount ->
                val source = dragCoordinates
                val destination = dragCoordinateSpace?.invoke()
                val amount = if (source?.isAttached == true && destination?.isAttached == true) {
                    // Convert a vector at the current transform; subtract origins so the
                    // moving indicator itself cannot contribute extra finger travel.
                    destination.localPositionOf(source, dragAmount) -
                        destination.localPositionOf(source, Offset.Zero)
                } else dragAmount
                onDrag(size, amount)
            }
        }

    fun press() {
        if (lensStyle != null) {
            nativeRelease?.cancel()
            nativeRelease = null
            if (!nativeWantsLifted) nativeLiftStarted = timeSource.markNow()
            nativeWantsLifted = true
        }
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        if (lensStyle != null) nativeRelease?.cancel()
        val liftStarted = nativeLiftStarted
        val operation = animationScope.launch {
            if (lensStyle == null) {
                withFrameNanos { }
                if (value != targetValue) {
                    val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                    snapshotFlow { valueAnimation.value }
                        .filter { abs(it - targetValue) < threshold }
                        .first()
                }
            } else {
                // UIKit's hang time is a minimum lifted lifetime, not a post-up delay.
                while (liftStarted != null && liftStarted.elapsedNow() < 220.milliseconds) {
                    withFrameNanos { }
                }
                nativeWantsLifted = false
            }
            launch { pressProgressAnimation.animateTo(0f, releaseProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, releaseScaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, releaseScaleYAnimationSpec) }
        }
        if (lensStyle != null) nativeRelease = operation
    }

    fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        requestedTarget = targetValue
        val request = ++targetRequest
        animationScope.launch {
            if (request != targetRequest) return@launch
            valueAnimation.animateTo(targetValue, valueAnimationSpec) { updateVelocity() }
        }
    }

    fun animateToValue(value: Float, animatePress: Boolean = true) {
        val targetValue = value.coerceIn(valueRange)
        requestedTarget = targetValue
        val request = ++targetRequest
        animationScope.launch {
            if (request != targetRequest) return@launch
            mutatorMutex.mutate {
                if (request != targetRequest) return@mutate
                if (animatePress) press()
                launch {
                    if (request == targetRequest) valueAnimation.animateTo(targetValue, valueAnimationSpec)
                }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            Clock.System.now().toEpochMilliseconds(),
            Offset(value, 0f)
        )
        val targetVelocity = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}
