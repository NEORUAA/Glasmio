package io.github.neoruaa.glasmio.core

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/** Window-local regions whose own gestures take priority over a full-page swipe back. */
@Stable
class GlasmioSwipeBackExclusionRegistry {
    private val regions = mutableMapOf<Any, LayoutCoordinates>()

    /** Query once at pointer down, using window coordinates rather than page-local pixels. */
    fun hitTest(windowPosition: Offset): Boolean {
        var hit = false
        val iterator = regions.values.iterator()
        while (iterator.hasNext()) {
            val coordinates = iterator.next()
            if (!coordinates.isAttached) {
                iterator.remove()
            } else if (coordinates.boundsInWindow().contains(windowPosition)) {
                hit = true
            }
        }
        return hit
    }

    internal fun register(key: Any, coordinates: LayoutCoordinates) {
        regions[key] = coordinates
    }

    internal fun unregister(key: Any) {
        regions.remove(key)
    }
}

/** Supplied by a navigation host; exclusions outside such a host have no effect. */
val LocalGlasmioSwipeBackExclusions = staticCompositionLocalOf<GlasmioSwipeBackExclusionRegistry?> { null }

/**
 * Preserve this region's horizontal or long-press gestures without consuming pointer events.
 * Apply to pagers, charts, embedded views, or other custom interaction surfaces as needed.
 */
fun Modifier.swipeBackExcluded(enabled: Boolean = true): Modifier = composed {
    val registry = LocalGlasmioSwipeBackExclusions.current
    if (!enabled || registry == null) this else {
        val key = remember { Any() }
        DisposableEffect(registry) {
            onDispose { registry.unregister(key) }
        }
        onGloballyPositioned { coordinates -> registry.register(key, coordinates) }
    }
}
