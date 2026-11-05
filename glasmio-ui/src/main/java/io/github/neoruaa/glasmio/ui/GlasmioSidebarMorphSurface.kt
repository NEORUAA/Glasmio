package io.github.neoruaa.glasmio.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import io.github.neoruaa.glasmio.core.LocalGlassAppearance
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.GlassColors
import io.github.neoruaa.glasmio.core.GlassAppearance
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import io.github.neoruaa.glasmio.glass.navigationGlassBackground
import io.github.neoruaa.glasmio.glass.rememberNavigationInnerShadows
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.rememberGlassMaterialContext
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A single changing silhouette for the top-tabs/sidebar backplate transition.
 * [bounds] and [cornerRadius] use pixels in this surface's local viewport. The caller owns
 * timing and content; neither panel controls nor this backplate should enter [backdrop].
 * Material 0 matches the resting top tab bar; material 1 matches [GlasmioSidebar]. The latter
 * remains a design-resource adaptation of UIKit's environment-resolved sidebar material.
 */
@Composable
fun GlasmioSidebarMorphSurface(
    backdrop: Backdrop,
    bounds: () -> Rect,
    cornerRadius: () -> Float,
    sidebarMaterialProgress: () -> Float,
    modifier: Modifier = Modifier,
    sidebarBackdrop: Backdrop = backdrop,
    tabsColors: GlassColors = LocalGlassColors.current,
    tabsAppearance: GlassAppearance = LocalGlassAppearance.current,
) {
    val density = LocalDensity.current
    val shadowOutsets = remember { LayerOutsets(64.dp, 64.dp, 64.dp, 64.dp) }
    val shape: () -> Shape = remember(density, cornerRadius) {
        {
            // RoundedRectangle and Capsule share the same continuous-corner implementation.
            // Radius is evaluated in drawing, never stretched with an anisotropic layer scale.
            RoundedRectangle(with(density) { cornerRadius().coerceAtLeast(0f).toDp() })
        }
    }
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            // Blend complete optical outputs additively on transparency, then composite once.
            // Two SrcOver half-alpha surfaces would leak 25% of the untreated backing.
            Box(Modifier.graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                outsets = shadowOutsets
            }) {
            CompositionLocalProvider(LocalGlassColors provides tabsColors, LocalGlassAppearance provides tabsAppearance) {
            val materialContext = rememberGlassMaterialContext()
            val innerShadows = rememberNavigationInnerShadows(tabsAppearance.usesBackdrop, renderScale = .5f)
            Box(Modifier.matchParentSize().graphicsLayer {
                alpha = 1f - sidebarMaterialProgress().coerceIn(0f, 1f)
                // Intermediate group opacity creates its own offscreen target. Preserve the
                // exterior shadow here before the enclosing material-mix layer composites it.
                outsets = shadowOutsets
            }.navigationGlassBackground(
                backdrop = backdrop,
                shape = shape,
                containerColor = tabsColors.container,
                navigationMaterial = tabsAppearance.usesBackdrop,
                innerShadows = innerShadows,
                isDark = tabsColors.isDark,
                appearance = tabsAppearance,
                materialContext = materialContext,
                material = NavigationGlassMaterial.RegularLarge,
                shadowAlpha = .5f,
            ))
            }
            Box(Modifier.matchParentSize().graphicsLayer {
                alpha = sidebarMaterialProgress().coerceIn(0f, 1f)
                blendMode = BlendMode.Plus
            }.sidebarMaterialBackground(sidebarBackdrop, shape))
            }
        },
    ) { measurables, constraints ->
        val frame = bounds()
        val width = frame.width.coerceAtLeast(0f).roundToInt()
        val height = frame.height.coerceAtLeast(0f).roundToInt()
        val layers = measurables.map { it.measure(Constraints.fixed(width, height)) }
        val viewportWidth = if (constraints.hasBoundedWidth) constraints.maxWidth
            else constraints.constrainWidth(ceil(frame.right.coerceAtLeast(0f)).toInt())
        val viewportHeight = if (constraints.hasBoundedHeight) constraints.maxHeight
            else constraints.constrainHeight(ceil(frame.bottom.coerceAtLeast(0f)).toInt())
        layout(viewportWidth, viewportHeight) {
            val x = frame.left.roundToInt()
            val y = frame.top.roundToInt()
            layers.forEach { it.place(x, y) }
        }
    }
}
