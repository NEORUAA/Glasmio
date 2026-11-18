package io.github.neoruaa.glasmio.scaffold

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.icons.SfIcon
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.sqrt

private val RefreshReveal = 56.dp
private val RefreshRingSize = 26.dp
private const val SpokeCount = 8
private const val SpokeAngle = 45f
private const val SpokeAlphaStep = 0.08f
private const val TickSeconds = 0.125
private const val PopSeconds = 4.0
private const val PopAngle = 179.42705f // π − 0.01 radians

/**
 * Reveals a refresh ring above a vertically scrollable child. [isRefreshing] controls how long
 * the ring spins and the space above the child stays open. [onRefresh] is called on release after
 * a sufficient pull. Use [indicatorPadding] to place the ring below a fixed toolbar.
 */
@Composable
fun GlasmioPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val motion = remember { RefreshMotion() }
    val density = LocalDensity.current.density
    val currentRefresh = rememberUpdatedState(onRefresh)
    val releaseObserver = remember(motion, density) {
        object : NestedScrollConnection {
            override suspend fun onPreFling(available: Velocity): Velocity {
                // UIKit measures vertical velocity in points per millisecond.
                val pointsPerMillis = abs(available.y) / (density * 1000f)
                motion.releaseStiffness = (pointsPerMillis * 48.333333f).coerceIn(5f, 150f)
                return Velocity.Zero
            }
        }
    }
    Box(
        modifier
            .clipToBounds()
            .nestedScroll(releaseObserver)
            .pullToRefresh(
                isRefreshing = isRefreshing,
                state = state,
                enabled = !isRefreshing,
                threshold = RefreshReveal,
                onRefresh = {
                    motion.pendingPopStiffness.floatValue = motion.releaseStiffness
                    currentRefresh.value()
                },
            ),
    ) {
        Box(
            Modifier.graphicsLayer {
                translationY = state.distanceFraction.coerceAtLeast(0f) * RefreshReveal.roundToPx()
            },
            content = content,
        )
        Box(Modifier.matchParentSize().padding(indicatorPadding).clipToBounds()) {
            RefreshRing(state, isRefreshing, motion, Modifier.align(Alignment.TopCenter))
        }
    }
}

private class RefreshMotion {
    var releaseStiffness = 5f
    val pendingPopStiffness = mutableFloatStateOf(0f)
    val popFraction = mutableFloatStateOf(0f)
    val tick = mutableIntStateOf(0)
    val exit = Animatable(0f)
    val active = mutableStateOf(false)
}

@Composable
private fun RefreshRing(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    motion: RefreshMotion,
    modifier: Modifier,
) {
    val releaseStiffness = motion.pendingPopStiffness.floatValue
    if (releaseStiffness > 0f) {
        // Attribute a handoff only to the composition that receives the pull request.
        SideEffect { motion.pendingPopStiffness.floatValue = 0f }
    }
    LaunchedEffect(state, isRefreshing, motion) {
        if (isRefreshing) {
            motion.popFraction.floatValue = if (releaseStiffness > 0f) 0f else 1f
            motion.tick.intValue = 0
            motion.exit.snapTo(0f)
            motion.active.value = true
            spinRefreshRing(motion, releaseStiffness, coroutineContext[MotionDurationScale])
        } else if (motion.active.value) {
            coroutineScope {
                val exitAnimation = launch(start = CoroutineStart.UNDISPATCHED) {
                    motion.exit.animateTo(1f, tween(durationMillis = 300, easing = EaseInOut))
                }
                snapshotFlow { state.distanceFraction <= 0f }.first { it }
                exitAnimation.cancel()
            }
            motion.active.value = false
        }
    }
    val progressSemantics = if (isRefreshing) Modifier.semantics {
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    } else Modifier
    Box(
        modifier
            .size(RefreshRingSize)
            .graphicsLayer {
                val reveal = state.distanceFraction.coerceAtLeast(0f) * RefreshReveal.roundToPx()
                translationY = (reveal - size.height) / 2f
                alpha = if (motion.active.value) {
                    (1f - motion.exit.value) * (reveal / size.height).coerceIn(0f, 1f)
                } else {
                    EaseInOut.transform((reveal / RefreshReveal.roundToPx()).coerceIn(0f, 1f))
                }
                val exitScale = if (motion.active.value) 1f - 0.999f * motion.exit.value else 1f
                scaleX = exitScale
                scaleY = exitScale
            }
            .then(progressSemantics),
    ) {
        SfIcon(
            systemName = "progress.indicator",
            contentDescription = null,
            tint = LocalGlassColors.current.content,
            size = RefreshRingSize,
            weight = FontWeight.Black,
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val diameter = size.maxDimension * 2f
                    val maskSize = Size(diameter, diameter)
                    val maskOrigin = Offset(
                        (size.width - maskSize.width) / 2f,
                        (size.height - maskSize.height) / 2f,
                    )
                    onDrawWithContent draw@{
                        val spinning = motion.active.value
                        val reveal = if (spinning) 1f else state.distanceFraction.coerceIn(0f, 1f)
                        if (!spinning && reveal == 0f) return@draw
                        val pop = motion.popFraction.floatValue
                        val tick = motion.tick.intValue
                        val rotation = if (spinning) {
                            PopAngle * (pop + motion.exit.value) + tick * SpokeAngle
                        } else 0f
                        val pullShade = if (spinning) 1f else EaseInOut.transform(reveal)
                        rotate(rotation) {
                            this@draw.drawContent()
                            // Mask the font's existing spokes. The opacity steps rotate with
                            // the discrete tick; the glyph also turns independently on entry.
                            for (index in 0 until SpokeCount) {
                                val opacity = if (spinning) {
                                    1f - pop * (1f - index * SpokeAlphaStep)
                                } else {
                                    (1f - index * (1f - pullShade)).coerceAtLeast(0f)
                                }
                                if (opacity < 1f) {
                                    drawArc(
                                        color = Color.White.copy(alpha = opacity),
                                        startAngle = -112.5f + index * SpokeAngle,
                                        sweepAngle = SpokeAngle,
                                        useCenter = true,
                                        topLeft = maskOrigin,
                                        size = maskSize,
                                        blendMode = BlendMode.DstIn,
                                    )
                                }
                            }
                        }
                    }
                },
        )
    }
}

private suspend fun spinRefreshRing(
    motion: RefreshMotion,
    releaseStiffness: Float,
    durationScale: MotionDurationScale?,
) {
    val frequency = sqrt(releaseStiffness.toDouble())
    var elapsedSeconds = 0.0
    snapshotFlow { durationScale?.scaleFactor ?: 1f }.collectLatest { scale ->
        if (scale <= 0f) {
            motion.popFraction.floatValue = 1f
            return@collectLatest
        }
        val startNanos = System.nanoTime()
        fun secondsNow() = elapsedSeconds + (System.nanoTime() - startNanos) / (1_000_000_000.0 * scale)
        try {
            while (true) {
                val seconds = secondsNow()
                val step = (seconds / TickSeconds).toLong()
                motion.tick.intValue = (step % SpokeCount).toInt()
                if (motion.popFraction.floatValue < 1f) {
                    // The native spring clamps excess damping to critical damping.
                    val time = frequency * seconds
                    motion.popFraction.floatValue = if (seconds >= PopSeconds) 1f
                    else (1 - (1 + time) * exp(-time)).toFloat()
                }
                if (motion.popFraction.floatValue < 1f) {
                    withFrameNanos { }
                } else {
                    // Book the next discrete tick against elapsed time to avoid accumulating drift.
                    val remaining = ((step + 1) * TickSeconds - secondsNow()) * scale * 1000.0
                    delay(ceil(remaining).toLong().coerceAtLeast(1))
                }
            }
        } finally {
            elapsedSeconds = secondsNow()
        }
    }
}
