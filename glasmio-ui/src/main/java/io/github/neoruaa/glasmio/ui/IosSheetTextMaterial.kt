package io.github.neoruaa.glasmio.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop

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
private val DarkSecondaryPlusColor = Color(1f, 1f, 1f, 0.300000012f)

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
    // Subtracting one from the bounded light backdrop is identically black; adding 0.95 to the
    // dark sheet glass backplate (RGB floor >= 0x1A/255 = 0.102) saturates identically to white.
    val plainBlack = role == IosSheetTextRole.Primary && context?.isDark == false && color == Color.Black
    val plainWhite = role == IosSheetTextRole.Primary && context?.isDark == true && color == Color.White
    val darkSecondaryDirect = useMaterial &&
        role == IosSheetTextRole.Secondary &&
        context?.isDark == true &&
        context.vibrancy >= 1f &&
        context.directBackplateVisible &&
        backdrop != null
    val material = useMaterial && !plainBlack && !plainWhite && !darkSecondaryDirect &&
        context != null && backdrop != null && context.vibrancy > 0f
    Text(
        text = text,
        modifier = when {
            darkSecondaryDirect -> modifier.graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                blendMode = BlendMode.Plus
            }
            material -> modifier.sheetTextMaterial(
                requireNotNull(context), requireNotNull(backdrop), role, color,
            )
            else -> modifier
        },
        style = style,
        color = when {
            darkSecondaryDirect -> DarkSecondaryPlusColor
            material -> Color.White
            else -> color
        },
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
    )
}

private fun Modifier.sheetTextMaterial(
    context: IosSheetMaterialContext,
    backdrop: Backdrop,
    role: IosSheetTextRole,
    semanticColor: Color,
): Modifier = this then SheetTextMaterialElement(context, backdrop, role, semanticColor)

private class SheetTextMaterialElement(
    private val context: IosSheetMaterialContext,
    private val backdrop: Backdrop,
    private val role: IosSheetTextRole,
    private val semanticColor: Color,
) : ModifierNodeElement<SheetTextMaterialNode>() {

    override fun create(): SheetTextMaterialNode =
        SheetTextMaterialNode(context, backdrop, role, semanticColor)

    override fun update(node: SheetTextMaterialNode) {
        node.update(context, backdrop, role, semanticColor)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "sheetTextMaterial"
        properties["context"] = context
        properties["backdrop"] = backdrop
        properties["role"] = role
        properties["semanticColor"] = semanticColor
    }

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is SheetTextMaterialElement &&
                context == other.context &&
                backdrop == other.backdrop &&
                role == other.role &&
                semanticColor == other.semanticColor
            )

    override fun hashCode(): Int {
        var result = context.hashCode()
        result = 31 * result + backdrop.hashCode()
        result = 31 * result + role.hashCode()
        result = 31 * result + semanticColor.hashCode()
        return result
    }
}

private class SheetTextMaterialNode(
    private var context: IosSheetMaterialContext,
    private var backdrop: Backdrop,
    private var role: IosSheetTextRole,
    private var semanticColor: Color,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    private fun currentTextFilter(): ColorFilter = when (role) {
        IosSheetTextRole.Primary -> if (context.isDark) DarkPrimaryTextFilter else LightPrimaryTextFilter
        IosSheetTextRole.Secondary -> if (context.isDark) DarkSecondaryTextFilter else LightSecondaryTextFilter
    }

    private val semanticPaint = Paint().apply {
        colorFilter = ColorFilter.tint(semanticColor, BlendMode.SrcIn)
    }
    private val filterSrcInPaint = Paint().apply {
        colorFilter = currentTextFilter()
        blendMode = BlendMode.SrcIn
    }
    private val vibrantPaint = Paint()
    private val lastTransform = FloatArray(16)
    private val scratchTransform = FloatArray(16)
    private val sourceMatrix = Matrix()
    private var lastSourceSize = IntSize.Zero
    private var lastGraphicsLayer: GraphicsLayer? = null
    private var hasValidSource = false
    private var lastRootPosition = Offset.Unspecified
    private var coordinates: LayoutCoordinates? = null
    private var sourceObservation: (() -> Unit)? = null

    fun update(
        context: IosSheetMaterialContext,
        backdrop: Backdrop,
        role: IosSheetTextRole,
        semanticColor: Color,
    ) {
        val contextChanged = this.context != context
        val backdropChanged = this.backdrop != backdrop
        val roleChanged = this.role != role
        val colorChanged = this.semanticColor != semanticColor
        if (!contextChanged && !backdropChanged && !roleChanged && !colorChanged) return

        val themeOrRoleChanged = this.context.isDark != context.isDark || roleChanged
        this.context = context
        this.role = role
        if (colorChanged) {
            this.semanticColor = semanticColor
            semanticPaint.colorFilter = ColorFilter.tint(semanticColor, BlendMode.SrcIn)
        }
        if (themeOrRoleChanged) {
            filterSrcInPaint.colorFilter = currentTextFilter()
        }
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
        if (size.width <= 0f || size.height <= 0f) return
        val canvas = drawContext.canvas
        if (canvas.nativeCanvas.quickReject(-1f, -1f, size.width + 1f, size.height + 1f)) return

        val sourceCoordinates = coordinates?.takeIf { it.isAttached }
        val currentBackdrop = backdrop
        if (currentBackdrop is LayerBackdrop && sourceCoordinates != null) {
            val srcSize = currentBackdrop.snapshotTransformIn(sourceCoordinates, lastTransform)
            if (srcSize != null) {
                lastSourceSize = srcSize
                lastGraphicsLayer = currentBackdrop.graphicsLayer
                hasValidSource = true
                lastTransform.copyInto(sourceMatrix.values, 0, 0, 16)
                val mapped = sourceMatrix.map(
                    Rect(0f, 0f, srcSize.width.toFloat(), srcSize.height.toFloat()),
                )
                if (mapped.right < -1f || mapped.left > size.width + 1f ||
                    mapped.bottom < -1f || mapped.top > size.height + 1f
                ) {
                    return
                }
            } else {
                hasValidSource = false
            }
        }

        val vibrant = if (sourceCoordinates == null) 0f else context.vibrancy.coerceIn(0f, 1f)
        val bounds = Rect(0f, 0f, size.width, size.height)
        if (vibrant < 1f) {
            semanticPaint.alpha = 1f - vibrant
            canvas.saveLayer(bounds, semanticPaint)
            try {
                drawContent()
            } finally {
                canvas.restore()
            }
        }
        if (vibrant > 0f) {
            // Keep the two carriers separate during a controller dismissal. The
            // backdrop matrix is constant; only the new white carrier's mask grows.
            vibrantPaint.alpha = vibrant
            canvas.saveLayer(bounds, vibrantPaint)
            try {
                canvas.clipRect(0f, 0f, size.width, size.height)
                drawContent()
                canvas.saveLayer(bounds, filterSrcInPaint)
                try {
                    with(currentBackdrop) {
                        drawBackdrop(
                            density = this@draw,
                            coordinates = sourceCoordinates,
                            layerBlock = null,
                        )
                    }
                } finally {
                    canvas.restore()
                }
            } finally {
                canvas.restore()
            }
        }
    }
}
