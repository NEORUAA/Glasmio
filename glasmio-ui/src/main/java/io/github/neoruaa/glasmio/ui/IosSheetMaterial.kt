package io.github.neoruaa.glasmio.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
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
    val directBackplateVisible: Boolean = true,
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
private val DarkCellPlusColor = Color(1f, 1f, 1f, 0.06f)

/**
 * Native grouped-cell material transforms the backdrop, not the white carrier pixels.
 * The live CAColorMatrix is RGB identity with a -0.06 light / +0.06 dark bias and alpha identity.
 */
internal fun Modifier.sheetCellBackground(
    context: IosSheetMaterialContext,
    backdrop: Backdrop?,
    @Suppress("UNUSED_PARAMETER") shape: Shape,
): Modifier {
    val vibrant = context.vibrancy.coerceIn(0f, 1f)
    if (context.groupedListStyle == GlasmioSheetGroupedListStyle.ToolbarTint) {
        // Paint once over the existing backplate, exactly like GlasmioSheetToolbarButton.
        // The opaque large carrier and its native dismissal transition remain unchanged.
        val tint = context.cellColor.copy(alpha = 1f - vibrant * (1f - SheetGroupedListBackgroundAlpha))
        return drawBehind { drawRect(tint) }
    }
    if (vibrant >= 1f && context.isDark && context.directBackplateVisible) {
        // Over an opaque sheet backplate, BlendMode.Plus with premultiplied (0.06, 0.06, 0.06, 0.06)
        // computes clamp(dst.rgb + 0.06, 0, 1) directly without saveLayer or backdrop DAG edges.
        return drawBehind { drawRect(DarkCellPlusColor, blendMode = BlendMode.Plus) }
    }
    if (vibrant > 0f && backdrop != null) {
        return this then SheetCellBackgroundElement(context, backdrop)
    }
    return drawBehind { drawRect(context.cellColor) }
}

private class SheetCellBackgroundElement(
    private val context: IosSheetMaterialContext,
    private val backdrop: Backdrop,
) : ModifierNodeElement<SheetCellBackgroundNode>() {

    override fun create(): SheetCellBackgroundNode = SheetCellBackgroundNode(context, backdrop)

    override fun update(node: SheetCellBackgroundNode) {
        node.update(context, backdrop)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "sheetCellBackground"
        properties["context"] = context
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is SheetCellBackgroundElement && context == other.context && backdrop == other.backdrop)

    override fun hashCode(): Int = 31 * context.hashCode() + backdrop.hashCode()
}

private class SheetCellBackgroundNode(
    private var context: IosSheetMaterialContext,
    private var backdrop: Backdrop,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    private val filterPaint = Paint()
    private val clipBounds = android.graphics.Rect()
    private val lastTransform = FloatArray(16)
    private val scratchTransform = FloatArray(16)
    private val sourceMatrix = Matrix()
    private var lastSourceSize = IntSize.Zero
    private var lastGraphicsLayer: GraphicsLayer? = null
    private var hasValidSource = false
    private var lastRootPosition = Offset.Unspecified
    private var coordinates: LayoutCoordinates? = null
    private var sourceObservation: (() -> Unit)? = null

    fun update(context: IosSheetMaterialContext, backdrop: Backdrop) {
        val contextChanged = this.context != context
        val backdropChanged = this.backdrop != backdrop
        if (!contextChanged && !backdropChanged) return
        this.context = context
        if (backdropChanged) {
            this.backdrop = backdrop
            hasValidSource = false
            lastGraphicsLayer = null
            lastRootPosition = Offset.Unspecified
            observeSource()
        }
        invalidateDraw()
    }

    private fun observeSource() {
        sourceObservation?.invoke()
        sourceObservation = if (isAttached) {
            backdrop.observeSourceChanges { checkSourceChangedAndInvalidate() }
        } else null
    }

    private fun checkSourceChangedAndInvalidate() {
        if (!isAttached) return
        val currentBackdrop = backdrop
        if (currentBackdrop !is LayerBackdrop) {
            invalidateDraw()
            return
        }
        val coords = coordinates?.takeIf { it.isAttached } ?: return
        val srcSize = currentBackdrop.snapshotTransformIn(coords, scratchTransform)
        if (!hasValidSource || srcSize == null || srcSize != lastSourceSize ||
            currentBackdrop.graphicsLayer !== lastGraphicsLayer || !scratchTransform.contentEquals(lastTransform)
        ) {
            invalidateDraw()
        }
    }

    override fun onAttach() {
        observeSource()
    }

    override fun onDetach() {
        sourceObservation?.invoke()
        sourceObservation = null
        coordinates = null
        hasValidSource = false
        lastGraphicsLayer = null
        lastRootPosition = Offset.Unspecified
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return
        this.coordinates = coordinates
        if (backdrop is LayerBackdrop) {
            checkSourceChangedAndInvalidate()
        } else {
            val pos = coordinates.positionInRoot()
            if (pos != lastRootPosition) {
                lastRootPosition = pos
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val canvas = drawContext.canvas
        val coords = coordinates?.takeIf { it.isAttached }
        if (canvas.nativeCanvas.getClipBounds(clipBounds)) {
            var left = maxOf(0f, clipBounds.left - 1f)
            var top = maxOf(0f, clipBounds.top - 1f)
            var right = minOf(size.width, clipBounds.right + 1f)
            var bottom = minOf(size.height, clipBounds.bottom + 1f)
            val currentBackdrop = backdrop
            if (currentBackdrop is LayerBackdrop && coords != null) {
                val srcSize = currentBackdrop.snapshotTransformIn(coords, lastTransform)
                if (srcSize != null) {
                    lastSourceSize = srcSize
                    lastGraphicsLayer = currentBackdrop.graphicsLayer
                    hasValidSource = true
                    lastTransform.copyInto(sourceMatrix.values, 0, 0, 16)
                    val mapped = sourceMatrix.map(
                        Rect(0f, 0f, srcSize.width.toFloat(), srcSize.height.toFloat()),
                    )
                    left = maxOf(left, mapped.left - 1f)
                    top = maxOf(top, mapped.top - 1f)
                    right = minOf(right, mapped.right + 1f)
                    bottom = minOf(bottom, mapped.bottom + 1f)
                } else {
                    hasValidSource = false
                }
            }
            if (right > left && bottom > top) {
                val visible = Rect(left, top, right, bottom)
                val vibrant = context.vibrancy.coerceIn(0f, 1f)
                if (vibrant < 1f) {
                    drawRect(
                        color = context.cellColor.copy(alpha = 1f - vibrant),
                        topLeft = visible.topLeft,
                        size = visible.size,
                    )
                }
                filterPaint.colorFilter = if (context.isDark) DarkCellFilter else LightCellFilter
                filterPaint.alpha = vibrant
                canvas.saveLayer(visible, filterPaint)
                try {
                    with(currentBackdrop) {
                        drawBackdrop(
                            density = this@draw,
                            coordinates = coords,
                            layerBlock = null,
                        )
                    }
                } finally {
                    canvas.restore()
                }
            }
        }
        drawContent()
    }
}
