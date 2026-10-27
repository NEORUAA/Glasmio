package io.github.neoruaa.glasmio.core

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Named variants in Apple's Text Styles and Dynamic Type design sheet. */
enum class IosTextVariant {
    Regular,
    Emphasized,
    Italic,
    EmphasizedItalic,
}

/** Apple's semantic text styles; [variants] contains only variants present in the design sheet. */
enum class IosTextStyle(
    val displayName: String,
    internal val tracking: Float,
    val variants: List<IosTextVariant>,
) {
    LargeTitle("Large Title", 0.40f, listOf(IosTextVariant.Regular, IosTextVariant.Emphasized)),
    Title1("Title 1", 0.38f, listOf(IosTextVariant.Regular, IosTextVariant.Emphasized)),
    Title2("Title 2", -0.26f, listOf(IosTextVariant.Regular, IosTextVariant.Emphasized)),
    Title3("Title 3", -0.45f, listOf(IosTextVariant.Regular, IosTextVariant.Emphasized)),
    Headline("Headline", -0.43f, listOf(IosTextVariant.Regular, IosTextVariant.Italic)),
    Body("Body", -0.43f, IosTextVariant.entries),
    Callout("Callout", -0.31f, IosTextVariant.entries),
    Subheadline("Subheadline", -0.23f, IosTextVariant.entries),
    Footnote("Footnote", -0.08f, IosTextVariant.entries),
    Caption1("Caption 1", 0f, IosTextVariant.entries),
    Caption2("Caption 2", 0.06f, IosTextVariant.entries),
}

/** The complete 12-column Dynamic Type table, including the five accessibility categories. */
enum class IosDynamicTypeSize(val displayName: String) {
    XSmall("xSmall"),
    Small("Small"),
    Medium("Medium"),
    Large("Large"),
    XLarge("xLarge"),
    XXLarge("xxLarge"),
    XXXLarge("xxxLarge"),
    AX1("AX1"),
    AX2("AX2"),
    AX3("AX3"),
    AX4("AX4"),
    AX5("AX5"),
}

/**
 * Apple semantic presets. Regular properties are convenient defaults; use [get] for emphasis
 * and italic variants. Fonts are supplied by the host and are never bundled by Glasmio.
 */
@Immutable
class GlasmioTextStyles internal constructor(
    private val styles: Map<Pair<IosTextStyle, IosTextVariant>, TextStyle>,
) {
    operator fun get(
        style: IosTextStyle,
        variant: IosTextVariant = IosTextVariant.Regular,
    ): TextStyle = requireNotNull(styles[style to variant]) {
        "${style.displayName} has no $variant variant in the source design."
    }

    val largeTitle: TextStyle get() = get(IosTextStyle.LargeTitle)
    val title1: TextStyle get() = get(IosTextStyle.Title1)
    val title2: TextStyle get() = get(IosTextStyle.Title2)
    val title3: TextStyle get() = get(IosTextStyle.Title3)
    val headline: TextStyle get() = get(IosTextStyle.Headline)
    val body: TextStyle get() = get(IosTextStyle.Body)
    val callout: TextStyle get() = get(IosTextStyle.Callout)
    val subheadline: TextStyle get() = get(IosTextStyle.Subheadline)
    val footnote: TextStyle get() = get(IosTextStyle.Footnote)
    val caption1: TextStyle get() = get(IosTextStyle.Caption1)
    val caption2: TextStyle get() = get(IosTextStyle.Caption2)

    internal fun resizeMaterialTypography(source: Typography): Typography {
        fun TextStyle.resized(role: IosTextStyle): TextStyle {
            val preset = get(role)
            return copy(
                fontSize = preset.fontSize,
                lineHeight = preset.lineHeight,
                letterSpacing = preset.letterSpacing,
            )
        }
        return source.copy(
            displayLarge = source.displayLarge.resized(IosTextStyle.LargeTitle),
            displayMedium = source.displayMedium.resized(IosTextStyle.Title1),
            displaySmall = source.displaySmall.resized(IosTextStyle.Title2),
            headlineLarge = source.headlineLarge.resized(IosTextStyle.LargeTitle),
            headlineMedium = source.headlineMedium.resized(IosTextStyle.Title1),
            headlineSmall = source.headlineSmall.resized(IosTextStyle.Title2),
            titleLarge = source.titleLarge.resized(IosTextStyle.Title3),
            titleMedium = source.titleMedium.resized(IosTextStyle.Headline),
            titleSmall = source.titleSmall.resized(IosTextStyle.Subheadline),
            bodyLarge = source.bodyLarge.resized(IosTextStyle.Body),
            bodyMedium = source.bodyMedium.resized(IosTextStyle.Subheadline),
            bodySmall = source.bodySmall.resized(IosTextStyle.Footnote),
            labelLarge = source.labelLarge.resized(IosTextStyle.Body),
            labelMedium = source.labelMedium.resized(IosTextStyle.Caption1),
            labelSmall = source.labelSmall.resized(IosTextStyle.Caption2),
        )
    }
}

/** Default Large-category values in sp, following the host's Android font scaling. */
fun glasmioTextStyles(
    fontFamily: FontFamily = FontFamily.Default,
    letterSpacing: TextUnit = TextUnit.Unspecified,
): GlasmioTextStyles =
    buildTextStyles(null, fontFamily, dynamicTypeSize = null, density = null, letterSpacing = letterSpacing)

/**
 * An explicit Apple category in logical points. [density] is required so Android's fontScale
 * (including nonlinear scaling) is inverted once, rather than applied on top of the category.
 */
fun glasmioTextStyles(
    fontFamily: FontFamily,
    dynamicTypeSize: IosDynamicTypeSize,
    density: Density,
    letterSpacing: TextUnit = TextUnit.Unspecified,
): GlasmioTextStyles = buildTextStyles(null, fontFamily, dynamicTypeSize, density, letterSpacing)

/**
 * Inherits each role's font family and platform settings from [typography].
 * Null [dynamicTypeSize] follows Android accessibility scaling; an explicit category replaces it
 * for these presets only and does not change the density or scaling of the surrounding UI.
 */
@Composable
fun rememberGlasmioTextStyles(
    dynamicTypeSize: IosDynamicTypeSize? = null,
    typography: Typography = MaterialTheme.typography,
    letterSpacing: TextUnit = TextUnit.Unspecified,
): GlasmioTextStyles {
    val density = LocalDensity.current
    return remember(typography, dynamicTypeSize, density, letterSpacing) {
        buildTextStyles(typography, FontFamily.Default, dynamicTypeSize, density, letterSpacing)
    }
}

val LocalGlasmioTextStyles = staticCompositionLocalOf { glasmioTextStyles() }
val LocalGlasmioLetterSpacing = staticCompositionLocalOf { TextUnit.Unspecified }

internal fun Typography.withLetterSpacing(letterSpacing: TextUnit): Typography {
    if (letterSpacing.isUnspecified) return this
    fun TextStyle.tracked() = copy(letterSpacing = letterSpacing)
    return copy(
        displayLarge = displayLarge.tracked(), displayMedium = displayMedium.tracked(), displaySmall = displaySmall.tracked(),
        headlineLarge = headlineLarge.tracked(), headlineMedium = headlineMedium.tracked(), headlineSmall = headlineSmall.tracked(),
        titleLarge = titleLarge.tracked(), titleMedium = titleMedium.tracked(), titleSmall = titleSmall.tracked(),
        bodyLarge = bodyLarge.tracked(), bodyMedium = bodyMedium.tracked(), bodySmall = bodySmall.tracked(),
        labelLarge = labelLarge.tracked(), labelMedium = labelMedium.tracked(), labelSmall = labelSmall.tracked(),
    )
}

private fun buildTextStyles(
    typography: Typography?,
    fontFamily: FontFamily,
    dynamicTypeSize: IosDynamicTypeSize?,
    density: Density?,
    letterSpacing: TextUnit,
): GlasmioTextStyles {
    val category = dynamicTypeSize ?: IosDynamicTypeSize.Large
    fun Float.textUnit() = if (dynamicTypeSize == null) sp else {
        requireNotNull(density) { "An explicit Dynamic Type size requires the host density." }
        with(density) { dp.toSp() }
    }
    return GlasmioTextStyles(buildMap {
        for (role in IosTextStyle.entries) {
            val (size, leading) = dynamicTypeMetrics[category.ordinal][role.ordinal]
            val source = if (typography == null) TextStyle(fontFamily = fontFamily) else when (role) {
                IosTextStyle.LargeTitle -> typography.headlineLarge
                IosTextStyle.Title1 -> typography.headlineMedium
                IosTextStyle.Title2 -> typography.headlineSmall
                IosTextStyle.Title3 -> typography.titleLarge
                IosTextStyle.Headline -> typography.titleMedium
                IosTextStyle.Body, IosTextStyle.Callout -> typography.bodyLarge
                IosTextStyle.Subheadline -> typography.bodyMedium
                IosTextStyle.Footnote -> typography.bodySmall
                IosTextStyle.Caption1 -> typography.labelMedium
                IosTextStyle.Caption2 -> typography.labelSmall
            }
            for (variant in role.variants) {
                val italic = variant == IosTextVariant.Italic ||
                    variant == IosTextVariant.EmphasizedItalic ||
                    (role == IosTextStyle.Callout && variant == IosTextVariant.Emphasized)
                val emphasized = variant == IosTextVariant.Emphasized ||
                    variant == IosTextVariant.EmphasizedItalic || role == IosTextStyle.Headline
                val weight = when {
                    !emphasized -> 400
                    italic -> 591 // Source variable-font axis is 590.7999878; Compose weights are integers.
                    role == IosTextStyle.LargeTitle || role == IosTextStyle.Title1 ||
                        role == IosTextStyle.Title2 -> 700
                    else -> 590
                }
                put(role to variant, source.copy(
                    fontSize = size.toFloat().textUnit(),
                    lineHeight = leading.toFloat().textUnit(),
                    letterSpacing = if (letterSpacing.isUnspecified) role.tracking.textUnit() else letterSpacing,
                    fontWeight = FontWeight(weight),
                    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                    // Keep first/last-line font extents: a host font can exceed the nominal
                    // design leading. Trimming those bounds could clip glyphs or fallback scripts.
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Proportional,
                        trim = LineHeightStyle.Trim.None,
                        mode = LineHeightStyle.Mode.Fixed,
                    ),
                ))
            }
        }
    })
}

// Figma 0:2194 / 5418:17465 / 5418:27121. Rows follow IosDynamicTypeSize;
// columns follow IosTextStyle. Values are exact font-size/line-height pairs.
// In particular, Title 3 is 43/51 in both AX3 and AX4 in the supplied Apple sheet.
private val dynamicTypeMetrics = arrayOf(
    arrayOf(31 to 38, 25 to 31, 19 to 23, 17 to 22, 14 to 19, 14 to 19, 13 to 18, 12 to 16, 12 to 16, 11 to 13, 11 to 13), // xSmall
    arrayOf(32 to 38, 26 to 31, 20 to 23, 18 to 22, 15 to 19, 15 to 19, 14 to 18, 13 to 16, 12 to 16, 11 to 13, 11 to 13), // Small
    arrayOf(33 to 40, 27 to 33, 21 to 28, 19 to 24, 16 to 21, 16 to 21, 15 to 20, 14 to 19, 12 to 16, 11 to 13, 11 to 13), // Medium
    arrayOf(34 to 41, 28 to 34, 22 to 28, 20 to 25, 17 to 22, 17 to 22, 16 to 21, 15 to 20, 13 to 18, 12 to 16, 11 to 13), // Large (Default)
    arrayOf(36 to 43, 30 to 36, 24 to 30, 22 to 28, 19 to 24, 19 to 24, 18 to 23, 17 to 22, 15 to 20, 14 to 19, 13 to 18), // xLarge
    arrayOf(38 to 46, 32 to 39, 26 to 32, 24 to 30, 21 to 26, 21 to 26, 20 to 24, 19 to 24, 17 to 22, 16 to 21, 15 to 20), // xxLarge
    arrayOf(40 to 48, 34 to 41, 28 to 34, 26 to 32, 23 to 29, 23 to 29, 22 to 28, 21 to 28, 19 to 24, 18 to 23, 17 to 22), // xxxLarge
    arrayOf(44 to 52, 38 to 46, 34 to 41, 31 to 38, 28 to 34, 28 to 34, 26 to 32, 25 to 31, 23 to 29, 22 to 28, 20 to 25), // AX1
    arrayOf(48 to 57, 43 to 51, 43 to 51, 37 to 44, 33 to 40, 33 to 40, 32 to 39, 30 to 37, 27 to 33, 26 to 32, 24 to 30), // AX2
    arrayOf(52 to 61, 48 to 57, 44 to 52, 43 to 51, 40 to 48, 40 to 48, 38 to 46, 36 to 43, 33 to 40, 32 to 39, 29 to 35), // AX3
    arrayOf(56 to 66, 53 to 62, 50 to 59, 43 to 51, 47 to 56, 47 to 56, 44 to 52, 42 to 50, 38 to 46, 37 to 44, 34 to 41), // AX4
    arrayOf(60 to 70, 58 to 68, 56 to 66, 55 to 65, 53 to 62, 53 to 62, 51 to 60, 49 to 58, 44 to 52, 43 to 51, 40 to 48), // AX5
)
