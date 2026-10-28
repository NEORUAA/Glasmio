package io.github.neoruaa.glasmio.core

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily

@Immutable
data class GlassAppearance(
    val liquidGlassEnabled: Boolean = true,
    val reduceTransparency: Boolean = false,
) {
    val usesRefraction: Boolean
        get() = liquidGlassEnabled && !reduceTransparency

    val usesBackdrop: Boolean
        get() = !reduceTransparency
}

val LocalGlassAppearance = staticCompositionLocalOf { GlassAppearance() }

/** Shared outer edge for top-toolbar buttons and aligned page content. */
val IosPageHorizontalPadding = 16.dp

@Immutable
data class GlassColors(
    val isDark: Boolean,
    val accent: Color,
    val content: Color,
    val secondaryContent: Color,
    val container: Color,
    val prominentContainer: Color,
    val subtleStroke: Color,
    val groupedBackground: Color,
    val elevatedBackground: Color,
    val separator: Color,
    val tertiaryContent: Color,
    val destructive: Color,
)

@Immutable
data class GlassDimensions(
    val compactCornerRadius: Dp = 16.dp,
    val regularCornerRadius: Dp = 26.dp,
    val sheetCornerRadius: Dp = 38.dp,
    val controlHeight: Dp = 48.dp,
    val iconButtonSize: Dp = 44.dp,
    val bottomBarHeight: Dp = 64.dp,
)

val GlassColors.segmentedControlBackground: Color
    get() = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0x1F767680)

/** UIKit's resolved semantic grays for nested filled controls. */
val GlassColors.systemGray4: Color
    get() = if (isDark) Color(0xFF3A3A3C) else Color(0xFFD1D1D6)

val GlassColors.systemGray5: Color
    get() = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)

/** iOS names for shared roles. Existing largeTitle/title2 aliases retain emphasized headlines. */
object IosTypography {
    val fontFamily: FontFamily
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge.fontFamily ?: FontFamily.Default
    val largeTitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineLarge
    val title1: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalGlasmioTextStyles.current.title1
    val title2: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineSmall
    val title3: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalGlasmioTextStyles.current.title3
    val headline: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium
    val body: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge
    val callout: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalGlasmioTextStyles.current.callout
    val subheadline: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium
    val footnote: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalGlasmioTextStyles.current.footnote
    val caption1: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalGlasmioTextStyles.current.caption1
    val caption2: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalGlasmioTextStyles.current.caption2
    val caption: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.labelMedium
}

val LocalGlassColors = staticCompositionLocalOf {
    GlassColors(
        isDark = false,
        accent = Color(0xFFFF3B30),
        content = Color.Black,
        secondaryContent = Color.Black.copy(alpha = 0.62f),
        container = Color.White,
        prominentContainer = Color(0xFFFF3B30).copy(alpha = 0.82f),
        subtleStroke = Color.White.copy(alpha = 0.55f),
        groupedBackground = Color(0xFFF2F2F7),
        elevatedBackground = Color.White,
        separator = Color(0xFFE6E6E6),
        tertiaryContent = Color(0x4D3C3C43),
        destructive = Color(0xFFFF383C),
    )
}

/** Content color inherited by symbols and text placed inside a glass surface. */
val LocalGlassContentColor = staticCompositionLocalOf<Color?> { null }
val LocalGroupedListIconColor = staticCompositionLocalOf<Color?> { null }
val LocalGroupedListBackgroundAlpha = staticCompositionLocalOf { 1f }

/** Legacy sheet-control tint. Native modal grouped cells use their rendering/vibrancy context. */
const val SheetGroupedListBackgroundAlpha = 0.55f

val LocalGlassDimensions = staticCompositionLocalOf { GlassDimensions() }

fun defaultGlassColors(
    isDark: Boolean,
    accent: Color = if (isDark) Color(0xFFFF4245) else Color(0xFFFF3B30),
): GlassColors {
    return GlassColors(
        isDark = isDark,
        accent = accent,
        content = if (isDark) Color.White else Color.Black,
        secondaryContent = if (isDark) {
            Color(0xB2EBEBF5)
        } else {
            Color.Black.copy(alpha = 0.58f)
        },
        container = if (isDark) {
            Color(0xFF1C1C1E).copy(alpha = 0.54f)
        } else {
            Color(0xFFF8F8FA).copy(alpha = 0.64f)
        },
        prominentContainer = accent.copy(alpha = if (isDark) 0.76f else 0.84f),
        subtleStroke = if (isDark) {
            Color(0x2BFFFFFF)
        } else {
            Color.White.copy(alpha = 0.64f)
        },
        groupedBackground = if (isDark) Color.Black else Color(0xFFF2F2F7),
        elevatedBackground = if (isDark) Color(0xFF1C1C1E) else Color.White,
        separator = if (isDark) Color(0x2BFFFFFF) else Color(0xFFE6E6E6),
        tertiaryContent = if (isDark) Color(0x4DEBEBF5) else Color(0x4D3C3C43),
        destructive = if (isDark) Color(0xFFFF4245) else Color(0xFFFF383C),
    )
}
