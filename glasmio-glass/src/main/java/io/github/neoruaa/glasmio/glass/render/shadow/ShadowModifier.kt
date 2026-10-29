package io.github.neoruaa.glasmio.glass.render.shadow

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import io.github.neoruaa.glasmio.glass.NavigationDropShadowRenderer
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import io.github.neoruaa.glasmio.glass.render.internal.ShapeProvider

internal data class ShadowElement(
    val shapeProvider: ShapeProvider,
    val shadow: () -> Shadow?,
) : ModifierNodeElement<ShadowNode>() {
    override fun create() = ShadowNode(shapeProvider, shadow)

    override fun update(node: ShadowNode) {
        node.shapeProvider = shapeProvider
        node.shadow = shadow
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassShadow"
        properties["shadow"] = shadow
    }
}

internal class ShadowNode(
    var shapeProvider: ShapeProvider,
    var shadow: () -> Shadow?,
) : Modifier.Node(), DrawModifierNode {
    private var renderer: NavigationDropShadowRenderer? = null
    private var configuration: Configuration? = null

    override fun ContentDrawScope.draw() {
        val effect = shadow()
        if (effect != null && effect.alpha > 0f && effect.color.alpha > 0f) {
            val nativeDefault = effect.radius == Shadow.Default.radius && effect.offset == Shadow.Default.offset &&
                effect.color == Shadow.Default.color && effect.blendMode == Shadow.Default.blendMode && !effect.radiusIsSigma
            val nextConfiguration = Configuration(effect.radius, effect.offset, effect.color.copy(alpha = 1f),
                effect.blendMode, nativeDefault, effect.radiusIsSigma)
            if (configuration != nextConfiguration) {
                renderer?.release()
                renderer = NavigationDropShadowRenderer(
                    cacheBlurResult = true,
                    shadowRadius = effect.radius,
                    shadowOffsetY = effect.offset.y,
                    shadowAlpha = 1f,
                    shadowOffsetX = effect.offset.x,
                    shadowColor = nextConfiguration.color,
                    shadowBlendMode = effect.blendMode,
                    useNativeProfile = nativeDefault,
                    nativeMaterial = NavigationGlassMaterial.RegularSmall,
                    gaussianRadiusIsSigma = effect.radiusIsSigma,
                )
                configuration = nextConfiguration
            }
            // Opacity is applied after cached GPU filtering, so press animation changes no resources.
            renderer!!.draw(this, shapeProvider.innerShape,
                alpha = effect.alpha * if (nativeDefault) 1f else effect.color.alpha)
        }
        drawContent()
    }

    override fun onDetach() {
        renderer?.release()
        renderer = null
        configuration = null
    }

    private data class Configuration(
        val radius: Dp,
        val offset: DpOffset,
        val color: Color,
        val blendMode: BlendMode,
        val nativeDefault: Boolean,
        val radiusIsSigma: Boolean,
    )
}
