/*
 * Copyright 2025 Kyant
 * Copyright 2026 Glasmio contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Adapted from AndroidLiquidGlass 2.0.1 generic capture/effect infrastructure.
 * Modified for Glasmio's owned namespace and Android material renderer.
 */

package io.github.neoruaa.glasmio.glass.render.backdrops

import androidx.annotation.RestrictTo
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize
import io.github.neoruaa.glasmio.glass.render.internal.recordLayer
import java.util.IdentityHashMap
import kotlin.math.ceil

// A later sibling may record the same public layer again before HWUI replays it.
// Keep direct visible drawing for all attached owners of a shared capture layer.
private val CaptureOwners = IdentityHashMap<GraphicsLayer, MutableSet<LayerBackdropNode>>()

fun Modifier.layerBackdrop(backdrop: LayerBackdrop): Modifier =
    layerBackdrop(backdrop, reuseContent = false, maxCaptureHeight = Dp.Unspecified)

/**
 * [reuseContent] records once and replays the captured content on screen. Opt in only when
 * content draws identically in the visible and capture scopes, including graphicsLayer reads.
 */
fun Modifier.layerBackdrop(backdrop: LayerBackdrop, reuseContent: Boolean): Modifier =
    layerBackdrop(backdrop, reuseContent = reuseContent, maxCaptureHeight = Dp.Unspecified)

internal fun Modifier.layerBackdrop(
    backdrop: LayerBackdrop,
    reuseContent: Boolean,
    maxCaptureHeight: Dp,
): Modifier = this then LayerBackdropElement(backdrop, reuseContent, captureEnabled = true, maxCaptureHeight = maxCaptureHeight)

/** Keep source coordinates attached while its owner has no visible or external consumer. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
fun Modifier.conditionalLayerBackdrop(
    backdrop: LayerBackdrop,
    captureEnabled: Boolean,
    maxCaptureHeight: Dp = Dp.Unspecified,
): Modifier = this then LayerBackdropElement(
    backdrop,
    reuseContent = false,
    captureEnabled = captureEnabled,
    maxCaptureHeight = maxCaptureHeight,
)

private class LayerBackdropElement(
    val backdrop: LayerBackdrop,
    val reuseContent: Boolean,
    val captureEnabled: Boolean,
    val maxCaptureHeight: Dp,
) : ModifierNodeElement<LayerBackdropNode>() {

    override fun create(): LayerBackdropNode {
        return LayerBackdropNode(backdrop, reuseContent, captureEnabled, maxCaptureHeight)
    }

    override fun update(node: LayerBackdropNode) {
        node.updateBackdrop(backdrop)
        node.reuseContent = reuseContent
        node.captureEnabled = captureEnabled
        node.maxCaptureHeight = maxCaptureHeight
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "layerBackdrop"
        properties["backdrop"] = backdrop
        properties["reuseContent"] = reuseContent
        properties["captureEnabled"] = captureEnabled
        properties["maxCaptureHeight"] = maxCaptureHeight
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayerBackdropElement) return false

        if (backdrop != other.backdrop) return false
        if (reuseContent != other.reuseContent || captureEnabled != other.captureEnabled || maxCaptureHeight != other.maxCaptureHeight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = 31 * backdrop.hashCode() + reuseContent.hashCode()
        result = 31 * result + captureEnabled.hashCode()
        result = 31 * result + maxCaptureHeight.hashCode()
        return result
    }
}

private class LayerBackdropNode(
    var backdrop: LayerBackdrop,
    var reuseContent: Boolean,
    var captureEnabled: Boolean,
    var maxCaptureHeight: Dp,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    private var registeredLayer: GraphicsLayer? = null

    fun updateBackdrop(value: LayerBackdrop) {
        if (backdrop === value) return
        unregisterCapture()
        backdrop.clearRecordingCoordinates()
        backdrop = value
        if (isAttached) registerCapture()
    }

    private fun registerCapture() {
        val layer = backdrop.graphicsLayer
        registeredLayer = layer
        val owners = CaptureOwners.getOrPut(layer) { LinkedHashSet() }
        owners.add(this)
        owners.forEach { it.invalidateDraw() }
    }

    private fun unregisterCapture() {
        val layer = registeredLayer ?: return
        registeredLayer = null
        val owners = CaptureOwners[layer] ?: return
        owners.remove(this)
        if (owners.isEmpty()) CaptureOwners.remove(layer)
        else owners.forEach { it.invalidateDraw() }
    }

    override fun onAttach() {
        registerCapture()
    }

    override fun ContentDrawScope.draw() {
        if (!captureEnabled) {
            drawContent()
            return
        }
        val layer = backdrop.graphicsLayer
        if (maxCaptureHeight.value.isFinite()) {
            drawContent()
            val full = size.toIntSize()
            if (full.width > 0 && full.height > 0) {
                val maxHeightPx = ceil(maxCaptureHeight.toPx()).toInt().coerceAtLeast(1)
                val captureSize = IntSize(full.width, minOf(full.height, maxHeightPx))
                layer.clip = true
                recordLayer(this@LayerBackdropNode, layer, captureSize) {
                    clipRect(0f, 0f, captureSize.width.toFloat(), captureSize.height.toFloat()) {
                        backdrop.onDraw(this@draw)
                    }
                }
            }
        } else if (reuseContent && backdrop.recordsContent && CaptureOwners[layer]?.size == 1 && layer.preservesContent()) {
            // Record once, then replay the same live display list on screen. Calling
            // drawContent before recording repeats all nested backdrop/effect recording.
            recordLayer(this@LayerBackdropNode, layer) { backdrop.onDraw(this@draw) }
            // Drawing callbacks can mutate this public layer during recording. Such a
            // capture must still leave the visible content independent of its properties.
            if (CaptureOwners[layer]?.size == 1 && layer.preservesContent()) drawLayer(layer) else drawContent()
        } else {
            // Custom captures and externally transformed layers are independent of the
            // visible content. Preserve their original direct-draw/capture behavior.
            layer.clip = false
            drawContent()
            recordLayer(this@LayerBackdropNode, layer) { backdrop.onDraw(this@draw) }
        }
        backdrop.notifySourceRecorded()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.layerCoordinates = coordinates
            backdrop.notifySourceRecorded()
        }
    }

    override fun onDetach() {
        unregisterCapture()
        backdrop.clearRecordingCoordinates()
    }
}

/** A plain display-list boundary neither isolates blending nor clips overflow. */
private fun GraphicsLayer.preservesContent(): Boolean =
    compositingStrategy == CompositingStrategy.Auto && !clip &&
        renderEffect == null && colorFilter == null && blendMode == BlendMode.SrcOver &&
        alpha == 1f && shadowElevation == 0f && topLeft == IntOffset.Zero &&
        scaleX == 1f && scaleY == 1f && translationX == 0f && translationY == 0f &&
        rotationX == 0f && rotationY == 0f && rotationZ == 0f
