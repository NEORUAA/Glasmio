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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.hasBuiltInGlassLayerBlockIndependentSource

@Composable
fun rememberCombinedBackdrop(
    backdrop1: Backdrop,
    backdrop2: Backdrop
): Backdrop {
    return remember(backdrop1, backdrop2) {
        Combined2Backdrops(backdrop1, backdrop2)
    }
}

@Composable
fun rememberCombinedBackdrop(
    backdrop1: Backdrop,
    backdrop2: Backdrop,
    backdrop3: Backdrop
): Backdrop {
    return remember(backdrop1, backdrop2, backdrop3) {
        Combined3Backdrops(backdrop1, backdrop2, backdrop3)
    }
}

@Composable
fun rememberCombinedBackdrop(vararg backdrops: Backdrop): Backdrop {
    return remember(*backdrops) {
        CombinedBackdrops(*backdrops)
    }
}

/** These captures use actual coordinates without interpreting the presentation layer block. */
internal fun Backdrop.hasBuiltInLayerBlockIndependentSource(): Boolean = when (this) {
    is LayerBackdrop -> true
    is Combined2Backdrops -> hasLayerBlockIndependentSources()
    is Combined3Backdrops -> hasLayerBlockIndependentSources()
    is CombinedBackdrops -> hasLayerBlockIndependentSources()
    else -> hasBuiltInCoordinateIndependentSource() || hasBuiltInGlassLayerBlockIndependentSource()
}

@Immutable
private class Combined2Backdrops(
    val backdrop1: Backdrop,
    val backdrop2: Backdrop
) : Backdrop {

    internal fun hasLayerBlockIndependentSources(): Boolean =
        backdrop1.hasBuiltInLayerBlockIndependentSource() && backdrop2.hasBuiltInLayerBlockIndependentSource()

    override val isCoordinatesDependent: Boolean =
        backdrop1.isCoordinatesDependent || backdrop2.isCoordinatesDependent

    override fun observeSourceChanges(observer: () -> Unit): () -> Unit {
        val first = backdrop1.observeSourceChanges(observer)
        val second = backdrop2.observeSourceChanges(observer)
        return { first(); second() }
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        with(backdrop1) { drawBackdrop(density, coordinates, layerBlock) }
        with(backdrop2) { drawBackdrop(density, coordinates, layerBlock) }
    }
}

@Immutable
private class Combined3Backdrops(
    val backdrop1: Backdrop,
    val backdrop2: Backdrop,
    val backdrop3: Backdrop
) : Backdrop {

    internal fun hasLayerBlockIndependentSources(): Boolean =
        backdrop1.hasBuiltInLayerBlockIndependentSource() && backdrop2.hasBuiltInLayerBlockIndependentSource() &&
            backdrop3.hasBuiltInLayerBlockIndependentSource()

    override val isCoordinatesDependent: Boolean =
        backdrop1.isCoordinatesDependent ||
                backdrop2.isCoordinatesDependent ||
                backdrop3.isCoordinatesDependent

    override fun observeSourceChanges(observer: () -> Unit): () -> Unit {
        val first = backdrop1.observeSourceChanges(observer)
        val second = backdrop2.observeSourceChanges(observer)
        val third = backdrop3.observeSourceChanges(observer)
        return { first(); second(); third() }
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        with(backdrop1) { drawBackdrop(density, coordinates, layerBlock) }
        with(backdrop2) { drawBackdrop(density, coordinates, layerBlock) }
        with(backdrop3) { drawBackdrop(density, coordinates, layerBlock) }
    }
}

@Immutable
private class CombinedBackdrops(
    vararg val backdrops: Backdrop
) : Backdrop {

    internal fun hasLayerBlockIndependentSources(): Boolean =
        backdrops.all { it.hasBuiltInLayerBlockIndependentSource() }

    override val isCoordinatesDependent: Boolean =
        backdrops.any { it.isCoordinatesDependent }

    override fun observeSourceChanges(observer: () -> Unit): () -> Unit {
        val observations = backdrops.map { it.observeSourceChanges(observer) }
        return { observations.forEach { it() } }
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        backdrops.forEach { backdrop ->
            with(backdrop) { drawBackdrop(density, coordinates, layerBlock) }
        }
    }
}

/**
 * Describes the position-dependent part of what this source records for [consumer] within
 * [region] (consumer pixels). Equal signatures record identical pixels, so a moved consumer can
 * keep its previous recording. Null means unknown: the consumer must record again.
 */
internal fun Backdrop.recordingSignature(consumer: LayoutCoordinates, region: Rect): Any? {
    return when (this) {
        is LayerBackdrop -> recordingSignature(consumer, region)
        is Combined2Backdrops -> listOf(
            backdrop1.recordingSignature(consumer, region) ?: return null,
            backdrop2.recordingSignature(consumer, region) ?: return null,
        )
        is Combined3Backdrops -> listOf(
            backdrop1.recordingSignature(consumer, region) ?: return null,
            backdrop2.recordingSignature(consumer, region) ?: return null,
            backdrop3.recordingSignature(consumer, region) ?: return null,
        )
        is CombinedBackdrops -> backdrops.map { it.recordingSignature(consumer, region) ?: return null }
        // These draw without reading any coordinates.
        else -> if (hasBuiltInCoordinateIndependentSource()) PositionIndependentSource else null
    }
}

private object PositionIndependentSource
