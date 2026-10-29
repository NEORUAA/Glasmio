package io.github.neoruaa.glasmio.glass

import io.github.neoruaa.glasmio.glass.render.ProvideGlassMaterialContext

import io.github.neoruaa.glasmio.glass.render.rememberGlassMaterialContext

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.icons.*
import io.github.neoruaa.glasmio.glass.render.BackdropLuminance
import io.github.neoruaa.glasmio.glass.render.LocalBackdropLuminance
import io.github.neoruaa.glasmio.glass.render.rememberBackdropLuminance
import io.github.neoruaa.glasmio.glass.render.rememberAdaptiveGlassDark
import io.github.neoruaa.glasmio.glass.render.GlassMaterialContext
import io.github.neoruaa.glasmio.glass.render.rememberResolvedGlassMaterialContext

import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.emptyBackdrop
import io.github.neoruaa.glasmio.glass.render.drawBackdrop
import io.github.neoruaa.glasmio.glass.render.effects.blur
import io.github.neoruaa.glasmio.glass.render.effects.lens
import io.github.neoruaa.glasmio.glass.render.effects.vibrancy
import io.github.neoruaa.glasmio.glass.render.highlight.Highlight
import io.github.neoruaa.glasmio.glass.render.highlight.HighlightStyle
import io.github.neoruaa.glasmio.glass.render.shadow.Shadow
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangularShape
import io.github.neoruaa.glasmio.glass.InteractiveHighlight
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.tanh

data class GlassPressHighlight(
    val progress: Float = 0f,
    val position: Offset = Offset(0.5f, 0.5f),
    val glowProgress: Float = progress,
    val dissipationProgress: Float = 0f,
)

enum class GlassEmphasis {
    Regular,
    Prominent,
}

enum class GlassSurfaceStyle {
    @Deprecated("Use Navigation; Standard resolves to the same material.", ReplaceWith("GlassSurfaceStyle.Navigation"))
    Standard,
    Navigation,
}

val LocalGlassSurfaceStyle = staticCompositionLocalOf { GlassSurfaceStyle.Navigation }
val LocalGlassSurfaceBrightness = staticCompositionLocalOf { 0f }

/** Adapt semantic foreground roles without changing the environment used by hysteresis. */
internal fun adaptiveGlassContentColors(
    colors: GlassColors,
    materialDark: Boolean,
    enabled: Boolean,
    darkFraction: Float = if (materialDark) 1f else 0f,
): GlassColors {
    if (!enabled) return colors
    val environment = defaultGlassColors(colors.isDark, colors.accent)
    val opposite = defaultGlassColors(!colors.isDark, colors.accent)
    val light = defaultGlassColors(false, colors.accent)
    val dark = defaultGlassColors(true, colors.accent)
    val fraction = (darkFraction.takeIf { it.isFinite() } ?: if (materialDark) 1f else 0f).coerceIn(0f, 1f)
    val resolved = when (fraction) {
        0f -> light
        1f -> dark
        else -> light.copy(
            content = androidx.compose.ui.graphics.lerp(light.content, dark.content, fraction),
            secondaryContent = androidx.compose.ui.graphics.lerp(light.secondaryContent, dark.secondaryContent, fraction),
            tertiaryContent = androidx.compose.ui.graphics.lerp(light.tertiaryContent, dark.tertiaryContent, fraction),
        )
    }
    return colors.copy(
        content = if (colors.content == environment.content || colors.content == opposite.content)
            resolved.content else colors.content,
        secondaryContent = if (colors.secondaryContent == environment.secondaryContent || colors.secondaryContent == opposite.secondaryContent)
            resolved.secondaryContent else colors.secondaryContent,
        tertiaryContent = if (colors.tertiaryContent == environment.tertiaryContent || colors.tertiaryContent == opposite.tertiaryContent)
            resolved.tertiaryContent else colors.tertiaryContent,
    )
}

/** Global opacity control for the navigation glass box-shadow stack. */
const val GlassBoxShadowAlpha = 0.3f

/** Shared liquid drag deformation used by glass controls and animated glass shells. */
fun GraphicsLayerScope.applyGlassDragScale(
    pressProgress: Float,
    offset: Offset,
    nativeButtonLift: Boolean = false,
    allowPressOvershoot: Boolean = false,
) {
    val progress = if (allowPressOvershoot) pressProgress else pressProgress.coerceIn(0f, 1f)
    val controlHeight = size.height.coerceAtLeast(1f)
    val lift = if (nativeButtonLift) nativeFlexLiftPoints(size, density) * density / size.maxDimension.coerceAtLeast(1f)
        else 4.dp.toPx() / controlHeight
    val scale = lerp(1f, 1f + lift, progress)
    val maxOffset = size.minDimension.coerceAtLeast(1f)
    translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
    translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)
    val maxDragScale = 4.dp.toPx() / controlHeight
    val angle = atan2(offset.y, offset.x)
    scaleX = scale
    scaleY = scale
    scaleX += maxDragScale * abs(cos(angle) * offset.x / size.maxDimension.coerceAtLeast(1f)) *
        (size.width / controlHeight).fastCoerceAtMost(1f)
    scaleY += maxDragScale * abs(sin(angle) * offset.y / size.maxDimension.coerceAtLeast(1f)) *
        (controlHeight / size.width.coerceAtLeast(1f)).fastCoerceAtMost(1f)
}

/**
 * Draws the crisp part of the navigation box shadow outside the glass outline.
 *
 * This is deliberately a separate expanded layer. Drawing the same strokes from
 * `onDrawSurface`/`onDrawFront` leaves them inside the backdrop's clipped layer, which
 * produces the pale fringe visible at the left and right edges of circles and capsules.
 */
fun Modifier.navigationGlassBoxShadow(
    shape: () -> Shape,
    alpha: Float,
    layerBlock: (GraphicsLayerScope.() -> Unit)?,
    outlineEnabled: Boolean = true,
): Modifier = then(NavigationGlassBoxShadowElement(shape, alpha, layerBlock, outlineEnabled))

private class NavigationGlassBoxShadowElement(
    private val shape: () -> Shape,
    private val alpha: Float,
    private val layerBlock: (GraphicsLayerScope.() -> Unit)?,
    private val outlineEnabled: Boolean,
) : ModifierNodeElement<NavigationGlassBoxShadowNode>() {

    override fun create(): NavigationGlassBoxShadowNode =
        NavigationGlassBoxShadowNode(shape, alpha, layerBlock, outlineEnabled)

    override fun update(node: NavigationGlassBoxShadowNode) {
        node.shape = shape
        node.alpha = alpha
        node.layerBlock = layerBlock
        node.outlineEnabled = outlineEnabled
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "navigationGlassBoxShadow"
        properties["shape"] = shape
        properties["alpha"] = alpha
        properties["layerBlock"] = layerBlock
    }

    override fun equals(other: Any?): Boolean =
        other is NavigationGlassBoxShadowElement &&
            shape == other.shape &&
            alpha == other.alpha &&
            layerBlock == other.layerBlock && outlineEnabled == other.outlineEnabled

    override fun hashCode(): Int {
        var result = shape.hashCode()
        result = 31 * result + alpha.hashCode()
        result = 31 * result + (layerBlock?.hashCode() ?: 0)
        result = 31 * result + outlineEnabled.hashCode()
        return result
    }
}

private class NavigationGlassBoxShadowNode(
    var shape: () -> Shape,
    var alpha: Float,
    var layerBlock: (GraphicsLayerScope.() -> Unit)?,
    var outlineEnabled: Boolean,
) : DrawModifierNode, Modifier.Node() {

    private var sideLayer: GraphicsLayer? = null
    private var outlineLayer: GraphicsLayer? = null
    private val layerScope = GraphicsLayerBlockScope()
    private var recordedSize = Size.Unspecified
    private var recordedShape: Shape? = null
    private var recordedAlpha = Float.NaN
    private var recordedDensity = Float.NaN
    private var recordedFontScale = Float.NaN
    private var recordedLayoutDirection: LayoutDirection? = null
    private var allocationSize = IntSize.Zero

    private val sidePaint = Paint().apply {
        style = PaintingStyle.Fill
        isAntiAlias = true
    }
    private val outlinePaint = Paint().apply {
        style = PaintingStyle.Stroke
        isAntiAlias = true
    }
    private val clearPaint = Paint().apply {
        style = PaintingStyle.Fill
        blendMode = BlendMode.Clear
        isAntiAlias = true
    }

    override fun ContentDrawScope.draw() {
        val sideLayer = sideLayer ?: return drawContent()
        val outlineLayer = outlineLayer ?: return drawContent()
        val shadowAlpha = alpha.coerceIn(0f, 1f)
        if (shadowAlpha <= 0f || size.minDimension <= 0f) return drawContent()

        // Observe the live transform before skipping invisible geometry so the next press redraws.
        layerScope.reset(this, size)
        layerBlock?.invoke(layerScope)
        if (layerScope.alpha <= 0f) return drawContent()

        val sideOffset = 0.7.dp.toPx()
        val outlineWidth = 0.65.dp.toPx()
        val padding = ceil(sideOffset + outlineWidth / 2f + 1f).toInt()
        val layerSize = IntSize(
            ceil(size.width).toInt() + padding * 2,
            ceil(size.height).toInt() + padding * 2,
        )
        // Keep backing textures through a morph instead of allocating a new width on
        // each frame. Geometry and transform pivots continue to use the exact size.
        allocationSize = IntSize(
            maxOf(allocationSize.width, (layerSize.width + 31) / 32 * 32),
            maxOf(allocationSize.height, (layerSize.height + 31) / 32 * 32),
        )
        val currentShape = shape()
        if (recordedSize != size || recordedShape != currentShape || recordedAlpha != shadowAlpha ||
            recordedDensity != density || recordedFontScale != fontScale ||
            recordedLayoutDirection != layoutDirection
        ) {
            val outline = currentShape.createOutline(size, layoutDirection, this)

            sidePaint.color = Color(0xFF3F3F3F).copy(alpha = shadowAlpha)
            outlinePaint.color = Color(0xFF6E6E6E).copy(alpha = shadowAlpha)
            outlinePaint.strokeWidth = outlineWidth

            // The side shadow is a horizontally expanded copy of the shape. Its height stays
            // exactly the element height, so it cannot create a shadow line on the top or bottom.
            // Clearing the original shape leaves only the two exposed side capsules.
            val drawingWidth = layerSize.width.toFloat()
            sideLayer.record(allocationSize) {
                translate(padding.toFloat(), padding.toFloat()) {
                    drawContext.canvas.save()
                    drawContext.canvas.translate(drawingWidth / 2f, 0f)
                    drawContext.canvas.scale(
                        (drawingWidth + sideOffset * 2f) / drawingWidth,
                        1f,
                    )
                    drawContext.canvas.translate(-drawingWidth / 2f, 0f)
                    drawContext.canvas.drawOutline(outline, sidePaint)
                    drawContext.canvas.restore()
                    drawContext.canvas.drawOutline(outline, clearPaint)
                }
            }

            outlineLayer.record(allocationSize) {
                translate(padding.toFloat(), padding.toFloat()) {
                    drawContext.canvas.drawOutline(outline, outlinePaint)

                    // Keep only the part of the outline that is outside the glass. This makes the
                    // border an outer stroke instead of an inset stroke.
                    drawContext.canvas.drawOutline(outline, clearPaint)
                }
            }

            // A modal moves in window space while this local outline remains unchanged.
            // Reuse the two offscreen border textures instead of clearing/rasterizing two
            // screen-sized buffers on every animation frame. Press transforms stay live below.
            recordedSize = size
            recordedShape = currentShape
            recordedAlpha = shadowAlpha
            recordedDensity = density
            recordedFontScale = fontScale
            recordedLayoutDirection = layoutDirection
        }

        // The outer layers are separate from drawBackdrop's own graphics layer, so apply the
        // exact same DragScale block to both. The glass layer remains untouched; this only keeps
        // the external shadow in lockstep with it.
        val pivotOffset = Offset(
            layerScope.transformOrigin.pivotFractionX * layerSize.width,
            layerScope.transformOrigin.pivotFractionY * layerSize.height,
        )
        sideLayer.alpha = layerScope.alpha
        sideLayer.scaleX = layerScope.scaleX
        sideLayer.scaleY = layerScope.scaleY
        sideLayer.translationX = layerScope.translationX
        sideLayer.translationY = layerScope.translationY
        sideLayer.rotationX = layerScope.rotationX
        sideLayer.rotationY = layerScope.rotationY
        sideLayer.rotationZ = layerScope.rotationZ
        sideLayer.cameraDistance = layerScope.cameraDistance
        sideLayer.pivotOffset = pivotOffset
        outlineLayer.alpha = layerScope.alpha
        outlineLayer.scaleX = layerScope.scaleX
        outlineLayer.scaleY = layerScope.scaleY
        outlineLayer.translationX = layerScope.translationX
        outlineLayer.translationY = layerScope.translationY
        outlineLayer.rotationX = layerScope.rotationX
        outlineLayer.rotationY = layerScope.rotationY
        outlineLayer.rotationZ = layerScope.rotationZ
        outlineLayer.cameraDistance = layerScope.cameraDistance
        outlineLayer.pivotOffset = pivotOffset

        // Put the expanded side capsules underneath the element, and keep the crisp outline on
        // top. Both are external-only, so neither layer interferes with the glass highlight.
        translate(-padding.toFloat(), -padding.toFloat()) {
            drawLayer(sideLayer)
        }
        drawContent()
        if (outlineEnabled) translate(-padding.toFloat(), -padding.toFloat()) { drawLayer(outlineLayer) }
    }

    override fun onAttach() {
        recordedShape = null
        allocationSize = IntSize.Zero
        sideLayer = requireGraphicsContext().createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }
        outlineLayer = requireGraphicsContext().createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }

    override fun onDetach() {
        sideLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        outlineLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        sideLayer = null
        outlineLayer = null
    }
}

/** Adapter used to evaluate a shared GraphicsLayerScope block outside a real graphics layer. */
class GraphicsLayerBlockScope : GraphicsLayerScope {
    private var densityValue: Density = Density(1f)

    override val density: Float get() = densityValue.density
    override val fontScale: Float get() = densityValue.fontScale

    override var scaleX: Float = 1f
    override var scaleY: Float = 1f
    override var alpha: Float = 1f
    override var translationX: Float = 0f
    override var translationY: Float = 0f
    override var shadowElevation: Float = 0f
    override var rotationX: Float = 0f
    override var rotationY: Float = 0f
    override var rotationZ: Float = 0f
    override var cameraDistance: Float = 8f
    override var transformOrigin: TransformOrigin = TransformOrigin.Center
    override var shape: Shape = RectangleShape
    override var clip: Boolean = false
    override var size: Size = Size.Zero

    fun reset(drawScope: Density, size: Size) {
        densityValue = drawScope
        this.size = size
        scaleX = 1f
        scaleY = 1f
        alpha = 1f
        translationX = 0f
        translationY = 0f
        shadowElevation = 0f
        rotationX = 0f
        rotationY = 0f
        rotationZ = 0f
        cameraDistance = 8f
        transformOrigin = TransformOrigin.Center
        shape = RectangleShape
        clip = false
    }
}

/** Original Navigation prominent tint; the shared surface still owns all optics and edge layers. */
private fun DrawScope.drawNavigationProminentTint(
    surfaceColor: Color,
    isLight: Boolean,
    brightness: Float,
    enabled: Boolean,
    alpha: Float = 1f,
) {
    drawRect(
        Color.White.copy(alpha = (if (isLight) 0.16f else 0.06f) + brightness * 0.18f),
        alpha = alpha,
        blendMode = BlendMode.Screen,
    )
    drawRect(surfaceColor.copy(alpha = 1f), alpha = 0.22f * alpha, blendMode = BlendMode.Hue)
    drawRect(surfaceColor.copy(alpha = surfaceColor.alpha * if (enabled) 1f else 0.8f), alpha = alpha)
}

private fun DrawScope.drawExplicitNavigationProminentTint(
    containerColor: Color,
    navigationSurfaceColor: Color?,
    enabled: Boolean,
    alpha: Float = 1f,
) {
    drawRect(Color.White.copy(alpha = 0.94f), alpha = alpha)
    val tint = navigationSurfaceColor ?: containerColor.copy(alpha = 1f)
    drawRect(tint.copy(alpha = tint.alpha * if (enabled) 1f else 0.8f), alpha = alpha)
}

const val LightNavigationContainerAlphaScale = 0.5f

/** Adjusts only the sampled image, before the neutral tint and foreground are drawn. */
fun DrawScope.drawLightNavigationBackdrop(alpha: Float = 1f) {
    drawRect(Color(0xFFF8F8F8).copy(alpha = 0.25f), alpha = alpha, blendMode = BlendMode.Luminosity)
    drawRect(Color(0xFF444444).copy(alpha = 0.15f), alpha = alpha, blendMode = BlendMode.Plus)
}

/** Neutral navigation material, applied to the sampled image before its inner shadows. */
fun DrawScope.drawNavigationGlassMaterial(
    containerColor: Color,
    isDark: Boolean,
    containerAlphaMultiplier: Float = 1.25f,
    material: NavigationGlassMaterial? = null,
    materialTint: Color? = null,
    alpha: Float = 1f,
) {
    val opacity = alpha.coerceIn(0f, 1f)
    if (opacity <= 0f) return
    if (material != null) {
        material.fills(isDark).forEach { fill ->
            drawRect(fill.color, alpha = opacity, blendMode = fill.blendMode)
        }
        materialTint?.let { tint ->
            drawRect(tint.copy(alpha = (tint.alpha * containerAlphaMultiplier).coerceIn(0f, 1f)), alpha = opacity)
        }
        return
    }
    if (isDark) {
        drawRect(Color(0xFF999999).copy(alpha = 0.17f), alpha = opacity, blendMode = BlendMode.Luminosity)
    } else {
        drawLightNavigationBackdrop(opacity)
    }
    drawRect(
        containerColor.copy(
            alpha = containerColor.alpha * containerAlphaMultiplier.coerceAtLeast(0f) *
                if (isDark) 0.6f else LightNavigationContainerAlphaScale,
        ),
        alpha = opacity,
    )
}

/** Both edge highlights use luminosity and additive passes, fading toward the center. */
@Composable
fun rememberNavigationInnerShadows(
    enabled: Boolean,
    renderScale: Float = 1f,
): NavigationInnerShadowRenderer? {
    val renderer = remember(enabled, renderScale) {
        if (enabled) NavigationInnerShadowRenderer(renderScale = renderScale) else null
    }
    DisposableEffect(renderer) {
        onDispose { renderer?.release() }
    }
    return renderer
}

/** The lens source keeps one material path while press changes its optical depth. */
fun Modifier.navigationLensSourceBackground(
    backdrop: Backdrop,
    shape: () -> Shape,
    containerColor: Color,
    isDark: Boolean,
    appearance: GlassAppearance,
    pressProgressState: androidx.compose.runtime.State<Float>,
    innerShadows: NavigationInnerShadowRenderer?,
    pressOverlay: (DrawScope.() -> Unit)? = null,
    opticalCenter: (() -> Offset)? = null,
    pressGlow: (() -> GlassPressHighlight?)? = null,
    pressGlowBounds: ((Size) -> Rect)? = null,
): Modifier {
    val recipe = NavigationMaterialRecipe.resolve(isDark, null)
    val sampledPressRenderer = if (Build.VERSION.SDK_INT < 36 && pressGlow != null) PressHighlightRenderer() else null
    return navigationLensSourceDropShadow(shape).drawBackdrop(
    backdrop = backdrop,
    shape = shape,
    effects = {
        val press = pressProgressState.value.coerceIn(0f, 1f)
        val blurProgress = press * press * (3f - 2f * press)
        if (innerShadows == null) vibrancy()
        blur(lerp(8f, 2f, blurProgress).dp.toPx())
        val contactShape = shape() as? LiquidContactShape
        if (contactShape != null && opticalCenter != null) {
            navigationContainerLens(contactShape, opticalCenter(), 24.dp.toPx() * press, 28.dp.toPx() * press)
        } else {
            lens(
                24.dp.toPx() * press,
                28.dp.toPx() * press,
                depthEffect = press > 0.01f,
                chromaticAberration = true,
            )
        }
        if (innerShadows != null && Build.VERSION.SDK_INT < 36) {
            val coating = innerShadows.materialEffect(
            recipe, shape = shape(), size = size, density = this, layoutDirection = layoutDirection,
            origin = Offset(padding, padding), highlight = Highlight.Default,
        )
            renderEffect = renderEffect?.let {
                RenderEffect.createChainEffect(coating, it.asAndroidRenderEffect()).asComposeRenderEffect()
            } ?: coating.asComposeRenderEffect()
        } else if (innerShadows == null && !isDark && appearance.usesBackdrop) liftNavigationShadows()
        if (sampledPressRenderer != null) {
            val glow = pressGlow?.invoke()
            if (glow != null && glow.glowProgress > 0f) {
                val bounds = (pressGlowBounds?.invoke(size) ?: Rect(Offset.Zero, size)).translate(Offset(padding, padding))
                val light = sampledPressRenderer.effect(glow.glowProgress,
                    bounds.topLeft + Offset(bounds.width * glow.position.x, bounds.height * glow.position.y),
                    bounds, density, isDark, null, false, glow.dissipationProgress)
                renderEffect = renderEffect?.let {
                    RenderEffect.createChainEffect(light, it.asAndroidRenderEffect()).asComposeRenderEffect()
                } ?: light.asComposeRenderEffect()
            }
        }
    },
    highlight = { Highlight.Default },
    shadow = null,
    onDrawSurface = {
        if (appearance.usesBackdrop) {
            if (innerShadows != null) innerShadows.drawMaterial(this, shape(), recipe, isDark = isDark)
            else drawNavigationGlassMaterial(containerColor, isDark, 1f)
        } else {
            drawRect(containerColor)
        }
    },
    onDrawFront = pressOverlay,
)
}

/** Shared outer navigation-glass material used by the expanded nav and floating controls. */
fun Modifier.navigationGlassBackground(
    backdrop: Backdrop,
    shape: () -> Shape,
    containerColor: Color,
    navigationMaterial: Boolean,
    innerShadows: NavigationInnerShadowRenderer?,
    isDark: Boolean = false,
    containerAlphaMultiplier: Float = 1.25f,
    surfaceOverlay: (DrawScope.() -> Unit)? = null,
    pressOverlay: (DrawScope.() -> Unit)? = null,
    accentHighlights: Boolean = false,
    pressProgress: Float = 0f,
    pressProgressState: androidx.compose.runtime.State<Float>? = null,
    highlightAngle: Float = 0f,
    sampleBackdrop: Boolean = true,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    appearance: GlassAppearance = GlassAppearance(),
    blurRadius: Dp = Dp.Unspecified,
    exportedBackdrop: LayerBackdrop? = null,
    highlightAlphaMultiplier: Float = 1f,
    shadowAlpha: Float = 1f,
    dropShadow: Shadow? = null,
    refractionHeight: Dp = Dp.Unspecified,
    refractionAmount: Dp = Dp.Unspecified,
    opticalHighlightBoost: Float = 0f,
    material: NavigationGlassMaterial? = null,
    materialTint: Color? = null,
    preserveBackdropColors: Boolean = false,
    innerShadowAlpha: Float = 1f,
    materialPaintAlpha: Float = 1f,
    opticalCenter: (() -> Offset)? = null,
    prominentTint: Color? = null,
    luminanceState: BackdropLuminance? = null,
    materialContext: GlassMaterialContext? = null,
    backgroundOverlay: (DrawScope.() -> Unit)? = null,
): Modifier = composed {
    val context = materialContext ?: run {
        val inherited = LocalBackdropLuminance.current.takeIf { luminanceState == null }
        val probe = luminanceState ?: inherited ?: rememberBackdropLuminance()
        rememberResolvedGlassMaterialContext(LocalGlassColors.current, appearance, isDark, probe,
            inherited == null, sampleBackdrop)
    }
    navigationGlassBackgroundResolved(
        backdrop = backdrop,
        shape = shape,
        containerColor = containerColor,
        navigationMaterial = navigationMaterial,
        innerShadows = innerShadows,
        isDark = context.isDark,
        darkFraction = context.darkFraction,
        containerAlphaMultiplier = containerAlphaMultiplier,
        surfaceOverlay = surfaceOverlay,
        pressOverlay = pressOverlay,
        accentHighlights = accentHighlights,
        pressProgress = pressProgress,
        pressProgressState = pressProgressState,
        highlightAngle = highlightAngle,
        sampleBackdrop = sampleBackdrop,
        layerBlock = layerBlock,
        appearance = appearance,
        blurRadius = blurRadius,
        exportedBackdrop = exportedBackdrop,
        highlightAlphaMultiplier = highlightAlphaMultiplier,
        shadowAlpha = shadowAlpha,
        dropShadow = dropShadow,
        refractionHeight = refractionHeight,
        refractionAmount = refractionAmount,
        opticalHighlightBoost = opticalHighlightBoost,
        material = material,
        materialTint = materialTint,
        preserveBackdropColors = preserveBackdropColors,
        innerShadowAlpha = innerShadowAlpha,
        materialPaintAlpha = materialPaintAlpha,
        opticalCenter = opticalCenter,
        prominentTint = prominentTint,
        luminanceState = context.luminanceState,
        backgroundOverlay = backgroundOverlay,
    )
}

internal fun Modifier.navigationGlassBackgroundResolved(
    backdrop: Backdrop,
    shape: () -> Shape,
    containerColor: Color,
    navigationMaterial: Boolean,
    innerShadows: NavigationInnerShadowRenderer?,
    isDark: Boolean = false,
    containerAlphaMultiplier: Float = 1.25f,
    surfaceOverlay: (DrawScope.() -> Unit)? = null,
    pressOverlay: (DrawScope.() -> Unit)? = null,
    accentHighlights: Boolean = false,
    pressProgress: Float = 0f,
    pressProgressState: androidx.compose.runtime.State<Float>? = null,
    highlightAngle: Float = 0f,
    sampleBackdrop: Boolean = true,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    appearance: GlassAppearance = GlassAppearance(),
    blurRadius: Dp = Dp.Unspecified,
    exportedBackdrop: LayerBackdrop? = null,
    highlightAlphaMultiplier: Float = 1f,
    shadowAlpha: Float = 1f,
    dropShadow: Shadow? = null,
    refractionHeight: Dp = Dp.Unspecified,
    refractionAmount: Dp = Dp.Unspecified,
    opticalHighlightBoost: Float = 0f,
    material: NavigationGlassMaterial? = null,
    materialTint: Color? = null,
    preserveBackdropColors: Boolean = false,
    innerShadowAlpha: Float = 1f,
    materialPaintAlpha: Float = 1f,
    opticalCenter: (() -> Offset)? = null,
    prominentTint: Color? = null,
    luminanceState: BackdropLuminance? = null,
    sampledPressHighlight: ((Size) -> SampledPressHighlight?)? = null,
    darkFraction: Float = if (isDark) 1f else 0f,
    backgroundOverlay: (DrawScope.() -> Unit)? = null,
): Modifier {
    val nativeRecipe = if (navigationMaterial && innerShadows != null && surfaceOverlay == null &&
        sampleBackdrop && appearance.usesBackdrop) {
        NavigationMaterialRecipe.resolve(darkFraction, material, prominentTint, materialTint, containerAlphaMultiplier)
    } else null
    val completeNativeMaterial = nativeRecipe != null
    val surfaceHighlight = Highlight.Default.copy(
        alpha = (highlightAlphaMultiplier.coerceIn(0f, 1f) + opticalHighlightBoost).coerceIn(0f, 1f),
        style = HighlightStyle.Default(angle = highlightAngle, clear = material == NavigationGlassMaterial.Clear),
    )
    val samplingScale: (Size, Density) -> Float = { size, density ->
        val outline = shape()
        if (outline !is LiquidContactShape && outline !is ToolbarActionShape &&
            (outline is RoundedRectangularShape || outline is CornerBasedShape || outline === RectangleShape))
            navigationGlassCaptureScale(size.minDimension / density.density) else 1f
    }
    val sampledPressRenderer = if (Build.VERSION.SDK_INT < 36 && sampledPressHighlight != null) PressHighlightRenderer() else null
    var geometryExtent = Float.NaN
    var cachedGeometry: NavigationGlassGeometryProfile? = null
    return this.then(
        if (dropShadow == null && !completeNativeMaterial) {
            Modifier.navigationGlassDropShadow(
                shape,
                if (shadowAlpha == 1f) layerBlock else {
                    {
                        layerBlock?.invoke(this)
                        alpha *= shadowAlpha.coerceIn(0f, 1f)
                    }
                },
                useNativeProfile = true,
                nativeMaterial = material,
            )
        } else Modifier,
    )
    .drawBackdrop(
        backdrop = if (sampleBackdrop && appearance.usesBackdrop) backdrop else emptyBackdrop(),
        exportedBackdrop = exportedBackdrop,
        luminanceState = luminanceState,
        samplingScale = samplingScale,
        shape = shape,
        effects = {
            if (sampleBackdrop && appearance.usesBackdrop) {
                val extent = size.minDimension / density
                val geometry = if (cachedGeometry == null || geometryExtent != extent) {
                    navigationGlassGeometryProfile(extent, material).also { cachedGeometry = it; geometryExtent = extent }
                } else requireNotNull(cachedGeometry)
                val height = if (refractionHeight.value.isFinite()) refractionHeight.toPx() else geometry.innerHeight * density
                val amount = if (refractionAmount.value.isFinite()) refractionAmount.toPx() else geometry.innerAmount * density
                val nativeOptics = !refractionHeight.value.isFinite() && !refractionAmount.value.isFinite()
                val surfaceShape = shape()
                val contactShape = surfaceShape as? LiquidContactShape
                val completion = nativeRecipe?.let { recipe ->
                    NativeGlassCompletion(recipe, isDark,
                        if (material == NavigationGlassMaterial.Clear) 1f else 0f,
                        materialAlpha = materialPaintAlpha, edgeAlpha = innerShadowAlpha,
                        shadow = if (dropShadow == null) navigationNativeShadowProfile(extent, material) else null,
                        shadowAlpha = shadowAlpha, highlightAlpha = surfaceHighlight.alpha,
                        highlightAngle = highlightAngle, darkFraction = darkFraction)
                }
                val completionBand = completion?.shadow?.let { (it.radius * 2f + 8f) * density + 2f } ?: 0f
                val nativeField = if (nativeOptics || completion != null) when (surfaceShape) {
                    is LiquidContactShape -> navigationContainerGlassField(surfaceShape, opticalCenter?.invoke(), completionBand)
                    is ToolbarActionShape -> navigationToolbarGlassField(surfaceShape, completionBand)
                    else -> null
                } else null
                if (nativeOptics) {
                    nativeNavigationGlassFilter(
                        geometry, isDark,
                        blurRadius = if (blurRadius.value.isFinite()) blurRadius.toPx() else geometry.blurRadius * density,
                        refractionEnabled = appearance.usesRefraction,
                        clear = material == NavigationGlassMaterial.Clear,
                        field = nativeField,
                        completion = completion,
                        darkFraction = darkFraction,
                    )
                } else {
                    val frost = if (blurRadius.value.isFinite()) blurRadius.toPx() else geometry.blurRadius * density
                    val explicitOptics: io.github.neoruaa.glasmio.glass.render.BackdropEffectScope.() -> Unit = {
                        if (nativeRecipe == null && !preserveBackdropColors) vibrancy()
                        blur(frost)
                        if (appearance.usesRefraction && height > 0f && amount > 0f) {
                            if (contactShape != null && opticalCenter != null) {
                                navigationContainerLens(contactShape, opticalCenter(), height, amount,
                                    chromaticAberration = true)
                            } else lens(height, amount, depthEffect = true, chromaticAberration = true)
                        }
                    }
                    if (completion != null) nativeControlGlassImpl(isDark, materialPaintAlpha,
                        material = material, completion = completion,
                        field = nativeField,
                        sourcePadding = maxOf(frost * 3f, kotlin.math.abs(amount) * 2f), optics = explicitOptics)
                    else explicitOptics()
                }
                if (nativeRecipe != null && !completeNativeMaterial && Build.VERSION.SDK_INT < 36) {
                    val coating = requireNotNull(innerShadows).materialEffect(
                        nativeRecipe, materialPaintAlpha, size.minDimension / density,
                        shape = shape(), size = size, density = this, layoutDirection = layoutDirection,
                        origin = Offset(padding, padding),
                        highlight = surfaceHighlight,
                    )
                    renderEffect = renderEffect?.let {
                        RenderEffect.createChainEffect(coating, it.asAndroidRenderEffect()).asComposeRenderEffect()
                    } ?: coating.asComposeRenderEffect()
                } else if (nativeRecipe == null && navigationMaterial && !isDark && !preserveBackdropColors) liftNavigationShadows()
                if (sampledPressRenderer != null) {
                    val captureScale = samplingScale(size, this)
                    val source = sampledPressHighlight?.invoke(size / captureScale)
                    val press = source?.highlight
                    if (source != null && press != null && press.glowProgress > 0f) {
                        val storage = this as? io.github.neoruaa.glasmio.glass.render.BackdropEffectScopeImpl
                        val fullResolution = storage?.nativeMaterialCompleted == true
                        val outputScale = if (fullResolution) 1f else captureScale
                        val outputOrigin = if (fullResolution) requireNotNull(storage).materialOutputOrigin else Offset(padding, padding)
                        val bounds = Rect(source.bounds.topLeft * outputScale + outputOrigin,
                            source.bounds.size * outputScale)
                        val light = sampledPressRenderer.effect(
                            press.glowProgress,
                            bounds.topLeft + Offset(bounds.width * press.position.x, bounds.height * press.position.y),
                            bounds, if (fullResolution) requireNotNull(storage).sourceDensity.density else density, isDark, prominentTint,
                            material == NavigationGlassMaterial.Clear, press.dissipationProgress,
                        )
                        renderEffect = renderEffect?.let {
                            RenderEffect.createChainEffect(light, it.asAndroidRenderEffect()).asComposeRenderEffect()
                        } ?: light.asComposeRenderEffect()
                    }
                }
            }
        },
        highlight = if (completeNativeMaterial) null else { { surfaceHighlight } },
        shadow = dropShadow?.let { shadow ->
            { shadow.copy(alpha = shadow.alpha * shadowAlpha.coerceIn(0f, 1f)) }
        },
        innerShadow = null,
        layerBlock = layerBlock,
        onDrawSurface = {
            if (appearance.reduceTransparency) {
                drawRect(containerColor.copy(alpha = 1f))
            } else if (completeNativeMaterial) {
                // Face, coverage, exterior strip and top highlight share the full-resolution kernel.
            } else if (nativeRecipe != null) {
                requireNotNull(innerShadows).drawMaterial(
                    this, shape(), nativeRecipe, isDark = isDark,
                    alpha = innerShadowAlpha, materialAlpha = materialPaintAlpha,
                )
            } else if (material == null && surfaceOverlay != null) {
                // Preserve the original Navigation overlay contract, including detail inner shadows.
                surfaceOverlay()
            } else {
                if (navigationMaterial && sampleBackdrop && appearance.usesBackdrop) {
                    drawNavigationGlassMaterial(containerColor, isDark, containerAlphaMultiplier, material, materialTint, alpha = materialPaintAlpha)
                } else {
                    drawRect(
                        containerColor.copy(
                            alpha = containerColor.alpha * containerAlphaMultiplier.coerceAtLeast(0f),
                        ),
                    )
                }
                surfaceOverlay?.invoke(this)
                if (navigationMaterial && sampleBackdrop && appearance.usesBackdrop) {
                    innerShadows?.draw(this, shape(), isDark = isDark, alpha = innerShadowAlpha)
                }
            }
            // Background carriers precede foreground content and remain in backdrop exports.
            backgroundOverlay?.invoke(this)
        },
        onDrawFront = {
            pressOverlay?.invoke(this)
        },
    )
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    shape: Shape = ContinuousRoundedRectangle(LocalGlassDimensions.current.regularCornerRadius),
    style: GlassSurfaceStyle = LocalGlassSurfaceStyle.current,
    navigationSurfaceColor: Color? = null,
    navigationSurfaceAlphaMultiplier: Float = 1.25f,
    emphasis: GlassEmphasis = GlassEmphasis.Regular,
    enabled: Boolean = true,
    refractionHeight: Dp = Dp.Unspecified,
    refractionAmount: Dp = Dp.Unspecified,
    opticalHighlightBoost: Float = 0f,
    sampleBackdrop: Boolean = true,
    exportedBackdrop: LayerBackdrop? = null,
    onVisualBoundsChanged: ((Rect, GlassPressHighlight) -> Unit)? = null,
    pressHighlight: (() -> GlassPressHighlight?)? = null,
    cancelPressFeedback: Boolean = false,
    groupSurfaceSeparation: (() -> Float)? = LocalToolbarActionSeparation.current,
    morphProgress: androidx.compose.runtime.State<Float>? = null,
    morphCaptureWidth: Dp? = null,
    morphVerticalTravel: Dp = 0.dp,
    nativeNavigationFlex: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    detailAction: Boolean = false,
    material: NavigationGlassMaterial? = null,
    frostRadius: Dp? = null,
    preserveBackdropColors: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalGlassColors.current
    // The deprecated Standard spelling shares Navigation's material and interaction contracts.
    val resolvedStyle = if (style == GlassSurfaceStyle.Navigation) style else GlassSurfaceStyle.Navigation
    val appearance = LocalGlassAppearance.current
    val toolbarSymbols = LocalIosToolbarActionGroup.current != null
    val environmentDark = colors.isDark
    val navigationMaterial = (material != null || emphasis == GlassEmphasis.Prominent || navigationSurfaceColor == null) &&
        sampleBackdrop && appearance.usesBackdrop
    val accentDetailAction = detailAction && resolvedStyle == GlassSurfaceStyle.Navigation &&
        emphasis == GlassEmphasis.Prominent && navigationSurfaceColor == null &&
        sampleBackdrop && appearance.usesBackdrop
    val innerShadows = rememberNavigationInnerShadows(
        resolvedStyle == GlassSurfaceStyle.Navigation && (navigationMaterial || accentDetailAction),
    )
    val brightness = LocalGlassSurfaceBrightness.current.coerceIn(0f, 1f)
    val animationScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val navigationFlexSize = remember(nativeNavigationFlex) {
        if (nativeNavigationFlex) mutableStateOf(Size.Zero) else null
    }
    val interactiveHighlight = remember(animationScope, navigationFlexSize, density) {
        InteractiveHighlight(animationScope = animationScope).apply {
            pressAnimationSpec = navigationFlexSize?.let { measuredSize ->
                { pressed ->
                    val longDimension = measuredSize.value.maxDimension / density.density
                    val progress = if (longDimension < 120f) 0.0
                    else ((longDimension.toDouble() - 44.0) / 116.0).coerceIn(0.0, 1.0)
                    val response = if (pressed) .262 + (.314 - .262) * progress
                    else .4 + (.36 - .4) * progress
                    val damping = if (pressed) .625f else (.375 + (.6 - .375) * progress).toFloat()
                    spring(damping, (4.0 * PI * PI / (response * response)).toFloat(), .001f)
                }
            }
        }
    }
    val toolbarMember = rememberToolbarActionMember(
        eligible = resolvedStyle == GlassSurfaceStyle.Navigation && emphasis == GlassEmphasis.Regular && !detailAction,
        material = ToolbarActionMaterial(navigationSurfaceColor, navigationSurfaceAlphaMultiplier, sampleBackdrop),
        separation = groupSurfaceSeparation,
        highlight = interactiveHighlight,
    )
    val sharesToolbarSurface = toolbarMember?.sharesSurface == true
    val contactMember = if (LocalBottomControlContact.current != null && toolbarMember == null &&
        resolvedStyle == GlassSurfaceStyle.Navigation && emphasis == GlassEmphasis.Regular && !detailAction
    ) {
        val contactTransformScope = remember(density) { GraphicsLayerBlockScope() }
        rememberBottomControlContactMember(
            materialPressProgress = { interactiveHighlight.pressProgress },
            visualBounds = { size ->
                contactTransformScope.reset(density, size)
                with(contactTransformScope) {
                    applyGlassDragScale(interactiveHighlight.pressProgress, interactiveHighlight.offset,
                        nativeButtonLift = onClick != null, allowPressOvershoot = nativeNavigationFlex)
                    Rect(
                        size.width * (1f - scaleX) / 2f + translationX,
                        size.height * (1f - scaleY) / 2f + translationY,
                        size.width * (1f + scaleX) / 2f + translationX,
                        size.height * (1f + scaleY) / 2f + translationY,
                    )
                }
            },
            highlight = { size ->
                val position = interactiveHighlight.highlightPosition(size)
                GlassPressHighlight(interactiveHighlight.pressProgress,
                    Offset(position.x / size.width.coerceAtLeast(1f), position.y / size.height.coerceAtLeast(1f)),
                    interactiveHighlight.glowProgress, interactiveHighlight.dissipationProgress)
            },
        )
    } else null
    val sharesContactSurface = contactMember?.sharesSurface == true
    val inheritedLuminance = LocalBackdropLuminance.current.takeIf { sharesToolbarSurface || sharesContactSurface }
    val materialContext = rememberGlassMaterialContext(sampleBackdrop,
        inheritLuminance = sharesToolbarSurface || sharesContactSurface)
    val luminance = materialContext.probe
    val adaptive = materialContext.adaptive
    val materialDark = materialContext.isDark
    val materialDarkFraction = materialContext.darkFraction
    val isLight = !materialDark
    LaunchedEffect(interactiveHighlight, cancelPressFeedback) {
        if (cancelPressFeedback) interactiveHighlight.cancelPress()
    }
    val surfaceColor = when (emphasis) {
        GlassEmphasis.Regular -> colors.container
        GlassEmphasis.Prominent -> colors.prominentContainer
    }
    val nativeSourceRecipe = remember(
        navigationMaterial, material, emphasis, surfaceColor, navigationSurfaceColor,
        navigationSurfaceAlphaMultiplier, materialDarkFraction, appearance,
    ) {
        if (navigationMaterial && sampleBackdrop && appearance.usesBackdrop) {
            NavigationMaterialRecipe.resolve(
                materialDarkFraction, material,
                prominentTint = (navigationSurfaceColor ?: surfaceColor).copy(alpha = 1f)
                    .takeIf { emphasis == GlassEmphasis.Prominent },
                materialTint = navigationSurfaceColor.takeIf { emphasis == GlassEmphasis.Regular },
                alphaMultiplier = navigationSurfaceAlphaMultiplier,
            )
        } else null
    }
    interactiveHighlight.materialIsDark = materialDark
    interactiveHighlight.materialTint = nativeSourceRecipe?.tint
    interactiveHighlight.materialClear = material == NavigationGlassMaterial.Clear
    val sourceMaterialEffect: ((Float) -> RenderEffect)? = remember(nativeSourceRecipe, innerShadows) {
        if (nativeSourceRecipe != null && innerShadows != null) {
            { alpha: Float -> innerShadows.materialEffect(nativeSourceRecipe, alpha) }
        } else null
    }
    // Callback identity can change when a parent recomposes without changing these pixels.
    // Keep the material/effect nodes stable while each draw still invokes the current owner.
    val currentPressHighlight = rememberUpdatedState(pressHighlight)
    val currentVisualBoundsChanged = rememberUpdatedState(onVisualBoundsChanged)
    val pressProgressState = remember(interactiveHighlight) {
        derivedStateOf(structuralEqualityPolicy()) {
            currentPressHighlight.value?.invoke()?.progress ?: interactiveHighlight.pressProgress
        }
    }
    val shapeProvider = remember(shape) { { shape } }
    val dragScaleLayerBlock: GraphicsLayerScope.() -> Unit = remember(interactiveHighlight, toolbarMember, onClick != null, nativeNavigationFlex) {
        {
            if (toolbarMember != null) toolbarMember.applyPressTransform(this) else applyGlassDragScale(
                pressProgress = interactiveHighlight.pressProgress,
                offset = interactiveHighlight.offset,
                nativeButtonLift = onClick != null,
                allowPressOvershoot = nativeNavigationFlex,
            )
            currentVisualBoundsChanged.value?.invoke(
                Rect(
                    size.width * (1f - scaleX) / 2f + translationX,
                    size.height * (1f - scaleY) / 2f + translationY,
                    size.width * (1f + scaleX) / 2f + translationX,
                    size.height * (1f + scaleY) / 2f + translationY,
                ),
                GlassPressHighlight(interactiveHighlight.pressProgress,
                    interactiveHighlight.highlightPosition(size).let {
                        Offset(it.x / size.width.coerceAtLeast(1f), it.y / size.height.coerceAtLeast(1f))
                    }, interactiveHighlight.glowProgress, interactiveHighlight.dissipationProgress),
            )
        }
    }
    // Some controls (notably sheet headers) need the navigation tint/highlight and the shared
    // drag physics without replaying or refracting the content behind them. Keep an empty
    // backdrop for that mode so the button remains a glass surface without lens/blur
    // sampling of the player window.
    val surfaceBackdrop = if (sampleBackdrop && appearance.usesBackdrop) backdrop else emptyBackdrop()
    val morphing = remember(morphProgress) {
        derivedStateOf {
            morphProgress?.value?.let { it != 0f && it != 1f } == true
        }
    }.value
    val morphGlassModifier = if (morphProgress != null && resolvedStyle == GlassSurfaceStyle.Navigation &&
        sampleBackdrop && emphasis == GlassEmphasis.Regular
    ) {
        rememberMorphingNavigationGlass(
            backdrop = surfaceBackdrop,
            active = morphing && !sharesContactSurface,
            shape = shape,
            tint = navigationSurfaceColor ?: surfaceColor,
            navigationMaterial = navigationMaterial,
            isDark = materialDark,
            darkFraction = materialDarkFraction,
            pressProgress = { pressProgressState.value },
            layerBlock = dragScaleLayerBlock,
            tintMultiplier = navigationSurfaceAlphaMultiplier,
            captureWidth = morphCaptureWidth,
            verticalTravel = morphVerticalTravel,
            morphProgress = { morphProgress.value },
            material = material,
            materialTint = navigationSurfaceColor,
            refractionHeight = refractionHeight,
            refractionAmount = refractionAmount,
            frostRadius = frostRadius,
        )
    } else null
    val glassModifier = if (sharesToolbarSurface) {
        Modifier.graphicsLayer { toolbarMember?.applyPressTransform(this) }
    } else if (sharesContactSurface) {
        Modifier.graphicsLayer(dragScaleLayerBlock).clip(shape)
    } else if (morphing && morphGlassModifier != null) {
        morphGlassModifier
    } else {
        val containerColor = navigationSurfaceColor ?: surfaceColor
        remember(
            surfaceBackdrop,
            shapeProvider,
            containerColor,
            emphasis,
            isLight,
            navigationMaterial,
            accentDetailAction,
            innerShadows,
            brightness,
            enabled,
            refractionHeight,
            refractionAmount,
            opticalHighlightBoost,
            exportedBackdrop,
            material,
            frostRadius,
            preserveBackdropColors,
            navigationSurfaceAlphaMultiplier,
            pressProgressState,
            sampleBackdrop,
            dragScaleLayerBlock,
            appearance,
            luminance,
            inheritedLuminance,
            adaptive,
            materialDarkFraction,
        ) {
            Modifier.navigationGlassBackgroundResolved(
                backdrop = surfaceBackdrop,
                shape = shapeProvider,
                containerColor = containerColor,
                navigationMaterial = navigationMaterial,
                innerShadows = innerShadows,
                isDark = materialDark,
                darkFraction = materialDarkFraction,
                containerAlphaMultiplier = navigationSurfaceAlphaMultiplier,
                surfaceOverlay = if (emphasis == GlassEmphasis.Prominent && !navigationMaterial) {
                    {
                        drawExplicitNavigationProminentTint(containerColor, navigationSurfaceColor, enabled)
                    }
                } else null,
                accentHighlights = accentDetailAction,
                pressProgressState = pressProgressState,
                sampleBackdrop = sampleBackdrop,
                layerBlock = dragScaleLayerBlock,
                appearance = appearance,
                exportedBackdrop = exportedBackdrop,
                refractionHeight = refractionHeight,
                refractionAmount = refractionAmount,
                opticalHighlightBoost = opticalHighlightBoost,
                material = material,
                materialTint = navigationSurfaceColor.takeIf { emphasis == GlassEmphasis.Regular },
                blurRadius = frostRadius ?: Dp.Unspecified,
                preserveBackdropColors = preserveBackdropColors,
                prominentTint = containerColor.copy(alpha = 1f).takeIf { emphasis == GlassEmphasis.Prominent },
                luminanceState = luminance.takeIf { adaptive && inheritedLuminance == null },
                sampledPressHighlight = { size ->
                    val press = currentPressHighlight.value?.invoke() ?: run {
                        val position = interactiveHighlight.highlightPosition(size)
                        GlassPressHighlight(interactiveHighlight.pressProgress,
                            Offset(position.x / size.width.coerceAtLeast(1f), position.y / size.height.coerceAtLeast(1f)),
                            interactiveHighlight.glowProgress, interactiveHighlight.dissipationProgress)
                    }
                    SampledPressHighlight(press, Rect(Offset.Zero, size))
                },
            )
        }
    }
    val sourceMaterialPaint: DrawScope.(Float) -> Unit = remember(
        surfaceColor, navigationSurfaceColor, navigationSurfaceAlphaMultiplier, material,
        emphasis, enabled, appearance, navigationMaterial, sampleBackdrop, isLight, brightness,
        nativeSourceRecipe, innerShadows,
    ) {
        { alpha ->
            val containerColor = navigationSurfaceColor ?: surfaceColor
            if (appearance.reduceTransparency) {
                drawRect(containerColor.copy(alpha = 1f), alpha = alpha)
            } else if (nativeSourceRecipe != null && innerShadows != null &&
                innerShadows.paintMaterial(this, nativeSourceRecipe, alpha)) {
                // The same destination-aware program paints a retained source on modern HWUI.
            } else if (material == null && emphasis == GlassEmphasis.Prominent) {
                drawNavigationProminentTint(containerColor, isLight, brightness, enabled, alpha)
            } else {
                if (navigationMaterial && sampleBackdrop && appearance.usesBackdrop) {
                    drawNavigationGlassMaterial(
                        containerColor, !isLight, navigationSurfaceAlphaMultiplier, material,
                        navigationSurfaceColor.takeIf { emphasis == GlassEmphasis.Regular }, alpha,
                    )
                } else {
                    drawRect(
                        containerColor.copy(alpha = containerColor.alpha * navigationSurfaceAlphaMultiplier.coerceAtLeast(0f)),
                        alpha = alpha,
                    )
                }
                if (emphasis == GlassEmphasis.Prominent) {
                    drawExplicitNavigationProminentTint(containerColor, navigationSurfaceColor, enabled, alpha)
                }
            }
        }
    }
    val menuAnchorForeground = rememberMenuAnchorForeground(
        material, frostRadius, sourceMaterialPaint,
        materialEffect = sourceMaterialEffect,
        usesShadowLift = navigationMaterial && !preserveBackdropColors,
        usesInnerShadows = innerShadows != null && !appearance.reduceTransparency,
        sourceNativeMaterial = remember(nativeSourceRecipe) { nativeSourceRecipe?.let(::GlassMaterialSource) },
    )
    val externalHighlightModifier = remember(interactiveHighlight) {
        interactiveHighlight.highlightModifier(
            progressProvider = { currentPressHighlight.value?.invoke()?.glowProgress ?: interactiveHighlight.glowProgress },
            dissipationProgressProvider = { currentPressHighlight.value?.invoke()?.dissipationProgress ?: interactiveHighlight.dissipationProgress },
            positionProvider = { size ->
                currentPressHighlight.value?.invoke()?.let { Offset(size.width * it.position.x, size.height * it.position.y) }
                    ?: interactiveHighlight.highlightPosition(size)
            },
        )
    }
    val drawSurface: @Composable (Modifier) -> Unit = { surfaceModifier ->
        Box(
            modifier = surfaceModifier
                .then(
                    if (sharesToolbarSurface || sharesContactSurface) Modifier
                    else if (pressHighlight != null) externalHighlightModifier
                    else if (onClick != null && enabled) interactiveHighlight.modifier else Modifier,
                )
                .then(
                    if (onClick != null) {
                        val click = if (onLongClick != null) Modifier.combinedClickable(
                            enabled = enabled,
                            interactionSource = null,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                            onLongClick = onLongClick,
                        ) else Modifier.clickable(
                            enabled = enabled,
                            interactionSource = null,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                        click.then(if (enabled) interactiveHighlight.gestureModifier else Modifier)
                    } else {
                        Modifier
                    },
                )
                .then(if (toolbarMember != null && !sharesToolbarSurface) {
                    Modifier.drawWithContent { toolbarMember.drawForeground(this) }
                } else Modifier)
                .then(menuAnchorForeground),
            contentAlignment = contentAlignment,
            content = {
                val contentColor = when {
                    emphasis == GlassEmphasis.Prominent ->
                        navigationGlassProminentContentColor(navigationSurfaceColor ?: surfaceColor, materialDark, material)
                    else -> materialContext.colors.content
                }
                val contentColors = remember(materialContext.colors, contentColor) {
                    materialContext.colors.copy(content = contentColor)
                }
                ProvideGlassMaterialContext(materialContext) {
                CompositionLocalProvider(
                    LocalGlassColors provides contentColors,
                    LocalContentColor provides contentColor,
                    LocalGlassContentColor provides contentColor,
                    LocalSfToolbarSymbols provides toolbarSymbols,
                    LocalIosToolbarActionGroup provides null,
                    LocalToolbarActionSeparation provides null,
                    LocalBottomControlContact provides null,
                ) {
                    if (accentDetailAction) {
                        Box(
                            modifier = Modifier.graphicsLayer {
                                blendMode = BlendMode.Plus
                                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                            },
                            contentAlignment = contentAlignment,
                            content = content,
                        )
                    } else {
                        content()
                    }
                }
                }
            },
        )
    }
    val measuredModifier = if (navigationFlexSize != null) modifier.onSizeChanged {
        navigationFlexSize.value = Size(it.width.toFloat(), it.height.toFloat())
    } else modifier
    if (toolbarMember == null && contactMember == null) {
        drawSurface(measuredModifier.then(glassModifier))
    } else {
        // Register an untransformed layout wrapper. The inner surface owns press scaling,
        // so a press cannot change adjacency or apply the same deformation twice.
        Box(
            modifier = measuredModifier
                .onGloballyPositioned {
                toolbarMember?.updateCoordinates(it)
                contactMember?.updateCoordinates(it)
            },
            propagateMinConstraints = true,
        ) {
            drawSurface(glassModifier)
        }
    }
}

