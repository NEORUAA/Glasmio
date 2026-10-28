package io.github.neoruaa.glasmio.icons

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/** Code points are resolved by the SF Symbols CLI using exact-name matching. */
@Immutable
enum class SfSymbol(
    val systemName: String,
    val codePoint: Int,
    val autoMirrored: Boolean = false,
) {
    House("house", 0x10039E),
    HouseFilled("house.fill", 0x10039F),
    Layers("square.3.layers.3d", 0x100BF0),
    InfoCircle("info.circle", 0x100174),
    Sparkles("sparkles", 0x1001BF),
    MusicNote("music.note", 0x10046A),
    MusicNoteSquareStackFilled("music.note.square.stack.fill", 0x10343A),
    RadioWaves("dot.radiowaves.left.and.right", 0x100319),
    RadioFilled("radio.fill", 0x100A95),
    Safari("safari", 0x1003AC),
    SafariFilled("safari.fill", 0x1003AD),
    MusicNoteList("music.note.list", 0x10046C),
    Microphone("mic", 0x1002B0),
    Heart("heart", 0x1002B4),
    Star("star", 0x1002C2),
    StarFilled("star.fill", 0x1002C3),
    Download("arrow.down.circle", 0x100078),
    DownloadFilled("arrow.down.circle.fill", 0x100079),
    Cloud("icloud", 0x10030B),
    CloudFilled("icloud.fill", 0x10030C),
    Clock("clock", 0x10042B),
    ClockFilled("clock.fill", 0x10042C),
    ArrowClockwise("arrow.clockwise", 0x100148),
    Warning("exclamationmark.triangle", 0x1001FE),
    Search("magnifyingglass", 0x1002AB),
    Settings("gear", 0x10035F),
    Gearshape("gearshape", 0x1008CB),
    PersonFilled("person.fill", 0x10026A),
    Waveform("waveform", 0x10066B),
    PlayFilled("play.fill", 0x100284),
    PauseFilled("pause.fill", 0x100286),
    ForwardFilled("forward.fill", 0x10028C),
    BackwardFilled("backward.fill", 0x10028A),
    Ellipsis("ellipsis", 0x100360),
    Close("xmark", 0x100184),
    ChevronBack("chevron.left", 0x100189, autoMirrored = true),
}

private data class SfGlyphDrawCache(
    val paint: Paint,
    val bounds: Rect,
)

val LocalSfToolbarSymbols = staticCompositionLocalOf { false }

/**
 * Renders an SF symbol with [size] as its layout slot and [fontSize] as its typographic size.
 * Disable [fitGlyphToBounds] to preserve native glyph metrics, including ink outside the slot.
 * Toolbar symbols always preserve their native metrics; vector fallbacks use the layout slot.
 */
@Composable
fun SfIcon(
    symbol: SfSymbol,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalGlasmioIconTint.current(),
    size: Dp = 24.dp,
    fontSize: TextUnit = TextUnit.Unspecified,
    weight: FontWeight? = null,
    mirrored: Boolean = false,
    fontFeatureSettings: String = "'ss16' 1",
    fitGlyphToBounds: Boolean = true,
) {
    val layoutDirection = LocalLayoutDirection.current
    val toolbar = LocalSfToolbarSymbols.current
    if (LocalGlasmioSymbolTypeface.current == null) {
        GlasmioResolvedIcon(symbol.systemName, contentDescription, modifier, tint, size,
            mirrored && symbol.autoMirrored && layoutDirection == LayoutDirection.Rtl)
        return
    }
    SfIconGlyph(
        codePoint = symbol.codePoint,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
        size = size,
        fontSize = resolveSymbolFontSize(size, fontSize, toolbar),
        weight = weight ?: if (toolbar) FontWeight.Medium else FontWeight.Normal,
        fitToSlot = fitGlyphToBounds && !toolbar,
        mirrored = mirrored && symbol.autoMirrored && layoutDirection == LayoutDirection.Rtl,
        features = fontFeatureSettings,
    )
}

/** String-name overload of [SfIcon], with the same layout and glyph-fitting behavior. */
@Composable
fun SfIcon(
    systemName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalGlasmioIconTint.current(),
    size: Dp = 24.dp,
    fontSize: TextUnit = TextUnit.Unspecified,
    weight: FontWeight? = null,
    mirrored: Boolean = false,
    fontFeatureSettings: String = "'ss16' 1",
    fitGlyphToBounds: Boolean = true,
) {
    val layoutDirection = LocalLayoutDirection.current
    val toolbar = LocalSfToolbarSymbols.current
    val codePoint = SfSymbolCatalog.codePointOrNull(systemName)
    if (LocalGlasmioSymbolTypeface.current == null || codePoint == null) {
        GlasmioResolvedIcon(systemName, contentDescription, modifier, tint, size,
            mirrored && layoutDirection == LayoutDirection.Rtl)
        return
    }
    SfIconGlyph(
        codePoint = codePoint,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
        size = size,
        fontSize = resolveSymbolFontSize(size, fontSize, toolbar),
        weight = weight ?: if (toolbar) FontWeight.Medium else FontWeight.Normal,
        fitToSlot = fitGlyphToBounds && !toolbar,
        mirrored = mirrored && layoutDirection == LayoutDirection.Rtl,
        features = fontFeatureSettings,
    )
}

private fun resolveSymbolFontSize(size: Dp, fontSize: TextUnit, toolbar: Boolean): TextUnit {
    if (!fontSize.isUnspecified) return fontSize
    // UIKit's navigation bar uses Body (17 pt), Medium, Large in its standard icon slot.
    // Scale the point size together with intentionally smaller/larger layout slots.
    return (size.value * if (toolbar) 17f / 24f else 1f).sp
}

@Composable
private fun SfIconGlyph(
    codePoint: Int,
    contentDescription: String?,
    modifier: Modifier,
    tint: Color,
    size: Dp,
    fontSize: TextUnit,
    weight: FontWeight,
    fitToSlot: Boolean,
    mirrored: Boolean,
    features: String,
) {
    val density = LocalDensity.current
    val baseTypeface = LocalGlasmioSymbolTypeface.current ?: return
    val typeface = remember(baseTypeface, weight) {
        Typeface.create(baseTypeface, weight.weight, false)
    }
    val glyph = remember(codePoint) { String(Character.toChars(codePoint)) }
    val requestedSize = with(density) { fontSize.toPx() }
    val iconSize = with(density) { size.toPx() }
    val drawCache = remember(typeface, glyph, requestedSize, iconSize, fitToSlot, features) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            textSize = requestedSize
            textAlign = Paint.Align.LEFT
            fontFeatureSettings = features
        }
        val bounds = Rect()
        paint.getTextBounds(glyph, 0, glyph.length, bounds)
        val safeWidth = iconSize * 0.88f
        val safeHeight = iconSize * 0.88f
        val scale = if (fitToSlot) minOf(
            1f,
            safeWidth / bounds.width().coerceAtLeast(1),
            safeHeight / bounds.height().coerceAtLeast(1),
        ) else 1f
        if (scale < 1f) {
            paint.textSize *= scale
            paint.getTextBounds(glyph, 0, glyph.length, bounds)
        }
        SfGlyphDrawCache(paint = paint, bounds = bounds)
    }
    val semanticsModifier = if (contentDescription == null) {
        Modifier.clearAndSetSemantics { }
    } else {
        Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
    }
    Canvas(
        modifier = modifier
            .size(size)
            .then(semanticsModifier)
            .then(if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
    ) {
        drawIntoCanvas { canvas ->
            val paint = drawCache.paint
            val bounds = drawCache.bounds
            paint.color = tint.toArgb()
            // Center from the actual ink bounds, not the font advance or line metrics.
            // SF Symbols contains wide/offset glyphs whose typographic box otherwise
            // appears shifted and is clipped at the right or bottom in a square control.
            val x = this.size.width / 2f - (bounds.left + bounds.right) / 2f
            val baseline = this.size.height / 2f - (bounds.top + bounds.bottom) / 2f
            canvas.nativeCanvas.drawText(glyph, x, baseline, paint)
        }
    }
}
