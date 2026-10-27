package io.github.neoruaa.glasmio.core

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle

/** Every rounded surface uses the same continuous curvature, including Material fallbacks. */
@Immutable
data class GlasmioShapes(
    val extraSmall: CornerBasedShape = ContinuousRoundedRectangle(8.dp),
    val small: CornerBasedShape = ContinuousRoundedRectangle(16.dp),
    val medium: CornerBasedShape = ContinuousRoundedRectangle(26.dp),
    val large: CornerBasedShape = ContinuousRoundedRectangle(38.dp),
    val extraLarge: CornerBasedShape = ContinuousRoundedRectangle(44.dp),
) {
    fun materialShapes() = Shapes(extraSmall, small, medium, large, extraLarge)
}

@Immutable
data class GlasmioSpacing(
    val compact: Dp = 8.dp,
    val regular: Dp = 16.dp,
    val section: Dp = 32.dp,
)

/** Shared motion defaults; specialized migrated interactions retain their reference physics. */
object GlasmioMotion {
    fun <T> spring() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
}

val LocalGlasmioShapes = staticCompositionLocalOf { GlasmioShapes() }
val LocalGlasmioSpacing = staticCompositionLocalOf { GlasmioSpacing() }

/** Apple semantic presets mapped to Material roles; hosts can supply their licensed family. */
fun glasmioTypography(fontFamily: FontFamily = FontFamily.Default): Typography {
    val text = glasmioTextStyles(fontFamily)
    return Typography(
        displayLarge = text[IosTextStyle.LargeTitle, IosTextVariant.Emphasized],
        displayMedium = text[IosTextStyle.Title1, IosTextVariant.Emphasized],
        displaySmall = text[IosTextStyle.Title2, IosTextVariant.Emphasized],
        headlineLarge = text[IosTextStyle.LargeTitle, IosTextVariant.Emphasized],
        headlineMedium = text[IosTextStyle.Title1, IosTextVariant.Emphasized],
        headlineSmall = text[IosTextStyle.Title2, IosTextVariant.Emphasized],
        titleLarge = text[IosTextStyle.Title3, IosTextVariant.Emphasized],
        titleMedium = text.headline,
        titleSmall = text[IosTextStyle.Subheadline, IosTextVariant.Emphasized],
        bodyLarge = text.body,
        bodyMedium = text.subheadline,
        bodySmall = text.footnote,
        labelLarge = text[IosTextStyle.Body, IosTextVariant.Emphasized],
        labelMedium = text.caption1,
        labelSmall = text.caption2,
    )
}

fun glasmioColorScheme(isDark: Boolean, accent: Color = Color(0xFF0088FF)): ColorScheme {
    val colors = defaultGlassColors(isDark, accent)
    return if (isDark) darkColorScheme(
        primary = accent, onPrimary = Color.White, background = colors.groupedBackground,
        onBackground = colors.content, surface = colors.elevatedBackground,
        onSurface = colors.content, onSurfaceVariant = colors.secondaryContent,
        error = colors.destructive, outlineVariant = colors.separator,
    ) else lightColorScheme(
        primary = accent, onPrimary = Color.White, background = colors.groupedBackground,
        onBackground = colors.content, surface = colors.elevatedBackground,
        onSurface = colors.content, onSurfaceVariant = colors.secondaryContent,
        error = colors.destructive, outlineVariant = colors.separator,
    )
}

@Composable
fun GlasmioTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    colorScheme: ColorScheme = glasmioColorScheme(isDark),
    typography: Typography = glasmioTypography(),
    colors: GlassColors = defaultGlassColors(isDark, colorScheme.primary),
    appearance: GlassAppearance = GlassAppearance(),
    dimensions: GlassDimensions = GlassDimensions(),
    shapes: GlasmioShapes = GlasmioShapes(),
    spacing: GlasmioSpacing = GlasmioSpacing(),
    dynamicTypeSize: IosDynamicTypeSize? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    content: @Composable () -> Unit,
) {
    val textStyles = rememberGlasmioTextStyles(dynamicTypeSize, typography, letterSpacing)
    val sizedTypography = if (dynamicTypeSize == null) typography
        else textStyles.resizeMaterialTypography(typography)
    val materialTypography = remember(sizedTypography, letterSpacing) { sizedTypography.withLetterSpacing(letterSpacing) }
    CompositionLocalProvider(
        LocalGlassColors provides colors,
        LocalGlassAppearance provides appearance,
        LocalGlassDimensions provides dimensions,
        LocalGlasmioShapes provides shapes,
        LocalGlasmioSpacing provides spacing,
        LocalGlasmioTextStyles provides textStyles,
        LocalGlasmioLetterSpacing provides letterSpacing,
        LocalContentColor provides colors.content,
    ) {
        MaterialTheme(colorScheme, shapes.materialShapes(), materialTypography, content)
    }
}

object GlasmioTheme {
    /** Honors the host override while retaining each component's reference tracking by default. */
    @Composable @ReadOnlyComposable
    fun letterSpacing(fallback: TextUnit): TextUnit = LocalGlasmioLetterSpacing.current.let {
        if (it.isUnspecified) fallback else it
    }
    val colors: GlassColors @Composable @ReadOnlyComposable get() = LocalGlassColors.current
    val appearance: GlassAppearance @Composable @ReadOnlyComposable get() = LocalGlassAppearance.current
    val dimensions: GlassDimensions @Composable @ReadOnlyComposable get() = LocalGlassDimensions.current
    val shapes: GlasmioShapes @Composable @ReadOnlyComposable get() = LocalGlasmioShapes.current
    val spacing: GlasmioSpacing @Composable @ReadOnlyComposable get() = LocalGlasmioSpacing.current
    val typography: Typography @Composable @ReadOnlyComposable get() = MaterialTheme.typography
    val textStyles: GlasmioTextStyles @Composable @ReadOnlyComposable get() = LocalGlasmioTextStyles.current
}
