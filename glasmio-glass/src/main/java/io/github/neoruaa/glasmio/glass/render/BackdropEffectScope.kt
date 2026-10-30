/*
 * Copyright 2025 Kyant
 * Copyright 2026 Glasmio contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Adapted from AndroidLiquidGlass 2.0.1 generic capture/effect infrastructure.
 * Modified for Glasmio's owned namespace and Android material renderer.
 */

package io.github.neoruaa.glasmio.glass.render

import io.github.neoruaa.glasmio.glass.NativeGlassMipAtlas
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize

interface BackdropEffectScope : Density, RuntimeShaderCache {

    /** Effect-texture pixels; density scales with the texture so dp geometry stays consistent. */
    val size: Size

    val layoutDirection: LayoutDirection

    val shape: Shape

    var padding: Float

    var renderEffect: RenderEffect?
}

internal abstract class BackdropEffectScopeImpl : BackdropEffectScope, RuntimeShaderCache {

    /** Only a library-owned draw node can opt into fully configured consumer snapshots. */
    internal open val supportsNativeControlLensConsumerSharing: Boolean get() = false
    internal open val supportsNativeControlLensPlaneStages: Boolean get() = false
    internal open val supportsNativeGlassHardwareLinearSampling: Boolean get() = false

    override var density: Float = 1f
    override var fontScale: Float = 1f
    override var size: Size = Size.Unspecified
    override var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    override var padding: Float = 0f
    override var renderEffect: RenderEffect? = null

    /** Atlas storage is independent of the padded base capture and its geometric origin. */
    internal var effectTextureSize: IntSize? = null
    internal var materializedInputEffects: List<RenderEffect> = emptyList()
    internal var materializedMipAtlas: NativeGlassMipAtlas? = null
    internal var colorManagedNativeGlass: Boolean = false
    internal var nativeGlassHeadroom: Float = 1f
    /** Final material pixels use the device raster, independently of the sampled atlas. */
    internal var materialOutputSize: IntSize? = null
    internal var materialOutputOrigin: Offset = Offset.Zero
    internal var nativeMaterialCompleted: Boolean = false
    /** Optional unfiltered portal/content plane, stored below the natural backdrop plane. */
    internal var auxiliaryBackdrop: Backdrop? = null

    var fullSize: Size = Size.Unspecified
        private set
    var samplingScale: Float = 1f
        private set
    private var originalDensity: Float = 1f
    val sourceDensity: Density = object : Density {
        override val density: Float get() = originalDensity
        override val fontScale: Float get() = this@BackdropEffectScopeImpl.fontScale
    }

    private val runtimeShaderCache = RuntimeShaderCacheImpl()
    private val resources = mutableMapOf<String, Any>()

    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> cachedResource(key: String, create: () -> T): T =
        resources.getOrPut(key, create) as T

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return runtimeShaderCache.obtainRuntimeShader(key, string)
    }

    fun update(scope: DrawScope, samplingScale: Float = 1f): Boolean {
        val newScale = resolveBackdropSamplingScale(samplingScale)
        val newDensity = scope.density * newScale
        val newFontScale = scope.fontScale
        val newFullSize = scope.size
        val newSize = Size(newFullSize.width * newScale, newFullSize.height * newScale)
        val newLayoutDirection = scope.layoutDirection

        val changed = newDensity != density ||
                newFontScale != fontScale ||
                newSize != size ||
                newFullSize != fullSize ||
                newScale != this.samplingScale ||
                scope.density != originalDensity ||
                newLayoutDirection != layoutDirection

        if (changed) {
            density = newDensity
            fontScale = newFontScale
            size = newSize
            fullSize = newFullSize
            this.samplingScale = newScale
            originalDensity = scope.density
            layoutDirection = newLayoutDirection
        }

        return changed
    }

    fun apply(effects: BackdropEffectScope.() -> Unit) {
        padding = 0f
        renderEffect = null
        effectTextureSize = null
        materializedInputEffects = emptyList()
        materializedMipAtlas = null
        materialOutputSize = null
        materialOutputOrigin = Offset.Zero
        nativeMaterialCompleted = false
        auxiliaryBackdrop = null
        effects()
    }

    fun reset() {
        density = 1f
        fontScale = 1f
        size = Size.Unspecified
        fullSize = Size.Unspecified
        samplingScale = 1f
        originalDensity = 1f
        layoutDirection = LayoutDirection.Ltr
        padding = 0f
        renderEffect = null
        effectTextureSize = null
        materializedInputEffects = emptyList()
        materializedMipAtlas = null
        materialOutputSize = null
        materialOutputOrigin = Offset.Zero
        nativeMaterialCompleted = false
        auxiliaryBackdrop = null
        resources.clear()
        colorManagedNativeGlass = false
        nativeGlassHeadroom = 1f
        runtimeShaderCache.clear()
    }
}

/** Custom effect scopes may opt out of caching without a global lifetime registry. */
internal fun <T : Any> BackdropEffectScope.cachedResource(key: String, create: () -> T): T =
    (this as? BackdropEffectScopeImpl)?.cachedResource(key, create) ?: create()

/** Default SDR is already gamma sRGB; wide/HDR windows need explicit native-space sampling. */
internal fun nativeGlassNeedsColorManagement(view: View): Boolean {
    val mode = (view.rootView.layoutParams as? WindowManager.LayoutParams)?.colorMode
    return mode == ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT || mode == ActivityInfo.COLOR_MODE_HDR
}

/** The recovered optical chooser saturates at 1.2; public output state retains the raw ratio. */
internal fun nativeGlassDisplayHeadroom(view: View): Float {
    val mode = (view.rootView.layoutParams as? WindowManager.LayoutParams)?.colorMode
    val display = view.display
    return if (mode == ActivityInfo.COLOR_MODE_HDR && Build.VERSION.SDK_INT >= 34 &&
        display != null && display.isHdrSdrRatioAvailable) {
        nativeGlassOpticalHeadroom(display.hdrSdrRatio)
    } else 1f
}

/** Ignore display-ratio motion above the native key/fill chooser's final control point. */
internal fun nativeGlassOpticalHeadroom(ratio: Float): Float =
    if (ratio.isFinite()) ratio.coerceIn(1f, 1.2f) else 1f
