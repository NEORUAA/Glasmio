package io.github.neoruaa.glasmio.glass

import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.RestrictTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.hasBuiltInLayerBlockIndependentSource

/** Refreshes a modal's capture when its presenting window draws and its geometry is settled. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Composable
fun rememberSettledBackdrop(
    backdrop: Backdrop,
    sourceView: View,
    isSettled: () -> Boolean,
): SettledBackdrop {
    val source = rememberCrossWindowBackdrop(backdrop)
    val currentGate = rememberUpdatedState(isSettled)
    val settled = remember(source) { SettledBackdrop(source) { currentGate.value() } }
    val consumerView = LocalView.current
    DisposableEffect(settled, sourceView, consumerView) {
        onDispose(settled.observe(sourceView, consumerView))
    }
    LaunchedEffect(settled) {
        snapshotFlow { settled.isSettled }.collect { if (it) settled.refresh() }
    }
    return settled
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class SettledBackdrop internal constructor(
    private val source: Backdrop,
    private val settled: () -> Boolean,
) : ViewportBackdrop {
    private val version = mutableIntStateOf(0)
    private val observers = LinkedHashSet<() -> Unit>()
    private var observerSnapshot = emptyList<() -> Unit>()

    val isSettled: Boolean get() = settled()
    val revision: Int get() = version.intValue
    override val isCoordinatesDependent: Boolean get() = source.isCoordinatesDependent
    internal fun hasLayerBlockIndependentSource(): Boolean = source.hasBuiltInLayerBlockIndependentSource()

    override fun observeSourceChanges(observer: () -> Unit): () -> Unit {
        if (observers.add(observer)) observerSnapshot = observers.toList()
        return { if (observers.remove(observer)) observerSnapshot = observers.toList() }
    }

    internal fun refresh() {
        if (!isSettled) return
        version.intValue++
        observerSnapshot.forEach { it() }
    }

    internal fun observe(sourceView: View, consumerView: View): () -> Unit {
        var pending = false
        val refreshNextFrame = Runnable {
            pending = false
            refresh()
        }
        fun requestRefresh() {
            // Source draw observers must not subscribe to the consumer's animation state.
            if (pending || !Snapshot.withoutReadObservation { isSettled }) return
            pending = true
            sourceView.postOnAnimation(refreshNextFrame)
        }
        val stopObserving = source.observeSourceChanges(::requestRefresh)
        val sourceTree = sourceView.viewTreeObserver
        // A nested layer can animate without re-recording the parent LayerBackdrop. Watch
        // the presenting window too, so a redraw of the modal cannot request another draw.
        val listener = ViewTreeObserver.OnDrawListener(::requestRefresh)
            .takeIf { sourceTree.isAlive && sourceTree !== consumerView.viewTreeObserver }
        listener?.let(sourceTree::addOnDrawListener)
        return {
            stopObserving()
            if (listener != null && sourceTree.isAlive) sourceTree.removeOnDrawListener(listener)
            sourceView.removeCallbacks(refreshNextFrame)
        }
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) = with(source) { drawBackdrop(density, coordinates, layerBlock) }

    override fun DrawScope.drawViewport(density: Density, coordinates: LayoutCoordinates, viewport: Rect) {
        val backdrop = source
        if (backdrop is ViewportBackdrop) with(backdrop) { drawViewport(density, coordinates, viewport) }
        else with(backdrop) { drawBackdrop(density, coordinates) }
    }
}
