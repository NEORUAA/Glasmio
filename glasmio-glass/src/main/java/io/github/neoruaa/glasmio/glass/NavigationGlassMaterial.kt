package io.github.neoruaa.glasmio.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
internal data class NavigationGlassFill(val color: Color, val blendMode: BlendMode)

/**
 * Semantic material names and measured iOS 27 optical defaults.
 *
 * Runtime UIKit uses a 5 pt regular frost and a separate, much clearer 0.75 pt control recipe.
 * Refraction follows the measured geometry profile unless a caller provides explicit values.
 * The retained ordered fills below document the design fallback, not the native face pipeline.
 */
enum class NavigationGlassMaterial(
    val frostRadius: Dp,
    val shadowRadius: Dp,
    val lightShadowAlpha: Float,
    val darkShadowAlpha: Float,
    val nativeLightIntensity: Float,
) {
    RegularSmall(5.dp, 4.dp, 0.04f, 0.04f, 0.25f),
    RegularMedium(5.dp, 24.dp, 0.6f, 0.6f, 0.25f),
    RegularLarge(5.dp, 24.dp, 0.6f, 0.6f, 0.25f),
    Clear(.75.dp, 0.dp, 0f, 0f, 0.4f);

    val shadowOffsetY: Dp get() = 8.dp

    internal fun fills(isDark: Boolean): List<NavigationGlassFill> = when (this) {
        RegularSmall -> if (isDark) SmallDark else SmallLight
        RegularMedium, RegularLarge -> if (isDark) MediumLargeDark else MediumLargeLight
        Clear -> ClearFills
    }
}

private val SmallLight = listOf(
    NavigationGlassFill(Color.Black.copy(alpha = 0.25f), BlendMode.SrcOver),
    NavigationGlassFill(Color.White.copy(alpha = 0.25f), BlendMode.SrcOver),
    NavigationGlassFill(Color(0xFF444444).copy(alpha = 0.6f), BlendMode.Plus),
    NavigationGlassFill(Color(0xFFF8F8F8).copy(alpha = 0.2f), BlendMode.Luminosity),
)
private val SmallDark = listOf(
    NavigationGlassFill(Color(0xFF999999).copy(alpha = 0.17f), BlendMode.Luminosity),
)
private val MediumLargeLight = listOf(
    NavigationGlassFill(Color.White.copy(alpha = 0.7f), BlendMode.Lighten),
    NavigationGlassFill(Color(0xFFBFBFBF).copy(alpha = 0.1f), BlendMode.Darken),
)
private val MediumLargeDark = listOf(
    NavigationGlassFill(Color(0xFF1A1A1A).copy(alpha = 0.7f), BlendMode.Luminosity),
    NavigationGlassFill(Color(0xFF1A1A1A).copy(alpha = 0.9f), BlendMode.Luminosity),
    NavigationGlassFill(Color(0xFF1A1A1A), BlendMode.Lighten),
)
private val ClearFills = listOf(
    NavigationGlassFill(Color(0xFF101010), BlendMode.Plus),
    NavigationGlassFill(Color.White.copy(alpha = 0.04f), BlendMode.Luminosity),
)
