package io.github.neoruaa.glasmio.scaffold.navigation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.GlasmioSwipeBackExclusionRegistry
import kotlin.math.abs
import kotlin.math.max

@Stable
internal class IosSwipeBackState {
    var tracking by mutableStateOf(false)
    var entryKey: Any? = null
    var progress by mutableFloatStateOf(0f)
    var startProgress = 0f
    var releaseVelocity = 0f
    var lastEventTimeMillis = 0L
    var generation = 0
    fun cancel() { if (tracking) { generation++; releaseVelocity = 0f; tracking = false } }
}

/** Child ownership is permanent for one sequence, including children with a smaller touch slop. */
internal class IosSwipeBackArbitrator(private val slop: Float) {
    private var travel = 0f
    private var cross = 0f
    private var childOwned = false
    var initialTravel = 0f
        private set
    fun observe(delta: Float, crossDelta: Float, consumed: Boolean): Int {
        if (childOwned) return -1
        if (delta == 0f && crossDelta == 0f) return 0
        travel += delta
        cross += crossDelta
        if (travel < -slop || abs(cross) > slop && abs(cross) > abs(travel) || consumed) {
            childOwned = true
            return -1
        }
        if (consumed || travel <= slop || travel < abs(cross)) return 0
        initialTravel = (travel - slop).coerceAtLeast(0f)
        return 1
    }
}

internal fun Modifier.iosSwipeBack(
    enabled: Boolean,
    direction: LayoutDirection,
    entryKey: Any,
    state: IosSwipeBackState,
    registry: GlasmioSwipeBackExclusionRegistry,
    coordinates: () -> LayoutCoordinates?,
    systemOwnsGesture: () -> Boolean,
    anchorProgress: () -> Float,
    onCommit: () -> Unit,
): Modifier = if (!enabled) this else pointerInput(state, direction, registry, entryKey) {
    val sign = if (direction == LayoutDirection.Ltr) 1f else -1f
    try {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            val root = coordinates()?.takeIf { it.isAttached } ?: return@awaitEachGesture
            if (systemOwnsGesture() || registry.hitTest(root.localToWindow(down.position))) return@awaitEachGesture
            val extent = size.width.toFloat().coerceAtLeast(1f)
            val arbitrator = IosSwipeBackArbitrator(max(viewConfiguration.touchSlop, 10.dp.toPx()))
            val velocity = VelocityTracker()
            velocity.addPosition(down.uptimeMillis, down.position)
            val release = IosSwipeBackRelease(extent / density)
            release.sample(anchorProgress().coerceIn(0f, 1f), down.uptimeMillis, 0f)
            var travel = 0f
            var claimed = false
            var claimGeneration = state.generation
            while (!claimed) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed || event.changes.count { it.pressed } != 1 || systemOwnsGesture() ||
                    change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) return@awaitEachGesture
                velocity.addPosition(change.uptimeMillis, change.position)
                val delta = change.positionChangeIgnoreConsumed()
                when (arbitrator.observe(sign * delta.x, delta.y, change.isConsumed)) {
                    -1 -> return@awaitEachGesture
                    1 -> {
                        travel = arbitrator.initialTravel
                        state.generation++
                        claimGeneration = state.generation
                        state.releaseVelocity = 0f
                        state.entryKey = entryKey
                        state.startProgress = anchorProgress().coerceIn(0f, 1f)
                        state.progress = (state.startProgress + travel / extent).coerceIn(0f, 1f)
                        state.lastEventTimeMillis = change.uptimeMillis
                        release.sample(state.progress, change.uptimeMillis, sign * velocity.calculateVelocity().x / extent)
                        state.tracking = true
                        change.consume()
                        claimed = true
                    }
                }
            }
            var cancelled = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null && !change.pressed && change.isConsumed) cancelled = true
                if (systemOwnsGesture() || state.generation != claimGeneration || change == null ||
                    event.changes.any { it.id != down.id && it.pressed }) {
                    cancelled = true
                    state.cancel()
                }
                event.changes.forEach { it.consume() }
                if (change != null) velocity.addPosition(change.uptimeMillis, change.position)
                if (event.changes.none { it.pressed }) {
                    if (change != null && !cancelled) {
                        travel += sign * change.positionChangeIgnoreConsumed().x
                        state.progress = (state.startProgress + travel / extent).coerceIn(0f, 1f)
                        release.sample(state.progress, change.uptimeMillis, sign * velocity.calculateVelocity().x / extent)
                    }
                    break
                }
                if (cancelled || change == null) continue
                velocity.addPosition(change.uptimeMillis, change.position)
                travel += sign * change.positionChangeIgnoreConsumed().x
                state.progress = (state.startProgress + travel / extent).coerceIn(0f, 1f)
                state.lastEventTimeMillis = change.uptimeMillis
                release.sample(state.progress, change.uptimeMillis, sign * velocity.calculateVelocity().x / extent)
            }
            val speed = sign * velocity.calculateVelocity().x / extent
            state.releaseVelocity = if (cancelled) 0f else speed
            val commit = !cancelled && release.shouldCommit(state.progress)
            state.tracking = false
            // Commit synchronously. A coroutine tied to the leaving entry can be cancelled
            // before its stack mutation when the outgoing page finishes its settle.
            if (commit) onCommit()
        }
    } finally {
        state.cancel()
    }
}

/** UIKit's three-sample progress projection, in normalized screen-width units. */
internal class IosSwipeBackRelease(widthPoints: Float) {
    private val threshold = minOf(187.5f / widthPoints.coerceAtLeast(1f), 0.5f)
    private val velocities = FloatArray(3)
    private val accelerations = FloatArray(3)
    private var count = 0
    private var previousProgress = 0f
    private var previousTime = 0L
    private var previousVelocity = 0f
    private var previousAcceleration = 0f
    fun sample(progress: Float, timeMillis: Long, observedVelocity: Float? = null) {
        if (count > 0 && timeMillis <= previousTime) return
        val seconds = if (count == 0) 0f else (timeMillis - previousTime) / 1000f
        val velocity = observedVelocity ?: if (seconds > 0f) (progress - previousProgress) / seconds else 0f
        val acceleration = if (seconds > 0f) ((velocity - previousVelocity) / seconds)
            .coerceIn(previousAcceleration - 5f, previousAcceleration + 5f) else 0f
        velocities[count % 3] = velocity
        accelerations[count % 3] = acceleration
        previousProgress = progress
        previousTime = timeMillis
        previousVelocity = velocity
        previousAcceleration = acceleration
        count++
    }
    fun shouldCommit(progress: Float): Boolean {
        val samples = minOf(count, 3)
        if (samples == 0) return false
        val velocity = velocities.sum() / samples
        if (count < 3) return velocity > 0f
        val acceleration = accelerations.sum() / samples
        return progress + 0.35f * velocity + 0.5f * 0.35f * 0.35f * acceleration > threshold
    }
}
