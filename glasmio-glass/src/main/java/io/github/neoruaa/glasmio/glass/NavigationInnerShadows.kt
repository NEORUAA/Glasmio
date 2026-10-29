package io.github.neoruaa.glasmio.glass

import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.RuntimeXfermode
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.neoruaa.glasmio.glass.render.highlight.Highlight
import io.github.neoruaa.glasmio.glass.render.highlight.HighlightStyle
import io.github.neoruaa.glasmio.glass.render.highlight.NativeCasdfHighlightMaskFunctions
import io.github.neoruaa.glasmio.glass.render.highlight.NativeGlassBackgroundEdgeFunctions
import io.github.neoruaa.glasmio.glass.render.highlight.NativeVibrantColorMatrix
import io.github.neoruaa.glasmio.glass.render.highlight.NativeVibrantColorMatrixFunctions
import io.github.neoruaa.glasmio.glass.render.highlight.nativeVibrantMatrix
import io.github.neoruaa.glasmio.glass.render.optics.GlassSdfShader
import io.github.neoruaa.glasmio.glass.render.optics.glassCornerRadii
import kotlin.math.PI

/** Native glassBackground ring/key-fill transfers; shared by live and retained surfaces. */
@Suppress("UNUSED_PARAMETER")
class NavigationInnerShadowRenderer(
    private val renderScale: Float = 1f,
    combineMasks: Boolean = Build.VERSION.SDK_INT >= 36,
) {
    private val materialRenderer = NavigationMaterialRenderer()
    private val maskShaders = mutableMapOf<String?, RuntimeShader>()
    private val effectShaders = mutableMapOf<String?, RuntimeShader>()
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastMaskShader: RuntimeShader? = null
    private var lastEffectShader: RuntimeShader? = null
    private val blender = if (Build.VERSION.SDK_INT >= 36) NativeEdgeBlender() else null
    private val path = Path()
    private var geometry: Geometry? = null
    private var effectGeometry: Geometry? = null
    private var effectRecipe: NavigationMaterialRecipe? = null
    private var effectOpacity = Float.NaN
    private var effectHighlight: Highlight? = null
    private var effectHighlightClearFraction: Float? = null
    private val highlightInterpolation = NativeVibrantColorMatrix.HighlightInterpolation()
    private var cachedEffect: RenderEffect? = null

    fun draw(
        scope: DrawScope,
        shape: Shape,
        bounds: Rect = Rect(Offset.Zero, scope.size),
        isDark: Boolean = false,
        alpha: Float = 1f,
    ) = drawInternal(scope, shape, bounds, isDark, alpha, null, 0f)

    internal fun drawEdges(
        scope: DrawScope,
        shape: Shape,
        bounds: Rect = Rect(Offset.Zero, scope.size),
        isDark: Boolean = false,
        alpha: Float = 1f,
        burnAlpha: Float = 1f,
        field: NativeGlassFilterField? = null,
        clearFraction: Float? = null,
    ) = drawInternal(scope, shape, bounds, isDark, alpha, null, 0f, field, clearFraction)

    internal fun materialEffect(
        recipe: NavigationMaterialRecipe,
        alpha: Float = 1f,
        minimumDimension: Float = Float.NaN,
        shape: Shape? = null,
        size: Size = Size.Unspecified,
        density: Density? = null,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        origin: Offset = Offset.Zero,
        field: NativeGlassFilterField? = null,
        clearFraction: Float? = null,
        highlight: Highlight? = null,
        highlightClearFraction: Float? = null,
    ): RenderEffect {
        val sourceField = field ?: (shape as? LiquidContactShape)?.let { navigationContainerGlassField(it) }
        val effectShader = obtainShader(sourceField, effect = true)
        val programChanged = effectShader !== lastEffectShader
        val scoped = materialRenderer.recipeForSize(recipe, minimumDimension)
        val opacity = alpha.coerceIn(0f, 1f)
        val nextGeometry = if (shape != null && density != null && size.width.isFinite() && size.height.isFinite() && size.minDimension > 0f)
            Geometry(shape, size, layoutDirection, density.density, density.fontScale,
                scoped.darkFraction, clearFraction?.coerceIn(0f, 1f) ?: if (scoped.isClear) 1f else 0f,
                origin, sourceField?.shaderFunctions) else null
        val recipeChanged = !scoped.matches(effectRecipe)
        val geometryChanged = nextGeometry != effectGeometry || programChanged || sourceField != null
        val highlightFraction = highlightClearFraction?.coerceIn(0f, 1f)
        if (recipeChanged || effectOpacity != opacity || geometryChanged || effectHighlight != highlight ||
            effectHighlightClearFraction != highlightFraction || cachedEffect == null) {
            if (recipeChanged || programChanged) updateNavigationMaterialUniforms(scoped, opacity, effectShader::setFloatUniform)
            else {
                effectShader.setFloatUniform("materialOpacity", opacity)
                effectShader.setFloatUniform("appearance_materialOpacity", opacity)
            }
            if (nextGeometry != null && geometryChanged) prepareGeometry(effectShader, nextGeometry, sourceField)
            sourceField?.configure?.invoke(effectShader)
            effectShader.setFloatUniform("nativeEdgeOpacity", if (nextGeometry == null) 0f else 1f)
            configureHighlight(effectShader, highlight, nextGeometry, highlightFraction)
            // HWUI snapshots a RuntimeShader's uniforms while creating its RenderEffect.
            cachedEffect = RenderEffect.createRuntimeShaderEffect(effectShader, "content")
            effectGeometry = nextGeometry
            effectRecipe = scoped
            effectOpacity = opacity
            effectHighlight = highlight
            effectHighlightClearFraction = highlightFraction
            lastEffectShader = effectShader
        }
        return requireNotNull(cachedEffect)
    }

    internal fun paintMaterial(scope: DrawScope, recipe: NavigationMaterialRecipe, alpha: Float = 1f): Boolean =
        materialRenderer.draw(scope, recipe, alpha)

    internal fun drawMaterial(
        scope: DrawScope,
        shape: Shape,
        recipe: NavigationMaterialRecipe,
        bounds: Rect = Rect(Offset.Zero, scope.size),
        isDark: Boolean = false,
        alpha: Float = 1f,
        materialAlpha: Float = 1f,
        burnAlpha: Float = 1f,
        field: NativeGlassFilterField? = null,
        clearFraction: Float? = null,
    ) = drawInternal(scope, shape, bounds, isDark, alpha, recipe, materialAlpha, field, clearFraction)

    private fun drawInternal(
        scope: DrawScope,
        shape: Shape,
        bounds: Rect,
        isDark: Boolean,
        alpha: Float,
        recipe: NavigationMaterialRecipe?,
        materialAlpha: Float,
        field: NativeGlassFilterField? = null,
        clearFraction: Float? = null,
    ) {
        val canvas = scope.drawContext.canvas.nativeCanvas
        if (!canvas.isHardwareAccelerated || bounds.isEmpty) return
        val scopedRecipe = recipe?.let { materialRenderer.recipeForSize(it, bounds.size.minDimension / scope.density) }
        // Older HWUI receives the same material/edge function in materialEffect on its sampled input.
        if (Build.VERSION.SDK_INT < 36) return
        val renderer = blender ?: return
        val sourceField = field ?: (shape as? LiquidContactShape)?.let { navigationContainerGlassField(it) }
        val edgeShader = obtainShader(sourceField, effect = false)
        val nextGeometry = Geometry(shape, bounds.size, scope.layoutDirection, scope.density, scope.fontScale,
            scopedRecipe?.darkFraction ?: if (isDark) 1f else 0f, clearFraction?.coerceIn(0f, 1f) ?: if (scopedRecipe?.isClear == true) 1f else 0f,
            fieldSource = sourceField?.shaderFunctions)
        if (nextGeometry != geometry || lastMaskShader !== edgeShader) {
            prepareGeometry(edgeShader, nextGeometry, sourceField)
            path.rewind()
            when (val outline = shape.createOutline(bounds.size, scope.layoutDirection, scope)) {
                is Outline.Rectangle -> path.addRect(outline.rect)
                is Outline.Rounded -> path.addRoundRect(outline.roundRect)
                is Outline.Generic -> path.addPath(outline.path)
            }
            geometry = nextGeometry
            lastMaskShader = edgeShader
            edgePaint.shader = edgeShader
        }
        sourceField?.configure?.invoke(edgeShader)
        edgeShader.setFloatUniform("nativeEdgeOpacity", alpha.coerceIn(0f, 1f))
        renderer.prepare(scopedRecipe, materialAlpha)
        edgePaint.xfermode = renderer.shader
        val save = canvas.save()
        try {
            canvas.translate(bounds.left, bounds.top)
            canvas.clipPath(path.asAndroidPath())
            canvas.drawRect(0f, 0f, bounds.width, bounds.height, edgePaint)
        } finally {
            canvas.restoreToCount(save)
        }
    }

    private fun obtainShader(field: NativeGlassFilterField?, effect: Boolean): RuntimeShader {
        val cache = if (effect) effectShaders else maskShaders
        return cache.getOrPut(field?.shaderFunctions) {
            val source = field?.shaderFunctions?.let { it + CustomEdgeFieldAdapter } ?: DefaultEdgeFieldShader
            RuntimeShader(if (effect) NavigationMaterialShaderFunctions + source + NativeGlassBackgroundEdgeFunctions +
                NativeCasdfHighlightMaskFunctions + NativeVibrantColorMatrixFunctions + MaterialEdgeEffectMain
                else source + NativeGlassBackgroundEdgeFunctions + EdgeMaskMain)
        }
    }

    private fun prepareGeometry(shader: RuntimeShader, geometry: Geometry, field: NativeGlassFilterField?) {
        if (field == null) {
            val density = Density(geometry.density, geometry.fontScale)
            shader.setFloatUniform("size", geometry.size.width, geometry.size.height)
            shader.setFloatUniform("cornerRadii", glassCornerRadii(geometry.shape, geometry.size, geometry.direction, density))
        }
        shader.setFloatUniform("nativeEdgeDensity", geometry.density)
        shader.setFloatUniform("nativeEdgeDark", geometry.darkFraction)
        shader.setFloatUniform("nativeEdgeClear", geometry.clear)
        shader.setFloatUniform("nativeEdgeOrigin", geometry.origin.x, geometry.origin.y)
    }

    private fun configureHighlight(shader: RuntimeShader, highlight: Highlight?, geometry: Geometry?, clearFraction: Float?) {
        val style = highlight?.style
        val matrix = style?.nativeVibrantMatrix()
        val opacity = if (highlight != null && style != null && matrix != null && geometry != null && highlight.width.value > 0f)
            (highlight.alpha * style.color.alpha).coerceIn(0f, 1f) else 0f
        shader.setFloatUniform("nativeSurfaceHighlightOpacity", opacity)
        shader.setFloatUniform("nativeSurfaceHighlightHeight", (highlight?.width?.value ?: 1f) * (geometry?.density ?: 1f))
        val nativeStyle = style as? HighlightStyle.Default
        shader.setFloatUniform("nativeSurfaceHighlightAngle", (nativeStyle?.angle ?: 0f) * (PI / 180.0).toFloat())
        shader.setFloatUniform("nativeSurfaceHighlightCurvature", .75f * (nativeStyle?.falloff ?: 1f).coerceAtLeast(.01f))
        if (matrix != null && clearFraction != null) highlightInterpolation.configure(clearFraction, shader::setFloatUniform)
        else (matrix ?: NativeVibrantColorMatrix.RegularHighlight).configure(shader::setFloatUniform)
    }

    fun release() {
        geometry = null
        effectGeometry = null
        effectRecipe = null
        effectHighlight = null
        effectHighlightClearFraction = null
        cachedEffect = null
        maskShaders.clear()
        effectShaders.clear()
        lastMaskShader = null
        lastEffectShader = null
        edgePaint.shader = null
    }

    private data class Geometry(
        val shape: Shape,
        val size: Size,
        val direction: LayoutDirection,
        val density: Float,
        val fontScale: Float,
        val darkFraction: Float,
        val clear: Float,
        val origin: Offset = Offset.Zero,
        val fieldSource: String? = null,
    )

    @RequiresApi(36)
    private class NativeEdgeBlender {
        val shader = RuntimeXfermode(MaterialEdgeBlenderShader)
        private var recipe: NavigationMaterialRecipe? = null
        private var opacity = Float.NaN

        fun prepare(value: NavigationMaterialRecipe?, alpha: Float) {
            val nextOpacity = alpha.coerceIn(0f, 1f)
            val changed = if (value == null) recipe != null else !value.matches(recipe)
            if (changed || opacity != nextOpacity) {
                updateNavigationMaterialUniforms(value, nextOpacity, shader::setFloatUniform)
                recipe = value
                opacity = nextOpacity
            }
        }
    }

    private companion object {
        val NavigationMaterialRecipe.isClear: Boolean get() = nativeFace && saturation == 1.06f && white == 1.08f

        const val DefaultEdgeFieldShader = GlassSdfShader + """
float3 nativeEdgeField(float2 p) { return glassField(p, 0.0); }
"""
        const val CustomEdgeFieldAdapter = """
float3 nativeEdgeField(float2 p) { return navigationGlassField(p); }
"""
        const val EdgeMaskMain = """
half4 main(float2 p) {
    float2 masks = nativeGlassBackgroundEdgeMasks(p);
    return half4(masks.x, masks.y, nativeEdgeOpacity, 1.0);
}
"""
        const val MaterialEdgeEffectMain = """
uniform shader content;
uniform float nativeSurfaceHighlightOpacity;
uniform float nativeSurfaceHighlightHeight;
uniform float nativeSurfaceHighlightAngle;
uniform float nativeSurfaceHighlightCurvature;
half4 nativeMaterialEdgeForAppearance(half4 sampled, float2 p, bool alternate) {
    half4 face = navigationMaterialFaceForAppearance(sampled, alternate);
    if (nativeEdgeOpacity > 0.0) face = nativeGlassBackgroundEdges(face, p);
    half4 result = materialOpacity > 0.0 ? navigationMaterialFinishForAppearance(sampled, face, alternate) : face;
    if (nativeSurfaceHighlightOpacity > 0.0) {
        float3 field = nativeEdgeField(p - nativeEdgeOrigin);
        float mask = nativeCasdfHighlightCoverage(field.x, field.yz, nativeSurfaceHighlightHeight,
            nativeSurfaceHighlightAngle, nativeSurfaceHighlightCurvature) * nativeSurfaceHighlightOpacity;
        result = half4(nativeVibrantColorMatrixOver(float4(0.0, 0.0, 0.0, mask), float4(result), float4(result)));
    }
    return result;
}
half4 main(float2 p) {
    half4 sampled = navigationWorkingToNative(content.eval(p));
    half4 result = nativeMaterialEdgeForAppearance(sampled, p, false);
    if (materialAppearanceMix > 0.0) result = half4(mix(float4(result),
        float4(nativeMaterialEdgeForAppearance(sampled, p, true)), materialAppearanceMix));
    return navigationNativeToWorking(result);
}
"""
        const val MaterialEdgeBlenderShader = NavigationMaterialShaderFunctions + DefaultEdgeFieldShader + NativeGlassBackgroundEdgeFunctions + """
half4 nativeMaterialEdgeForAppearance(half4 sampled, half4 masks, bool alternate) {
    half4 face = navigationMaterialFaceForAppearance(sampled, alternate);
    face = nativeApplyGlassBackgroundEdges(face, float2(masks.r, masks.g), float(masks.b));
    return materialOpacity > 0.0 ? navigationMaterialFinishForAppearance(sampled, face, alternate) : face;
}
half4 main(half4 src, half4 dst) {
    dst = navigationWorkingToNative(dst);
    half4 result = nativeMaterialEdgeForAppearance(dst, src, false);
    if (materialAppearanceMix > 0.0) result = half4(mix(float4(result),
        float4(nativeMaterialEdgeForAppearance(dst, src, true)), materialAppearanceMix));
    return navigationNativeToWorking(result);
}
"""
    }
}

/** The menu skin is one clockwise contour, so nonzero winding gives its exact union. */
fun expandedLiquidMenuShadowHole(outline: android.graphics.Path, spread: Float): android.graphics.Path {
    val bounds = android.graphics.RectF().also { outline.computeBounds(it, true) }
    return android.graphics.Path().apply {
        fillType = android.graphics.Path.FillType.WINDING
        for (x in floatArrayOf(-spread, spread)) {
            for (y in floatArrayOf(-spread, spread)) addPath(outline, x, y)
        }
        // Keep the connecting strips in the same winding path. PathOp intersections
        // of nearly coincident morph edges can drop large regions even on success.
        addRect(bounds.centerX() - spread, bounds.top - spread,
            bounds.centerX() + spread, bounds.bottom + spread, android.graphics.Path.Direction.CW)
        addRect(bounds.left - spread, bounds.centerY() - spread,
            bounds.right + spread, bounds.centerY() + spread, android.graphics.Path.Direction.CW)
    }
}
