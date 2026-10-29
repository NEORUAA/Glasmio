package io.github.neoruaa.glasmio.glass

import kotlin.math.max
import kotlin.math.min

/** Native control/panel measurements expressed in points before host-density conversion. */
internal data class NavigationGlassGeometryProfile(
    val blurRadius: Float,
    val innerHeight: Float,
    val innerAmount: Float,
    val outerHeight: Float,
    val outerAmount: Float,
    val captureScale: Float,
    val mainBlurDistance0: Float,
    val mainBlurAlpha0: Float,
    val mainBlurAlpha1: Float,
)

/**
 * Bounded interpolation through the measured 50, 150 and 200 pt UIKit/SwiftUI specimens.
 * This is a portable calibration, not a claim that these expressions are Apple's private code.
 */
internal fun navigationGlassGeometryProfile(
    minimumDimension: Float,
    material: NavigationGlassMaterial? = null,
): NavigationGlassGeometryProfile {
    val extent = minimumDimension.takeIf { it.isFinite() && it > 0f } ?: 50f
    val scale = navigationGlassCaptureScale(extent)
    return if (material == NavigationGlassMaterial.Clear) {
        // Native refraction opacity gates only the second outer ray. Clear's inner ray
        // remains active at opacity zero, matching its observed magnified edge.
        NavigationGlassGeometryProfile(.75f, 18f, 32.5f, 6.25f, 10f, scale, -extent * .5f, 1f, .5f)
    } else {
        // The bounded 64..160 calibration matches all measured 50/150/200 pt specimens;
        // it is shared with the native shadow profile, not a recovered CPU chooser.
        val sizeProgress = navigationGlassSizeProgress(extent)
        NavigationGlassGeometryProfile(
            5f, min(extent * .25f, 20f), min(extent * .5f, 60f),
            max(16f, extent * .2f), max(16f, extent * .25f), scale,
            -extent * .5f, .8f * sizeProgress, .4f * sizeProgress,
        )
    }
}

/** The native minimum-distance size gate corresponds to half the shorter rectangular edge. */
internal fun navigationGlassUsesLargeFace(minimumDimension: Float): Boolean = minimumDimension > 128f

internal fun navigationGlassSizeProgress(minimumDimension: Float): Float =
    ((minimumDimension.takeIf { it.isFinite() } ?: 50f) - 64f).div(96f).coerceIn(0f, 1f)

/** Morph interpolation is separate from the measured endpoint profile construction. */
internal fun navigationGlassMorphProfile(
    minimumDimension: Float,
    source: NavigationGlassMaterial?,
    target: NavigationGlassMaterial?,
    progress: Float,
): NavigationGlassGeometryProfile {
    val from = navigationGlassGeometryProfile(minimumDimension, source)
    val to = navigationGlassGeometryProfile(minimumDimension, target)
    val t = progress.coerceIn(0f, 1f)
    fun mix(a: Float, b: Float) = a + (b - a) * t
    return NavigationGlassGeometryProfile(mix(from.blurRadius, to.blurRadius), mix(from.innerHeight, to.innerHeight),
        mix(from.innerAmount, to.innerAmount), mix(from.outerHeight, to.outerHeight), mix(from.outerAmount, to.outerAmount),
        mix(from.captureScale, to.captureScale), mix(from.mainBlurDistance0, to.mainBlurDistance0),
        mix(from.mainBlurAlpha0, to.mainBlurAlpha0), mix(from.mainBlurAlpha1, to.mainBlurAlpha1))
}

/** Scalar policy used by the capture node without allocating a profile on each frame. */
internal fun navigationGlassCaptureScale(minimumDimension: Float): Float =
    if (minimumDimension.isFinite() && minimumDimension > 0f) (50f / minimumDimension).coerceIn(.25f, .5f) else .5f
