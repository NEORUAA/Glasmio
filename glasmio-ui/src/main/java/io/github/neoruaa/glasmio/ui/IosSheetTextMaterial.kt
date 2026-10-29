package io.github.neoruaa.glasmio.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.neoruaa.glasmio.glass.render.Backdrop

internal enum class IosSheetTextRole { Primary, Secondary }

private fun sheetTextFilter(bias: Float) = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    1f, 0f, 0f, 0f, bias * 255f,
    0f, 1f, 0f, 0f, bias * 255f,
    0f, 0f, 1f, 0f, bias * 255f,
    0f, 0f, 0f, 1f, 0f,
)))

private val LightPrimaryTextFilter = sheetTextFilter(-1f)
private val DarkPrimaryTextFilter = sheetTextFilter(0.949999988f)
private val LightSecondaryTextFilter = sheetTextFilter(-0.400000006f)
private val DarkSecondaryTextFilter = sheetTextFilter(0.300000012f)

/**
 * UIKit's material text uses the glyphs as a white mask over a transformed backdrop. Its
 * semantic color remains available for the ordinary carrier after leaving the glass context.
 */
@Composable
internal fun IosSheetText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color,
    role: IosSheetTextRole = IosSheetTextRole.Primary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    useMaterial: Boolean = true,
) {
    val context = LocalIosSheetMaterialContext.current
    val backdrop = LocalIosSheetBackdrop.current
    // Subtracting one from the bounded light backdrop is identically black.
    val plainBlack = role == IosSheetTextRole.Primary && context?.isDark == false && color == Color.Black
    val material = useMaterial && !plainBlack && context != null && backdrop != null && context.vibrancy > 0f
    Text(
        text = text,
        modifier = if (material) modifier.sheetTextMaterial(
            requireNotNull(context), requireNotNull(backdrop), role, color,
        ) else modifier,
        style = style,
        color = if (material) Color.White else color,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
    )
}

@Composable
private fun Modifier.sheetTextMaterial(
    context: IosSheetMaterialContext,
    backdrop: Backdrop,
    role: IosSheetTextRole,
    semanticColor: Color,
): Modifier {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return onGloballyPositioned { coordinates = it }
        .drawWithCache {
            val glyphs = obtainGraphicsLayer()
            val bounds = Rect(Offset.Zero, size)
            val filter = when (role) {
                IosSheetTextRole.Primary -> if (context.isDark) DarkPrimaryTextFilter else LightPrimaryTextFilter
                IosSheetTextRole.Secondary -> if (context.isDark) DarkSecondaryTextFilter else LightSecondaryTextFilter
            }
            val semanticPaint = Paint().apply { colorFilter = ColorFilter.tint(semanticColor, BlendMode.SrcIn) }
            val filterPaint = Paint().apply { colorFilter = filter }
            val maskPaint = Paint().apply { blendMode = BlendMode.DstIn }
            val vibrantPaint = Paint()

            onDrawWithContent {
                // Record only text, never its backdrop. Text semantics and layout remain owned
                // by the original Material Text node, including GetTextLayoutResult.
                glyphs.record { this@onDrawWithContent.drawContent() }
                val canvas = drawContext.canvas
                val sourceCoordinates = coordinates?.takeIf { it.isAttached }
                val vibrant = if (sourceCoordinates == null) 0f else context.vibrancy.coerceIn(0f, 1f)

                if (vibrant < 1f) {
                    semanticPaint.alpha = 1f - vibrant
                    canvas.saveLayer(bounds, semanticPaint)
                    drawLayer(glyphs)
                    canvas.restore()
                }
                if (vibrant > 0f) {
                    // Keep the two carriers separate during a controller dismissal. The
                    // backdrop matrix is constant; only the new white carrier's mask grows.
                    vibrantPaint.alpha = vibrant
                    canvas.saveLayer(bounds, vibrantPaint)
                    canvas.clipRect(0f, 0f, size.width, size.height)
                    canvas.saveLayer(bounds, filterPaint)
                    with(backdrop) {
                        drawBackdrop(
                            density = this@onDrawWithContent,
                            coordinates = sourceCoordinates,
                            layerBlock = null,
                        )
                    }
                    canvas.restore()
                    canvas.saveLayer(bounds, maskPaint)
                    drawLayer(glyphs)
                    canvas.restore()
                    canvas.restore()
                }
            }
        }
}
