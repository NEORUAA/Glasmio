package io.github.neoruaa.glasmio.scaffold

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember

/**
 * Shared clock for the navigation-to-search morph. UIKit's floating tab bar uses a critically
 * damped spring with a 0.45 s response, so the search bar expands and the navigation collapses
 * on the same curve instead of chaining two separate fades.
 */
private val SearchMorphSpring = spring<Float>(
    dampingRatio = 1f,
    stiffness = 195f,
    visibilityThreshold = 0.001f,
)

@Stable
class GlasmioSearchTransition internal constructor(initialProgress: Float) {
    private val animatable = Animatable(initialProgress)

    /** Reads the live value; safe inside layout and draw lambdas. */
    val progress: Float get() = animatable.value

    /** Snapshot state for callers that fade or scale alongside the morph. */
    val progressState: State<Float> = animatable.asState()

    val settled: Boolean get() = animatable.value == animatable.targetValue

    internal suspend fun animateTo(active: Boolean) {
        animatable.animateTo(if (active) 1f else 0f, SearchMorphSpring)
    }
}

/** Remembers the morph clock shared by the navigation bar and the floating search bar. */
@Composable
fun rememberGlasmioSearchTransition(active: Boolean): GlasmioSearchTransition {
    val transition = remember { GlasmioSearchTransition(if (active) 1f else 0f) }
    LaunchedEffect(active) { transition.animateTo(active) }
    return transition
}

/** Reveal window for the trailing cancel control; kept off the main expansion curve. */
internal fun cancelRevealProgress(progress: Float): Float =
    ((progress - 0.35f) / 0.45f).coerceIn(0f, 1f)

/** Content fades in once the capsule has enough width to carry the text. */
internal fun searchContentProgress(progress: Float): Float =
    ((progress - 0.25f) / 0.45f).coerceIn(0f, 1f)
