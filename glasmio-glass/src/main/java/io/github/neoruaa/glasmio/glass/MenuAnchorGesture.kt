package io.github.neoruaa.glasmio.glass

import androidx.annotation.RestrictTo
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

/** Keeps the original touch in the anchor's window while the popup tracks it in screen space. */
@Stable
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class GlasmioMenuDragSelection internal constructor() {
    private var owner: Any? = null

    var origin: Offset? by mutableStateOf(null)
        private set

    var position: Offset? by mutableStateOf(null)
        private set

    var onRelease: ((Offset) -> Unit)? = null

    fun acquire(token: Any, origin: Offset): Boolean {
        if (owner != null) return false
        owner = token
        this.origin = origin
        return true
    }

    fun move(token: Any, position: Offset) {
        if (owner !== token) return
        this.position = position
    }

    fun finish(token: Any, position: Offset? = null) {
        if (owner !== token) return
        owner = null
        val wasDragging = this.position != null
        this.position = null
        origin = null
        if (wasDragging && position != null) onRelease?.invoke(position)
    }
}

private class MenuGestureCoordinates {
    var layout: LayoutCoordinates? = null

    fun screenPosition(offset: Offset): Offset? =
        layout?.takeIf { it.isAttached }?.localToScreen(offset)?.takeIf { it.isFinite }
}

/**
 * Opens a menu on touch and stylus holds while preserving the anchor's ordinary click.
 * Use the same [state] on the popup and the trigger. This modifier leaves anchor drawing
 * and layout to the caller.
 */
@Composable
fun Modifier.glasmioMenuGesture(
    state: GlasmioMenuAnchorState,
    onOpenMenu: () -> Unit,
): Modifier {
    val currentOpen by rememberUpdatedState(onOpenMenu)
    val coordinates = remember { MenuGestureCoordinates() }
    val selection = state.dragSelection
    return onGloballyPositioned { coordinates.layout = it }
        .pointerInput(selection) {
            val dragThreshold = 4.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (down.type != PointerType.Touch && down.type != PointerType.Stylus) return@awaitEachGesture
                val origin = coordinates.screenPosition(down.position) ?: return@awaitEachGesture
                val token = Any()
                if (!selection.acquire(token, origin)) {
                    // A different trigger sharing this state already owns the touch. Swallow
                    // this down before the other button can turn its up into an ordinary click.
                    down.consume()
                    return@awaitEachGesture
                }

                try {
                    var holdPoint = origin
                    val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val changes = awaitPointerEvent(PointerEventPass.Final).changes
                            val pointer = changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointer.pressed || changes.any { it.id != down.id && it.pressed }) break
                            // The anchor button consumes DOWN. Subsequent consumed moves belong
                            // to another gesture, such as scrolling the page under the anchor.
                            if (pointer.previousPressed && pointer.isConsumed) break
                            holdPoint = coordinates.screenPosition(pointer.position) ?: break
                            if ((holdPoint - origin).getDistance() > viewConfiguration.touchSlop) break
                        }
                        true
                    } ?: false
                    if (cancelled) return@awaitEachGesture

                    currentOpen()
                    while (true) {
                        val changes = awaitPointerEvent(PointerEventPass.Initial).changes
                        val pointer = changes.firstOrNull { it.id == down.id } ?: break
                        // The platform sends a consumed UP when it cancels a pointer stream.
                        if (pointer.isConsumed || changes.any { it.id != down.id && it.pressed }) break
                        val point = coordinates.screenPosition(pointer.position)
                        pointer.consume()
                        if (!pointer.pressed) {
                            selection.finish(token, point)
                            break
                        }
                        if (point != null &&
                            (selection.position != null || (point - holdPoint).getDistance() > dragThreshold)
                        ) {
                            selection.move(token, point)
                        }
                    }
                } finally {
                    selection.finish(token)
                }
            }
        }
}
