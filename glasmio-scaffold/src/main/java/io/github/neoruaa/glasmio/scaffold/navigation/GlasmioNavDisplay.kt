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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import io.github.neoruaa.glasmio.glass.navigationGlassDropShadow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.withFrameNanos
import io.github.neoruaa.glasmio.glass.LiquidMenuSpring
import io.github.neoruaa.glasmio.glass.LiquidMenuSpringSpec
import kotlinx.coroutines.isActive
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
import android.view.RoundedCorner
import androidx.compose.ui.platform.LocalView
import com.kyant.capsule.AbsoluteContinuousRoundedRectangle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.neoruaa.glasmio.core.GlasmioSwipeBackExclusionRegistry
import io.github.neoruaa.glasmio.core.LocalGlasmioSwipeBackExclusions
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
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
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
        iosTransitionSpec(LocalLayoutDirection.current, LocalDensity.current.density),
    popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        iosPopTransitionSpec(LocalLayoutDirection.current, LocalDensity.current.density),
    predictivePopTransitionSpec:
    AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform =
        iosPredictivePopTransitionSpec(LocalLayoutDirection.current, LocalDensity.current.density),
    transitionEffects: NavDisplayTransitionEffects = IosNavigationTransitionEffects,
    swipeBackEnabled: Boolean = true,
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
        swipeBackEnabled = swipeBackEnabled,
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
        iosTransitionSpec(LocalLayoutDirection.current, LocalDensity.current.density),
    popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        iosPopTransitionSpec(LocalLayoutDirection.current, LocalDensity.current.density),
    predictivePopTransitionSpec:
    AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform =
        iosPredictivePopTransitionSpec(LocalLayoutDirection.current, LocalDensity.current.density),
    transitionEffects: NavDisplayTransitionEffects = IosNavigationTransitionEffects,
    swipeBackEnabled: Boolean,
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
        swipeBackEnabled = swipeBackEnabled,
        onBack = onBack,
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
fun <T : Any> GlasmioNavDisplay(
    sceneState: SceneState<T>,
    motion: GlasmioNavHeaderMotion,
    navigationEventState: NavigationEventState<SceneInfo<T>>,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    sizeTransform: SizeTransform? = null,
    transitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        iosTransitionSpec(LocalLayoutDirection.current, LocalDensity.current.density),
    popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        iosPopTransitionSpec(LocalLayoutDirection.current, LocalDensity.current.density),
    predictivePopTransitionSpec:
    AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform =
        iosPredictivePopTransitionSpec(LocalLayoutDirection.current, LocalDensity.current.density),
    transitionEffects: NavDisplayTransitionEffects = IosNavigationTransitionEffects,
    swipeBackEnabled: Boolean = true,
    onBack: (() -> Unit)? = null,
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
    val swipe = remember { IosSwipeBackState() }
    val systemTracking = navigationEventState.transitionState is InProgress
    SideEffect { if (systemTracking) swipe.cancel() }
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val windowInsets = LocalView.current.rootWindowInsets
    val fallbackCorner = if (transitionEffects.enableCornerClip && windowInsets == null) getRoundedCorner() else 0.dp
    val windowShape = with(density) {
        fun radius(position: Int) = if (windowInsets == null) fallbackCorner
            else (windowInsets.getRoundedCorner(position)?.radius ?: 0).toDp()
        AbsoluteContinuousRoundedRectangle(
            topLeft = radius(RoundedCorner.POSITION_TOP_LEFT),
            topRight = radius(RoundedCorner.POSITION_TOP_RIGHT),
            bottomRight = radius(RoundedCorner.POSITION_BOTTOM_RIGHT),
            bottomLeft = radius(RoundedCorner.POSITION_BOTTOM_LEFT),
        )
    }
    val exclusions = remember { GlasmioSwipeBackExclusionRegistry() }
    val displayCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val currentOnBack by androidx.compose.runtime.rememberUpdatedState(onBack)
    val currentScene by androidx.compose.runtime.rememberUpdatedState(scene)
    var predictivePresentation by remember { mutableStateOf(false) }
    val entryClock = remember { object { var durationNanos = 350_000_000L } }
    fun currentPopProgress(): Float = when {
        transitionState.currentState == previousScene && transitionState.targetState == currentScene ->
            1f - iosNavigationPosition(transitionState.fraction, entryClock.durationNanos)
        predictivePresentation && transitionState.currentState == currentScene -> transitionState.fraction
        else -> 0f
    }.coerceIn(0f, 1f)
    val canSwipeBack = swipeBackEnabled && hasPreviousScene && onBack != null &&
        motion.resolveOperation(scene.entries.last().contentKey,
            previousScene?.entries?.last()?.contentKey ?: scene.entries.last().contentKey, true) != GlasmioNavigationOperation.Tab
    val currentCanSwipeBack by androidx.compose.runtime.rememberUpdatedState(canSwipeBack)
    val swipeModifier = Modifier.onGloballyPositioned { displayCoordinates[0] = it }.iosSwipeBack(
        enabled = canSwipeBack,
        direction = layoutDirection, entryKey = scene.key, state = swipe, registry = exclusions,
        coordinates = { displayCoordinates[0] },
        systemOwnsGesture = { navigationEventState.transitionState is InProgress },
        anchorProgress = { currentPopProgress() },
        onCommit = { currentOnBack?.invoke() },
    )
    val inPredictiveBack by remember(hasPreviousScene) {
        derivedStateOf {
            (navigationEventState.transitionState is InProgress || swipe.tracking && swipe.entryKey == currentScene.key) && hasPreviousScene
        }
    }
    val swipeEdge by remember(layoutDirection) {
        derivedStateOf {
            if (swipe.tracking) {
                if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) NavigationEvent.EDGE_LEFT else NavigationEvent.EDGE_RIGHT
            } else when (val gesture = navigationEventState.transitionState) {
                is Idle -> NavigationEvent.EDGE_NONE
                is InProgress -> gesture.latestEvent.swipeEdge
            }
        }
    }

    // Remember the swipe edge for use after finger lift (when isPop but not inPredictiveBack)
    var lastSwipeEdge by remember { mutableStateOf(NavigationEvent.EDGE_NONE) }
    var localPredictive by remember { mutableStateOf(false) }
    var interruptedPushPresentation by remember { mutableStateOf(false) }
    if (!inPredictiveBack && !predictivePresentation && transition.totalDurationNanos > 0L) {
        entryClock.durationNanos = transition.totalDurationNanos
    }
    if (inPredictiveBack) {
        if (!predictivePresentation) interruptedPushPresentation = transition.currentState == previousScene
        predictivePresentation = true
        localPredictive = swipe.tracking && navigationEventState.transitionState !is InProgress
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

    if (inPredictiveBack && previousScene != null) {
        LaunchedEffect(previousScene) {
            motion.begin(scene.entries.last().contentKey, previousScene.entries.last().contentKey,
                GlasmioNavigationOperation.Pop, GlasmioNavigationPhase.Tracking)
            val initialProgress = if (swipe.tracking) swipe.startProgress else currentPopProgress()
            val following = LiquidMenuSpring(valueTolerance = 0.0001f, velocityTolerance = 0.001f).apply {
                reset(initialProgress)
            }
            val trackingSpring = LiquidMenuSpringSpec(dampingRatio = 0.85f, response = 0.08f)
            fun latestSample(): Pair<Float, Long>? =
                (navigationEventState.transitionState as? InProgress)?.latestEvent?.let {
                    (initialProgress + it.progress).coerceIn(0f, 1f) to it.frameTimeMillis
                } ?: if (swipe.tracking) swipe.progress to swipe.lastEventTimeMillis else null
            // Register the destination even at zero progress; an immediate Back commit
            // must still settle toward the previous scene rather than snap to it.
            if (transition.currentState == previousScene) {
                transitionState.seekTo(iosNavigationFraction(1f - initialProgress, entryClock.durationNanos), scene)
            } else {
                transitionState.seekTo(following.value.coerceIn(0f, 1f), previousScene)
            }
            var previousFrame = withFrameNanos { it }
            var previousSample: Pair<Float, Long>? = null
            var sample = latestSample()
            // Retarget one running oscillator rather than restart animateTo at every MOVE.
            // Once settled, suspend on input instead of requesting idle frames indefinitely.
            while (isActive && sample != null) {
                if (sample != previousSample) {
                    motion.gesture(sample.first, sample.second)
                    following.target(sample.first, trackingSpring)
                    previousSample = sample
                }
                if (following.isSettled) {
                    sample = snapshotFlow { latestSample() }.first { it != previousSample }
                    if (sample == null) break
                    // Time spent resting must not be integrated into the newly retargeted spring.
                    previousFrame = withFrameNanos { it }
                    continue
                }
                val frame = withFrameNanos { it }
                following.advance(((frame - previousFrame) / 1e9).toFloat())
                previousFrame = frame
                val progress = following.value.coerceIn(0f, 1f)
                if (transition.currentState == previousScene) {
                    transitionState.seekTo(iosNavigationFraction(1f - progress, entryClock.durationNanos), scene)
                } else {
                    transitionState.seekTo(progress, previousScene)
                }
                sample = latestSample()
            }
        }
    } else {
        LaunchedEffect(scene) {
            val key = scene.entries.last().contentKey
            val gestureFrame = motion.frame.value
            val resolvesGesture = key == gestureFrame.fromEntryKey || key == gestureFrame.toEntryKey
            if (predictivePresentation && !resolvesGesture) {
                // A new push owns the presentation immediately. It must not finish the old
                // swipe's cancellation and then snap directly to the unrelated destination.
                predictivePresentation = false
                interruptedPushPresentation = false
            }
            if (motion.frame.value.phase == GlasmioNavigationPhase.Tracking && resolvesGesture) {
                motion.phase(if (motion.frame.value.toEntryKey == key) GlasmioNavigationPhase.Settling else GlasmioNavigationPhase.Canceling)
            } else if (motion.frame.value.toEntryKey != key || motion.frame.value.phase == GlasmioNavigationPhase.Tracking) {
                motion.beginNavigation(motion.interactionKey, key, isPop)
            }
            if (predictivePresentation) {
                // Keep the tracking transform linear until the release spring settles. Switching
                // back to the noninteractive easing here would move the page at finger lift.
                val finalFraction = if (transitionState.targetState == scene) 1f else 0f
                val velocity = if (localPredictive) swipe.releaseVelocity else motion.frame.value.gestureVelocity
                val releaseVelocity = if (interruptedPushPresentation) -velocity else velocity
                val startPosition = if (interruptedPushPresentation)
                    iosNavigationPosition(transitionState.fraction, entryClock.durationNanos) else transitionState.fraction
                val settling = Animatable(startPosition)
                coroutineScope {
                    val seek = launch {
                        snapshotFlow { settling.value }.collectLatest {
                            val fraction = if (interruptedPushPresentation)
                                iosNavigationFraction(it, entryClock.durationNanos) else it.coerceIn(0f, 1f)
                            transitionState.seekTo(fraction)
                        }
                    }
                    settling.animateTo(finalFraction, initialVelocity = releaseVelocity,
                        animationSpec = spring(dampingRatio = 0.85f,
                            stiffness = IosNavigationStiffness, visibilityThreshold = 0.0001f))
                    seek.cancel()
                }
                transitionState.snapTo(scene)
            } else if (transitionState.currentState != scene) {
                transitionState.animateTo(scene)
            }
            if (transitionState.currentState == scene && transitionState.targetState == scene) {
                motion.finish(key)
                predictivePresentation = false
                interruptedPushPresentation = false
            }
        }
    }

    val activeSwipeEdge = if (inPredictiveBack) swipeEdge else lastSwipeEdge

    val contentTransform: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
        when {
            inPredictiveBack || predictivePresentation -> {
                if (interruptedPushPresentation) {
                    transitionScene.contentTransform(NavDisplay.TransitionKey)?.invoke(this)
                        ?: transitionSpec(this)
                } else {
                    transitionScene.predictivePopSpec()?.invoke(this, activeSwipeEdge)
                        ?: predictivePopTransitionSpec(activeSwipeEdge)
                }
            }

            isPop -> {
                transitionScene.contentTransform(NavDisplay.PopTransitionKey)?.invoke(this)
                    ?: popTransitionSpec(this)
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
        modifier = modifier.then(swipeModifier),
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
            LocalGlasmioSwipeBackExclusions provides exclusions,
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

            val roundedModifier = if (transitionEffects.enableCornerClip && isTopScene &&
                motion.frame.value.operation != GlasmioNavigationOperation.Tab) {
                Modifier.clip(windowShape)
            } else {
                Modifier
            }

            val blockerCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
            val blockInputModifier =
                if (transitionEffects.blockInputDuringTransition && !isSettled && targetScene != transition.targetState) {
                    Modifier.onGloballyPositioned { blockerCoordinates[0] = it }.pointerInput(targetScene.key) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val point = blockerCoordinates[0]?.takeIf { it.isAttached }?.localToWindow(down.position)
                            val allowNavigationMove = currentCanSwipeBack && point != null && !exclusions.hitTest(point)
                            down.consume()
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.fastForEach { change ->
                                    // Keep outgoing content inert to taps while allowing the parent
                                    // navigation recognizer to grab the visible moving presentation.
                                    if (!allowNavigationMove || change.positionChangeIgnoreConsumed() == Offset.Zero || !change.pressed) {
                                        change.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                } else Modifier
            val shadowModifier = if (isTopScene && motion.frame.value.operation != GlasmioNavigationOperation.Tab) {
                Modifier.navigationGlassDropShadow(shape = { if (transitionEffects.enableCornerClip) windowShape else RectangleShape }, cacheBlurResult = true,
                    shadowRadius = 12.dp, shadowOffsetY = (-3).dp, shadowAlpha = 0.04f)
            } else Modifier

            Box(
                modifier =
                    Modifier
                        .then(shadowModifier)
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
                                    val physical = if (predictivePresentation) transitionState.fraction
                                        else iosNavigationPosition(transitionState.fraction, transition.totalDurationNanos)
                                    alpha = if (!settled) {
                                        (if (isCurrentScene) physical else 1f - physical) * transitionEffects.dimAmount
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

internal data class AnimatedSceneKey(val clazz: KClass<*>, val key: Any) {
    constructor(scene: Scene<*>) : this(scene::class, scene.key)
}
