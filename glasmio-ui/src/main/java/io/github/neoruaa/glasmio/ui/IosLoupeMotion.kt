package io.github.neoruaa.glasmio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.math.tanh

/** One display frame at 60 Hz: the cadence UIKit's loupe constants are expressed in. */
private const val ReferenceFrameSeconds = 1f / 60f

/** Largest frame gap accepted by an integrator, so a long stall cannot fling a lens across the screen. */
private const val MaxFrameSeconds = 0.1f

/**
 * Integration step. The follow spring reaches `ω ≈ 31 rad/s`, where semi-implicit Euler only stays
 * stable below `dt < 2ζ/ω ≈ 45 ms`; UIKit's CA springs are analytic and have no such limit. Every
 * frame is therefore split into substeps of at most this length, which keeps a 30 Hz or stalled
 * frame from overshooting or diverging.
 */
private const val MaxSubstepSeconds = 1f / 120f

/** Distance under which the follower parks on its target instead of writing a new frame. */
private const val ParkDistance = 0.05f

/** Speed under which a parked follower discards its remaining velocity. */
private const val ParkSpeed = 0.5f

/** UIKit's line-break dismissal progress at which the lens closes. */
internal const val LoupeDismissalThreshold = 0.85f

/** Distance, in dp, that counts as a full line break for [loupeLineBreakProgress]. */
internal const val LoupeLineBreakDistance = 39f

/**
 * Timing for the glass caret loupe recovered from `UIKitCore` (`_UITextMagnifiedGlassLoupeView`,
 * `UITextLoupeSession`, `_UIFlexInteraction`).
 *
 * Springs are declared as UIKit does, with a damping ratio and a response (the period of the
 * underlying undamped spring), and converted to Compose stiffness with [loupeSpringStiffness].
 * [followLowPass] is UIKit's per-frame coefficient, so it is rescaled by the real frame time
 * before use; the remaining motion is a plain spring, not a hand-tuned curve.
 */
@Immutable
data class GlasmioLoupeMotion(
    /**
     * Weight of the touch point in the per-60 Hz low-pass that produces the model `center`.
     * UIKit blends `0.3 * target + 0.7 * previous model` on every display link tick, i.e. this is
     * the `0.3`, and it is rescaled by the real frame time before use.
     */
    val followLowPass: Float = 0.3f,
    /** Damping ratio of the spring that lands the low-passed position. */
    val followDampingRatio: Float = 0.7f,
    /** UIKit response of the follow spring, in seconds. */
    val followResponseSeconds: Float = 0.2f,
    /** Damping ratio of the alpha and tone pass that opens the lens in place. */
    val openRevealDampingRatio: Float = 1f,
    /** UIKit response of the open reveal pass, in seconds. */
    val openRevealResponseSeconds: Float = 0.4f,
    /** Damping ratio of the pass that grows the lens and lifts it off the finger. */
    val openMorphDampingRatio: Float = 0.8f,
    /** UIKit response of the open morph pass, in seconds. */
    val openMorphResponseSeconds: Float = 0.5f,
    /** Damping ratio of the dismissal spring. */
    val closeDampingRatio: Float = 1f,
    /** UIKit response of the dismissal spring, in seconds. */
    val closeResponseSeconds: Float = 0.23f,
    /** Dragging deformation of the lens body. */
    val flex: GlasmioLoupeFlexMotion = GlasmioLoupeFlexMotion(),
    /** Dismissal progress at which the lens closes while the gesture continues. */
    val dismissalThreshold: Float = LoupeDismissalThreshold,
) {
    init {
        require(followLowPass > 0f && followLowPass <= 1f) { "Follow low-pass must be in (0, 1]." }
        require(followDampingRatio > 0f) { "Follow damping ratio must be positive." }
        require(followResponseSeconds > 0f) { "Follow response must be positive." }
        require(openRevealDampingRatio > 0f && openMorphDampingRatio > 0f && closeDampingRatio > 0f) {
            "Damping ratios must be positive."
        }
        require(openRevealResponseSeconds > 0f && openMorphResponseSeconds > 0f && closeResponseSeconds > 0f) {
            "Responses must be positive."
        }
        require(dismissalThreshold > 0f && dismissalThreshold <= 1f) { "Dismissal threshold must be in (0, 1]." }
    }
}

/**
 * Acceleration-driven deformation of the loupe body (`_UIFlexInteractionLoupeVariantSpec`).
 *
 * Deformation is sourced from acceleration, not displacement, so a finger moving at constant
 * speed leaves the lens undeformed and only acceleration or deceleration stretches it. [drift]
 * anchors the leading edge instead of translating the whole body, which is what makes the
 * compression read as a liquid rather than as a slide.
 */
@Immutable
data class GlasmioLoupeFlexMotion(
    /** Lower bound of the compression factor. */
    val minScale: Float = 0.75f,
    /** Upper bound of the stretch factor. */
    val maxScale: Float = 1.15f,
    /** Absolute deformation budget in dp; only constrains views smaller than twice this. */
    val scalePoints: Float = 100f,
    /** Acceleration, in dp/s², that saturates the deformation. */
    val normalization: Float = 2500f,
    /** Damping ratio of the spring that lands the deformation. */
    val dampingRatio: Float = 1f,
    /** Response of the deformation spring, in seconds. */
    val responseSeconds: Float = 0.5f,
    /** Damping ratio used while the drag is still tracking. */
    val trackingDampingRatio: Float = 0.9f,
    /**
     * Peak compression of the motion axis when the interaction activates, as a fraction of the body.
     *
     * UIKit gets this from `scaleSpring.retargetImpulse`, which injects a velocity the first time the
     * interaction targets a new state instead of easing in from rest. The loupe variant leaves
     * `retargetImpulse` at its default and the runtime value is unverified, so the port reproduces the
     * envelope UIKit's impulse produces (a small non-uniform scale that settles in [responseSeconds])
     * instead of the impulse itself.
     */
    val activationSquash: Float = 0.02f,
    /** Peak stretch of the perpendicular axis when the interaction activates. */
    val activationStretch: Float = 0.012f,
    /** Whether the activation pulse runs at all. */
    val activationEnabled: Boolean = true,
    /** Whether the lens deforms at all. */
    val enabled: Boolean = true,
) {
    init {
        require(minScale > 0f && maxScale > 0f) { "Flex scales must be positive." }
        require(scalePoints >= 0f) { "Flex scale points must be non-negative." }
        require(normalization > 0f) { "Flex normalization must be positive." }
        require(dampingRatio > 0f && trackingDampingRatio > 0f) { "Flex damping ratios must be positive." }
        require(responseSeconds > 0f) { "Flex response must be positive." }
        require(activationSquash >= 0f && activationSquash < 1f) { "Activation squash must be in [0, 1)." }
        require(activationStretch >= 0f && activationStretch < 1f) { "Activation stretch must be in [0, 1)." }
    }
}

/** Non-uniform scale and leading-edge drift of the loupe body. Identity when nothing moves. */
@Immutable
data class GlasmioLoupeFlex(
    val scale: Offset = Offset(1f, 1f),
    val drift: Offset = Offset.Zero,
) {
    val isIdentity: Boolean get() = scale == Offset(1f, 1f) && drift == Offset.Zero
}

/**
 * Writing axis of the edited text, from `-[UITextInteractionAssistant loupeOrientation]`
 * (`isDisplayingVerticalSelection ? 2 : 1`). It swaps the lens to `85 × 115` and moves the lift from
 * `(0, -75)` to `(+75, 0)`.
 */
enum class GlasmioLoupeOrientation {
    Horizontal,
    Vertical,
}

/** A single integration result of [loupeFollowStep] and [loupeSpringStep]. */
@Immutable
internal data class LoupeFollowFrame(
    val position: Offset,
    val velocity: Offset,
    /**
     * The `model` position: what the display link last wrote to `center`, i.e. the low-passed target
     * that the portal samples. The body ([position]) trails it by the spring's lag.
     * [loupeSpringStep] springs straight at its target, so it reports the target itself.
     */
    val model: Offset,
)

/** Compose stiffness for a UIKit spring expressed as a response period. */
internal fun loupeSpringStiffness(responseSeconds: Float): Float {
    val angular = 2f * PI.toFloat() / responseSeconds
    return angular * angular
}

/**
 * Per-frame coefficient equivalent to [alphaPerFrame] at 60 Hz, rescaled for the real frame time.
 * UIKit hard-codes 0.3 per display-link tick; Android panels vary between 60 and 144 Hz, so the
 * coefficient is recomputed to keep the 47 ms time constant instead of the per-frame ratio.
 */
internal fun loupeLowPassAlpha(alphaPerFrame: Float, elapsedSeconds: Float): Float {
    val frame = elapsedSeconds.coerceIn(0f, MaxFrameSeconds) / ReferenceFrameSeconds
    return 1f - (1f - alphaPerFrame).pow(frame)
}

/** UIKit's first-order low-pass: `next = alpha * target + (1 - alpha) * current`. */internal fun loupeLowPass(current: Offset, target: Offset, alphaPerFrame: Float, elapsedSeconds: Float): Offset {
    if (current == target) return target
    val alpha = loupeLowPassAlpha(alphaPerFrame, elapsedSeconds)
    return Offset(
        current.x + (target.x - current.x) * alpha,
        current.y + (target.y - current.y) * alpha,
    )
}

/**
 * One display frame of `-[_UITextMagnifiedGlassLoupeView _displayLinkFired:]` (`0x1c4108e34`).
 *
 * UIKit keeps **two** positions and this function needs both to be faithful:
 *
 * 1. `model` is the model `center`. The display link blends it towards the constrained touch point
 *    with the 60 Hz coefficient `0.3` (`0x1c4108e34`, `center' = 0.3 * target + 0.7 * center`, read
 *    back from `-center`), and that blended value is what the portal is transformed by, so the
 *    magnified content sits on this series.
 * 2. `position` is the presentation `center`, which the re-targeted `damping 0.7 / response 0.2`
 *    spring drags towards the model it was last given.
 *
 * The order matters. Low-passing `position` instead of `model` folds the spring's own lag back into
 * the filter, so the remaining distance to the finger has to satisfy `alpha * gap == spring error`
 * and the two lags compose in the wrong proportion: a 500 dp/s drag then trails by ~73 dp instead of
 * ~35 dp, which reads as the lens not keeping up.
 */
internal fun loupeFollowStep(
    position: Offset,
    velocity: Offset,
    model: Offset,
    target: Offset,
    motion: GlasmioLoupeMotion,
    elapsedSeconds: Float,
): LoupeFollowFrame {
    val dt = elapsedSeconds.coerceIn(0f, MaxFrameSeconds)
    if (dt <= 0f) return LoupeFollowFrame(position, velocity, model)
    // UIKit pauses the display link once `dist(modelPosition, center)` collapses, so parking needs
    // both series on the target rather than just the presentation.
    if ((model - target).getDistance() < ParkDistance &&
        (position - model).getDistance() < ParkDistance &&
        velocity.getDistance() < ParkSpeed
    ) {
        return LoupeFollowFrame(target, Offset.Zero, target)
    }
    val goal = loupeLowPass(model, target, motion.followLowPass, dt)
    val stiffness = loupeSpringStiffness(motion.followResponseSeconds)
    val damping = 2f * motion.followDampingRatio * sqrt(stiffness)
    var integrated = position
    var current = velocity
    var remaining = dt
    while (remaining > 0f) {
        val step = minOf(remaining, MaxSubstepSeconds)
        current = Offset(
            current.x + (stiffness * (goal.x - integrated.x) - damping * current.x) * step,
            current.y + (stiffness * (goal.y - integrated.y) - damping * current.y) * step,
        )
        integrated += current * step
        remaining -= step
    }
    return LoupeFollowFrame(integrated, current, goal)
}

/**
 * Centre of the lens on its lift axis: the touch point offset by [lift] while the body fits, and
 * clamped towards the middle of `[lower, upper]` when it does not.
 *
 * UIKit only hides the body in that case (`needsHideLoupe:`, no callers in 26), which would hide the
 * lens for every field near the top of the screen. Flipping the body past the finger would break the
 * fixed offset the rest of the geometry assumes, so the frame is clamped instead and the sample
 * stays anchored on the touch point.
 */
internal fun loupeLiftCenter(finger: Float, lift: Float, extent: Float, lower: Float, upper: Float): Float {
    val minimum = lower + extent / 2f
    val maximum = upper - extent / 2f
    if (maximum < minimum) return (lower + upper) / 2f
    return (finger - lift).coerceIn(minimum, maximum)
}

/**
 * Horizontal clamp of the lens over the gesture (`_LoupeConstrainPointHorizontally`).
 *
 * UIKit insets the container by `inset` on both sides to get the upper bound but uses the same
 * `inset` as the lower bound, so the two bounds are not symmetric: the reachable maximum is
 * `width - 2 * inset - radius`, not `width - inset - radius`. Only x is clamped because a horizontal
 * lens is wider than the finger.
 */
internal fun loupeClampX(x: Float, left: Float, right: Float, lensWidth: Float, inset: Float): Float {
    val radius = lensWidth / 2f
    val minimum = left + inset + radius
    val maximum = left + (right - left) - 2f * inset - radius
    if (maximum < minimum) return (left + right) / 2f
    return x.coerceIn(minimum, maximum)
}

/** UIKit's `__UILerp`. */
internal fun loupeLerp(amount: Float, from: Float, to: Float): Float = (1f - amount) * from + amount * to

/** Initial slope of UIKit's soft-clamp shoulder (`0x1c553f980`). */
private const val SoftClampSlope = 0.55f

/**
 * UIKit's soft clamp around an axis range (`sub_1C553F980`): exact inside `[lower, upper]`, then a
 * `tanh` shoulder whose initial slope is 0.55 and whose asymptotes sit one third of the range
 * outside the bounds. The loupe's `[0.75, 1.15]` therefore saturates at `0.617 / 1.283`, so neither
 * a hard flick nor its exact reverse can drive the body past roughly ±30%. UIKit applies it to each
 * interpolated term, not to the sum.
 */
internal fun loupeSoftClamp(value: Float, lower: Float, upper: Float): Float {
    val margin = (upper - lower) / 3f
    if (margin <= 0f) return value.coerceIn(lower, upper)
    return when {
        value > upper -> upper + margin * tanh((value - upper) / margin * SoftClampSlope)
        value < lower -> {
            val floor = lower - margin
            floor + margin * (tanh((value - floor) / margin * SoftClampSlope - SoftClampSlope) + 1f)
        }
        else -> value
    }
}

private fun flexLowerBound(dimension: Float, motion: GlasmioLoupeFlexMotion): Float =
    max(motion.minScale, (dimension - motion.scalePoints) / dimension)

private fun flexUpperBound(dimension: Float, motion: GlasmioLoupeFlexMotion): Float =
    min(motion.maxScale, (dimension + motion.scalePoints) / dimension)

/**
 * `_UIFlexInteraction`'s loupe deformation. [acceleration] and [velocity] are in dp per second and
 * dp per second squared; [width] and [height] are the lens body in dp at its resting size, and
 * [bodyScale] is the current opening or closing scale of that body.
 *
 * The normalized acceleration is interpolated as UIKit does, signed: positive acceleration stretches
 * the motion axis towards [GlasmioLoupeFlexMotion.maxScale] and compresses the perpendicular one
 * towards [GlasmioLoupeFlexMotion.minScale], and a negative value interpolates the other way. Each
 * interpolated term then passes through [loupeSoftClamp], which is what keeps the mirrored halves
 * near ±30% instead of letting a hard flick collapse the body.
 *
 * The sign is not the direction of travel. UIKit feeds a directionless acceleration
 * ([loupeDirectionlessAcceleration]), so a positive entry means the finger is speeding up and a
 * negative one means it is slowing down; dragging left and dragging right flatten the same way, and
 * only the drift's `sign(velocity)` mirrors. Callers must not differentiate raw pointer positions:
 * sub-pixel quantisation turns into thousands of dp/s² and would leave the body permanently
 * deformed.
 *
 * `_UIFlexInteraction.update` reads the interaction view's live `bounds` every frame, so both the
 * absolute budget and the drift length follow the body while the opening pass grows it from the
 * caret. [bodyScale] is that live size; passing the resting size during the opening pass makes the
 * drift up to twenty times too large, which reads as the small opening body being flung sideways.
 */
internal fun loupeFlex(
    acceleration: Offset,
    velocity: Offset,
    width: Float,
    height: Float,
    motion: GlasmioLoupeFlexMotion,
    bodyScale: Offset = Offset(1f, 1f),
): GlasmioLoupeFlex {
    if (!motion.enabled || acceleration == Offset.Zero) return GlasmioLoupeFlex()
    val liveWidth = (width * bodyScale.x).coerceAtLeast(1f)
    val liveHeight = (height * bodyScale.y).coerceAtLeast(1f)
    val nx = acceleration.x / motion.normalization
    val ny = acceleration.y / motion.normalization
    var scaleX = 1f
    var scaleY = 1f
    var driftX = 0f
    var driftY = 0f
    if (nx != 0f) {
        val widthLower = flexLowerBound(liveWidth, motion)
        val widthUpper = flexUpperBound(liveWidth, motion)
        val stretch = loupeSoftClamp(loupeLerp(nx, 1f, widthUpper), widthLower, widthUpper)
        scaleX += stretch - 1f
        val heightLower = flexLowerBound(liveHeight, motion)
        val heightUpper = flexUpperBound(liveHeight, motion)
        val squeeze = loupeSoftClamp(loupeLerp(nx, 1f, heightLower), heightLower, heightUpper)
        scaleY += squeeze - 1f
        driftX = (1f - stretch) * 0.5f * liveWidth * sign(velocity.x)
    }
    if (ny != 0f) {
        val heightLower = flexLowerBound(liveHeight, motion)
        val heightUpper = flexUpperBound(liveHeight, motion)
        val stretch = loupeSoftClamp(loupeLerp(ny, 1f, heightUpper), heightLower, heightUpper)
        scaleY += stretch - 1f
        driftY = (1f - stretch) * 0.5f * liveHeight * sign(velocity.y)
    }
    return GlasmioLoupeFlex(scale = Offset(scaleX, scaleY), drift = Offset(driftX, driftY))
}

/**
 * Non-uniform scale of the activation pulse, which has no counterpart in UIKit's `update`: it stands in
 * for the one-shot velocity `scaleSpring.retargetImpulse` adds when the interaction first activates.
 *
 * [pulse] decays from `1` to `0` on the flex spring, so the body starts `activationSquash` narrower
 * and `activationStretch` taller than its resting frame and relaxes into it. The motion axis is the
 * body's width, matching an interaction that just settled on a caret instead of moving.
 */
internal fun loupeActivationScale(pulse: Float, motion: GlasmioLoupeFlexMotion): Offset {
    if (!motion.enabled || !motion.activationEnabled || pulse == 0f) return Offset(1f, 1f)
    return Offset(1f - motion.activationSquash * pulse, 1f + motion.activationStretch * pulse)
}

/**
 * Position of a lens driven by [loupeFollowStep]. Styles and lenses that own their own placement
 * can drive this directly; [GlasmioMagnifier] uses one internally for its center.
 */
@Stable
class GlasmioLoupeFollowState internal constructor(initialPosition: Offset) {
    /** Current center of the lens in overlay pixels. */
    var position by mutableStateOf(initialPosition)
        private set

    /**
     * Point the magnified sample is anchored to, in overlay pixels.
     *
     * This is UIKit's model `center`: the display link blends it towards the touch point and hands
     * it to `_updatePortalViewTransformForPosition:zoomScale:` before the body springs after it, so
     * the content leads the body. It has to be a series of its own — low-passing [position] instead
     * folds the spring's lag into the filter and doubles the distance the body trails by.
     */
    var samplePosition by mutableStateOf(initialPosition)
        private set

    internal var velocity: Offset = Offset.Zero
        private set

    /** Restart the follower from [target] without a catch-up animation. */
    fun snapTo(target: Offset) {
        position = target
        samplePosition = target
        velocity = Offset.Zero
    }

    internal fun advance(target: Offset, motion: GlasmioLoupeMotion, elapsedSeconds: Float) {
        val frame = loupeFollowStep(position, velocity, samplePosition, target, motion, elapsedSeconds)
        velocity = frame.velocity
        if (frame.position != position) position = frame.position
        if (frame.model != samplePosition) samplePosition = frame.model
    }
}

/**
 * Remember a [GlasmioLoupeFollowState] that tracks [target] every display frame.
 *
 * A jump farther than [snapDistance] restarts the follower instead of dragging the lens across the
 * screen: UIKit begins a new loupe session for a new long press, and a text field reused for a
 * different gesture must not fly its lens over the content. The follower stops requesting frames
 * while [enabled] is false and resumes from the current value when the lens returns.
 */
@Composable
fun rememberGlasmioLoupeFollow(
    target: Offset,
    motion: GlasmioLoupeMotion = GlasmioLoupeMotion(),
    snapDistance: Float = Float.POSITIVE_INFINITY,
    enabled: Boolean = true,
): GlasmioLoupeFollowState {
    val state = remember(motion) { GlasmioLoupeFollowState(target) }
    val currentTarget = rememberUpdatedState(target)
    val currentSnap = rememberUpdatedState(snapDistance)
    LaunchedEffect(state, enabled) {
        if (!enabled) return@LaunchedEffect
        var lastFrame = 0L
        while (isActive) {
            val frameTime = withFrameNanos { it }
            val elapsed = if (lastFrame == 0L) ReferenceFrameSeconds
            else (frameTime - lastFrame) / 1_000_000_000f
            lastFrame = frameTime
            val next = currentTarget.value
            if ((state.position - next).getDistance() > currentSnap.value) state.snapTo(next)
            else state.advance(next, motion, elapsed)
        }
    }
    return state
}

/** Velocity and acceleration sampled from a moving position, in dp units. */
@Immutable
internal data class LoupeVelocitySample(val velocity: Offset, val acceleration: Offset) {
    companion object {
        val Zero = LoupeVelocitySample(Offset.Zero, Offset.Zero)
    }
}

/**
 * Velocity of a sampled run of positions, from a least-squares slope over the newest window.
 *
 * `_UIVelocityIntegrator` keeps a ring of samples and an `accelerationFilter`; differencing two
 * consecutive pointer positions instead amplifies sub-pixel quantisation into thousands of dp/s²,
 * which would saturate any deformation built on it. The arrays hold [size] samples in chronological
 * order with the newest last, and only samples inside [windowNanos] of the newest participate.
 */
internal fun loupeWindowedVelocity(
    times: LongArray,
    positionsX: FloatArray,
    positionsY: FloatArray,
    size: Int,
    windowNanos: Long,
): Offset? {
    if (size < 2) return null
    val newest = times[size - 1]
    val oldest = newest - windowNanos
    var count = 0
    var sumT = 0.0
    var sumT2 = 0.0
    var sumX = 0.0
    var sumY = 0.0
    var sumTX = 0.0
    var sumTY = 0.0
    for (index in size - 1 downTo 0) {
        val time = times[index]
        if (time < oldest) break
        val t = (time - newest) / 1_000_000_000.0
        val x = positionsX[index].toDouble()
        val y = positionsY[index].toDouble()
        count++
        sumT += t
        sumT2 += t * t
        sumX += x
        sumY += y
        sumTX += t * x
        sumTY += t * y
    }
    if (count < 2) return null
    val denominator = count * sumT2 - sumT * sumT
    if (denominator <= 1e-12) return null
    return Offset(
        ((count * sumTX - sumT * sumX) / denominator).toFloat(),
        ((count * sumTY - sumT * sumY) / denominator).toFloat(),
    )
}

/** Samples kept for [loupeWindowedVelocity]; 24 frames cover a 120 Hz window plus slack. */
private const val VelocitySampleCapacity = 24

/** Window over which a velocity sample is fitted. */
private const val VelocityWindowNanos = 56_000_000L

/** Delay between the two velocity samples whose difference is the acceleration. */
private const val AccelerationWindowNanos = 56_000_000L

/** Time constant of the exponential filter applied to the acceleration. */
private const val AccelerationSmoothingSeconds = 0.1f

/**
 * Acceleration as `_UIVelocityIntegrator` reports it to the flex interaction: the difference of the
 * **absolute** velocity components over [spanSeconds].
 *
 * `_UIFlexInteraction` sets `prefersDirectionlessAcceleration = YES` in its initializer
 * (`0x1c5079d30`, `MOV W2, #1`), and `-[_UIVelocityIntegrator addSample3D:withTimestamp:]` then
 * runs both samples through `fabs` before subtracting (`0x1c484ee38`–`0x1c484ee80`). Speeding up is
 * therefore positive and slowing down is negative **no matter which way the finger travels**, which
 * is why the native lens flattens for a drag in either direction and only rises when the drag is
 * arrested. Differentiating the signed velocity instead would mirror the body on reversal.
 */
internal fun loupeDirectionlessAcceleration(
    velocity: Offset,
    previousVelocity: Offset,
    spanSeconds: Float,
): Offset {
    if (spanSeconds <= 0f) return Offset.Zero
    return Offset(
        (abs(velocity.x) - abs(previousVelocity.x)) / spanSeconds,
        (abs(velocity.y) - abs(previousVelocity.y)) / spanSeconds,
    )
}

/**
 * Velocity and acceleration of [position], mirroring `_UIVelocityIntegrator` closely enough to drive
 * [loupeFlex]: the velocity is fitted over a 56 ms window and the acceleration is the difference
 * between that fit and the fit 56 ms earlier, low-passed with a 100 ms time constant. The exact
 * smoothing of the UIKit integrator is not recovered, so these windows are the smallest values that
 * keep pointer quantisation below a few percent of [GlasmioLoupeFlexMotion.normalization]. The
 * acceleration is directionless; see [loupeDirectionlessAcceleration].
 */
@Composable
internal fun rememberLoupeVelocitySample(position: Offset, enabled: Boolean = true): LoupeVelocitySample {
    val current = rememberUpdatedState(position)
    var sample by remember { mutableStateOf(LoupeVelocitySample.Zero) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        val times = LongArray(VelocitySampleCapacity)
        val positionsX = FloatArray(VelocitySampleCapacity)
        val positionsY = FloatArray(VelocitySampleCapacity)
        var size = 0
        val velocityTimes = LongArray(VelocitySampleCapacity)
        val velocityX = FloatArray(VelocitySampleCapacity)
        val velocityY = FloatArray(VelocitySampleCapacity)
        var velocitySize = 0
        var acceleration = Offset.Zero
        var lastFrame = 0L
        while (isActive) {
            val frameTime = withFrameNanos { it }
            val elapsed = if (lastFrame == 0L) ReferenceFrameSeconds
            else (frameTime - lastFrame) / 1_000_000_000f
            lastFrame = frameTime
            if (size == VelocitySampleCapacity) {
                times.copyInto(times, 0, 1)
                positionsX.copyInto(positionsX, 0, 1)
                positionsY.copyInto(positionsY, 0, 1)
                size--
            }
            val next = current.value
            times[size] = frameTime
            positionsX[size] = next.x
            positionsY[size] = next.y
            size++
            val velocity = loupeWindowedVelocity(times, positionsX, positionsY, size, VelocityWindowNanos)
                ?: continue
            if (velocitySize == velocityTimes.size) {
                velocityTimes.copyInto(velocityTimes, 0, 1)
                velocityX.copyInto(velocityX, 0, 1)
                velocityY.copyInto(velocityY, 0, 1)
                velocitySize--
            }
            velocityTimes[velocitySize] = frameTime
            velocityX[velocitySize] = velocity.x
            velocityY[velocitySize] = velocity.y
            velocitySize++
            val reference = frameTime - AccelerationWindowNanos
            val previousIndex = (velocitySize - 1 downTo 0).firstOrNull { velocityTimes[it] <= reference }
            if (previousIndex != null) {
                val span = (frameTime - velocityTimes[previousIndex]) / 1_000_000_000f
                if (span > 1f / 240f) {
                    val instant = loupeDirectionlessAcceleration(
                        velocity = velocity,
                        previousVelocity = Offset(velocityX[previousIndex], velocityY[previousIndex]),
                        spanSeconds = span,
                    )
                    acceleration = acceleration + (instant - acceleration) *
                        loupeLowPassAlpha(AccelerationTimeAlpha, elapsed)
                    sample = LoupeVelocitySample(velocity, acceleration)
                }
            } else {
                sample = LoupeVelocitySample(velocity, acceleration)
            }
        }
    }
    return sample
}

/** Per-frame coefficient of a first-order filter whose time constant is [AccelerationSmoothingSeconds]. */
private val AccelerationTimeAlpha: Float =
    1f - kotlin.math.exp(-ReferenceFrameSeconds / AccelerationSmoothingSeconds)

/**
 * One display frame of a damped spring towards [target], keeping the current [velocity].
 *
 * Compose's `animate*AsState` restarts its spring with zero velocity whenever the target changes,
 * which turns a per-frame retargeted spring into a slow crawl. Integrating the spring here keeps the
 * momentum that a tracking gesture establishes, so a released deformation returns at the pace its
 * damping and stiffness describe.
 */
internal fun loupeSpringStep(
    position: Offset,
    velocity: Offset,
    target: Offset,
    dampingRatio: Float,
    stiffness: Float,
    elapsedSeconds: Float,
): LoupeFollowFrame {
    val dt = elapsedSeconds.coerceIn(0f, MaxFrameSeconds)
    if (dt <= 0f) return LoupeFollowFrame(position, velocity, target)
    if ((position - target).getDistance() < ParkDistance && velocity.getDistance() < ParkSpeed) {
        return LoupeFollowFrame(target, Offset.Zero, target)
    }
    val damping = 2f * dampingRatio * sqrt(stiffness)
    var integrated = position
    var current = velocity
    var remaining = dt
    while (remaining > 0f) {
        val step = minOf(remaining, MaxSubstepSeconds)
        current = Offset(
            current.x + (stiffness * (target.x - integrated.x) - damping * current.x) * step,
            current.y + (stiffness * (target.y - integrated.y) - damping * current.y) * step,
        )
        integrated += current * step
        remaining -= step
    }
    return LoupeFollowFrame(integrated, current, target)
}

/** Velocity of a spring that is integrated every frame instead of being restarted per target. */
private class LoupeSpringHolder(initial: Offset) {
    var value by mutableStateOf(initial)
    var velocity: Offset = Offset.Zero
}

/**
 * Spring an offset value towards [target] at display rate, retaining momentum across retargets.
 * Stops requesting frames while [enabled] is false.
 */
@Composable
internal fun rememberLoupeSpringOffset(
    target: Offset,
    dampingRatio: Float,
    stiffness: Float,
    initial: Offset = target,
    enabled: Boolean = true,
): Offset {
    val holder = remember { LoupeSpringHolder(initial) }
    val currentTarget = rememberUpdatedState(target)
    val currentDamping = rememberUpdatedState(dampingRatio)
    LaunchedEffect(stiffness, enabled) {
        if (!enabled) return@LaunchedEffect
        var lastFrame = 0L
        while (isActive) {
            val frameTime = withFrameNanos { it }
            val elapsed = if (lastFrame == 0L) ReferenceFrameSeconds
            else (frameTime - lastFrame) / 1_000_000_000f
            lastFrame = frameTime
            val frame = loupeSpringStep(
                position = holder.value,
                velocity = holder.velocity,
                target = currentTarget.value,
                dampingRatio = currentDamping.value,
                stiffness = stiffness,
                elapsedSeconds = elapsed,
            )
            holder.velocity = frame.velocity
            if (frame.position != holder.value) holder.value = frame.position
        }
    }
    return holder.value
}

/** Dismissal progress of a loupe drag that has left its line by [distance], in the units of UIKit's 39 pt threshold. */
internal fun loupeLineBreakProgress(distance: Float, lineHeight: Float = LoupeLineBreakDistance): Float {
    if (lineHeight <= 0f) return 0f
    return (distance / lineHeight).coerceIn(0f, 1f)
}

/** Diagonal of the lens, the distance over which a follower restarts instead of catching up. */
internal fun loupeSnapDistance(width: Float, height: Float): Float = hypot(width, height) * 2f

/** Whether a progress value should close the lens. */
internal fun isLoupeDismissed(progress: Float, motion: GlasmioLoupeMotion): Boolean =
    progress >= motion.dismissalThreshold
