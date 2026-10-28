package io.github.neoruaa.glasmio.ui

import androidx.compose.animation.core.FloatAnimationSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** Native sheet motion recovered from UIKitCore; see docs/ios-sheet-motion.md. */
internal object IosSheetMotion {
    // _UISheetPresentationMetrics uses _UISpringParameters' response initializer.
    const val ResponseSeconds = 0.3441442326
    const val DampingRatio = 1f
    const val HighSpeedDampingRatio = 0.8f
    const val DecelerationRate = 0.99
    const val MinimumProjectionSpeed = 250f
    const val HighSpeedThreshold = 1000f
    const val RubberBandCoefficient = 0.55f
    const val SettlingEpsilon = 0.001

    val angularFrequency: Double = 2.0 * PI / ResponseSeconds
    val stiffness: Float = (angularFrequency * angularFrequency).toFloat()
    private val normalSpring = IosSheetSettlingMotion(highSpeed = false)
    private val highSpeedSpring = IosSheetSettlingMotion(highSpeed = true)

    fun springSpec(highSpeed: Boolean = false): IosSheetSettlingMotion =
        if (highSpeed) highSpeedSpring else normalSpring

    /** Material fractions use the same normalized settling solver as the native UIView animator. */
    fun materialSpringSpec(): IosSheetSettlingMotion = normalSpring

    /** _UIHyperInteractor._projectedPoint uses velocity in points per second. */
    fun effectiveVelocity(velocity: Float, density: Float): Float =
        if (abs(velocity) < MinimumProjectionSpeed * density) 0f else velocity

    fun projectedOffset(offset: Float, velocity: Float, density: Float = 1f): Float =
        (offset.toDouble() + effectiveVelocity(velocity, density) *
            DecelerationRate / (1.0 - DecelerationRate) / 1000.0).toFloat()

    /** _UIHyperInteractor's touch rubber-band function, bounded by the layout's edge extent. */
    fun rubberBand(excess: Float, extent: Float): Float = when {
        excess == 0f || extent <= 0f -> 0f
        else -> sign(excess) * extent *
            (1f - 1f / (abs(excess) / extent * RubberBandCoefficient + 1f))
    }

    fun unconstrainedExcess(displacement: Float, extent: Float): Float = when {
        displacement == 0f || extent <= 0f -> 0f
        else -> {
            val fraction = (abs(displacement) / extent).coerceAtMost(0.999999f)
            sign(displacement) * extent * fraction / ((1f - fraction) * RubberBandCoefficient)
        }
    }

    /** HyperAnimator captures the constrained velocity before switching to its detent region. */
    fun constrainedReleaseVelocity(
        velocity: Float,
        rawOffset: Float,
        minimumOffset: Float,
        maximumOffset: Float,
        minimumExtent: Float,
        maximumExtent: Float,
        density: Float,
    ): Float {
        val effective = effectiveVelocity(velocity, density)
        val (excess, extent) = when {
            rawOffset < minimumOffset -> rawOffset - minimumOffset to minimumExtent
            rawOffset > maximumOffset -> rawOffset - maximumOffset to maximumExtent
            else -> return effective
        }
        // UIKit uses displacement/raw-distance, rather than the rubber function's derivative.
        return effective * rubberBand(excess, extent) / excess
    }

    fun upperRubberBandExtent(anchors: GlasmioSheetAnchors, safeAreaTop: Float, density: Float): Float =
        (anchors.large - safeAreaTop).coerceIn(0f, 100f * density)

    fun lowerRubberBandExtent(anchors: GlasmioSheetAnchors, density: Float): Float =
        min((anchors.hidden - anchors.large) * 0.25f, 200f * density)

    /** Resolve the one-dimensional detent continuum from the projected release endpoint. */
    fun closestValue(
        offset: Float,
        velocity: Float,
        anchors: GlasmioSheetAnchors,
        dismissible: Boolean,
        density: Float = 1f,
    ): GlasmioSheetValue {
        val projected = projectedOffset(offset, velocity, density)
        val values = anchors.values(dismissible)
        fun nearest(position: Float): Int = values.indices.minWith(
            compareBy<Int> { abs(anchors.offsetOf(values[it]) - position) },
        )
        val current = nearest(offset)
        val destination = nearest(projected)
        // _UISheetInteraction advances one adjacent detent for a high-speed release that has
        // passed its nearest detent but whose projected point still maps to that same detent.
        if (abs(velocity) >= HighSpeedThreshold * density && current == destination &&
            ((offset >= anchors.offsetOf(values[current])) xor (velocity < 0f))) {
            return values[(current + if (velocity < 0f) -1 else 1).coerceIn(values.indices)]
        }
        return values[destination]
    }
}

/**
 * UIView's implicit spring duration is based on unit displacement and normalized velocity, not
 * on a pixel visibility threshold. The Lambert-W approximation and underdamped envelope come
 * from UIKitCore's settling-duration helper at 0x1c5531da0.
 */
internal class IosSheetSettlingMotion(private val highSpeed: Boolean) : FloatAnimationSpec {
    val dampingRatio: Float get() = if (highSpeed) IosSheetMotion.HighSpeedDampingRatio else IosSheetMotion.DampingRatio
    val stiffness: Float get() = IosSheetMotion.stiffness
    private val ratio = if (highSpeed) 0.8 else 1.0
    private val omega = IosSheetMotion.angularFrequency
    private val attenuation = ratio * omega
    private val dampedFrequency = if (highSpeed) omega * sqrt(1.0 - ratio * ratio) else 0.0

    override fun getValueFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        targetValue: Float,
        initialVelocity: Float,
    ): Float {
        if (playTimeNanos <= 0L) return initialValue
        if (playTimeNanos >= getDurationNanos(initialValue, targetValue, initialVelocity)) return targetValue
        val time = playTimeNanos / 1_000_000_000.0
        val displacement = initialValue.toDouble() - targetValue
        val remainder = if (highSpeed) {
            val coefficient = (initialVelocity + attenuation * displacement) / dampedFrequency
            (displacement * cos(dampedFrequency * time) + coefficient * sin(dampedFrequency * time)) * exp(-attenuation * time)
        } else {
            (displacement + (initialVelocity + omega * displacement) * time) * exp(-omega * time)
        }
        return (targetValue + remainder).toFloat()
    }

    override fun getVelocityFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        targetValue: Float,
        initialVelocity: Float,
    ): Float {
        if (playTimeNanos <= 0L) return initialVelocity
        if (playTimeNanos >= getDurationNanos(initialValue, targetValue, initialVelocity)) return 0f
        val time = playTimeNanos / 1_000_000_000.0
        val displacement = initialValue.toDouble() - targetValue
        return if (highSpeed) {
            val coefficient = (initialVelocity + attenuation * displacement) / dampedFrequency
            val cosine = cos(dampedFrequency * time)
            val sine = sin(dampedFrequency * time)
            ((-attenuation * displacement + dampedFrequency * coefficient) * cosine +
                (-attenuation * coefficient - dampedFrequency * displacement) * sine)
                .times(exp(-attenuation * time)).toFloat()
        } else {
            (initialVelocity - omega * (initialVelocity + omega * displacement) * time)
                .times(exp(-omega * time)).toFloat()
        }
    }

    override fun getDurationNanos(initialValue: Float, targetValue: Float, initialVelocity: Float): Long {
        val distance = targetValue.toDouble() - initialValue
        if (distance == 0.0 && initialVelocity == 0f) return 0L
        // The interaction applies its one-millisecond release nudge before constructing this
        // animation. Retain its normalized -1000 fallback if Float precision erases that nudge.
        val normalizedVelocity = if (distance == 0.0) -1000.0 else initialVelocity / distance
        return (settlingDurationSeconds(normalizedVelocity) * 1_000_000_000.0).roundToLong()
    }

    internal fun settlingDurationSeconds(normalizedVelocity: Double): Double {
        if (highSpeed) {
            val amplitude = abs((attenuation - normalizedVelocity) / dampedFrequency)
            return if (amplitude < 1.1920928955078125e-7) {
                6.907755278982137 / attenuation
            } else max(-ln(IosSheetMotion.SettlingEpsilon / (amplitude + 1.0)) / attenuation, 0.0)
        }
        val coefficient = omega - normalizedVelocity
        val logarithm = -1.0 - ln(abs(omega * exp(-omega / coefficient) *
            IosSheetMotion.SettlingEpsilon / coefficient))
        val lambert = -1.0 - logarithm - 5.950609937518595 * (1.0 - 1.0 /
            (1.0 + 0.3361 * sqrt(0.5 * logarithm) /
                (1.0 - 0.0042 * logarithm * exp(-0.0201 * sqrt(logarithm)))))
        val duration = -(omega + coefficient * lambert) / (omega * coefficient)
        return if (duration.isFinite() && duration >= 0.0) duration else finiteCriticalDuration(normalizedVelocity)
    }

    /** Android guard for the native approximation's non-finite, near-exponential limit. */
    private fun finiteCriticalDuration(normalizedVelocity: Double): Double {
        val coefficient = omega - normalizedVelocity
        if (coefficient == 0.0) return -ln(IosSheetMotion.SettlingEpsilon) / omega
        fun remainder(time: Double) = abs((1.0 + coefficient * time) * exp(-omega * time))
        val turningTime = max(0.0, -normalizedVelocity / (omega * coefficient))
        val smallOvershoot = coefficient < 0.0 && remainder(turningTime) <= IosSheetMotion.SettlingEpsilon
        var lower = if (smallOvershoot) 0.0 else turningTime
        var upper = if (smallOvershoot) -1.0 / coefficient else max(lower + 1.0 / omega, 1.0 / omega)
        while (remainder(upper) > IosSheetMotion.SettlingEpsilon) upper *= 2.0
        repeat(96) {
            val middle = (lower + upper) * 0.5
            if (remainder(middle) > IosSheetMotion.SettlingEpsilon) lower = middle else upper = middle
        }
        return upper
    }
}

/**
 * The ordinary presentation controller uses a fixed 500 ms CA spring, separately from the
 * interaction's settling spring. The physical probe records mass 3, stiffness 1000 and damping
 * 500 with allowsOverdamping=false, so Core Animation clamps it to critical damping.
 */
internal object IosSheetPresentationMotion : FloatAnimationSpec {
    const val DurationMillis = 500
    private const val DurationNanos = DurationMillis * 1_000_000L
    private val angularFrequency = kotlin.math.sqrt(1000.0 / 3.0)

    override fun getValueFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        targetValue: Float,
        initialVelocity: Float,
    ): Float {
        if (playTimeNanos <= 0L) return initialValue
        if (playTimeNanos >= DurationNanos) return targetValue
        val time = playTimeNanos / 1_000_000_000.0
        val displacement = initialValue.toDouble() - targetValue
        val coefficient = initialVelocity + angularFrequency * displacement
        return (targetValue + (displacement + coefficient * time) * exp(-angularFrequency * time)).toFloat()
    }

    override fun getVelocityFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        targetValue: Float,
        initialVelocity: Float,
    ): Float {
        if (playTimeNanos <= 0L) return initialVelocity
        if (playTimeNanos >= DurationNanos) return 0f
        val time = playTimeNanos / 1_000_000_000.0
        val displacement = initialValue.toDouble() - targetValue
        val coefficient = initialVelocity + angularFrequency * displacement
        return ((initialVelocity - angularFrequency * coefficient * time) * exp(-angularFrequency * time)).toFloat()
    }

    override fun getDurationNanos(initialValue: Float, targetValue: Float, initialVelocity: Float): Long =
        DurationNanos
}
