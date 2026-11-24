package io.github.neoruaa.glasmio.glass

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.icons.*

import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.requireView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import kotlinx.coroutines.CoroutineScope
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.hasBuiltInLayerBlockIndependentSource
import io.github.neoruaa.glasmio.glass.render.backdrops.isLocalColorFill
import io.github.neoruaa.glasmio.glass.render.backdrops.isUnitTranslation
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberBoundedLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.ceil

val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop> {
    error("Glass controls must be hosted by GlasmioBackdropHost or GlasmioBackdropProvider")
}

/** Backdrop source reserved for modal surfaces that sample the rendered page. */
val LocalBlurBackdrop = staticCompositionLocalOf<Backdrop> {
    error("Blur glass must be hosted by the app backdrop provider")
}

@Composable
fun GlasmioBackdropProvider(
    backdrop: Backdrop,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalGlassBackdrop provides backdrop,
        LocalBlurBackdrop provides backdrop,
        content = content,
    )
}

/**
 * Keeps sampled content and glass overlays in separate layers. Glass controls must never be
 * placed inside [sampledContent], otherwise the backdrop can recursively sample itself.
 */
@Composable
fun GlasmioBackdropHost(
    modifier: Modifier = Modifier,
    sampledContent: @Composable BoxScope.(LayerBackdrop) -> Unit,
    overlayContent: @Composable BoxScope.(LayerBackdrop) -> Unit,
) = BackdropHost(modifier, uniformFill = false, bounded = false, sampledContent, overlayContent)

/** When requested, each material samples within the source bounds, including after later layer changes. */
@Composable
fun GlasmioBackdropHost(
    sampledContentClipped: Boolean,
    modifier: Modifier = Modifier,
    sampledContent: @Composable BoxScope.(LayerBackdrop) -> Unit,
    overlayContent: @Composable BoxScope.(LayerBackdrop) -> Unit,
) = BackdropHost(modifier, uniformFill = false, bounded = sampledContentClipped, sampledContent, overlayContent)

/**
 * A [GlasmioBackdropHost] whose sampled content is [sampledColor] filling the host.
 *
 * The host declares the fill itself, so glass that moves while sampling only this color, such as
 * a control in scrolled content, keeps its rendered result instead of repeating every pass.
 * Glass is rendered again as usual when an edge of the fill moves within its samples.
 */
@Composable
fun GlasmioBackdropHost(
    sampledColor: Color,
    modifier: Modifier = Modifier,
    overlayContent: @Composable BoxScope.(LayerBackdrop) -> Unit,
) = BackdropHost(modifier, uniformFill = true, bounded = false, { Box(Modifier.fillMaxSize().background(sampledColor)) }, overlayContent)

@Composable
private fun BackdropHost(
    modifier: Modifier,
    uniformFill: Boolean,
    bounded: Boolean,
    sampledContent: @Composable BoxScope.(LayerBackdrop) -> Unit,
    overlayContent: @Composable BoxScope.(LayerBackdrop) -> Unit,
) {
    val backdrop = if (bounded) rememberBoundedLayerBackdrop() else rememberLayerBackdrop()
    // The fill draws nothing else and covers the layer bounds exactly.
    backdrop.fillsBoundsUniformly = uniformFill
    CompositionLocalProvider(
        LocalGlassBackdrop provides backdrop,
        LocalBlurBackdrop provides backdrop,
    ) {
        Box(modifier) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .trackBackdropPosition(backdrop),
            ) {
                sampledContent(backdrop)
            }
            overlayContent(backdrop)
        }
    }
}

fun Modifier.glassBackdropSource(backdrop: LayerBackdrop): Modifier = layerBackdrop(backdrop)

fun Modifier.glassBackdropSource(backdrop: LayerBackdrop, maxCaptureHeight: Dp): Modifier =
    layerBackdrop(backdrop, reuseContent = false, maxCaptureHeight = maxCaptureHeight)

/** Weak values prevent modifier callbacks from retaining their backdrop keys and detached trees. */
private val backdropSourcePositions = WeakHashMap<LayerBackdrop, WeakReference<LayoutCoordinates>>()
private val backdropSourceFrames = WeakHashMap<LayerBackdrop, State<Int>>()

/** Attach next to a [layerBackdrop] recording so dialog-window glass can locate the source. */
fun Modifier.trackBackdropPosition(backdrop: LayerBackdrop, frameVersion: State<Int>? = null): Modifier {
    frameVersion?.let { backdropSourceFrames[backdrop] = it }
    return onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) {
            backdropSourcePositions[backdrop] = WeakReference(coordinates)
        }
    }
}

/**
 * Samples a [LayerBackdrop] that was recorded in another window (e.g. app content from a
 * dialog/sheet window). LayerBackdrop maps coordinates with localPositionOf, which cannot
 * cross compose owners and silently yields a wrong offset there, so glass in a dialog
 * samples nothing. Map through screen coordinates so ancestor transforms, including
 * predictive-back scaling, are preserved across windows.
 */
class CrossWindowBackdrop(
    private val source: LayerBackdrop,
    private val rasterized: GraphicsLayer? = null,
    private val renderScale: Float = 1f,
    private val captureHeight: Dp? = null,
    private val sourceFrame: State<Int>? = null,
) : Backdrop {

    private val sourceToGlass = Matrix()
    private val screenToGlass = Matrix()
    private var recordedSourceSize = IntSize.Zero
    private var recordedSourceFrame: Int? = null
    internal var detachedSlot: DetachedSharedBackdrop.Slot? = null

    override val isCoordinatesDependent: Boolean get() = true

    override fun observeSourceChanges(observer: () -> Unit): () -> Unit = source.observeSourceChanges(observer)

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val glassCoordinates = coordinates ?: return
        if (!glassCoordinates.isAttached) return
        val sourceCoordinates = backdropSourcePositions[source]?.get()?.takeIf { it.isAttached } ?: return
        // A window draw includes foreground changes isolated in child graphics layers.
        val crossWindow = glassCoordinates.findRootCoordinates() !== sourceCoordinates.findRootCoordinates()
        val frame = if (crossWindow) {
            (sourceFrame ?: backdropSourceFrames[source])?.value
        } else null
        val sourceSize = source.graphicsLayer.size
        val captureTop = captureHeight?.let {
            (sourceSize.height - with(density) { it.roundToPx() }).coerceAtLeast(0)
        } ?: 0
        val layer = rasterized?.also { cached ->
            val bufferSize = IntSize(
                ceil(sourceSize.width * renderScale).toInt(),
                ceil((sourceSize.height - captureTop) * renderScale).toInt(),
            )
            if (bufferSize.width <= 0 || bufferSize.height <= 0) return
            val slot = detachedSlot?.takeIf { crossWindow && drawContext.canvas.nativeCanvas.isHardwareAccelerated }
            val image = slot?.image(
                DetachedSharedBackdrop.Viewport(
                    bufferSize, sourceSize, captureTop, frame, Density(density.density, density.fontScale), layoutDirection,
                ),
                cached,
            )
            if (cached.size != bufferSize || recordedSourceSize != sourceSize ||
                (frame != null && recordedSourceFrame != frame) || detachedSlot?.recordedImage !== image
            ) {
                cached.clip = true
                cached.compositingStrategy = CompositingStrategy.Offscreen
                cached.record(bufferSize) {
                    if (image != null) {
                        drawImage(image)
                    } else {
                        withTransform({
                            scale(renderScale, renderScale, Offset.Zero)
                            translate(0f, -captureTop.toFloat())
                        }) {
                            drawLayer(source.graphicsLayer)
                        }
                    }
                }
                detachedSlot?.recordedImage = image
                recordedSourceSize = sourceSize
                recordedSourceFrame = frame
            }
            if (image == null) slot?.requestWhenLive()
        } ?: source.graphicsLayer
        // Include ancestor scale/translation so the sampled page stays screen-anchored
        // while the sheet shrinks during predictive back. This also includes layerBlock.
        sourceToGlass.reset()
        sourceCoordinates.transformToScreen(sourceToGlass)
        screenToGlass.reset()
        glassCoordinates.transformToScreen(screenToGlass)
        screenToGlass.invert()
        sourceToGlass *= screenToGlass
        withTransform({
            transform(sourceToGlass)
            translate(0f, captureTop.toFloat())
            if (rasterized != null) scale(1f / renderScale, 1f / renderScale, Offset.Zero)
        }) {
            drawLayer(layer)
        }
    }
}

/**
 * Wraps a [LayerBackdrop] for glass rendered in a separate window (dialog/sheet).
 * Non-layer backdrops pass through unchanged.
 */
@Composable
fun rememberCrossWindowBackdrop(backdrop: Backdrop): Backdrop = remember(backdrop) {
    if (backdrop is LayerBackdrop) CrossWindowBackdrop(backdrop) else backdrop
}

/** Samples from a stable host while only the glass outline follows its animated layer. */
@Composable
fun rememberStationaryBackdrop(
    backdrop: Backdrop,
    samplingCoordinates: () -> LayoutCoordinates?,
): Backdrop {
    val source = rememberCrossWindowBackdrop(backdrop)
    return remember(source, samplingCoordinates) { StationaryBackdrop(source, samplingCoordinates) }
}

private class StationaryBackdrop(
    private val source: Backdrop,
    private val samplingCoordinates: () -> LayoutCoordinates?,
) : Backdrop {
    private val layerScope = GraphicsLayerBlockScope()
    private val inverseLayer = Matrix()

    override val isCoordinatesDependent: Boolean get() = true

    override fun observeSourceChanges(observer: () -> Unit): () -> Unit = source.observeSourceChanges(observer)

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val host = samplingCoordinates()?.takeIf { it.isAttached } ?: return
        inverseLayer.reset()
        if (layerBlock != null) {
            // Evaluate the live block during recording as well as graphicsLayer updates.
            // This observes animation state and avoids reading an OwnedLayer matrix that
            // may still contain the previous frame's scale or moving pivot.
            layerScope.reset(density, Size(host.size.width.toFloat(), host.size.height.toFloat()))
            layerBlock(layerScope)
            inverseLayer.resetToPivotedTransform(
                pivotX = layerScope.size.width * layerScope.transformOrigin.pivotFractionX,
                pivotY = layerScope.size.height * layerScope.transformOrigin.pivotFractionY,
                translationX = layerScope.translationX,
                translationY = layerScope.translationY,
                rotationX = layerScope.rotationX,
                rotationY = layerScope.rotationY,
                rotationZ = layerScope.rotationZ,
                scaleX = layerScope.scaleX,
                scaleY = layerScope.scaleY,
            )
            inverseLayer.invert()
        }
        withTransform({ transform(inverseLayer) }) {
            // The host sits outside the animated layer. Passing null prevents source
            // backdrops from applying the same inverse transform a second time.
            with(source) { drawBackdrop(density, host, null) }
        }
    }
}

/** Share a live, flattened player source between modal glass and its small glass controls. */
@Composable
fun rememberRasterizedBackdrop(source: LayerBackdrop, enabled: Boolean = true): Backdrop {
    val rasterized = rememberGraphicsLayer()
    val view = LocalView.current
    val sourceFrame = remember(view) { mutableIntStateOf(0) }
    DisposableEffect(view, sourceFrame, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        var frame = sourceFrame.intValue
        val listener = ViewTreeObserver.OnPreDrawListener {
            // Only cross-window drawing observes this state; the source stays independent.
            sourceFrame.intValue = ++frame
            true
        }
        view.viewTreeObserver.addOnPreDrawListener(listener)
        onDispose {
            val observer = view.viewTreeObserver
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
        }
    }
    return remember(source, rasterized, sourceFrame) {
        CrossWindowBackdrop(source, rasterized, renderScale = 0.25f, sourceFrame = sourceFrame)
    }
}

/**
 * Share rasterized page regions between glass consumers instead of replaying the display
 * list (including nested blur and navigation layers) for every lens. This is a live layer
 * reference: source changes invalidate the GPU cache even when the recording is unchanged.
 * Only sampled pixels use half resolution; text, glass outlines and highlights stay native.
 */
@Composable
fun rememberSharedGlassBackdrop(base: Backdrop, source: LayerBackdrop): Backdrop {
    val full = rememberGraphicsLayer()
    val bottom = rememberGraphicsLayer()
    return remember(base, source, full, bottom) {
        SharedGlassBackdrop(base, source, full, bottom)
    }
}

/**
 * Gives one modal window its own sampled textures in place of repeatedly traversing an idle host.
 *
 * Call in the source window. Enable only for sources that rasterize identically outside that
 * window, without native RenderNode lighting such as elevation shadows. [enabled] must become
 * false before source pixels can change without a source draw, for example during
 * RenderThread-only animations or independently animated drawables. Source draws invalidate the
 * sampled textures. The original live layer is used when textures are unavailable, in the source
 * window, and outside supported SDR windows.
 */
@Composable
fun rememberDetachedSharedGlassBackdrop(backdrop: Backdrop, enabled: () -> Boolean): Backdrop {
    val shared = backdrop as? SharedGlassBackdrop ?: return backdrop
    if (Build.VERSION.SDK_INT < 34) return backdrop
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val currentEnabled = rememberUpdatedState(enabled)
    val graphics = LocalGraphicsContext.current
    val activation = remember(shared, scope, view, graphics) {
        DeferredDetachedCapture(shared, view, graphics, scope) { currentEnabled.value() }
    }
    DisposableEffect(activation) { onDispose(activation::dispose) }
    return remember(shared, activation) { shared.withDeferredCapture(activation) }
}

internal fun Backdrop.observeDetachedSharedBackdrop(
    node: DelegatableNode,
    coordinates: LayoutCoordinates?,
    canvas: Canvas,
) {
    (this as? SharedGlassBackdrop)?.observeDetachedCapture(node, coordinates, canvas)
}

internal fun Backdrop.hasDeferredSharedCapture(): Boolean =
    this is SharedGlassBackdrop && hasDeferredCapture

/** A morph records in root coordinates, but only needs pixels inside its capture viewport. */
interface ViewportBackdrop : Backdrop {
    fun DrawScope.drawViewport(density: Density, coordinates: LayoutCoordinates, viewport: Rect)
}

/** Only these final wrappers map actual coordinates without evaluating the layer block. */
internal fun Backdrop.hasBuiltInGlassLayerBlockIndependentSource(): Boolean = when (this) {
    is CrossWindowBackdrop -> true
    is SharedGlassBackdrop -> hasLayerBlockIndependentBase()
    is SettledBackdrop -> hasLayerBlockIndependentSource()
    else -> false
}

private val BottomCaptureHeight = 240.dp

private class SharedGlassBackdrop(
    private val base: Backdrop,
    private val source: LayerBackdrop,
    fullRaster: GraphicsLayer?,
    bottomRaster: GraphicsLayer?,
    private val detachedCapture: DetachedSharedBackdrop? = null,
    private val deferredCapture: DeferredDetachedCapture? = null,
    private val liveFallback: SharedGlassBackdrop? = null,
) : ViewportBackdrop {
    private val full: CrossWindowBackdrop = liveFallback?.full ?: CrossWindowBackdrop(source, fullRaster, renderScale = 0.5f).apply {
        detachedSlot = detachedCapture?.full
    }
    private val bottom: CrossWindowBackdrop = liveFallback?.bottom ?: CrossWindowBackdrop(
        source, bottomRaster, renderScale = 0.5f, captureHeight = BottomCaptureHeight,
    ).apply {
        detachedSlot = detachedCapture?.bottom
    }

    fun withDetachedCapture(
        fullLayer: GraphicsLayer,
        bottomLayer: GraphicsLayer,
        capture: DetachedSharedBackdrop,
    ): SharedGlassBackdrop = SharedGlassBackdrop(base, source, fullLayer, bottomLayer, capture)

    fun withDeferredCapture(activation: DeferredDetachedCapture): Backdrop =
        SharedGlassBackdrop(base, source, null, null, deferredCapture = activation, liveFallback = this)

    val hasDeferredCapture: Boolean get() = deferredCapture != null

    fun observeDetachedCapture(
        node: DelegatableNode, coordinates: LayoutCoordinates?, canvas: Canvas,
    ) {
        // Read the activation at the outer draw node. A new private raster must invalidate
        // the recorded parent even if it got cached inside a material's display list.
        deferredCapture?.observeConsumer(node.requireView(), coordinates, canvas.nativeCanvas.isHardwareAccelerated)
    }

    internal fun hasLayerBlockIndependentBase(): Boolean = base.hasBuiltInLayerBlockIndependentSource()

    override val isCoordinatesDependent: Boolean get() = true

    override fun observeSourceChanges(observer: () -> Unit): () -> Unit {
        if (base === source) return source.observeSourceChanges(observer)
        val baseObservation = base.observeSourceChanges(observer)
        val sourceObservation = source.observeSourceChanges(observer)
        return { baseObservation(); sourceObservation() }
    }

    internal fun boundedSamples(coordinates: LayoutCoordinates, logicalSize: Size): Rect? {
        val lower = base.boundedSampleRegion(coordinates, logicalSize) ?: return null
        val recorded = backdropSourcePositions[source]?.get()?.takeIf { it.isAttached } ?: return null
        if (!coordinates.isAttached || recorded.findRootCoordinates() !== coordinates.findRootCoordinates() ||
            recorded.size != source.graphicsLayer.size) return null
        // Both private rasterized sources are clipped to their physical buffer. The bottom band
        // never extends above the full source. Ceil and linear filtering can reach its outer texel.
        sampleToConsumer.reset()
        recorded.transformToScreen(sampleToConsumer)
        screenToConsumerBounds.reset()
        coordinates.transformToScreen(screenToConsumerBounds)
        screenToConsumerBounds.invert()
        sampleToConsumer *= screenToConsumerBounds
        if (!sampleToConsumer.isUnitTranslation()) return null
        val upper = sampleToConsumer.map(Rect(-4f, -4f, recorded.size.width + 4f, recorded.size.height + 4f))
        return Rect(minOf(lower.left, upper.left), minOf(lower.top, upper.top),
            maxOf(lower.right, upper.right), maxOf(lower.bottom, upper.bottom))
    }

    private val sampleToConsumer = Matrix()
    private val screenToConsumerBounds = Matrix()

    private fun sample(density: Density, coordinates: LayoutCoordinates?, top: Float): Backdrop {
        val recorded = backdropSourcePositions[source]?.get()
        if (coordinates?.isAttached != true || recorded?.isAttached != true) return full
        val sourceTop = coordinates.positionOnScreen().y - recorded.positionOnScreen().y + top
        val bandTop = source.graphicsLayer.size.height - with(density) { BottomCaptureHeight.toPx() }
        // Include the largest navigation lens/blur padding. Upper controls and full-sheet
        // captures use the full page; bottom morphs never replay the top toolbar's blur.
        return if (sourceTop - with(density) { 32.dp.toPx() } >= bandTop) bottom else full
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val ready = deferredCapture?.readyFor(coordinates, drawContext.canvas.nativeCanvas.isHardwareAccelerated)
        if (ready != null) {
            with(ready) { drawBackdrop(density, coordinates, layerBlock) }
        } else {
            with(base) { drawBackdrop(density, coordinates, layerBlock) }
            with(sample(density, coordinates, 0f)) { drawBackdrop(density, coordinates, layerBlock) }
        }
    }

    override fun DrawScope.drawViewport(
        density: Density,
        coordinates: LayoutCoordinates,
        viewport: Rect,
    ) {
        val ready = deferredCapture?.readyFor(coordinates, drawContext.canvas.nativeCanvas.isHardwareAccelerated)
        if (ready != null) {
            with(ready) { drawViewport(density, coordinates, viewport) }
        } else {
            with(base) { drawBackdrop(density, coordinates, null) }
            with(sample(density, coordinates, viewport.top)) { drawBackdrop(density, coordinates, null) }
        }
    }
}

/**
 * Keep the original live sampling graph for the first buffer. Allocate private sampling
 * targets only after HWUI reports a submitted consumer frame.
 */
private class DeferredDetachedCapture(
    private val live: SharedGlassBackdrop,
    private val sourceView: View,
    private val graphics: GraphicsContext,
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean,
) {
    private var active = true
    private var pending: Pending? = null
    private var targets: Targets? = null
    private val publication = mutableStateOf<SharedGlassBackdrop?>(null)

    private class Pending(val view: View, val tree: ViewTreeObserver) {
        lateinit var callback: Runnable
        lateinit var attachment: View.OnAttachStateChangeListener
    }

    private data class Targets(
        val capture: DetachedSharedBackdrop,
        val full: GraphicsLayer,
        val bottom: GraphicsLayer,
        val stop: () -> Unit,
    )

    private var consumerRoot: LayoutCoordinates? = null

    fun readyFor(coordinates: LayoutCoordinates?, hardwareCanvas: Boolean): SharedGlassBackdrop? {
        return publication.value?.takeIf {
            active && hardwareCanvas && coordinates?.isAttached == true &&
                coordinates.findRootCoordinates() === consumerRoot
        }
    }

    fun observeConsumer(view: View, coordinates: LayoutCoordinates?, hardwareCanvas: Boolean) {
        // Before activation the existing source graph stays live regardless of the gate.
        val current = publication.value
        if (!active || !hardwareCanvas || view.rootView === sourceView.rootView || coordinates?.isAttached != true) return
        if (current != null) {
            consumerRoot = coordinates.findRootCoordinates()
            targets?.capture?.observeConsumer(view)
            return
        }
        if (pending != null || !supports(view)) return
        val tree = view.viewTreeObserver
        if (!tree.isAlive) return
        val request = Pending(view, tree)
        consumerRoot = coordinates.findRootCoordinates()
        val handler = view.handler ?: return
        request.callback = Runnable { handler.post { activate(request) } }
        request.attachment = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) {
                if (pending === request) cancelPending()
            }
        }
        pending = request
        view.addOnAttachStateChangeListener(request.attachment)
        tree.registerFrameCommitCallback(request.callback)
        // Registration from a draw can miss this traversal's callback collection.
        // Ensure that even a consumer with no running motion gets one next frame.
        view.postInvalidateOnAnimation()
    }

    private fun cancelPending() {
        val request = pending ?: return
        pending = null
        request.view.removeOnAttachStateChangeListener(request.attachment)
        if (request.tree.isAlive) request.tree.unregisterFrameCommitCallback(request.callback)
    }

    private fun activate(request: Pending) {
        if (pending !== request) return
        cancelPending()
        if (!active || consumerRoot?.isAttached != true || !supports(request.view)) return

        val capture = DetachedSharedBackdrop(scope, sourceView, enabled)
        val full = graphics.createGraphicsLayer()
        val bottom = graphics.createGraphicsLayer()
        val stop = capture.attach(live)
        targets = Targets(capture, full, bottom, stop)
        Snapshot.withoutReadObservation { publication.value = live.withDetachedCapture(full, bottom, capture) }
    }

    private fun supports(view: View): Boolean {
        val display = sourceView.display ?: return false
        return sourceView.isDefaultHardwareWindow() && view.isDefaultHardwareWindow() &&
            display.displayId == view.display?.displayId && view.rootView !== sourceView.rootView
    }

    fun dispose() {
        active = false
        consumerRoot = null
        cancelPending()
        val current = targets ?: return
        targets = null
        current.stop()
        // A cancelled capture may still be releasing its offscreen renderer on R/T. Keep
        // the input layers alive until that job has joined through completion.
        current.capture.afterCapture {
            graphics.releaseGraphicsLayer(current.full)
            graphics.releaseGraphicsLayer(current.bottom)
        }
    }
}

internal fun Backdrop.boundedSampleRegion(coordinates: LayoutCoordinates, logicalSize: Size): Rect? = when {
    this is LayerBackdrop -> boundedSamples(coordinates)
    this is SharedGlassBackdrop -> boundedSamples(coordinates, logicalSize)
    isLocalColorFill() -> Rect(Offset.Zero, logicalSize)
    else -> null
}
