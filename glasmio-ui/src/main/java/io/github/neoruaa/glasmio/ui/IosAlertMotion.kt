package io.github.neoruaa.glasmio.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.exp

/** UIKit's UIAlertControllerVisualStyleAlert in iOS 27.0 (24A434). */
internal object IosAlertMotion {
    const val InitialScale = 1.2f
    const val DurationMillis = 404

    // UIView's duration-based spring clamps the requested damping ratio (600) to 1.
    // Its solver chooses omega so (1 + omega * duration) * exp(-omega * duration) = .001.
    // This duration is a settling interval, not a spring response. See docs/alert-motion.md.
    private const val AngularFrequency = 22.854984466344746

    internal val Timing = Easing { fraction ->
        when {
            fraction <= 0f -> 0f
            fraction >= 1f -> 1f // CA removes the animation at its exact duration.
            else -> {
                val position = AngularFrequency * (DurationMillis / 1000.0) * fraction
                (1.0 - (1.0 + position) * exp(-position)).toFloat()
            }
        }
    }

    suspend fun animate(
        visible: Boolean,
        scale: Animatable<Float, AnimationVector1D>,
        opacity: Animatable<Float, AnimationVector1D>,
        dimming: Animatable<Float, AnimationVector1D>,
    ) = coroutineScope {
        val timing = tween<Float>(DurationMillis, easing = Timing)
        // A dismissal keeps the panel's transform and only fades it and its dimming view.
        // Cancellation retains the presentation values for a subsequent reversal (option 6).
        if (visible) launch { scale.animateTo(1f, timing) }
        launch { opacity.animateTo(if (visible) 1f else 0f, timing) }
        launch { dimming.animateTo(if (visible) 1f else 0f, timing) }
    }
}
