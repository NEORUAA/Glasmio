package io.github.neoruaa.glasmio.glass

/** Retains a known source material without exposing its native rendering recipe. */
class GlassMaterialSource internal constructor(
    internal val recipe: NavigationMaterialRecipe,
    internal val minimumDimension: Float = Float.NaN,
) {
    private var smallFace: GlassMaterialSource? = null
    private var largeFace: GlassMaterialSource? = null

    /** Freeze the measured source class before its contour starts shrinking. */
    internal fun forSize(minimumDimension: Float): GlassMaterialSource {
        if (!minimumDimension.isFinite()) return this
        if (this.minimumDimension == minimumDimension) return this
        return if (navigationGlassUsesLargeFace(minimumDimension)) {
            largeFace?.takeIf { it.minimumDimension == minimumDimension }
                ?: GlassMaterialSource(recipe.forMinimumDimension(minimumDimension), minimumDimension).also { largeFace = it }
        } else {
            smallFace?.takeIf { it.minimumDimension == minimumDimension }
                ?: GlassMaterialSource(recipe.forMinimumDimension(minimumDimension), minimumDimension).also { smallFace = it }
        }
    }
}

internal fun NavigationMaterialRecipe.forMinimumDimension(minimumDimension: Float): NavigationMaterialRecipe {
    if (!minimumDimension.isFinite()) return this
    val sized = if (automaticSize && luminanceRollOff != 0f)
        copy(automaticSize = false, luminanceRollOff = if (navigationGlassUsesLargeFace(minimumDimension)) .65f else .4f)
    else this
    val alternate = appearanceAlternate?.forMinimumDimension(minimumDimension)
    return if (alternate === appearanceAlternate) sized else sized.copy(appearanceAlternate = alternate)
}
