/*
 * Copyright 2025 Kyant
 * Copyright 2026 Glasmio contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Adapted from AndroidLiquidGlass 2.0.1 generic capture/effect infrastructure.
 * Modified for Glasmio's owned namespace and Android material renderer.
 */

package io.github.neoruaa.glasmio.glass.render.backdrops

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.unit.Density
import io.github.neoruaa.glasmio.glass.render.Backdrop

private val DefaultOnDraw: ContentDrawScope.() -> Unit = { drawContent() }

@Composable
fun rememberLayerBackdrop(
    graphicsLayer: GraphicsLayer = rememberGraphicsLayer(),
    onDraw: ContentDrawScope.() -> Unit = DefaultOnDraw
): LayerBackdrop {
    return remember(graphicsLayer, onDraw) {
        LayerBackdrop(graphicsLayer, onDraw)
    }
}

@Composable
internal fun rememberBoundedLayerBackdrop(): LayerBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { LayerBackdrop(layer, DefaultOnDraw, clipSampleToBounds = true) }
}

@Stable
class LayerBackdrop @JvmOverloads internal constructor(
    val graphicsLayer: GraphicsLayer,
    internal val onDraw: ContentDrawScope.() -> Unit,
    private val clipSampleToBounds: Boolean = false,
) : Backdrop {

    internal val recordsContent: Boolean = onDraw === DefaultOnDraw

    override val isCoordinatesDependent: Boolean = true

    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)

    /**
     * Set only by owners that record nothing but one color filling the layer bounds. Every
     * consumer region inside those bounds then samples identical pixels at any translation.
     */
    internal var fillsBoundsUniformly: Boolean = false

    private val sourceToConsumer = Matrix()
    private val screenToConsumer = Matrix()
    private val sourceObservers = LinkedHashSet<() -> Unit>()
    private var sourceObserverSnapshot = emptyList<() -> Unit>()

    override fun observeSourceChanges(observer: () -> Unit): () -> Unit {
        if (sourceObservers.add(observer)) sourceObserverSnapshot = sourceObservers.toList()
        return {
            if (sourceObservers.remove(observer)) sourceObserverSnapshot = sourceObservers.toList()
        }
    }

    internal fun notifySourceRecorded() {
        // An observer can detach while handling a source change.
        // Rebuild the snapshot on membership changes instead of every recorded frame.
        sourceObserverSnapshot.forEach { it() }
    }

    internal fun clearRecordingCoordinates() {
        layerCoordinates = null
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val coordinates = coordinates?.takeIf { it.isAttached } ?: return
        val layerCoordinates = layerCoordinates?.takeIf { it.isAttached } ?: return
        if (!updateTransform(layerCoordinates, coordinates)) return
        withTransform({
            // Coordinates include the actual consumer graphicsLayer, including layerBlock.
            // Applying its manual inverse again would double the press scale/rotation.
            transform(sourceToConsumer)
        }) {
            if (clipSampleToBounds) {
                clipRect(0f, 0f, layerCoordinates.size.width.toFloat(), layerCoordinates.size.height.toFloat()) {
                    drawLayer(graphicsLayer)
                }
            } else drawLayer(graphicsLayer)
        }
    }

    internal fun boundedSamples(consumer: LayoutCoordinates): Rect? {
        if (!clipSampleToBounds || !consumer.isAttached) return null
        val recorded = layerCoordinates?.takeIf { it.isAttached } ?: return null
        if (!updateTransform(recorded, consumer) || !sourceToConsumer.isUnitTranslation()) return null
        return sourceToConsumer.map(Rect(0f, 0f, recorded.size.width.toFloat(), recorded.size.height.toFloat()))
    }

    private fun updateTransform(source: LayoutCoordinates, consumer: LayoutCoordinates): Boolean {
        sourceToConsumer.reset()
        if (source.findRootCoordinates() === consumer.findRootCoordinates()) {
            try {
                consumer.transformFrom(source, sourceToConsumer)
                return sourceToConsumer.values.all { it.isFinite() }
            } catch (_: Exception) {
                // Detached or custom coordinates may not implement transformFrom.
            }
        }
        return try {
            // A full screen transform preserves independent window/ancestor scale and rotation.
            sourceToConsumer.reset()
            source.transformToScreen(sourceToConsumer)
            screenToConsumer.reset()
            consumer.transformToScreen(screenToConsumer)
            screenToConsumer.invert()
            sourceToConsumer *= screenToConsumer
            sourceToConsumer.values.all { it.isFinite() }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Describes what [drawBackdrop] records for [consumer] within [region] (consumer pixels).
     * Equal signatures record identical pixels; null means the recording cannot be described.
     */
    internal fun recordingSignature(consumer: LayoutCoordinates, region: Rect): Any? {
        if (!consumer.isAttached) return null
        val source = layerCoordinates?.takeIf { it.isAttached } ?: return null
        if (!updateTransform(source, consumer)) return null
        val values = sourceToConsumer.values
        if (fillsBoundsUniformly && values.isAxisAligned()) {
            // A uniform fill records only the color over its bounds. Within the region, the result
            // is fully described by where those bounds fall: identical edges give identical
            // coverage, and edges outside the region do not reach the recorded pixels.
            val bounds = sourceToConsumer.map(Rect(0f, 0f, source.size.width.toFloat(), source.size.height.toFloat()))
            val left = bounds.left.takeIf { it > region.left }
            val top = bounds.top.takeIf { it > region.top }
            val right = bounds.right.takeIf { it < region.right }
            val bottom = bounds.bottom.takeIf { it < region.bottom }
            return UniformFillSignature(this, left, top, right, bottom)
        }
        return TransformSignature(source, values.copyOf())
    }
}

internal fun Matrix.isUnitTranslation(): Boolean {
    val matrix = values
    return matrix.all { it.isFinite() } && matrix[0] == 1f && matrix[5] == 1f &&
        matrix[10] == 1f && matrix[14] == 0f && matrix.isAxisAligned()
}

private fun FloatArray.isAxisAligned(): Boolean =
    this[1] == 0f && this[2] == 0f && this[3] == 0f && this[4] == 0f && this[6] == 0f &&
        this[7] == 0f && this[8] == 0f && this[9] == 0f && this[11] == 0f && this[15] == 1f

/** Fill edges that lie inside the consumer region, in consumer pixels; null edges lie outside. */
private data class UniformFillSignature(
    val backdrop: LayerBackdrop,
    val left: Float?,
    val top: Float?,
    val right: Float?,
    val bottom: Float?,
)

private class TransformSignature(val source: LayoutCoordinates, val values: FloatArray) {
    override fun equals(other: Any?): Boolean =
        other is TransformSignature && other.source === source && other.values.contentEquals(values)

    override fun hashCode(): Int = System.identityHashCode(source) * 31 + values.contentHashCode()
}
