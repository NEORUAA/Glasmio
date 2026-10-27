// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("GlasmioNavDisplayKt")

package io.github.neoruaa.glasmio.scaffold.navigation

import androidx.collection.mutableObjectFloatMapOf
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachReversed
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.NavigationBackHandler
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SceneState
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay.popTransitionSpec
import androidx.navigation3.ui.NavDisplay.predictivePopTransitionSpec
import androidx.navigation3.ui.NavDisplay.transitionSpec
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState.Idle
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import androidx.navigationevent.compose.NavigationEventState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

// Maintained from miuix-navigation3-ui 0.9.3. Single-pane only; header and body share the clock.
import androidx.navigation3.ui.*
import io.github.neoruaa.glasmio.scaffold.*
import kotlin.math.roundToLong

@Composable
fun <T : Any> GlasmioNavDisplay(
    backStack: List<T>,
    motion: GlasmioNavHeaderMotion,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    onBack: () -> Unit = {
        if (backStack is MutableList<T>) {
            backStack.removeLastOrNull()
        }
    },
    entryDecorators: List<NavEntryDecorator<T>> =
        listOf(rememberSaveableStateHolderNavEntryDecorator()),
    sizeTransform: SizeTransform? = null,
    transitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        defaultTransitionSpec(),
    popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        defaultPopTransitionSpec(),
    predictivePopTransitionSpec:
    AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform =
        defaultPredictivePopTransitionSpec(),
    transitionEffects: NavDisplayTransitionEffects = NavDisplayTransitionEffects.Default,
    entryProvider: (key: T) -> NavEntry<T>,
) {
    require(backStack.isNotEmpty()) { "NavDisplay backstack cannot be empty" }

    val entries =
        rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = entryDecorators,
            entryProvider = entryProvider,
        )

    GlasmioNavDisplay(
        entries = entries,
        motion = motion,
        modifier = modifier,
        contentAlignment = contentAlignment,
        sizeTransform = sizeTransform,
        transitionSpec = transitionSpec,
        popTransitionSpec = popTransitionSpec,
        predictivePopTransitionSpec = predictivePopTransitionSpec,
        transitionEffects = transitionEffects,
        onBack = onBack,
    )
}

/**
 * A nav display that renders and animates between different [Scene]s, each of which can render one
 * or more [NavEntry]s.
 *
 * The [Scene]s are calculated with the given list of [SceneStrategy] in the order of the list. If
 * no [Scene] is calculated, the fallback will be to a [SinglePaneSceneStrategy].
 *
 * It is allowable for different [Scene]s to render the same [NavEntry]s, perhaps on some conditions
 * as determined by the [sceneStrategies] based on window size, form factor, other arbitrary logic.
 *
 * If this happens, and these [Scene]s are rendered at the same time due to animation or predictive
 * back, then the content for the [NavEntry] will only be rendered in the most recent [Scene] that
 * is the target for being the current scene as determined by [sceneStrategies]. This enforces a
 * unique invocation of each [NavEntry], even if it is displayable by two different [Scene]s.
 *
 * By default, AnimatedContent transitions are prioritized in this order:
 * ```
 * transitioning [NavEntry.metadata] > current [Scene.metadata] > NavDisplay defaults
 * ```
 *
 * However, a [Scene.metadata] does have the ability to override [NavEntry.metadata]. Nevertheless,
 * the final fallback will always be the NavDisplay's default transitions.
 *
 * **WHEN TO USE** This overload can be used when you need to switch between different backStacks
 * and each with their own separate decorator states, or when you want to concatenate backStacks and
 * their states to form a larger backstack.
 *
 * **HOW TO USE** The [entries] can first be created via [rememberDecoratedNavEntries] in order to
 * associate a backStack with a particular set of states.
 *
 * @param entries the list of [NavEntry] built from a backStack. The entries can be created from a
 *   backStack decorated with [NavEntryDecorator] via [rememberDecoratedNavEntries].
 * @param modifier the modifier to be applied to the layout.
 * @param contentAlignment The [Alignment] of the [AnimatedContent]
 * @param sceneStrategies the list of [SceneStrategy] to determine which scene to render a list of
 *   entries.
 * @param sceneDecoratorStrategies list of [SceneDecoratorStrategy] to add content to the scene.
 * @param sharedTransitionScope the [SharedTransitionScope] to allow transitions between scenes.
 * @param sizeTransform the [SizeTransform] for the [AnimatedContent].
 * @param transitionSpec Default [ContentTransform] when navigating to [NavEntry]s.
 * @param popTransitionSpec Default [ContentTransform] when popping [NavEntry]s.
 * @param predictivePopTransitionSpec Default [ContentTransform] when popping [NavEntry]s with
 *   predictive back.
 * @param transitionEffects configuration for MIUI-specific transition effects (corner clipping,
 *   dimming, input blocking).
 * @param onBack a callback for handling system back press.
 * @see [rememberDecoratedNavEntries]
 */
@Composable
private fun <T : Any> GlasmioNavDisplay(
    entries: List<NavEntry<T>>,
    motion: GlasmioNavHeaderMotion,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    sizeTransform: SizeTransform? = null,
    transitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        defaultTransitionSpec(),
    popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        defaultPopTransitionSpec(),
    predictivePopTransitionSpec:
    AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform =
        defaultPredictivePopTransitionSpec(),
    transitionEffects: NavDisplayTransitionEffects = NavDisplayTransitionEffects.Default,
    onBack: () -> Unit,
) {
    require(entries.isNotEmpty()) { "NavDisplay entries cannot be empty" }

    val sceneState =
        rememberSceneState(
            entries,
            listOf(SinglePaneSceneStrategy()),
            emptyList(),
            null,
            onBack,
        )

    // Predictive Back Handling
    val navigationEventState = rememberNavigationEventState(sceneState)
    NavigationBackHandler(sceneState, navigationEventState, onBack)

    GlasmioNavDisplay(
        sceneState,
        motion,
        navigationEventState,
        modifier,
        contentAlignment,
        sizeTransform,
        transitionSpec,
        popTransitionSpec,
        predictivePopTransitionSpec,
        transitionEffects,
    )
}

/**
 * A nav display that renders and animates between different [Scene]s, each of which can render one
 * or more [NavEntry]s.
 *
 * By default, AnimatedContent transitions are prioritized in this order:
 * ```
 * transitioning [NavEntry.metadata] > current [Scene.metadata] > NavDisplay defaults
 * ```
 *
 * However, a [Scene.metadata] does have the ability to override [NavEntry.metadata]. Nevertheless,
 * the final fallback will always be the NavDisplay's default transitions.
 *
 * @param sceneState the state that determines what current scene of the NavDisplay.
 * @param modifier the modifier to be applied to the layout.
 * @param contentAlignment The [Alignment] of the [AnimatedContent]
 * @param navigationEventState the [NavigationEventState] responsible for handling back navigation
 * @param sizeTransform the [SizeTransform] for the [AnimatedContent].
 * @param transitionSpec Default [ContentTransform] when navigating to [NavEntry]s.
 * @param popTransitionSpec Default [ContentTransform] when popping [NavEntry]s.
 * @param predictivePopTransitionSpec Default [ContentTransform] when popping [NavEntry]s with
 *   predictive back.
 * @param transitionEffects configuration for MIUI-specific transition effects (corner clipping,
 *   dimming, input blocking).
 * @see [rememberSceneState]
 */
@Composable
private fun <T : Any> GlasmioNavDisplay(
    sceneState: SceneState<T>,
    motion: GlasmioNavHeaderMotion,
    navigationEventState: NavigationEventState<SceneInfo<T>>,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    sizeTransform: SizeTransform? = null,
    transitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        defaultTransitionSpec(),
    popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        defaultPopTransitionSpec(),
    predictivePopTransitionSpec:
    AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform =
        defaultPredictivePopTransitionSpec(),
    transitionEffects: NavDisplayTransitionEffects = NavDisplayTransitionEffects.Default,
) {
    // Calculate current Scene and set up transitions
    val scene = sceneState.currentScene
    val transitionState = remember {
        // The state returned here cannot be nullable cause it produces the input of the
        // transitionSpec passed into the AnimatedContent and that must match the non-nullable
        // scope exposed by the transitions on the NavHost and composable APIs.
        SeekableTransitionState(scene)
    }

    val transition = rememberTransition(transitionState, label = "scene")
    val headerClock = remember(transitionState, transition) {
        { (transitionState.fraction.toDouble() * transition.totalDurationNanos).roundToLong() }
    }
    SideEffect { motion.bindClock(headerClock) }

    // Transition Handling
    // Track content keys (value-based) to reliably detect pop vs push, even during
    // rapid consecutive navigations where entry references may be recreated.
    // Uses a ref-holder to avoid writing mutableStateOf during composition.
    val currentContentKeys = remember(sceneState.entries) {
        sceneState.entries.map { it.contentKey }
    }
    val isPopRef = remember {
        object {
            var previousKeys = currentContentKeys
            var isPop = false
        }
    }
    if (isPopRef.previousKeys != currentContentKeys) {
        isPopRef.isPop = isPop(isPopRef.previousKeys, currentContentKeys)
        isPopRef.previousKeys = currentContentKeys
    }
    val isPop = isPopRef.isPop

    // Set up Gesture Back tracking
    val previousScene = sceneState.previousScenes.lastOrNull()

    val hasPreviousScene = previousScene != null
    val inPredictiveBack by remember(hasPreviousScene) {
        derivedStateOf {
            navigationEventState.transitionState is InProgress && hasPreviousScene
        }
    }
    val swipeEdge by remember {
        derivedStateOf {
            when (val gesture = navigationEventState.transitionState) {
                is Idle -> NavigationEvent.EDGE_NONE
                is InProgress -> gesture.latestEvent.swipeEdge
            }
        }
    }

    // Remember the swipe edge for use after finger lift (when isPop but not inPredictiveBack)
    var lastSwipeEdge by remember { mutableStateOf(NavigationEvent.EDGE_NONE) }
    if (inPredictiveBack) {
        lastSwipeEdge = swipeEdge
    }

    // Track currently rendered Scenes and their ZIndices
    val sceneMap = remember { mutableStateMapOf<AnimatedSceneKey, Scene<T>>() }
    val zIndices = remember { mutableObjectFloatMapOf<AnimatedSceneKey>() }
    val initialKey = AnimatedSceneKey(transition.currentState)
    val targetKey = AnimatedSceneKey(transition.targetState)
    val initialZIndex = zIndices.getOrPut(initialKey) { 0f }
    // Preserve existing z-index for scenes already tracked in the current transition.
    // This prevents z-index from flipping during settle animations (e.g. after committing
    // a predictive back that interrupted a forward push).
    val isInterruptingEntry = inPredictiveBack && transition.currentState == previousScene
    val targetZIndex =
        when {
            initialKey == targetKey -> initialZIndex
            zIndices.containsKey(targetKey) -> zIndices[targetKey]
            (isPop || inPredictiveBack) && !isInterruptingEntry -> initialZIndex - 1f
            else -> initialZIndex + 1f
        }
    sceneMap[targetKey] = transition.targetState
    zIndices[targetKey] = targetZIndex

    // Determine which NavEntry's transition to use(if any), prioritizing the one with highest
    // zIndex
    val transitionScene =
        if (initialZIndex >= targetZIndex) {
            transition.currentState
        } else {
            transition.targetState
        }

    // check if in gesture back
    // Capture the fraction when we start interrupting an entry animation
    var entryInterruptionFraction by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(inPredictiveBack) {
        if (inPredictiveBack && transition.currentState == previousScene) {
            entryInterruptionFraction = transitionState.fraction
        }
    }

    if (inPredictiveBack && previousScene != null) {
        LaunchedEffect(previousScene) {
            motion.begin(scene.entries.last().contentKey, previousScene.entries.last().contentKey,
                GlasmioNavigationOperation.Pop, GlasmioNavigationPhase.Tracking)
            snapshotFlow { navigationEventState.transitionState }
                .collectLatest { gestureState ->
                    if (gestureState is InProgress) {
                        val progress = gestureState.latestEvent.progress
                        motion.gesture(progress, gestureState.latestEvent.frameTimeMillis)
                        if (transition.currentState == previousScene) {
                            // Interrupting entry: A -> B
                            // Scale progress to match the partial entry state
                            val startPhysical =
                                NavAnimationEasing.transform(entryInterruptionFraction)
                            val targetPhysical = startPhysical * (1f - progress)
                            val targetFraction =
                                NavAnimationEasing.inverseTransform(targetPhysical)
                            transitionState.seekTo(targetFraction, scene)
                        } else {
                            // Standard back: B -> A
                            transitionState.seekTo(progress, previousScene)
                        }
                    }
                }
        }
    } else {
        LaunchedEffect(scene) {
            val key = scene.entries.last().contentKey
            if (motion.frame.value.phase == GlasmioNavigationPhase.Tracking &&
                key in setOf(motion.frame.value.fromEntryKey, motion.frame.value.toEntryKey)) {
                motion.phase(if (motion.frame.value.toEntryKey == key) GlasmioNavigationPhase.Settling else GlasmioNavigationPhase.Canceling)
            } else if (motion.frame.value.toEntryKey != key || motion.frame.value.phase == GlasmioNavigationPhase.Tracking) {
                motion.beginNavigation(motion.interactionKey, key, isPop)
            }
            if (transitionState.currentState != scene) {
                if (transitionState.targetState == scene) {
                    // Predictive Back has been committed
                    // so now we need to animate to the final state
                    transitionState.animateTo(
                        scene,
                        animationSpec = tween(500, easing = NavAnimationEasing),
                    )
                } else {
                    // We are animating to the final state for regular navigate forward
                    // and regular pop
                    transitionState.animateTo(scene)
                }
            } else {
                // Predictive Back has either been completed or cancelled
                // so now we need to seekTo+snapTo the final state

                // convert from nanoseconds to milliseconds
                val totalDuration = transition.totalDurationNanos / 1000000
                // Which way we have to seek depends on whether the
                // Predictive Back was completed or cancelled
                val predictiveBackCompleted = transition.targetState == scene
                val (finalFraction, remainingDuration) =
                    if (predictiveBackCompleted) {
                        // If it completed, animate to the state we were
                        // already seeking to with the remaining duration
                        1f to ((1f - transitionState.fraction) * totalDuration).toInt()
                    } else {
                        // It it got cancelled, animate back to the
                        // initial state, reversing what we seeked to
                        0f to (transitionState.fraction * totalDuration).toInt()
                    }
                animate(
                    transitionState.fraction,
                    finalFraction,
                    animationSpec = tween(remainingDuration, easing = NavAnimationEasing),
                ) { value, _ ->
                    this@LaunchedEffect.launch {
                        if (value != finalFraction) {
                            // Seek the transition towards the finalFraction
                            transitionState.seekTo(value)
                        }
                        if (value == finalFraction) {
                            // Once the animation finishes, we need to snap to the right state.
                            transitionState.snapTo(scene)
                            motion.finish(key)
                        }
                    }
                }
            }
            if (transitionState.currentState == scene && transitionState.targetState == scene) motion.finish(key)
        }
    }

    val shouldFlipDirection = transitionEffects.popDirectionFollowsSwipeEdge &&
            (if (inPredictiveBack) swipeEdge else lastSwipeEdge) == NavigationEvent.EDGE_RIGHT

    val contentTransform: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
        when {
            inPredictiveBack -> {
                if (shouldFlipDirection) {
                    flippedPredictivePopTransitionSpec()
                } else {
                    transitionScene.predictivePopSpec()?.invoke(this, swipeEdge)
                        ?: predictivePopTransitionSpec(swipeEdge)
                }
            }

            isPop -> {
                if (shouldFlipDirection) {
                    flippedPopTransitionSpec()
                } else {
                    transitionScene.contentTransform(NavDisplay.PopTransitionKey)?.invoke(this)
                        ?: popTransitionSpec(this)
                }
            }

            else -> {
                transitionScene.contentTransform(NavDisplay.TransitionKey)?.invoke(this)
                    ?: transitionSpec(this)
            }
        }
    }

    // allows OverlayScenes to access animatedContentScope even if it's no-op so that
    // LocalNavAnimatedContentScope doesn't need to be nullable
    lateinit var animatedContentScope: AnimatedContentScope

    transition.AnimatedContent(
        contentKey = { scene -> AnimatedSceneKey(scene) },
        contentAlignment = contentAlignment,
        modifier = modifier,
        transitionSpec = {
            ContentTransform(
                targetContentEnter = contentTransform(this).targetContentEnter,
                initialContentExit = contentTransform(this).initialContentExit,
                // z-index increases during navigate and decreases during pop.
                targetContentZIndex = targetZIndex,
                sizeTransform = sizeTransform,
            )
        },
    ) { targetScene ->
        // If there is a transition in progress, set the maximum state of the scene (and every
        // entry within the scene) to STARTED - only allow the RESUMED state when the
        // AnimatedContent has settled into its final state
        val isSettled = transition.currentState == transition.targetState
        val sceneLifecycleOwner =
            rememberLifecycleOwner(
                maxLifecycle =
                    if (isSettled) Lifecycle.State.RESUMED
                    else Lifecycle.State.STARTED
            )
        animatedContentScope = remember { this }
        CompositionLocalProvider(
            LocalLifecycleOwner provides sceneLifecycleOwner,
            LocalNavAnimatedContentScope provides animatedContentScope,
        ) {
            val myZIndex =
                zIndices.getOrElse(AnimatedSceneKey(targetScene)) { targetZIndex }
            val topZIndex = maxOf(initialZIndex, targetZIndex)

            val isTopScene =
                !isSettled &&
                        myZIndex > minOf(initialZIndex, targetZIndex) &&
                        initialZIndex != targetZIndex

            val roundedModifier = if (transitionEffects.enableCornerClip && isTopScene) {
                val corner = if (!isInMultiWindowMode()) getRoundedCorner() else 0.dp
                if (shouldFlipDirection) {
                    Modifier.clip(AbsoluteRoundedCornerShape(
                        topLeft = 0.dp,
                        topRight = corner,
                        bottomRight = corner,
                        bottomLeft = 0.dp,
                    ))
                } else {
                    Modifier.clip(AbsoluteRoundedCornerShape(
                        topLeft = corner,
                        topRight = 0.dp,
                        bottomRight = 0.dp,
                        bottomLeft = corner,
                    ))
                }
            } else {
                Modifier
            }

            val blockInputModifier =
                if (transitionEffects.blockInputDuringTransition && !isSettled && targetScene != transition.targetState) {
                    remember {
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event =
                                        awaitPointerEvent(pass = PointerEventPass.Initial)
                                    event.changes.fastForEach { it.consume() }
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }

            Box(
                modifier =
                    Modifier
                        .then(roundedModifier)
                        .then(blockInputModifier),
            ) {
                targetScene.content()

                if (transitionEffects.dimAmount > 0f && myZIndex < topZIndex) {
                    val isCurrentScene = targetScene == transition.currentState
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val settled =
                                        transition.currentState == transition.targetState
                                    alpha = if (!settled) {
                                        if (isCurrentScene) {
                                            transitionState.fraction *
                                                    transitionEffects.dimAmount
                                        } else {
                                            (1f - transitionState.fraction) *
                                                    transitionEffects.dimAmount
                                        }
                                    } else {
                                        0f
                                    }
                                }
                                .background(Color.Black),
                    )
                }
            }
        }
    }

    // Clean-up scene book-keeping once the transition is finished
    LaunchedEffect(transition) {
        snapshotFlow { transition.isRunning }
            .filter { !it }
            .collect {
                val targetKey = AnimatedSceneKey(transition.targetState)
                // Creating a copy to avoid ConcurrentModificationException
                @Suppress("ListIterator")
                sceneMap.keys.toList().forEach { key ->
                    if (key != targetKey) {
                        sceneMap.remove(key)
                    }
                }
                // Creating a copy to avoid ConcurrentModificationException
                zIndices.removeIf { key, _ -> key != targetKey }
                lastSwipeEdge = NavigationEvent.EDGE_NONE
            }
    }


}

private fun <T : Any> isPop(oldBackStack: List<T>, newBackStack: List<T>): Boolean {
    // entire stack replaced
    if (oldBackStack.first() != newBackStack.first()) return false
    // navigated
    if (newBackStack.size > oldBackStack.size) return false

    val divergingIndex =
        newBackStack.indices.firstOrNull { index -> newBackStack[index] != oldBackStack[index] }
    // if newBackStack never diverged from oldBackStack, then it is a clean subset of the oldStack
    // and is a pop
    return divergingIndex == null && newBackStack.size != oldBackStack.size
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> Scene<T>.contentTransform(
    key: NavMetadataKey<AnimatedContentTransitionScope<Scene<*>>.() -> ContentTransform>,
): (AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform)? {
    return metadata[key] as? AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> Scene<T>.predictivePopSpec():
        (AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform?)? {
    return metadata[NavDisplay.PredictivePopTransitionKey]
            as?
            AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform?
}

/** Default [transitionSpec] for forward navigation to be used by [NavDisplay]. */
/** Flipped [popTransitionSpec] for right-edge swipe (exit to left). */
private fun flippedPopTransitionSpec(): ContentTransform =
    ContentTransform(
        slideInHorizontally(
            initialOffsetX = { it / 4 },
            animationSpec = tween(durationMillis = 500, easing = NavAnimationEasing),
        ),
        slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(durationMillis = 500, easing = NavAnimationEasing),
        ),
    )

/** Flipped [predictivePopTransitionSpec] for right-edge swipe (exit to left). */
private fun flippedPredictivePopTransitionSpec(): ContentTransform =
    ContentTransform(
        slideInHorizontally(
            initialOffsetX = { it / 4 },
            animationSpec = tween(durationMillis = 550, easing = LinearEasing),
        ),
        slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(durationMillis = 550, easing = LinearEasing),
        ),
    )

/** Default easing for navigation animations. */
private val NavAnimationEasing = NavTransitionEasing(0.8f, 0.95f)

internal data class AnimatedSceneKey(val clazz: KClass<*>, val key: Any) {
    constructor(scene: Scene<*>) : this(scene::class, scene.key)
}
