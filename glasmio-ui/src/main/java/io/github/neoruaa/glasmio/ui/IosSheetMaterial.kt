package io.github.neoruaa.glasmio.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.drawPlainBackdrop
import io.github.neoruaa.glasmio.glass.render.effects.colorFilter
import io.github.neoruaa.glasmio.glass.render.effects.opacity
import io.github.neoruaa.glasmio.core.SheetGroupedListBackgroundAlpha

/** Native cells by default; ToolbarTint matches sheet controls while the sheet is translucent. */
enum class GlasmioSheetGroupedListStyle { Native, ToolbarTint }

/** Scope an explicit sheet treatment without changing other presentations or their large palette. */
val LocalGlasmioSheetGroupedListStyle = staticCompositionLocalOf { GlasmioSheetGroupedListStyle.Native }

/** Portable equivalent of the sheet's rendering-mode/vibrancy trait environment. */
@Immutable
internal data class IosSheetMaterialContext(
    val isDark: Boolean,
    val vibrancy: Float,
    val groupedListStyle: GlasmioSheetGroupedListStyle = GlasmioSheetGroupedListStyle.Native,
) {
    val cellColor: Color get() = if (isDark) Color(0xFF2C2C2E) else Color.White
    val separatorColor: Color get() = if (isDark) Color(0xFF545458).copy(alpha = 0.5f)
        else Color(0xFF3C3C43).copy(alpha = 0.12f)
    val secondaryLabelColor: Color get() = if (isDark) Color(0xFFEBEBF5).copy(alpha = 0.6f)
        else Color(0xFF3C3C43).copy(alpha = 0.6f)
    val tableColor: Color get() =
        (if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)).copy(alpha = 1f - vibrancy.coerceIn(0f, 1f))

    fun opaque() = copy(vibrancy = 0f)
}

internal val LocalIosSheetMaterialContext = compositionLocalOf<IosSheetMaterialContext?> { null }
internal val LocalIosSheetBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

private fun sheetCellFilter(bias: Float): ColorFilter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    1f, 0f, 0f, 0f, bias * 255f,
    0f, 1f, 0f, 0f, bias * 255f,
    0f, 0f, 1f, 0f, bias * 255f,
    0f, 0f, 0f, 1f, 0f,
)))

private val LightCellFilter = sheetCellFilter(-0.06f)
private val DarkCellFilter = sheetCellFilter(0.06f)

/**
 * Native grouped-cell material transforms the backdrop, not the white carrier pixels.
 * The live CAColorMatrix is RGB identity with a -0.06 light / +0.06 dark bias and alpha identity.
 */
internal fun Modifier.sheetCellBackground(
    context: IosSheetMaterialContext,
    backdrop: Backdrop?,
    shape: Shape,
): Modifier {
    val vibrant = context.vibrancy.coerceIn(0f, 1f)
    if (context.groupedListStyle == GlasmioSheetGroupedListStyle.ToolbarTint) {
        // Paint once over the existing backplate, exactly like GlasmioSheetToolbarButton.
        // The opaque large carrier and its native dismissal transition remain unchanged.
        val tint = context.cellColor.copy(alpha = 1f - vibrant * (1f - SheetGroupedListBackgroundAlpha))
        return drawBehind { drawRect(tint) }
    }
    if (vibrant > 0f && backdrop != null) {
        return drawPlainBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                colorFilter(if (context.isDark) DarkCellFilter else LightCellFilter)
                opacity(vibrant)
            },
            onDrawBehind = {
                if (vibrant < 1f) drawRect(context.cellColor.copy(alpha = 1f - vibrant))
            },
        )
    }
    return drawBehind { drawRect(context.cellColor) }
}
