package io.github.neoruaa.glasmio.glass.render

import android.content.pm.ActivityInfo
import android.os.Build
import android.view.Display
import android.view.Window

/** A window request and display report, rather than proof of emitted HDR luminance. */
data class GlassDynamicRangeState(
    val hdrRequested: Boolean,
    val displaySupportsHdr: Boolean,
    /** Requested linear HDR/SDR luminance ratio; null when this platform cannot constrain it. */
    val requestedHeadroom: Float?,
    /** Current display report; null when unavailable. This can change with ambient conditions. */
    val availableHeadroom: Float?,
)

/**
 * Applies a host-owned HDR policy before the host builds its material layers.
 *
 * [desiredHeadroom] is a linear luminance ratio, not an sRGB channel value. No material gain is
 * selected here. A host opts in after its complete shader pipeline handles the destination color
 * space, and restores its previous window policy if this request is temporary. API 33-34 can
 * request HDR but cannot constrain the window's headroom with the API 35 control.
 * Zero selects Android's automatic policy. The native source clamp is not a bound on the
 * complete highlight result, because its recovered mask may exceed one before source-over.
 */
fun configureGlassDynamicRange(
    window: Window,
    hdrEnabled: Boolean,
    desiredHeadroom: Float = 0f,
): GlassDynamicRangeState {
    require(desiredHeadroom.isFinite() && (desiredHeadroom == 0f || desiredHeadroom in 1f..10_000f)) {
        "Desired HDR headroom must be zero (automatic) or finite and in [1, 10000]."
    }
    val display = window.glassDisplay()
    val requestHdr = hdrEnabled && display?.isHdr == true
    window.colorMode = if (requestHdr) ActivityInfo.COLOR_MODE_HDR else ActivityInfo.COLOR_MODE_DEFAULT
    if (Build.VERSION.SDK_INT >= 35) {
        window.desiredHdrHeadroom = if (requestHdr) desiredHeadroom else 1f
    }
    return readGlassDynamicRange(window)
}

/** Read again after a display HDR/SDR-ratio callback; a requested mode is not a negotiated mode. */
fun readGlassDynamicRange(window: Window): GlassDynamicRangeState {
    val display = window.glassDisplay()
    return GlassDynamicRangeState(
        hdrRequested = window.colorMode == ActivityInfo.COLOR_MODE_HDR,
        displaySupportsHdr = display?.isHdr == true,
        requestedHeadroom = if (Build.VERSION.SDK_INT >= 35) window.desiredHdrHeadroom else null,
        availableHeadroom = display?.availableGlassHeadroom(),
    )
}

/** Activity display context is available before its decor attaches during onCreate. */
private fun Window.glassDisplay(): Display? =
    decorView.display ?: runCatching { context.display }.getOrNull()

private fun Display.availableGlassHeadroom(): Float? =
    if (Build.VERSION.SDK_INT >= 34 && isHdrSdrRatioAvailable) {
        hdrSdrRatio.takeIf { it.isFinite() && it >= 1f }
    } else null
