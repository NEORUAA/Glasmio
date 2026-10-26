package io.github.neoruaa.glasmio.core

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** System fonts are the default. A host may supply its own licensed font family. */
fun glasmioTypography(fontFamily: FontFamily = FontFamily.Default): Typography {
    val base = TextStyle(fontFamily = fontFamily)
    return Typography(
        displayLarge = base.copy(fontSize = 57.sp, lineHeight = 64.sp),
        displayMedium = base.copy(fontSize = 45.sp, lineHeight = 52.sp),
        displaySmall = base.copy(fontSize = 36.sp, lineHeight = 44.sp),
        headlineLarge = base.copy(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp),
        headlineMedium = base.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
        headlineSmall = base.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
        titleLarge = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp),
        titleMedium = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
        titleSmall = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
        bodyLarge = base.copy(fontSize = 17.sp, lineHeight = 22.sp),
        bodyMedium = base.copy(fontSize = 15.sp, lineHeight = 20.sp),
        bodySmall = base.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = base.copy(fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp),
        labelMedium = base.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = base.copy(fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 12.sp),
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
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalGlassColors provides colors,
        LocalGlassAppearance provides appearance,
        LocalGlassDimensions provides dimensions,
        LocalGlasmioShapes provides shapes,
        LocalGlasmioSpacing provides spacing,
    ) {
        MaterialTheme(colorScheme, shapes.materialShapes(), typography, content)
    }
}

object GlasmioTheme {
    val colors: GlassColors @Composable @ReadOnlyComposable get() = LocalGlassColors.current
    val appearance: GlassAppearance @Composable @ReadOnlyComposable get() = LocalGlassAppearance.current
    val dimensions: GlassDimensions @Composable @ReadOnlyComposable get() = LocalGlassDimensions.current
    val shapes: GlasmioShapes @Composable @ReadOnlyComposable get() = LocalGlasmioShapes.current
    val spacing: GlasmioSpacing @Composable @ReadOnlyComposable get() = LocalGlasmioSpacing.current
    val typography: Typography @Composable @ReadOnlyComposable get() = MaterialTheme.typography
}
