package io.github.neoruaa.glasmio.ui

import androidx.compose.runtime.Immutable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal enum class IosSheetPlacement { Bottom, Floating, CompactEdge, FullScreen }

/**
 * UIKit's sheet layout uses natural, unscaled content bounds for its detent continuum, then
 * transforms the entire platter into an inset floating frame. All inputs and outputs use pixels.
 */
@Immutable
internal data class IosSheetLayout(
    val containerWidth: Float,
    val containerHeight: Float,
    val fullTop: Float,
    val safeBottom: Float,
    val density: Float,
    val compactHeight: Boolean = false,
    val placement: IosSheetPlacement = IosSheetPlacement.Bottom,
    val naturalWidth: Float = containerWidth,
    val naturalLeft: Float = 0f,
    val windowHeight: Float = containerHeight,
) {
    init {
        require(containerWidth.isFinite() && containerWidth > 0f) { "Sheet width must be finite and positive." }
        require(containerHeight.isFinite() && containerHeight > 0f) { "Sheet height must be finite and positive." }
        require(fullTop.isFinite() && fullTop < containerHeight) { "The full detent must lie above the bottom edge." }
        require(safeBottom.isFinite() && safeBottom >= 0f) { "The bottom safe area must be finite and non-negative." }
        require(density.isFinite() && density > 0f) { "Density must be finite and positive." }
    }

    /** The custom-detent resolver's maximum value excludes the bottom safe area. */
    val maximumDetentValue: Float get() = max(0f, containerHeight - fullTop - safeBottom)

    /** The resolver rounds the detent, then adds its bottom safe area back to the content bounds. */
    val mediumNaturalHeight: Float?
        get() = if (compactHeight) null else {
            val ratio = if (windowHeight / density > 568f) 0.56f else 0.63f
            min(containerHeight - fullTop, (maximumDetentValue * ratio).roundToInt().toFloat() + safeBottom)
        }

    val mediumNaturalTop: Float? get() = mediumNaturalHeight?.let { containerHeight - it }

    fun anchors(
        skipPartiallyExpanded: Boolean,
        fitContentHeight: Float? = null,
    ): GlasmioSheetAnchors {
        require(fitContentHeight == null || fitContentHeight.isFinite() && fitContentHeight > 0f) {
            "A fitted content height must be finite and positive."
        }
        val large = if (fitContentHeight == null) fullTop else max(fullTop, containerHeight - fitContentHeight)
        val medium = if (skipPartiallyExpanded || fitContentHeight != null) null else
            mediumNaturalTop?.takeIf { it > large && it < containerHeight }
        return GlasmioSheetAnchors(hidden = containerHeight, large = large, medium = medium)
    }

    /**
     * Map a natural detent offset to the displayed platter. While entering or leaving below the
     * first detent, the viewport keeps that detent's content height and the whole platter moves.
     * A fitted short panel has no physical full-height destination and stays inset throughout.
     */
    fun frame(
        naturalOffset: Float,
        lowestDetentTop: Float,
        fullHeightDetentAvailable: Boolean = true,
    ): IosSheetFrame {
        require(naturalOffset.isFinite() && lowestDetentTop.isFinite()) { "Sheet offsets must be finite." }
        require(lowestDetentTop >= fullTop && lowestDetentTop < containerHeight) {
            "The first visible detent must lie between the full detent and the bottom edge."
        }
        val expansion = when {
            !fullHeightDetentAvailable -> 0f
            lowestDetentTop <= fullTop -> 1f
            else -> ((lowestDetentTop - naturalOffset) / (lowestDetentTop - fullTop)).coerceIn(0f, 1f)
        }
        val restingHeight = containerHeight - lowestDetentTop
        val capsular = if (placement != IosSheetPlacement.FullScreen &&
            !fullHeightDetentAvailable && restingHeight <= 100f * density) 1f else 0f
        val inset = if (placement == IosSheetPlacement.Bottom || placement == IosSheetPlacement.CompactEdge) {
            if (capsular > 0f) 28f * density else 8f * density * (1f - expansion)
        } else 0f
        val scale = max(0.01f, (naturalWidth - inset * 2f) / naturalWidth)
        val boundsTop = if (placement == IosSheetPlacement.Floating) {
            naturalOffset.coerceIn(fullTop, lowestDetentTop)
        } else min(naturalOffset, lowestDetentTop)
        val naturalHeight = containerHeight - boundsTop
        val translation = naturalOffset - boundsTop
        val visibleHeight = naturalHeight * scale
        return IosSheetFrame(
            naturalHeight = naturalHeight,
            scale = scale,
            sideInset = inset,
            bottomInset = inset,
            visibleLeft = naturalLeft + inset,
            visibleTop = containerHeight - inset - visibleHeight + translation,
            visibleWidth = naturalWidth * scale,
            visibleHeight = visibleHeight,
            expansionProgress = expansion,
            capsularProgress = capsular,
        )
    }

    companion object {
        fun page(
            width: Float,
            height: Float,
            safeTop: Float,
            safeLeft: Float,
            safeBottom: Float,
            safeRight: Float,
            keyboardBottom: Float,
            density: Float,
            hasMultipleDetents: Boolean,
            edgeAttachedInCompactHeight: Boolean,
        ): IosSheetLayout {
            val compactHeight = height < 480f * density
            val availableWidth = (width - safeLeft - safeRight).coerceAtLeast(1f)
            val horizontalSafeZone = max(safeLeft, safeRight)
            val centeredAvailableWidth = width - 2f * horizontalSafeZone
            val preferredWidth = 936f * density
            val floatingMargin = 25f * density
            val placement = when {
                compactHeight && edgeAttachedInCompactHeight -> IosSheetPlacement.CompactEdge
                compactHeight -> IosSheetPlacement.FullScreen
                centeredAvailableWidth >= 840f * density -> IosSheetPlacement.Floating
                else -> IosSheetPlacement.Bottom
            }
            val floating = placement == IosSheetPlacement.Floating
            val naturalWidth = when (placement) {
                IosSheetPlacement.Floating -> min(preferredWidth, centeredAvailableWidth - 2f * floatingMargin)
                IosSheetPlacement.CompactEdge -> availableWidth
                else -> width
            }
            val floatingSafeBottom = if (hasMultipleDetents) safeBottom else max(safeTop, safeBottom)
            val availableBottom = (height - if (floating) max(floatingSafeBottom, keyboardBottom) else keyboardBottom)
                .coerceAtLeast(1f)
            val floatingTop = (if (hasMultipleDetents) safeTop else max(safeTop, safeBottom)) + 44f * density
            val minimumTop = floatingTop.coerceAtMost(availableBottom - 1f)
            val maximumHeight = availableBottom - minimumTop
            val floatingHeight = if (hasMultipleDetents) maximumHeight else min(maximumHeight, naturalWidth * sqrt(2f))
            val fullTop = when (placement) {
                IosSheetPlacement.Floating -> ((height - floatingHeight) / 2f)
                    .coerceIn(minimumTop, max(minimumTop, availableBottom - floatingHeight))
                IosSheetPlacement.FullScreen -> 0f
                else -> safeTop.coerceAtMost(availableBottom - 1f)
            }
            val naturalLeft = when (placement) {
                IosSheetPlacement.Floating -> (width - naturalWidth) / 2f
                IosSheetPlacement.CompactEdge -> safeLeft.coerceAtMost(width - naturalWidth)
                else -> 0f
            }
            return IosSheetLayout(
                containerWidth = width,
                containerHeight = if (floating) fullTop + floatingHeight else availableBottom,
                fullTop = fullTop,
                safeBottom = if (floating) 0f else safeBottom,
                density = density,
                compactHeight = compactHeight,
                placement = placement,
                naturalWidth = naturalWidth,
                naturalLeft = naturalLeft,
                windowHeight = height,
            )
        }
    }
}

@Immutable
internal data class IosSheetFrame(
    val naturalHeight: Float,
    val scale: Float,
    val sideInset: Float,
    val bottomInset: Float,
    val visibleLeft: Float,
    val visibleTop: Float,
    val visibleWidth: Float,
    val visibleHeight: Float,
    val expansionProgress: Float,
    val capsularProgress: Float,
)
