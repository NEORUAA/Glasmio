package io.github.neoruaa.glasmio.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
internal data class NavigationGlassFill(val color: Color, val blendMode: BlendMode)

/**
 * Ordered paint and spatial parameters from Apple's iOS 27 native GLASS materials.
 *
 * The source effects use refraction 0.7, depth 30, light angle 0, dispersion 0.2 and splay 0.2.
 * Their native optical units are not mapped to Backdrop lens units here; the calibrated shared
 * Navigation lens remains 10/24 dp. Regular light intensity is 0.25; Clear uses 0.4.
 * Frost radii are spatial design units, converted to pixels by the rendering host's density.
 */
enum class NavigationGlassMaterial(
    val frostRadius: Dp,
    val shadowRadius: Dp,
    val lightShadowAlpha: Float,
    val darkShadowAlpha: Float,
    val nativeLightIntensity: Float,
) {
    RegularSmall(6.dp, 15.dp, 0.02f, 0.04f, 0.25f),
    RegularMedium(16.dp, 48.dp, 0.25f, 0.45f, 0.25f),
    RegularLarge(16.dp, 48.dp, 0.25f, 0.45f, 0.25f),
    Clear(6.dp, 15.dp, 0.02f, 0.02f, 0.4f);

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
