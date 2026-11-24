package io.github.neoruaa.glasmio.glass

import io.github.neoruaa.glasmio.glass.render.isRuntimeShaderSupported
import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.icons.*

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RestrictTo
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint as ComposePaint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.abs
import io.github.neoruaa.glasmio.glass.render.highlight.NativeGlassEdgeFunctions
import io.github.neoruaa.glasmio.glass.render.optics.GlassSdfShader
import io.github.neoruaa.glasmio.glass.render.optics.glassCornerRadii

internal data class NativeNavigationShadowProfile(val radius: Float, val opacity: Float)

/** Calibration through the captured 50, 150 and 200 pt native surfaces. */
internal fun navigationNativeShadowProfile(minimumDimension: Float, material: NavigationGlassMaterial?): NativeNavigationShadowProfile {
    if (material == NavigationGlassMaterial.Clear) return NativeNavigationShadowProfile(0f, 0f)
    val t = when (material) {
        NavigationGlassMaterial.RegularSmall -> 0f
        NavigationGlassMaterial.RegularMedium, NavigationGlassMaterial.RegularLarge -> 1f
        else -> ((minimumDimension.takeIf { it.isFinite() } ?: 50f) - 64f).div(96f).coerceIn(0f, 1f)
    }
    return NativeNavigationShadowProfile(4f + 20f * t, .04f + .56f * t)
}

/** Window padding for an adaptive Regular shadow around a body measured in physical pixels. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
fun navigationRegularGlassShadowPadding(minimumBodySize: Int, density: Float): Int {
    val radius = navigationNativeShadowProfile(minimumBodySize / density, material = null).radius
    return ceil(navigationShadowSupport(radius, density)).toInt()
}

// The native key/fill offset and one device pixel for antialiasing.
internal fun navigationShadowSupport(radius: Float, density: Float): Float =
    if (radius > 0f) radius * density * 2f + 8f * density + 1f else 0f

/** Exterior-only shadow with a retained GPU mask and native material profile support. */
class NavigationDropShadowRenderer(
    private val renderScale: Float = 1f,
    private val cacheBlurResult: Boolean = false,
    private val shadowRadius: Dp = 4.dp,
    private val shadowOffsetY: Dp = 8.dp,
    private val shadowAlpha: Float = 0.04f,
    private val shadowOffsetX: Dp = 0.dp,
    private val shadowColor: ComposeColor = ComposeColor.Black,
    private val shadowBlendMode: ComposeBlendMode = ComposeBlendMode.SrcOver,
    private val useNativeProfile: Boolean = false,
    private val nativeMaterial: NavigationGlassMaterial? = null,
    private val gaussianRadiusIsSigma: Boolean = false,
) {
    init {
        require(renderScale.isFinite() && renderScale > 0f)
    }

    private val mask = RenderNode("Navigation exterior drop shadow")
    private var blurResult: RenderNode? = null
    private var blurResultDirty = true
    private var transformNode: RenderNode? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = shadowColor.copy(alpha = 1f).toArgb() }
    private val compositingPaint = ComposePaint().apply {
        blendMode = shadowBlendMode
        filterQuality = androidx.compose.ui.graphics.FilterQuality.Low
    }
    private val nativeShaders = mutableMapOf<String?, RuntimeShader>()
    private val useNativeShader = useNativeProfile && isRuntimeShaderSupported()
    private val nativePaint = if (useNativeShader) Paint(Paint.ANTI_ALIAS_FLAG) else null
    private var outlinePath = android.graphics.Path()
    private var key: GeometryKey? = null
    private var blurDensity = Float.NaN
    private var activeRadius = Float.NaN
    private var activeAlpha = shadowAlpha
    private var padding = 0f
    private var verticalOffset = 0f
    private var horizontalOffset = 0f
    private var maskWidth = 0
    private var maskHeight = 0
    private var maskClipBounds = android.graphics.Rect()
    private var maskCompositingConfigured = false
    private var transformWidth = 0
    private var transformHeight = 0

    fun draw(
        scope: DrawScope,
        shape: Shape,
        bounds: Rect = Rect(Offset.Zero, scope.size),
        alpha: Float = 1f,
    ) = draw(scope, shape, bounds, alpha, null, null)

    internal fun draw(
        scope: DrawScope,
        shape: Shape,
        bounds: Rect,
        alpha: Float,
        field: NativeGlassFilterField?,
        profileOverride: NativeNavigationShadowProfile? = null,
    ) {
        val canvas = scope.drawContext.canvas.nativeCanvas
        if (!canvas.isHardwareAccelerated || bounds.isEmpty || alpha <= 0f) return
        prepare(scope, shape, bounds.size, field, profileOverride)
        if (activeAlpha <= 0f) return
        val shadow = prepareShadowNode()
        if (shadow !== mask || !maskCompositingConfigured) {
            shadow.setUseCompositingLayer(true, compositingPaint.asFrameworkPaint())
            if (shadow === mask) maskCompositingConfigured = true
        }
        shadow.alpha = activeAlpha * alpha.coerceIn(0f, 1f)
        val save = canvas.save()
        try {
            canvas.translate(bounds.left, bounds.top)
            drawMask(canvas, shadow)
        } finally {
            canvas.restoreToCount(save)
        }
    }

    fun drawTransformed(
        scope: DrawScope,
        shape: Shape,
        transform: GraphicsLayerBlockScope,
    ) {
        if (transform.alpha == 1f && transform.scaleX == 1f && transform.scaleY == 1f &&
            transform.translationX == 0f && transform.translationY == 0f &&
            transform.rotationX == 0f && transform.rotationY == 0f && transform.rotationZ == 0f
        ) {
            draw(scope, shape)
            return
        }
        val canvas = scope.drawContext.canvas.nativeCanvas
        if (!canvas.isHardwareAccelerated || scope.size.minDimension <= 0f || transform.alpha <= 0f) return
        prepare(scope, shape, scope.size)
        if (activeAlpha <= 0f) return
        val shadow = prepareShadowNode()
        if (shadow !== mask || !maskCompositingConfigured) {
            shadow.setUseCompositingLayer(true, compositingPaint.asFrameworkPaint())
            if (shadow === mask) maskCompositingConfigured = true
        }
        shadow.alpha = activeAlpha
        val node = transformNode ?: RenderNode("Navigation drop shadow transform").also {
            it.setClipToBounds(false)
            transformNode = it
        }
        val requiredWidth = ceil(scope.size.width + padding * 2f).toInt()
        val requiredHeight = ceil(scope.size.height + padding * 2f + verticalOffset).toInt()
        transformWidth = maxOf(transformWidth, (requiredWidth + 63) / 64 * 64)
        transformHeight = maxOf(transformHeight, (requiredHeight + 63) / 64 * 64)
        val width = transformWidth
        val height = transformHeight
        node.setPosition(0, 0, width, height)
        // Keep the filtered mask cached, but always refresh this small display list:
        // HWUI can evict a child's native list while a retained surface is hidden.
        val recording = node.beginRecording(width, height)
        try {
            recording.translate(padding, padding)
            drawMask(recording, shadow)
        } finally {
            node.endRecording()
        }
        node.alpha = transform.alpha.coerceIn(0f, 1f)
        node.scaleX = transform.scaleX
        node.scaleY = transform.scaleY
        node.translationX = transform.translationX
        node.translationY = transform.translationY
        node.rotationX = transform.rotationX
        node.rotationY = transform.rotationY
        node.rotationZ = transform.rotationZ
        node.cameraDistance = transform.cameraDistance
        node.pivotX = padding + scope.size.width * transform.transformOrigin.pivotFractionX
        node.pivotY = padding + scope.size.height * transform.transformOrigin.pivotFractionY
        val save = canvas.save()
        try {
            canvas.translate(-padding, -padding)
            canvas.drawRenderNode(node)
        } finally {
            canvas.restoreToCount(save)
        }
    }

    private fun prepare(
        scope: DrawScope,
        shape: Shape,
        size: Size,
        fieldOverride: NativeGlassFilterField? = null,
        profileOverride: NativeNavigationShadowProfile? = null,
    ) {
        val nextKey = GeometryKey(shape, size, scope.layoutDirection, scope.density, scope.fontScale,
            fieldOverride?.shaderFunctions, profileOverride, (shape as? LiquidContactShape)?.nativeUnion)
        if (nextKey == key && mask.hasDisplayList()) return
        if (!useNativeShader) {
            val outline = shape.createOutline(size, scope.layoutDirection, scope)
            outlinePath = Path().apply {
                when (outline) {
                    is Outline.Rectangle -> addRect(outline.rect)
                    is Outline.Rounded -> addRoundRect(outline.roundRect)
                    is Outline.Generic -> addPath(outline.path)
                }
            }.asAndroidPath()
        }
        val bodyExtent = (shape as? NavigationGlassOutlineShape)?.bodyDimension(size, scope)
            ?: ((shape as? NavigationGlassUnionShape)?.minimumDimension ?: size.minDimension) / scope.density
        val profile = profileOverride ?: if (useNativeShader) navigationNativeShadowProfile(bodyExtent, nativeMaterial) else null
        val radiusPoints = profile?.radius ?: shadowRadius.value
        activeAlpha = if (profile != null) profile.opacity * 0.3f else shadowAlpha
        if (blurDensity != scope.density || activeRadius != radiusPoints) {
            // SDF profiles retain native inverse-radius packing; ordinary CALayer sigma is opt-in.
            val sigma = (radiusPoints * if (useNativeShader || gaussianRadiusIsSigma) 1f else 0.5f).coerceAtLeast(0f) * scope.density
            val radius = ((sigma * renderScale - 0.5f) / 0.57735f).coerceAtLeast(0.001f)
            mask.setRenderEffect(if (useNativeShader) null else RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
            verticalOffset = shadowOffsetY.value * scope.density
            horizontalOffset = shadowOffsetX.value * scope.density
            padding = if (nativePaint != null) {
                ceil(maxOf(2f * sigma + abs(horizontalOffset) + abs(verticalOffset) + 1f, 2f))
            } else {
                ceil(3f * sigma + maxOf(abs(horizontalOffset), abs(verticalOffset)))
            }
            blurDensity = scope.density
            activeRadius = radiusPoints
        }
        if (nativePaint != null && isRuntimeShaderSupported()) {
            val field = fieldOverride ?: when (shape) {
                is LiquidContactShape -> navigationContainerGlassField(shape, minimumBand = padding)
                is ToolbarActionShape -> navigationToolbarGlassField(shape, minimumBand = padding)
                is NavigationGlassUnionShape -> shape.nativeField()
                is NavigationGlassOutlineShape -> shape.nativeField(size, scope.layoutDirection, scope, padding)
                else -> null
            }
            val shader = nativeShaders.getOrPut(field?.shaderFunctions) {
                val source = field?.shaderFunctions?.let { functions ->
                    val distanceExpr = when {
                        functions.contains("navigationGlassShadowDistance(") -> "navigationGlassShadowDistance(p)"
                        functions.contains("navigationGlassShapeField(") -> "navigationGlassShapeField(p).x"
                        else -> "navigationGlassField(p).x"
                    }
                    functions + "\nfloat nativeShadowDistance(float2 p) { return $distanceExpr; }\n"
                } ?: (GlassSdfShader + "\nfloat nativeShadowDistance(float2 p) { return glassDistance(p); }\n")
                RuntimeShader(source + NativeGlassEdgeFunctions + NativeExteriorShadowMain)
            }
            if (field == null) {
                shader.setFloatUniform("size", size.width, size.height)
                shader.setFloatUniform("cornerRadii", glassCornerRadii(shape, size, scope.layoutDirection, scope))
            } else field.configure(shader)
            shader.setFloatUniform("shadowOffset", horizontalOffset, verticalOffset)
            shader.setFloatUniform("shadowInvRadius", if (radiusPoints > 0f) 1f / (radiusPoints * scope.density) else 0f)
            shader.setFloatUniform("shadowCutScale", renderScale)
            nativePaint.shader = shader
        }
        val requiredWidth = ceil((size.width + padding * 2f) * renderScale).toInt()
        val requiredHeight = ceil((size.height + padding * 2f) * renderScale).toInt()
        maskClipBounds.set(0, 0, requiredWidth, requiredHeight)
        // Retain allocation buckets through a morph without scaling the outline or its blur.
        // Transparent slack extends the same transparent CLAMP boundary as the original mask.
        maskWidth = maxOf(maskWidth, (requiredWidth + 63) / 64 * 64)
        maskHeight = maxOf(maskHeight, (requiredHeight + 63) / 64 * 64)
        val width = maskWidth
        val height = maskHeight
        mask.setPosition(0, 0, width, height)
        val recording = mask.beginRecording(width, height)
        try {
            recording.scale(renderScale, renderScale)
            recording.translate(padding, padding)
            if (nativePaint != null) {
                recording.drawRect(-padding, -padding, size.width + padding, size.height + padding, nativePaint)
            } else recording.drawPath(outlinePath, fill)
        } finally {
            mask.endRecording()
        }
        key = nextKey
        blurResultDirty = true
    }

    private fun prepareShadowNode(): RenderNode {
        if (!cacheBlurResult || useNativeShader) return mask
        // Cache opaque filtered coverage so live opacity keeps the original post-blur
        // quantization. The native-resolution body cut stays in the outer draw pass.
        mask.alpha = 1f
        val node = blurResult ?: RenderNode("Navigation exterior blurred coverage").also {
            it.setUseCompositingLayer(true, null)
            blurResult = it
        }
        if (blurResultDirty || !node.hasDisplayList()) {
            node.setPosition(0, 0, maskWidth, maskHeight)
            val recording = node.beginRecording(maskWidth, maskHeight)
            try {
                recording.drawRenderNode(mask)
            } finally {
                node.endRecording()
            }
            blurResultDirty = false
        }
        return node
    }

    private fun drawMask(canvas: Canvas, shadow: RenderNode) {
        val save = canvas.save()
        try {
            // Non-native fallback masks clip out the body at native resolution after blur;
            // native SDF masks perform the exact subpixel interior cut inside NativeExteriorShadowMain.
            if (!useNativeShader) canvas.clipOutPath(outlinePath)
            // The native SDF already includes the physical offset in shadowOffset.
            canvas.translate(
                (if (useNativeProfile) 0f else horizontalOffset) - padding,
                (if (useNativeProfile) 0f else verticalOffset) - padding,
            )
            canvas.scale(1f / renderScale, 1f / renderScale)
            // Preserve the original RenderNode's exact output bounds even when its
            // retained allocation is larger than this frame's mask.
            canvas.clipRect(maskClipBounds)
            canvas.drawRenderNode(shadow)
        } finally {
            canvas.restoreToCount(save)
        }
    }

    fun release() {
        mask.discardDisplayList()
        blurResult?.discardDisplayList()
        transformNode?.discardDisplayList()
        key = null
        blurResultDirty = true
        maskWidth = 0
        maskHeight = 0
        maskClipBounds.setEmpty()
        maskCompositingConfigured = false
        nativeShaders.clear()
        nativePaint?.shader = null
        transformWidth = 0
        transformHeight = 0
    }

    private data class GeometryKey(
        val shape: Shape,
        val size: Size,
        val layoutDirection: LayoutDirection,
        val density: Float,
        val fontScale: Float,
        val fieldFunctions: String?,
        val profileOverride: NativeNavigationShadowProfile?,
        val union: NavigationNativeUnion?,
    )

}

private const val NativeExteriorShadowMain = """
uniform float2 shadowOffset;
uniform float shadowInvRadius;
uniform float shadowCutScale;
half4 main(float2 p) {
    float body = nativeShadowDistance(p);
    float cut = clamp(body * shadowCutScale + 0.5, 0.0, 1.0);
    if (cut <= 0.0) return half4(0.0);
    float d = nativeShadowDistance(p - shadowOffset);
    float coverage = shadowInvRadius > 0.0 ? nativeGaussianTail(d * shadowInvRadius) : 0.0;
    return half4(0.0, 0.0, 0.0, coverage * cut);
}
"""

/** Draws outside the backdrop clip while sharing only its live press transform. */
fun Modifier.navigationGlassDropShadow(
    shape: () -> Shape,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    cacheBlurResult: Boolean = false,
    shadowRadius: Dp = 4.dp,
    shadowOffsetY: Dp = 8.dp,
    shadowAlpha: Float = 0.04f,
    useNativeProfile: Boolean = false,
    nativeMaterial: NavigationGlassMaterial? = null,
    renderScale: Float = 1f,
): Modifier = then(NavigationGlassDropShadowElement(shape, layerBlock, cacheBlurResult, shadowRadius, shadowOffsetY, shadowAlpha, useNativeProfile, nativeMaterial, renderScale))

private data class NavigationGlassDropShadowElement(
    val shape: () -> Shape,
    val layerBlock: (GraphicsLayerScope.() -> Unit)?,
    val cacheBlurResult: Boolean,
    val shadowRadius: Dp,
    val shadowOffsetY: Dp,
    val shadowAlpha: Float,
    val useNativeProfile: Boolean,
    val nativeMaterial: NavigationGlassMaterial?,
    val renderScale: Float,
) : ModifierNodeElement<NavigationGlassDropShadowNode>() {
    override fun create() = NavigationGlassDropShadowNode(shape, layerBlock, cacheBlurResult, shadowRadius, shadowOffsetY, shadowAlpha, useNativeProfile, nativeMaterial, renderScale)

    override fun update(node: NavigationGlassDropShadowNode) {
        node.shape = shape
        node.layerBlock = layerBlock
        node.updateConfiguration(cacheBlurResult, shadowRadius, shadowOffsetY, shadowAlpha, useNativeProfile, nativeMaterial, renderScale)
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "navigationGlassDropShadow"
        properties["shape"] = shape
        properties["layerBlock"] = layerBlock
        properties["cacheBlurResult"] = cacheBlurResult
        properties["renderScale"] = renderScale
    }
}

private class NavigationGlassDropShadowNode(
    var shape: () -> Shape,
    var layerBlock: (GraphicsLayerScope.() -> Unit)?,
    private var cacheBlurResult: Boolean,
    private var shadowRadius: Dp,
    private var shadowOffsetY: Dp,
    private var shadowAlpha: Float,
    private var useNativeProfile: Boolean,
    private var nativeMaterial: NavigationGlassMaterial?,
    private var renderScale: Float,
) : DrawModifierNode, Modifier.Node() {
    private var renderer = NavigationDropShadowRenderer(
        renderScale = renderScale,
        cacheBlurResult = cacheBlurResult,
        shadowRadius = shadowRadius,
        shadowOffsetY = shadowOffsetY,
        shadowAlpha = shadowAlpha,
        useNativeProfile = useNativeProfile,
        nativeMaterial = nativeMaterial,
    )
    private val layerScope = GraphicsLayerBlockScope()

    fun updateConfiguration(enabled: Boolean, radius: Dp, offsetY: Dp, alpha: Float, native: Boolean, material: NavigationGlassMaterial?, scale: Float) {
        if (cacheBlurResult == enabled && shadowRadius == radius && shadowOffsetY == offsetY &&
            shadowAlpha == alpha && useNativeProfile == native && nativeMaterial == material && renderScale == scale) return
        renderer.release()
        cacheBlurResult = enabled
        shadowRadius = radius
        shadowOffsetY = offsetY
        shadowAlpha = alpha
        useNativeProfile = native
        nativeMaterial = material
        renderScale = scale
        renderer = NavigationDropShadowRenderer(
            renderScale = scale,
            cacheBlurResult = enabled,
            shadowRadius = radius,
            shadowOffsetY = offsetY,
            shadowAlpha = alpha,
            useNativeProfile = native,
            nativeMaterial = material,
        )
    }

    override fun ContentDrawScope.draw() {
        val block = layerBlock
        if (block == null) {
            renderer.draw(this, shape())
        } else {
            layerScope.reset(this, size)
            block(layerScope)
            renderer.drawTransformed(this, shape(), layerScope)
        }
        drawContent()
    }

    override fun onDetach() {
        renderer.release()
    }
}

/** The hidden sampling source uses the same native default shadow as visible glass. */
private class NavigationLensSourceDropShadowRenderer {
    private val renderer = NavigationDropShadowRenderer(
        cacheBlurResult = true,
        shadowRadius = 4.dp,
        shadowOffsetY = 8.dp,
        shadowAlpha = 0.04f,
        useNativeProfile = true,
    )

    fun draw(scope: DrawScope, shape: Shape) = renderer.draw(scope, shape)

    fun release() = renderer.release()
}

/** Draws the hidden lens source's default shadow with GPU filtering and the original body cut. */
fun Modifier.navigationLensSourceDropShadow(shape: () -> Shape): Modifier =
    then(NavigationLensSourceDropShadowElement(shape))

private data class NavigationLensSourceDropShadowElement(
    val shape: () -> Shape,
) : ModifierNodeElement<NavigationLensSourceDropShadowNode>() {
    override fun create() = NavigationLensSourceDropShadowNode(shape)

    override fun update(node: NavigationLensSourceDropShadowNode) {
        node.shape = shape
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "navigationLensSourceDropShadow"
        properties["shape"] = shape
    }
}

private class NavigationLensSourceDropShadowNode(
    var shape: () -> Shape,
) : DrawModifierNode, Modifier.Node() {
    private val renderer = NavigationLensSourceDropShadowRenderer()

    override fun ContentDrawScope.draw() {
        renderer.draw(this, shape())
        drawContent()
    }

    override fun onDetach() {
        renderer.release()
    }
}
