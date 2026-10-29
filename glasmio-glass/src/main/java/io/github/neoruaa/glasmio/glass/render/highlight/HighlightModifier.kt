package io.github.neoruaa.glasmio.glass.render.highlight

import android.os.Build
import androidx.compose.ui.graphics.nativeCanvas

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.LayoutDirection
import io.github.neoruaa.glasmio.glass.LiquidContactShape
import io.github.neoruaa.glasmio.glass.render.RuntimeShader
import io.github.neoruaa.glasmio.glass.render.RuntimeShaderCacheImpl
import io.github.neoruaa.glasmio.glass.render.asComposeShader
import io.github.neoruaa.glasmio.glass.render.internal.ShapeProvider

internal data class HighlightElement(
    val shapeProvider: ShapeProvider,
    val highlight: () -> Highlight?,
) : ModifierNodeElement<HighlightNode>() {
    override fun create() = HighlightNode(shapeProvider, highlight)

    override fun update(node: HighlightNode) {
        node.shapeProvider = shapeProvider
        node.highlight = highlight
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassHighlight"
        properties["highlight"] = highlight
    }
}

internal class HighlightNode(
    var shapeProvider: ShapeProvider,
    var highlight: () -> Highlight?,
) : Modifier.Node(), DrawModifierNode {
    private val cache = RuntimeShaderCacheImpl()
    private val paint = Paint()
    private val path = Path()
    private var geometry: Geometry? = null
    private var style: HighlightStyle? = null
    private var shader: RuntimeShader? = null
    private var lastWidth = Float.NaN
    private var nativeBlender: NativeVibrantColorMatrixBlender? = null

    override fun ContentDrawScope.draw() {
        drawContent()
        val effect = highlight() ?: return
        if (size.minDimension <= 0f || effect.alpha <= 0f || effect.width.value <= 0f) return
        val nextStyle = effect.style
        val matrix = nextStyle.nativeVibrantMatrix()
        // API 33–35 applies this transfer in the sampled post-tint material shader.
        // A foreground white overlay cannot reproduce destination-driven vibrancy.
        if (matrix != null && Build.VERSION.SDK_INT < 36) return
        val shape = shapeProvider.innerShape
        val nextGeometry = Geometry(shape, size, layoutDirection, density, fontScale)
        if (geometry != nextGeometry) {
            path.rewind()
            when (val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)) {
                is Outline.Rectangle -> path.addRect(outline.rect)
                is Outline.Rounded -> path.addRoundRect(outline.roundRect)
                is Outline.Generic -> path.addPath(outline.path)
            }
        }
        if (geometry != nextGeometry || style != nextStyle || shape is LiquidContactShape) {
            shader = with(nextStyle) { createShader(shape, cache) }
            paint.shader = shader?.asComposeShader()
            geometry = nextGeometry
            style = nextStyle
            lastWidth = Float.NaN
        }
        val width = effect.width.toPx()
        val nativeStyle = nextStyle is HighlightStyle.Default || nextStyle is HighlightStyle.Ambient
        if (nativeStyle && shader != null && lastWidth != width) {
            shader!!.setFloatUniform("height", width)
            lastWidth = width
        }
        paint.asFrameworkPaint().xfermode = null
        paint.blendMode = nextStyle.blendMode
        if (matrix != null && Build.VERSION.SDK_INT >= 36) {
            if (!drawContext.canvas.nativeCanvas.isHardwareAccelerated) return
            val blender = nativeBlender ?: NativeVibrantColorMatrixBlender().also { nativeBlender = it }
            blender.prepare(matrix)
            paint.asFrameworkPaint().xfermode = blender.shader
        }
        paint.alpha = (effect.alpha * nextStyle.color.alpha).coerceIn(0f, 1f)
        val canvas = drawContext.canvas
        canvas.save()
        try {
            // Keep continuous/custom shapes exact even when the shader uses primitive SDF geometry.
            canvas.clipPath(path)
            if (shader == null) {
                paint.color = nextStyle.color.copy(alpha = 1f)
                paint.alpha = (effect.alpha * nextStyle.color.alpha).coerceIn(0f, 1f)
                paint.style = PaintingStyle.Stroke
                paint.strokeWidth = width * 2f
                canvas.drawPath(path, paint)
            } else {
                paint.style = PaintingStyle.Fill
                canvas.drawRect(0f, 0f, size.width, size.height, paint)
            }
        } finally {
            canvas.restore()
        }
    }

    override fun onDetach() {
        cache.clear()
        paint.shader = null
        shader = null
        style = null
        geometry = null
        nativeBlender = null
    }

    private data class Geometry(
        val shape: Shape,
        val size: Size,
        val direction: LayoutDirection,
        val density: Float,
        val fontScale: Float,
    )
}
