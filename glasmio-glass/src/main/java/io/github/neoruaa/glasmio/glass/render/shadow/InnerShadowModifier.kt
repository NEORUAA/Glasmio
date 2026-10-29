package io.github.neoruaa.glasmio.glass.render.shadow

import android.graphics.BlendModeColorFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint as ComposePaint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.LayoutDirection
import io.github.neoruaa.glasmio.glass.render.internal.ShapeProvider
import io.github.neoruaa.glasmio.glass.LiquidContactShape
import io.github.neoruaa.glasmio.glass.navigationContainerGlassField
import io.github.neoruaa.glasmio.glass.render.highlight.NativeGlassEdgeFunctions
import io.github.neoruaa.glasmio.glass.render.optics.GlassSdfShader
import io.github.neoruaa.glasmio.glass.render.optics.glassCornerRadii
import kotlin.math.abs
import kotlin.math.ceil

internal data class InnerShadowElement(
    val shapeProvider: ShapeProvider,
    val shadow: () -> InnerShadow?,
) : ModifierNodeElement<InnerShadowNode>() {
    override fun create() = InnerShadowNode(shapeProvider, shadow)

    override fun update(node: InnerShadowNode) {
        node.shapeProvider = shapeProvider
        node.shadow = shadow
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassInnerShadow"
        properties["shadow"] = shadow
    }
}

/** Native defaults use the recovered SDF ring; explicit custom styles retain GPU inverse coverage. */
internal class InnerShadowNode(
    var shapeProvider: ShapeProvider,
    var shadow: () -> InnerShadow?,
) : Modifier.Node(), DrawModifierNode {
    private val mask = RenderNode("Glasmio inner shadow inverse coverage")
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = android.graphics.BlendMode.CLEAR }
    private val compositingPaint = ComposePaint()
    private val path = Path()
    private val nativeShaders = mutableMapOf<String?, RuntimeShader>()
    private val nativePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var key: Geometry? = null
    private var pathKey: Geometry? = null
    private var blurRadius = Float.NaN
    private var padding = Float.NaN
    private var width = 0
    private var height = 0
    private var tint = 0

    override fun ContentDrawScope.draw() {
        drawContent()
        val effect = shadow() ?: return
        val canvas = drawContext.canvas.nativeCanvas
        if (!canvas.isHardwareAccelerated || size.minDimension <= 0f || effect.alpha <= 0f || effect.color.alpha <= 0f) return
        val shape = shapeProvider.innerShape
        val nextKey = Geometry(shape, size, layoutDirection, density, fontScale)
        val nativeDefault = effect.radius == InnerShadow.Default.radius && effect.offset == InnerShadow.Default.offset &&
            effect.color == InnerShadow.Default.color && effect.blendMode == InnerShadow.Default.blendMode
        if (nativeDefault) {
            if (nextKey != pathKey) {
                updatePath()
                pathKey = nextKey
            }
            val field = (shape as? LiquidContactShape)?.let { navigationContainerGlassField(it) }
            val shader = nativeShaders.getOrPut(field?.shaderFunctions) {
                val source = field?.shaderFunctions?.let { it + "\nfloat nativeInnerDistance(float2 p) { return navigationGlassField(p).x; }\n" }
                    ?: (GlassSdfShader + "\nfloat nativeInnerDistance(float2 p) { return glassDistance(p); }\n")
                RuntimeShader(source + NativeGlassEdgeFunctions + NativeInnerShadowMain)
            }
            if (field == null) {
                shader.setFloatUniform("size", size.width, size.height)
                shader.setFloatUniform("cornerRadii", glassCornerRadii(shape, size, layoutDirection, this))
            } else field.configure(shader)
            shader.setFloatUniform("innerOffset", effect.offset.x.toPx(), effect.offset.y.toPx())
            shader.setFloatUniform("innerSigma", effect.radius.toPx())
            shader.setFloatUniform("innerWidth", 4f * density)
            shader.setFloatUniform("innerOpacity", .06f * effect.alpha)
            nativePaint.shader = shader
            val save = canvas.save()
            try {
                canvas.clipPath(path.asAndroidPath())
                canvas.drawRect(0f, 0f, size.width, size.height, nativePaint)
            } finally { canvas.restoreToCount(save) }
            return
        }
        val sigma = effect.radius.toPx().coerceAtLeast(0f) * 0.5f
        val offsetX = effect.offset.x.toPx()
        val offsetY = effect.offset.y.toPx()
        val nextPadding = ceil(3f * sigma + maxOf(abs(offsetX), abs(offsetY)) + 2f)
        if (nextKey != pathKey) {
            updatePath()
            pathKey = nextKey
        }
        if (blurRadius != sigma) {
            val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.001f)
            mask.setRenderEffect(if (sigma > 0f) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) else null)
            blurRadius = sigma
        }
        if (nextKey != key || nextPadding != padding || !mask.hasDisplayList()) {
            padding = nextPadding
            val requiredWidth = ceil(size.width + padding * 2f).toInt()
            val requiredHeight = ceil(size.height + padding * 2f).toInt()
            width = maxOf(width, (requiredWidth + 63) / 64 * 64)
            height = maxOf(height, (requiredHeight + 63) / 64 * 64)
            mask.setPosition(0, 0, width, height)
            val recording = mask.beginRecording(width, height)
            try {
                recording.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
                recording.translate(padding, padding)
                recording.drawPath(path.asAndroidPath(), clear)
            } finally {
                mask.endRecording()
            }
            key = nextKey
        }
        val nextTint = effect.color.copy(alpha = 1f).toArgb()
        if (tint != nextTint) {
            compositingPaint.asFrameworkPaint().colorFilter = BlendModeColorFilter(nextTint, android.graphics.BlendMode.SRC_IN)
            tint = nextTint
        }
        compositingPaint.blendMode = effect.blendMode
        mask.setUseCompositingLayer(true, compositingPaint.asFrameworkPaint())
        mask.alpha = (effect.alpha * effect.color.alpha).coerceIn(0f, 1f)
        val save = canvas.save()
        try {
            // The source is an infinite exterior mask, clipped to the exact inner boundary.
            canvas.clipPath(path.asAndroidPath())
            canvas.translate(offsetX - padding, offsetY - padding)
            canvas.drawRenderNode(mask)
        } finally {
            canvas.restoreToCount(save)
        }
    }

    private fun ContentDrawScope.updatePath() {
        path.rewind()
        when (val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)) {
            is Outline.Rectangle -> path.addRect(outline.rect)
            is Outline.Rounded -> path.addRoundRect(outline.roundRect)
            is Outline.Generic -> path.addPath(outline.path)
        }
    }

    override fun onDetach() {
        mask.discardDisplayList()
        key = null
        pathKey = null
        width = 0
        height = 0
        nativeShaders.clear()
        nativePaint.shader = null
    }

    private data class Geometry(
        val shape: Shape,
        val size: Size,
        val direction: LayoutDirection,
        val density: Float,
        val fontScale: Float,
    )
}

private const val NativeInnerShadowMain = """
uniform float2 innerOffset;
uniform float innerSigma;
uniform float innerWidth;
uniform float innerOpacity;
half4 main(float2 p) {
    float d = nativeInnerDistance(p - innerOffset);
    float inverseSigma = 0.7071067811865476 / max(innerSigma, 0.0001);
    float ring = clamp(nativeGaussianTail(d * inverseSigma)
        - nativeGaussianTail((d + innerWidth) * inverseSigma), 0.0, 1.0);
    return half4(0.0, 0.0, 0.0, ring * innerOpacity);
}
"""
