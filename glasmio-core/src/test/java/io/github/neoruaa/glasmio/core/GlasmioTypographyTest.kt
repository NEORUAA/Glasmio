package io.github.neoruaa.glasmio.core

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GlasmioTypographyTest {
    @Test
    fun hostTrackingOverrideCoversSemanticVariantsAndMaterialRoles() {
        val defaults = glasmioTextStyles(FontFamily.Monospace)
        val tracked = glasmioTextStyles(FontFamily.Monospace, letterSpacing = 0.6.sp)
        for (role in IosTextStyle.entries) for (variant in role.variants) {
            assertEquals(defaults[role, variant].copy(letterSpacing = 0.6.sp), tracked[role, variant])
        }
        val material = glasmioTypography(FontFamily.Serif).withLetterSpacing(0.6.sp)
        listOf(material.displayLarge, material.displayMedium, material.displaySmall,
            material.headlineLarge, material.headlineMedium, material.headlineSmall,
            material.titleLarge, material.titleMedium, material.titleSmall,
            material.bodyLarge, material.bodyMedium, material.bodySmall,
            material.labelLarge, material.labelMedium, material.labelSmall).forEach {
            assertEquals(0.6.sp, it.letterSpacing)
            assertEquals(FontFamily.Serif, it.fontFamily)
        }
    }

    @Test
    fun defaultPresetsUseLargeMetricsAndTheHostFont() {
        val text = glasmioTextStyles(FontFamily.Monospace)

        assertEquals(FontFamily.Monospace, text.body.fontFamily)
        assertEquals(17.sp, text.body.fontSize)
        assertEquals(22.sp, text.body.lineHeight)
        assertEquals((-0.43).sp, text.body.letterSpacing)
        assertEquals(FontWeight.Normal, text.body.fontWeight)
        assertEquals(34.sp, text.largeTitle.fontSize)
        assertEquals(41.sp, text.largeTitle.lineHeight)
        assertEquals(16.sp, text.callout.fontSize)
        assertEquals(21.sp, text.callout.lineHeight)
        assertEquals(11.sp, text.caption2.fontSize)
        assertEquals(13.sp, text.caption2.lineHeight)
    }

    @Test
    fun materialDefaultsMapToAppleRolesAndKeepEmphasizedHeadlines() {
        val text = glasmioTextStyles(FontFamily.Serif)
        val material = glasmioTypography(FontFamily.Serif)

        assertEquals(text.body, material.bodyLarge)
        assertEquals(text.caption1, material.labelMedium)
        assertEquals(text[IosTextStyle.LargeTitle, IosTextVariant.Emphasized], material.headlineLarge)
        assertEquals(text[IosTextStyle.Title2, IosTextVariant.Emphasized], material.headlineSmall)
        assertEquals(material.headlineLarge, material.displayLarge)
        assertEquals(FontFamily.Serif, material.headlineLarge.fontFamily)
    }

    @Test
    fun variantsKeepTheirSemanticWeightAndItalicSelection() {
        val text = glasmioTextStyles()

        assertEquals(FontWeight(590), text.headline.fontWeight)
        assertEquals(FontStyle.Normal, text.headline.fontStyle)
        assertEquals(FontWeight(700), text[IosTextStyle.Title1, IosTextVariant.Emphasized].fontWeight)
        assertEquals(FontWeight(590), text[IosTextStyle.Body, IosTextVariant.Emphasized].fontWeight)
        assertEquals(FontStyle.Normal, text[IosTextStyle.Body, IosTextVariant.Emphasized].fontStyle)
        assertEquals(FontWeight.Normal, text[IosTextStyle.Body, IosTextVariant.Italic].fontWeight)
        assertEquals(FontStyle.Italic, text[IosTextStyle.Body, IosTextVariant.Italic].fontStyle)
        assertEquals(FontWeight(591), text[IosTextStyle.Body, IosTextVariant.EmphasizedItalic].fontWeight)
        assertEquals(FontStyle.Italic, text[IosTextStyle.Body, IosTextVariant.EmphasizedItalic].fontStyle)
        // The supplied Apple sheet assigns an italic face to Callout/Emphasized.
        assertEquals(FontStyle.Italic, text[IosTextStyle.Callout, IosTextVariant.Emphasized].fontStyle)
    }

    @Test
    fun unsupportedVariantsAreRejectedInsteadOfSynthesized() {
        val text = glasmioTextStyles()

        assertThrows(IllegalArgumentException::class.java) {
            text[IosTextStyle.LargeTitle, IosTextVariant.Italic]
        }
        assertThrows(IllegalArgumentException::class.java) {
            text[IosTextStyle.Headline, IosTextVariant.Emphasized]
        }
    }

    @Test
    fun explicitAccessibilityCategoryDoesNotMultiplyAndroidFontScale() {
        val density = Density(density = 2f, fontScale = 1.6f)
        val explicit = glasmioTextStyles(FontFamily.Default, IosDynamicTypeSize.AX1, density)
        val systemScaled = glasmioTextStyles()

        with(density) {
            assertEquals(28f, explicit.body.fontSize.toDp().value, 0.001f)
            assertEquals(34f, explicit.body.lineHeight.toDp().value, 0.001f)
            assertEquals(-0.43f, explicit.body.letterSpacing.toDp().value, 0.001f)
            assertEquals(44f, explicit.largeTitle.fontSize.toDp().value, 0.001f)
            assertEquals(52f, explicit.largeTitle.lineHeight.toDp().value, 0.001f)
            assertTrue(systemScaled.body.fontSize.toDp().value > 17f)
        }
    }
}
