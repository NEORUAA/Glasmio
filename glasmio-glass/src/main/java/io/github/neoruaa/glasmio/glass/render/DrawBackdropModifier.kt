/*
 * Copyright 2025 Kyant
 * Copyright 2026 Glasmio contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Adapted from AndroidLiquidGlass 2.0.1 generic capture/effect infrastructure.
 * Modified for Glasmio's owned namespace and Android material renderer.
 */

package io.github.neoruaa.glasmio.glass.render

import io.github.neoruaa.glasmio.glass.NativeGlassAtlasBackingCopy
import io.github.neoruaa.glasmio.glass.NativeGlassAtlasPasses
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.requireView
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.hasBuiltInCoordinateIndependentSource
import io.github.neoruaa.glasmio.glass.render.backdrops.hasBuiltInLayerBlockIndependentSource
import io.github.neoruaa.glasmio.glass.render.highlight.Highlight
import io.github.neoruaa.glasmio.glass.render.highlight.HighlightElement
import io.github.neoruaa.glasmio.glass.render.internal.ShapeProvider
import io.github.neoruaa.glasmio.glass.render.internal.recordLayer
import io.github.neoruaa.glasmio.glass.render.shadow.InnerShadow
import io.github.neoruaa.glasmio.glass.render.shadow.InnerShadowElement
import io.github.neoruaa.glasmio.glass.render.shadow.Shadow
import io.github.neoruaa.glasmio.glass.render.shadow.ShadowElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

private val DefaultHighlight = { Highlight.Default }
private val DefaultShadow = { Shadow.Default }
private val FullResolutionSampling: (Size, Density) -> Float = { _, _ -> 1f }
private val DefaultOnDrawBackdrop: DrawScope.(DrawScope.() -> Unit) -> Unit = { it() }
private val NoBackdropEffects: BackdropEffectScope.() -> Unit = {}

/** Observe raw local backing without adding any visible material, layout layer or clip. */
fun Modifier.sampleBackdropLuminance(
    backdrop: Backdrop,
    luminanceState: BackdropLuminance,
    sampleBounds: (() -> Rect)? = null,
): Modifier = this.then(DrawBackdropElement(
    backdrop = backdrop, shapeProvider = ShapeProvider { RectangleShape }, effects = NoBackdropEffects,
    layerBlock = null, exportedBackdrop = null, luminanceState = luminanceState,
    samplingScale = FullResolutionSampling, onDrawBehind = null,
    onDrawBackdrop = DefaultOnDrawBackdrop, onDrawSurface = null, onDrawFront = null,
    luminanceOnly = true, sampleBounds = sampleBounds,
))

/** [samplingScale] reduces only the effect texture; source and surface callbacks retain logical geometry. */
fun Modifier.drawPlainBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    luminanceState: BackdropLuminance? = null,
    samplingScale: (Size, Density) -> Float = FullResolutionSampling,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = DefaultOnDrawBackdrop,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null
): Modifier {
    val shapeProvider = ShapeProvider(shape)
    return this
        .then(
            if (layerBlock != null) {
                Modifier.graphicsLayer(layerBlock)
            } else {
                Modifier
            }
        )
        .then(
            DrawBackdropElement(
                backdrop = backdrop,
                shapeProvider = shapeProvider,
                effects = effects,
                layerBlock = layerBlock,
                exportedBackdrop = exportedBackdrop,
                luminanceState = luminanceState,
                samplingScale = samplingScale,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = onDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront
            )
        )
}

/** [samplingScale] leaves shape clipping, highlights, shadows, coatings and content at full resolution. */
fun Modifier.drawBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    highlight: (() -> Highlight?)? = DefaultHighlight,
    shadow: (() -> Shadow?)? = DefaultShadow,
    innerShadow: (() -> InnerShadow?)? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    luminanceState: BackdropLuminance? = null,
    samplingScale: (Size, Density) -> Float = FullResolutionSampling,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = DefaultOnDrawBackdrop,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null
): Modifier {
    val shapeProvider = ShapeProvider(shape)
    return this
        .then(
            if (layerBlock != null) {
                Modifier.graphicsLayer(layerBlock)
            } else {
                Modifier
            }
        )
        .then(
            if (innerShadow != null) {
                InnerShadowElement(
                    shapeProvider = shapeProvider,
                    shadow = innerShadow
                )
            } else {
                Modifier
            }
        )
        .then(
            if (shadow != null) {
                ShadowElement(
                    shapeProvider = shapeProvider,
                    shadow = shadow
                )
            } else {
                Modifier
            }
        )
        .then(
            if (highlight != null) {
                HighlightElement(
                    shapeProvider = shapeProvider,
                    highlight = highlight
                )
            } else {
                Modifier
            }
        )
        .then(
            DrawBackdropElement(
                backdrop = backdrop,
                shapeProvider = shapeProvider,
                effects = effects,
                layerBlock = layerBlock,
                exportedBackdrop = exportedBackdrop,
                luminanceState = luminanceState,
                samplingScale = samplingScale,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = onDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront
            )
        )
}

private class DrawBackdropElement(
    val backdrop: Backdrop,
    val shapeProvider: ShapeProvider,
    val effects: BackdropEffectScope.() -> Unit,
    val layerBlock: (GraphicsLayerScope.() -> Unit)?,
    val exportedBackdrop: LayerBackdrop?,
    val luminanceState: BackdropLuminance?,
    val samplingScale: (Size, Density) -> Float,
    val onDrawBehind: (DrawScope.() -> Unit)?,
    val onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    val onDrawSurface: (DrawScope.() -> Unit)?,
    val onDrawFront: (DrawScope.() -> Unit)?,
    val luminanceOnly: Boolean = false,
    val sampleBounds: (() -> Rect)? = null,
) : ModifierNodeElement<DrawBackdropNode>() {

    override fun create(): DrawBackdropNode {
        return DrawBackdropNode(
            backdrop = backdrop,
            shapeProvider = shapeProvider,
            effects = effects,
            layerBlock = layerBlock,
            exportedBackdrop = exportedBackdrop,
            luminanceState = luminanceState,
            samplingScale = samplingScale,
            onDrawBehind = onDrawBehind,
            onDrawBackdrop = onDrawBackdrop,
            onDrawSurface = onDrawSurface,
            onDrawFront = onDrawFront,
            luminanceOnly = luminanceOnly,
            sampleBounds = sampleBounds,
        )
    }

    override fun update(node: DrawBackdropNode) {
        node.updateBackdrop(backdrop)
        node.shapeProvider = shapeProvider
        node.effects = effects
        node.layerBlock = layerBlock
        if (node.exportedBackdrop != exportedBackdrop) {
            node.exportedBackdrop?.clearRecordingCoordinates()
            node.exportedBackdrop = exportedBackdrop
        }
        node.updateLuminanceState(luminanceState)
        node.samplingScale = samplingScale
        node.onDrawBehind = onDrawBehind
        node.onDrawBackdrop = onDrawBackdrop
        node.onDrawSurface = onDrawSurface
        node.onDrawFront = onDrawFront
        node.luminanceOnly = luminanceOnly
        node.sampleBounds = sampleBounds
        node.invalidateDrawCache()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "drawBackdrop"
        properties["backdrop"] = backdrop
        properties["shapeProvider"] = shapeProvider
        properties["effects"] = effects
        properties["layerBlock"] = layerBlock
        properties["exportedBackdrop"] = exportedBackdrop
        properties["luminanceState"] = luminanceState
        properties["samplingScale"] = samplingScale
        properties["onDrawBehind"] = onDrawBehind
        properties["onDrawBackdrop"] = onDrawBackdrop
        properties["onDrawSurface"] = onDrawSurface
        properties["onDrawFront"] = onDrawFront
        properties["luminanceOnly"] = luminanceOnly
        properties["sampleBounds"] = sampleBounds
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrawBackdropElement) return false

        if (backdrop != other.backdrop) return false
        if (shapeProvider != other.shapeProvider) return false
        if (effects != other.effects) return false
        if (layerBlock != other.layerBlock) return false
        if (exportedBackdrop != other.exportedBackdrop) return false
        if (luminanceState != other.luminanceState) return false
        if (samplingScale != other.samplingScale) return false
        if (onDrawBehind != other.onDrawBehind) return false
        if (onDrawBackdrop != other.onDrawBackdrop) return false
        if (onDrawSurface != other.onDrawSurface) return false
        if (onDrawFront != other.onDrawFront) return false
        if (luminanceOnly != other.luminanceOnly) return false
        if (sampleBounds != other.sampleBounds) return false

        return true
    }

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + shapeProvider.hashCode()
        result = 31 * result + effects.hashCode()
        result = 31 * result + (layerBlock?.hashCode() ?: 0)
        result = 31 * result + (exportedBackdrop?.hashCode() ?: 0)
        result = 31 * result + (luminanceState?.hashCode() ?: 0)
        result = 31 * result + samplingScale.hashCode()
        result = 31 * result + (onDrawBehind?.hashCode() ?: 0)
        result = 31 * result + onDrawBackdrop.hashCode()
        result = 31 * result + (onDrawSurface?.hashCode() ?: 0)
        result = 31 * result + (onDrawFront?.hashCode() ?: 0)
        result = 31 * result + luminanceOnly.hashCode()
        result = 31 * result + (sampleBounds?.hashCode() ?: 0)
        return result
    }
}

private class DrawBackdropNode(
    private var backdrop: Backdrop,
    var shapeProvider: ShapeProvider,
    var effects: BackdropEffectScope.() -> Unit,
    var layerBlock: (GraphicsLayerScope.() -> Unit)?,
    var exportedBackdrop: LayerBackdrop?,
    var luminanceState: BackdropLuminance?,
    var samplingScale: (Size, Density) -> Float,
    var onDrawBehind: (DrawScope.() -> Unit)?,
    var onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    var onDrawSurface: (DrawScope.() -> Unit)?,
    var onDrawFront: (DrawScope.() -> Unit)?,
    luminanceOnly: Boolean,
    var sampleBounds: (() -> Rect)?,
) : LayoutModifierNode, DrawModifierNode, GlobalPositionAwareModifierNode, ObserverModifierNode, Modifier.Node() {

    var luminanceOnly by mutableStateOf(luminanceOnly)

    private val effectScope =
        object : BackdropEffectScopeImpl() {

            override val supportsNativeControlLensConsumerSharing: Boolean get() = true
            override val supportsNativeGlassHardwareLinearSampling: Boolean get() = true
            override val supportsNativeControlLensPlaneStages: Boolean
                get() = onDrawBackdrop === DefaultOnDrawBackdrop &&
                    (layerBlock == null || (backdrop.hasBuiltInLayerBlockIndependentSource() &&
                        (this.auxiliaryBackdrop?.hasBuiltInLayerBlockIndependentSource() ?: true)))

            override val shape: Shape get() = shapeProvider.innerShape
        }

    private var graphicsLayer: GraphicsLayer? = null
    private var atlasPasses: NativeGlassAtlasPasses? = null
    private var atlasProducerLayer: GraphicsLayer? = null
    private var atlasBackingLayer: GraphicsLayer? = null
    private var luminanceSourceLayer: GraphicsLayer? = null
    private var luminanceProbeLayer: GraphicsLayer? = null
    private var luminancePixels: IntArray? = null
    private var luminanceJob: Job? = null
    private var luminanceCapturing = false
    private var luminanceGeneration = 0L
    private var luminanceRevision = 0L
    private var luminanceProbeRevision = 0L
    private var lastLuminanceStartNanos = 0L
    private var sourceObservation: (() -> Unit)? = null
    private var luminanceSourceDirty = false
    private var luminanceLogicalSize = Size.Zero
    private var luminanceLogicalDensity: Density = Density(1f)
    private var luminanceLayoutDirection = LayoutDirection.Ltr
    private val onBackdropSourceChanged: () -> Unit = {
        if (isAttached && luminanceState != null) {
            luminanceRevision++
            luminanceSourceDirty = true
            luminanceProbeLayer?.let { probe -> luminanceState?.let { captureLuminance(it, probe) } }
        }
    }
    private val foregroundClipPath = Path()
    private var foregroundClipRoundRect: RoundRect? = null
    private var completedLayout by mutableStateOf(false)

    private val layoutLayerBlock: GraphicsLayerScope.() -> Unit = {
        // The native background owns its SDF coverage, including the exterior key/fill strip.
        // Clip foreground explicitly below instead of cropping the entire control layer.
        clip = !completedLayout
        shape = shapeProvider.shape
        compositingStrategy = if (completedLayout) androidx.compose.ui.graphics.CompositingStrategy.Auto
            else androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }

    private var layoutCoordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())

    private var padding by mutableFloatStateOf(0f)

    private fun DrawScope.recordSource(applyDrawCallback: Boolean, source: GraphicsLayer? = null) {
        val canvas = drawContext.canvas
        val sourcePadding = padding
        val previousSize = drawContext.size
        val previousDensity = drawContext.density
        val scale = effectScope.samplingScale
        canvas.save()
        try {
            if (effectScope.effectTextureSize != null) {
                canvas.clipRect(0f, 0f, effectScope.size.width + sourcePadding * 2f,
                    effectScope.size.height + sourcePadding * 2f)
            }
            // Public backdrop callbacks and coordinate transforms always operate in logical pixels.
            // Only the recording canvas and the effect scope use reduced texture pixels.
            drawContext.size = effectScope.fullSize
            drawContext.density = effectScope.sourceDensity
            if (sourcePadding != 0f) canvas.translate(sourcePadding, sourcePadding)
            if (scale != 1f) canvas.scale(scale, scale)
            if (applyDrawCallback) onDrawBackdrop { drawSource(source) }
            else drawSource(source)
        } finally {
            drawContext.size = previousSize
            drawContext.density = previousDensity
            canvas.restore()
        }
        if (applyDrawCallback) recordAuxiliarySource()
    }

    private fun DrawScope.recordAuxiliarySource() {
        val auxiliary = effectScope.auxiliaryBackdrop ?: return
        val sourcePadding = padding
        val width = ceil(effectScope.size.width + sourcePadding * 2f)
        val height = ceil(effectScope.size.height + sourcePadding * 2f)
        val canvas = drawContext.canvas
        val previousSize = drawContext.size
        val previousDensity = drawContext.density
        canvas.save()
        try {
            canvas.clipRect(0f, height, width, height * 2f)
            canvas.translate(sourcePadding, height + sourcePadding)
            val scale = effectScope.samplingScale
            if (scale != 1f) canvas.scale(scale, scale)
            drawContext.size = effectScope.fullSize
            drawContext.density = effectScope.sourceDensity
            with(auxiliary) {
                drawBackdrop(density = effectScope.sourceDensity, coordinates = coordinatesForSource(auxiliary), layerBlock = layerBlock)
            }
        } finally {
            drawContext.size = previousSize
            drawContext.density = previousDensity
            canvas.restore()
        }
    }

    private fun coordinatesForSource(source: Backdrop): LayoutCoordinates? {
        val defaultDrawing = luminanceState == null && !luminanceOnly && sampleBounds == null &&
            exportedBackdrop == null && layerBlock == null && samplingScale === FullResolutionSampling &&
            onDrawBackdrop === DefaultOnDrawBackdrop && onDrawBehind == null &&
            onDrawSurface == null && onDrawFront == null
        return if (defaultDrawing && source.hasBuiltInCoordinateIndependentSource() && !source.isCoordinatesDependent) {
            // Keep the actual argument, suppressing only an irrelevant position subscription.
            // The source callback and every other captured state read remain observed.
            androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { layoutCoordinates }
        } else layoutCoordinates
    }

    private fun DrawScope.drawSource(source: GraphicsLayer?) {
        if (source == null) {
            with(backdrop) {
                drawBackdrop(density = effectScope.sourceDensity, coordinates = coordinatesForSource(backdrop), layerBlock = layerBlock)
            }
        } else {
            val canvas = drawContext.canvas
            val scale = effectScope.samplingScale
            canvas.save()
            try {
                // The raw luminance source already contains the sampling transform and padding.
                // Undo that baseline inside the callback, preserving any callback-local transform.
                if (scale != 1f) canvas.scale(1f / scale, 1f / scale)
                if (padding != 0f) canvas.translate(-padding, -padding)
                drawLayer(source)
            } finally {
                canvas.restore()
            }
        }
    }

    private val recordBackdropBlock: DrawScope.() -> Unit = { recordSource(applyDrawCallback = true) }
    private val recordLuminanceSourceBlock: DrawScope.() -> Unit = { recordSource(applyDrawCallback = false) }
    private val recordBackdropFromLuminanceBlock: DrawScope.() -> Unit = {
        recordSource(applyDrawCallback = true, source = luminanceSourceLayer)
    }
    private val recordAtlasBackingBlock: DrawScope.() -> Unit = {
        val atlas = effectScope.materializedMipAtlas
        if (atlas?.hasControlPlanes == true) atlasPasses?.drawControlPlanes(this, atlas)
        else atlasProducerLayer?.let { drawLayer(it) }
    }
    private val recordAtlasConsumerBlock: DrawScope.() -> Unit = { atlasBackingLayer?.let { drawLayer(it) } }

    private fun DrawScope.drawRecordedBackdrop() {
        val layer = graphicsLayer ?: return
        val scale = effectScope.samplingScale
        val canvas = drawContext.canvas
        canvas.save()
        try {
            if (effectScope.nativeMaterialCompleted) {
                val origin = effectScope.materialOutputOrigin
                canvas.translate(-origin.x, -origin.y)
                // Runtime image filters report unbounded output. Bound the final draw to
                // its complete recorded consumer, including the atlas sampled by the effect.
                canvas.clipRect(0f, 0f, layer.size.width.toFloat(), layer.size.height.toFloat())
            } else {
                if (scale != 1f) canvas.scale(1f / scale, 1f / scale)
                if (padding != 0f) canvas.translate(-padding, -padding)
            }
            drawLayer(layer)
        } finally {
            canvas.restore()
        }
    }

    private val drawBackdropLayer: DrawScope.() -> Unit = {
        val layer = graphicsLayer
        if (layer != null) {
            val baseSize = IntSize(
                ceil(effectScope.size.width + padding * 2f).toInt().coerceAtLeast(1),
                ceil(effectScope.size.height + padding * 2f).toInt().coerceAtLeast(1)
            )
            val recordingSize = effectScope.effectTextureSize ?: baseSize
            val outputSize = effectScope.materialOutputSize
            val consumerSize = if (outputSize == null) recordingSize else IntSize(
                maxOf(recordingSize.width, outputSize.width), maxOf(recordingSize.height, outputSize.height))
            val source = if (luminanceState != null) {
                luminanceSourceLayer ?: requireGraphicsContext().createGraphicsLayer().also {
                    luminanceSourceLayer = it
                }
            } else null
            if (source != null) {
                recordLayer(this@DrawBackdropNode, source, baseSize, recordLuminanceSourceBlock)
                recordLuminanceProbe(source)
            }
            val producerEffects = effectScope.materializedInputEffects
            val mipAtlas = effectScope.materializedMipAtlas
            if (mipAtlas != null || producerEffects.isNotEmpty()) {
                val context = requireGraphicsContext()
                val passes = atlasPasses ?: NativeGlassAtlasPasses(context).also { atlasPasses = it }
                val previous = if (mipAtlas?.hasControlPlanes == true) {
                    passes.recordControlPlanes(mipAtlas,
                        record = { target, targetSize, block ->
                            recordLayer(this@DrawBackdropNode, target, targetSize, block)
                        },
                        sourceBlock = if (source != null) recordBackdropFromLuminanceBlock else recordBackdropBlock)
                    null
                } else if (mipAtlas != null) passes.recordPyramid(
                    mipAtlas,
                    record = { target, targetSize, block ->
                        recordLayer(this@DrawBackdropNode, target, targetSize, block)
                    },
                    sourceBlock = if (source != null) recordBackdropFromLuminanceBlock else recordBackdropBlock,
                ) else {
                    var inputLayer: GraphicsLayer? = null
                    for (producer in passes.layers(producerEffects)) {
                        val input = inputLayer
                        recordLayer(this@DrawBackdropNode, producer, recordingSize,
                            if (input != null) { { drawLayer(input) } }
                            else if (source != null) recordBackdropFromLuminanceBlock else recordBackdropBlock)
                        inputLayer = producer
                    }
                    inputLayer
                }
                atlasProducerLayer = previous
                // The final layer's raw repaint evaluates the producer over the complete
                // atlas before the final effect is composed into the visible shape.
                if (NativeGlassAtlasBackingCopy) {
                    val backing = atlasBackingLayer ?: context.createGraphicsLayer().also { atlasBackingLayer = it }
                    backing.clip = true
                    backing.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                    backing.renderEffect = null
                    recordLayer(this@DrawBackdropNode, backing, recordingSize, recordAtlasBackingBlock)
                    recordLayer(this@DrawBackdropNode, layer, consumerSize, recordAtlasConsumerBlock)
                } else recordLayer(this@DrawBackdropNode, layer, consumerSize, recordAtlasBackingBlock)
            } else if (source != null) {
                recordLayer(this@DrawBackdropNode, layer, consumerSize, recordBackdropFromLuminanceBlock)
            } else {
                recordLayer(this@DrawBackdropNode, layer, consumerSize, recordBackdropBlock)
            }

            layer.topLeft = IntOffset.Zero
            drawRecordedBackdrop()
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            if (luminanceOnly) placeable.place(IntOffset.Zero)
            else placeable.placeWithLayer(IntOffset.Zero, layerBlock = layoutLayerBlock)
        }
    }

    override fun ContentDrawScope.draw() {
        if (luminanceState != null) {
            luminanceLogicalSize = size
            if (luminanceLogicalDensity.density != density || luminanceLogicalDensity.fontScale != fontScale) {
                luminanceLogicalDensity = Density(density, fontScale)
            }
            luminanceLayoutDirection = layoutDirection
        }
        if (luminanceOnly) {
            recordStandaloneLuminanceProbe()
            drawContent()
            return
        }
        val managed = nativeGlassNeedsColorManagement(requireView())
        val colorSpaceChanged = effectScope.colorManagedNativeGlass != managed
        effectScope.colorManagedNativeGlass = managed
        val headroom = nativeGlassDisplayHeadroom(requireView())
        val headroomChanged = effectScope.nativeGlassHeadroom != headroom
        effectScope.nativeGlassHeadroom = headroom
        if (effectScope.update(this, samplingScale(size, this)) || colorSpaceChanged || headroomChanged) {
            observeEffects()
        }

        onDrawBehind?.invoke(this)
        if (effectScope.nativeMaterialCompleted) {
            drawBackdropLayer()
            withForegroundClip {
                onDrawSurface?.invoke(this)
                this@draw.drawContent()
                onDrawFront?.invoke(this)
            }
        } else {
            drawBackdropLayer()
            onDrawSurface?.invoke(this)
            drawContent()
            onDrawFront?.invoke(this)
        }

        exportedBackdrop?.graphicsLayer?.let { layer ->
            recordLayer(this@DrawBackdropNode, layer) {
                onDrawBehind?.invoke(this)
                // Exports retain full logical dimensions and full-resolution coatings/foreground.
                if (effectScope.nativeMaterialCompleted) {
                    drawRecordedBackdrop()
                    withForegroundClip {
                        onDrawSurface?.invoke(this)
                        onDrawFront?.invoke(this)
                    }
                } else {
                    drawRecordedBackdrop()
                    onDrawSurface?.invoke(this)
                    onDrawFront?.invoke(this)
                }
            }
            exportedBackdrop?.notifySourceRecorded()
        }
    }

    private inline fun DrawScope.withForegroundClip(block: DrawScope.() -> Unit) {
        val canvas = drawContext.canvas
        canvas.save()
        try {
            when (val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)) {
                is Outline.Rectangle -> canvas.clipRect(outline.rect)
                is Outline.Rounded -> {
                    // Preserve stable geometry's path generation so HWUI can reuse its
                    // exact antialiased mask when the drawing transform also permits it.
                    if (foregroundClipRoundRect != outline.roundRect) {
                        foregroundClipPath.rewind()
                        foregroundClipPath.addRoundRect(outline.roundRect)
                        foregroundClipRoundRect = outline.roundRect
                    }
                    canvas.clipPath(foregroundClipPath)
                }
                is Outline.Generic -> canvas.clipPath(outline.path)
            }
            block()
        } finally { canvas.restore() }
    }

    fun updateBackdrop(value: Backdrop) {
        if (backdrop === value) return
        // An old source's in-flight snapshot must not publish into the replacement.
        luminanceGeneration++
        luminanceJob?.cancel()
        luminanceJob = null
        luminanceCapturing = false
        lastLuminanceStartNanos = 0L
        backdrop = value
        observeBackdropSource()
        onBackdropSourceChanged()
    }

    private fun observeBackdropSource() {
        sourceObservation?.invoke()
        sourceObservation = if (isAttached && luminanceState != null)
            backdrop.observeSourceChanges(onBackdropSourceChanged) else null
    }

    fun updateLuminanceState(state: BackdropLuminance?) {
        if (luminanceState === state) return
        luminanceGeneration++
        luminanceJob?.cancel()
        luminanceJob = null
        luminanceCapturing = false
        luminanceState = state
        luminanceSourceDirty = false
        luminanceRevision = 0L
        luminanceProbeRevision = 0L
        lastLuminanceStartNanos = 0L
        observeBackdropSource()
        if (state == null && isAttached) releaseLuminanceLayers()
    }

    private fun DrawScope.recordLuminanceProbe(source: GraphicsLayer) {
        val state = luminanceState ?: return
        val width = effectScope.size.width
        val height = effectScope.size.height
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return
        luminanceRevision++
        if (luminanceCapturing) return

        val probe = luminanceProbeLayer ?: requireGraphicsContext().createGraphicsLayer().also {
            luminanceProbeLayer = it
        }
        val sourcePadding = padding
        recordLayer(this@DrawBackdropNode, probe, IntSize(16, 16)) {
            scale(16f / width, 16f / height, Offset.Zero) {
                translate(-sourcePadding, -sourcePadding) { drawLayer(source) }
            }
        }
        luminanceSourceDirty = false
        luminanceProbeRevision = luminanceRevision
        captureLuminance(state, probe)
    }

    private fun DrawScope.recordStandaloneLuminanceProbe() {
        val state = luminanceState ?: return
        luminanceRevision++
        if (luminanceCapturing) return
        val probe = luminanceProbeLayer ?: requireGraphicsContext().createGraphicsLayer().also {
            luminanceProbeLayer = it
        }
        if (recordRetainedLuminanceProbe(probe)) captureLuminance(state, probe)
    }

    /** Refresh only the retained raw 16x16 probe, independently of visible glass rendering. */
    private fun recordRetainedLuminanceProbe(probe: GraphicsLayer): Boolean {
        val logicalSize = luminanceLogicalSize
        val region = if (luminanceOnly) sampleBounds?.invoke() ?: Rect(Offset.Zero, logicalSize)
            else Rect(Offset.Zero, logicalSize)
        if (!region.left.isFinite() || !region.top.isFinite() ||
            !region.width.isFinite() || !region.height.isFinite() || region.isEmpty) return false
        val logicalDensity = luminanceLogicalDensity
        probe.record(logicalDensity, luminanceLayoutDirection, IntSize(16, 16)) {
            val previousSize = drawContext.size
            val previousDensity = drawContext.density
            val canvas = drawContext.canvas
            canvas.save()
            try {
                canvas.scale(16f / region.width, 16f / region.height)
                canvas.translate(-region.left, -region.top)
                drawContext.size = logicalSize
                drawContext.density = logicalDensity
                with(backdrop) {
                    drawBackdrop(density = logicalDensity, coordinates = layoutCoordinates,
                        layerBlock = if (luminanceOnly) null else this@DrawBackdropNode.layerBlock)
                }
            } finally {
                drawContext.size = previousSize
                drawContext.density = previousDensity
                canvas.restore()
            }
        }
        luminanceSourceDirty = false
        luminanceProbeRevision = luminanceRevision
        return true
    }

    private fun captureLuminance(state: BackdropLuminance, probe: GraphicsLayer) {
        if (luminanceJob != null) return

        val generation = luminanceGeneration
        val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
            var capturedRevision = luminanceProbeRevision
            try {
                val elapsed = System.nanoTime() - lastLuminanceStartNanos
                // Bound readback to 30 Hz, with at most one snapshot in flight.
                val waitNanos = 33_333_333L - elapsed
                if (waitNanos > 0L) delay((waitNanos + 999_999L) / 1_000_000L)
                if (!isAttached || generation != luminanceGeneration || luminanceState !== state) return@launch
                if (luminanceSourceDirty) {
                    capturedRevision = luminanceRevision
                    if (!recordRetainedLuminanceProbe(probe)) return@launch
                }
                luminanceCapturing = true
                capturedRevision = luminanceProbeRevision
                lastLuminanceStartNanos = System.nanoTime()
                val image = probe.toImageBitmap()
                if (!isAttached || generation != luminanceGeneration || luminanceState !== state) return@launch
                val pixels = luminancePixels ?: IntArray(16 * 16).also { luminancePixels = it }
                image.readPixels(pixels)
                var weightedLuminance = 0f
                var totalAlpha = 0f
                for (pixel in pixels) {
                    val alpha = ((pixel ushr 24) and 255) / 255f
                    val red = ((pixel ushr 16) and 255) / 255f
                    val green = ((pixel ushr 8) and 255) / 255f
                    val blue = (pixel and 255) / 255f
                    weightedLuminance += alpha * (0.2126f * red + 0.7152f * green + 0.0722f * blue)
                    totalAlpha += alpha
                }
                if (totalAlpha > 0f) state.publish(weightedLuminance / totalAlpha)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A missing GPU snapshot retains the last completed region sample.
            } finally {
                if (generation == luminanceGeneration) {
                    luminanceCapturing = false
                    luminanceJob = null
                    if (isAttached && luminanceState === state && luminanceRevision > capturedRevision) {
                        // Source recording and readback do not request another window frame.
                        // Coalesce changes received in flight into the next budgeted small probe.
                        luminanceSourceDirty = true
                        captureLuminance(state, probe)
                    }
                }
            }
        }
        luminanceJob = job
        job.start()
    }

    private fun releaseLuminanceLayers() {
        val graphicsContext = requireGraphicsContext()
        luminanceSourceLayer?.let(graphicsContext::releaseGraphicsLayer)
        luminanceProbeLayer?.let(graphicsContext::releaseGraphicsLayer)
        luminanceSourceLayer = null
        luminanceProbeLayer = null
        luminancePixels = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            // An effect may attach a coordinate-dependent portal after this layout pass,
            // even when its primary source is an empty or coordinate-free backdrop.
            layoutCoordinates = coordinates
            exportedBackdrop?.layerCoordinates = coordinates
        }
    }

    override fun onObservedReadsChanged() {
        invalidateDrawCache()
    }

    fun invalidateDrawCache() {
        observeEffects()
    }

    private fun observeEffects() {
        observeReads { updateEffects() }
    }

    private fun updateEffects() {
        if (!isRenderEffectSupported()) return
        if (!effectScope.size.width.isFinite() || !effectScope.size.height.isFinite()) return

        effectScope.apply(effects)
        completedLayout = effectScope.nativeMaterialCompleted
        graphicsLayer?.renderEffect = effectScope.renderEffect
        padding = effectScope.padding
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        graphicsLayer = graphicsContext.createGraphicsLayer()
        observeBackdropSource()

        observeEffects()
    }

    override fun onDetach() {
        sourceObservation?.invoke()
        sourceObservation = null
        luminanceGeneration++
        luminanceJob?.cancel()
        luminanceJob = null
        luminanceCapturing = false
        releaseLuminanceLayers()
        val graphicsContext = requireGraphicsContext()
        graphicsLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            graphicsLayer = null
        }
        atlasPasses?.release()
        atlasPasses = null
        atlasBackingLayer?.let(graphicsContext::releaseGraphicsLayer)
        atlasProducerLayer = null
        atlasBackingLayer = null

        effectScope.reset()
        completedLayout = false
        layoutCoordinates = null
        exportedBackdrop?.clearRecordingCoordinates()
    }
}
