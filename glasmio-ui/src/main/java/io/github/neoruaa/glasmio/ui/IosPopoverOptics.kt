package io.github.neoruaa.glasmio.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScope
import io.github.neoruaa.glasmio.glass.render.asAndroidRuntimeShader
import io.github.neoruaa.glasmio.glass.render.effects.runtimeShaderEffect
import io.github.neoruaa.glasmio.glass.render.isRuntimeShaderSupported
import kotlin.math.ceil

/** Retains one contour texture for a presented popover. */
internal class PopoverOptics {
    private data class Geometry(
        val shape: Shape,
        val size: Size,
        val density: Float,
        val fontScale: Float,
        val layoutDirection: LayoutDirection,
    )

    internal data class Mask(val input: BitmapShader, val scale: Float, val margin: Float)

    private var geometry: Geometry? = null
    private var mask: Mask? = null

    fun mask(scope: BackdropEffectScope): Mask? = with(scope) {
        if (!size.width.isFinite() || !size.height.isFinite() || size.minDimension <= 0f) {
            return null
        }
        val next = Geometry(shape, size, density, fontScale, layoutDirection)
        if (next == geometry) return mask
        val outline = shape.createOutline(size, layoutDirection, Density(density, fontScale))
        if (outline !is Outline.Generic) {
            geometry = next
            mask = null
            return null
        }

        // The mask is only an optical normal map. The full-resolution outline still clips the glass.
        val scale = minOf(1f, 1f / density, 240f / size.maxDimension)
        val softRadius = (5.dp.toPx() * scale).coerceAtLeast(1f)
        val margin = ceil(softRadius * 3f + 2f)
        val bitmap = Bitmap.createBitmap(
            ceil(size.width * scale + 2f * margin).toInt(),
            ceil(size.height * scale + 2f * margin).toInt(),
            Bitmap.Config.ARGB_8888,
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            maskFilter = BlurMaskFilter(softRadius, BlurMaskFilter.Blur.NORMAL)
        }
        // Scale the path, not the canvas: BlurMaskFilter uses the canvas transform for its radius.
        val maskPath = android.graphics.Path()
        outline.path.asAndroidPath().transform(Matrix().apply {
            setScale(scale, scale)
            postTranslate(margin, margin)
        }, maskPath)
        Canvas(bitmap).drawPath(maskPath, paint)
        val input = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            if (isRuntimeShaderSupported()) setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        }
        return Mask(input, scale, margin).also {
            geometry = next
            mask = it
        }
    }
}

internal fun BackdropEffectScope.popoverEdgeRefraction(optics: PopoverOptics) {
    if (!isRuntimeShaderSupported()) return
    val contour = optics.mask(this) ?: return
    val bend = 16.dp.toPx()
    padding = maxOf(padding, bend)
    runtimeShaderEffect("Glasmio.Popover.EdgeRefraction", PopoverRefractionShader, "content") {
        asAndroidRuntimeShader().setInputBuffer("contour", contour.input)
        setFloatUniform("capturePadding", padding)
        setFloatUniform("maskScale", contour.scale)
        setFloatUniform("maskMargin", contour.margin)
        setFloatUniform("bend", bend)
    }
}

private const val PopoverRefractionShader = """
uniform shader content;
uniform shader contour;
uniform float capturePadding;
uniform float maskScale;
uniform float maskMargin;
uniform float bend;

float coverage(float2 point) {
    return float(contour.eval(point).a);
}

half4 main(float2 point) {
    float2 local = (point - capturePadding) * maskScale + maskMargin;
    float center = coverage(local);
    if (center < 0.03 || center > 0.98) return content.eval(point);

    float2 dx = float2(1.5, 0.0);
    float2 dy = float2(0.0, 1.5);
    float2 normal = float2(
        coverage(local + dx) - coverage(local - dx),
        coverage(local + dy) - coverage(local - dy)
    );
    float rim = smoothstep(0.03, 0.16, center) * (1.0 - smoothstep(0.80, 0.98, center));
    return content.eval(point + normal * (bend * rim));
}
"""
