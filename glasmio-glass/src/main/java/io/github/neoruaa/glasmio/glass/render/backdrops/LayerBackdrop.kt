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
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
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

@Stable
class LayerBackdrop internal constructor(
    val graphicsLayer: GraphicsLayer,
    internal val onDraw: ContentDrawScope.() -> Unit
) : Backdrop {

    internal val recordsContent: Boolean = onDraw === DefaultOnDraw

    override val isCoordinatesDependent: Boolean = true

    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)

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
            drawLayer(graphicsLayer)
        }
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
}
