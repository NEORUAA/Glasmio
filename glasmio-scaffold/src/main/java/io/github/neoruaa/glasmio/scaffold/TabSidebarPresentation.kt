package io.github.neoruaa.glasmio.scaffold

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import io.github.neoruaa.glasmio.ui.GlasmioTabBarPresentation
import io.github.neoruaa.glasmio.core.GlassColors
import io.github.neoruaa.glasmio.core.GlassAppearance

/** One presentation owner for the adaptive host and its measured top-tabs slot. */
internal class AdaptiveSidebarCoordinator(initialExpanded: Boolean) {
    val motion = TabSidebarMotion(initialExpanded)
    var tabHosts by mutableIntStateOf(0)
    var usesTabMorph by mutableStateOf(false)
    var hostOrigin by mutableStateOf(Offset.Zero)
    var tabsBounds by mutableStateOf<Rect?>(null)
    var measuredTabsBounds by mutableStateOf<Rect?>(null)
    var tabsLayer by mutableStateOf<GraphicsLayer?>(null)
    var tabsLayerSize by mutableStateOf(IntSize.Zero)
    var tabsColors by mutableStateOf<GlassColors?>(null)
    var tabsAppearance by mutableStateOf<GlassAppearance?>(null)
    val tabPresentation = object : GlasmioTabBarPresentation {
        override val backgroundVisible = { !usesTabMorph || !motion.running && !motion.expanded }
        override val contentAlpha = { if (usesTabMorph && motion.expanded) 0f else 1f }
        override val interactive: Boolean get() = !usesTabMorph || !motion.running && !motion.expanded
        override val capturesPreview: Boolean get() = usesTabMorph && motion.running
        override val drawsContentInPlace: Boolean get() = !capturesPreview
        override fun onPreviewLayer(layer: GraphicsLayer, size: IntSize) {
            tabsLayer = layer
            tabsLayerSize = size
        }
        override fun onMaterialEndpoint(colors: GlassColors, appearance: GlassAppearance) {
            tabsColors = colors
            tabsAppearance = appearance
        }
    }

    fun publishTabsBounds(windowBounds: Rect) {
        if (windowBounds.width <= 0f || windowBounds.height <= 0f) return
        measuredTabsBounds = windowBounds.translate(-hostOrigin)
        if (!motion.running) tabsBounds = measuredTabsBounds
    }
}

internal val LocalAdaptiveSidebarCoordinator = staticCompositionLocalOf<AdaptiveSidebarCoordinator?> { null }

/** AnimationKit's axis-1 portal: retain endpoint layout and scale equally on both axes. */
@Composable
internal fun TabSidebarContentPreview(
    layer: () -> GraphicsLayer?,
    endpointSize: () -> IntSize,
    bounds: () -> Rect,
    cornerRadius: () -> Float,
    progress: () -> Float,
) {
    val density = LocalDensity.current
    val rendered = rememberGraphicsLayer()
    Box(Modifier.fillMaxSize().drawWithCache {
        val source = layer()
        val endpoint = endpointSize()
        val padding = with(density) { 8.dp.roundToPx() }
        // Keep a reference to the owned endpoint layer. Its display list can update without
        // recording this padding wrapper again for every spring frame.
        if (source != null && endpoint.width > 0 && endpoint.height > 0) {
            rendered.record(size = IntSize(endpoint.width + padding * 2, endpoint.height + padding * 2)) {
                translate(padding.toFloat(), padding.toFloat()) { drawLayer(source) }
            }
        }
        val clip = Path()
        onDrawWithContent {
            if (source == null || endpoint.width <= 0 || endpoint.height <= 0) return@onDrawWithContent
            val p = progress().coerceIn(0f, 1f)
            if (p <= 0f) return@onDrawWithContent
            val frame = bounds()
            if (frame.width <= 0f || frame.height <= 0f) return@onDrawWithContent
            val blur = (1f - p) * with(density) { 4.dp.toPx() }
            rendered.alpha = p
            rendered.renderEffect = if (blur > .01f) RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.DECAL).asComposeRenderEffect() else null
            val shape = RoundedRectangle(with(density) { cornerRadius().coerceAtLeast(0f).toDp() })
            val outline = shape.createOutline(Size(frame.width, frame.height), layoutDirection, density)
            clip.rewind()
            clip.apply {
                when (outline) {
                    is Outline.Generic -> addPath(outline.path)
                    is Outline.Rounded -> addRoundRect(outline.roundRect)
                    is Outline.Rectangle -> addRect(outline.rect)
                }
                translate(Offset(frame.left, frame.top))
            }
            clipPath(clip) {
                translate(frame.left, frame.top) {
                    val s = frame.width / endpoint.width
                    scale(s, s, Offset.Zero) {
                        translate(-padding.toFloat(), -padding.toFloat()) { drawLayer(rendered) }
                    }
                }
            }
        }
    })
}
