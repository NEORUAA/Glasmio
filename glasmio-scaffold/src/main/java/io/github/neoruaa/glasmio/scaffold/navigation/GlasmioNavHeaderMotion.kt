package io.github.neoruaa.glasmio.scaffold.navigation

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.github.neoruaa.glasmio.scaffold.*

/** Navigation-owned motion clock shared by the body display and the scaffold header. */
class GlasmioNavHeaderMotion(
    initialKey: Any,
    private val operationFor: (Any, Any, Boolean) -> GlasmioNavigationOperation = { _, _, pop ->
        if (pop) GlasmioNavigationOperation.Pop else GlasmioNavigationOperation.Push
    },
) : GlasmioHeaderMotion {
    private val state = mutableStateOf(GlasmioNavigationFrame(0, initialKey, initialKey))
    private var clock = mutableStateOf<(() -> Long)?>(null)
    override val frame: State<GlasmioNavigationFrame> = derivedStateOf {
        state.value.copy(playTimeNanos = clock.value?.invoke() ?: state.value.playTimeNanos)
    }
    var committedKey: Any = initialKey
        private set
    val interactionKey: Any get() = if (state.value.phase == GlasmioNavigationPhase.Canceling || state.value.phase == GlasmioNavigationPhase.Tracking) state.value.fromEntryKey else state.value.toEntryKey
    fun bindClock(clock: () -> Long) { if (this.clock.value !== clock) this.clock.value = clock }
    private var eventTimeMillis = 0L
    private var eventProgress = 0f
    private var sampleTimeNanos = 0L
    internal fun resolveOperation(from: Any, to: Any, isPop: Boolean) = operationFor(from, to, isPop)
    fun beginNavigation(from: Any, to: Any, isPop: Boolean) {
        begin(from, to, operationFor(from, to, isPop), GlasmioNavigationPhase.Settling)
    }
    fun begin(from: Any, to: Any, operation: GlasmioNavigationOperation, phase: GlasmioNavigationPhase) {
        eventTimeMillis = 0L
        eventProgress = 0f
        sampleTimeNanos = 0L
        state.value = GlasmioNavigationFrame(state.value.transitionId + 1, from, to, operation, phase)
    }
    fun phase(phase: GlasmioNavigationPhase) { state.value = state.value.copy(phase = phase) }
    fun gesture(progress: Float, timeMillis: Long) {
        val now = System.nanoTime()
        // NavigationEvent has no velocity field. Use event time when valid, otherwise
        // derive progress/s from monotonic arrival times (synthetic events may report zero).
        val seconds = if (eventTimeMillis > 0L && timeMillis > eventTimeMillis) (timeMillis - eventTimeMillis) / 1000f
            else if (sampleTimeNanos != 0L) (now - sampleTimeNanos) / 1e9f else 0f
        val velocity = if (seconds > 0f) (progress - eventProgress) / seconds else 0f
        sampleTimeNanos = now
        eventTimeMillis = timeMillis
        eventProgress = progress
        state.value = state.value.copy(gestureProgress = progress, gestureVelocity = velocity)
    }
    fun finish(key: Any) {
        // The host retains outgoing layers and their physical tracks after body completion.
        val old = state.value
        committedKey = key
        state.value = old.copy(fromEntryKey = key, toEntryKey = key,
            phase = GlasmioNavigationPhase.Idle, gestureProgress = 0f, gestureVelocity = 0f)
    }
}
