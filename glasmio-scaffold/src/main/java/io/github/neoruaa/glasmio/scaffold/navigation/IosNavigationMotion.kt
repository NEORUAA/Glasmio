package io.github.neoruaa.glasmio.scaffold.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FloatAnimationSpec
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFloatAnimationSpec
import androidx.compose.animation.core.tween
import kotlin.math.roundToInt
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplayTransitionEffects
import androidx.navigationevent.NavigationEvent
import kotlin.math.PI
import kotlin.math.exp

/** UIKit's navigation parallax: the covered page travels thirty percent of its width. */
internal const val IosNavigationParallax = 0.3f
internal const val IosNavigationDurationMillis = 350
private val IosNavigationOmega = 2.0 * PI / 0.3
internal val IosNavigationStiffness = (IosNavigationOmega * IosNavigationOmega).toFloat()
internal val IosNavigationTrackingStiffness = (2.0 * PI / 0.08).let { (it * it).toFloat() }
internal val IosNavigationEasing = Easing { iosNavigationPosition(it, 350_000_000L) }

/** Stock FloatSpringSpec rounds time to milliseconds, which breaks inverse seeking mid-push. */
internal object IosNavigationFloatSpring : FloatAnimationSpec {
    private val duration = FloatSpringSpec(dampingRatio = 1f, stiffness = IosNavigationStiffness)

    override fun getValueFromNanos(playTimeNanos: Long, initialValue: Float, targetValue: Float, initialVelocity: Float): Float {
        val time = playTimeNanos.coerceAtLeast(0L) / 1e9
        val displacement = initialValue.toDouble() - targetValue
        val coefficient = initialVelocity + IosNavigationOmega * displacement
        return (targetValue + (displacement + coefficient * time) * exp(-IosNavigationOmega * time)).toFloat()
    }

    override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: Float, targetValue: Float, initialVelocity: Float): Float {
        val time = playTimeNanos.coerceAtLeast(0L) / 1e9
        val coefficient = initialVelocity + IosNavigationOmega * (initialValue.toDouble() - targetValue)
        return ((initialVelocity - IosNavigationOmega * coefficient * time) * exp(-IosNavigationOmega * time)).toFloat()
    }

    override fun getDurationNanos(initialValue: Float, targetValue: Float, initialVelocity: Float): Long =
        duration.getDurationNanos(initialValue, targetValue, initialVelocity)

    override fun getEndVelocity(initialValue: Float, targetValue: Float, initialVelocity: Float): Float = 0f
}

internal object IosNavigationSlideSpring : FiniteAnimationSpec<IntOffset> {
    override fun <V : AnimationVector> vectorize(converter: TwoWayConverter<IntOffset, V>): VectorizedFloatAnimationSpec<V> =
        VectorizedFloatAnimationSpec(IosNavigationFloatSpring)
}

/** Unnormalized critical spring, sampled on the actual transition clock rather than cut at 350ms. */
internal fun iosNavigationPosition(fraction: Float, durationNanos: Long): Float {
    val time = fraction.coerceIn(0f, 1f) * durationNanos / 1e9
    return (1.0 - (1.0 + IosNavigationOmega * time) * exp(-IosNavigationOmega * time)).toFloat()
}

internal fun iosNavigationFraction(position: Float, durationNanos: Long = 350_000_000L): Float {
    var low = 0f
    var high = 1f
    repeat(22) {
        val middle = (low + high) / 2f
        if (iosNavigationPosition(middle, durationNanos) < position) low = middle else high = middle
    }
    return ((low + high) / 2f).coerceIn(0f, 1f)
}

internal fun iosNavigationParallaxOffset(widthPixels: Int, density: Float): Int =
    (widthPixels - (widthPixels / density * 0.7f).roundToInt() * density).roundToInt()

/** Native slide navigation with the window corner mask and native ten-percent dimming. */
val IosNavigationTransitionEffects = NavDisplayTransitionEffects(
    enableCornerClip = true,
    dimAmount = 0.1f,
    blockInputDuringTransition = true,
)

fun <T : Any> iosTransitionSpec(layoutDirection: LayoutDirection = LayoutDirection.Ltr, density: Float = 1f):
    AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    val sign = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    ContentTransform(
        slideInHorizontally(IosNavigationSlideSpring) { it * sign },
        slideOutHorizontally(IosNavigationSlideSpring) {
            -iosNavigationParallaxOffset(it, density) * sign
        },
    )
}

fun <T : Any> iosPopTransitionSpec(layoutDirection: LayoutDirection = LayoutDirection.Ltr, density: Float = 1f):
    AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    val sign = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    ContentTransform(
        slideInHorizontally(IosNavigationSlideSpring) {
            -iosNavigationParallaxOffset(it, density) * sign
        },
        slideOutHorizontally(IosNavigationSlideSpring) { it * sign },
    )
}

fun <T : Any> iosPredictivePopTransitionSpec(layoutDirection: LayoutDirection = LayoutDirection.Ltr, density: Float = 1f):
    AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform = {
    val sign = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    ContentTransform(
        slideInHorizontally(tween(IosNavigationDurationMillis, easing = LinearEasing)) {
            -iosNavigationParallaxOffset(it, density) * sign
        },
        slideOutHorizontally(tween(IosNavigationDurationMillis, easing = LinearEasing)) { it * sign },
    )
}
