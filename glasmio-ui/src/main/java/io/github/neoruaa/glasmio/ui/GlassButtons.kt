package io.github.neoruaa.glasmio.ui

import io.github.neoruaa.glasmio.glass.*

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.icons.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.glass.InteractiveHighlight
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.tanh

/** Text-button sizes from Apple's Liquid Glass button component. */
enum class GlasmioButtonSize { Small, Medium, Large }

object GlasmioButtonDefaults {
    val SmallHeight = 28.dp
    val MediumHeight = 34.dp
    val LargeHeight = 50.dp

    /** A null size respects the host theme's established control geometry. */
    fun height(size: GlasmioButtonSize?, dimensions: GlassDimensions): Dp = when (size) {
        GlasmioButtonSize.Small -> SmallHeight
        GlasmioButtonSize.Medium -> MediumHeight
        GlasmioButtonSize.Large -> LargeHeight
        null -> dimensions.controlHeight
    }
}

/** Liquid Glass / Glass Prominent text button, with caller-owned interaction state. */
@Composable
fun GlasmioButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    style: GlassSurfaceStyle = LocalGlassSurfaceStyle.current,
    navigationSurfaceColor: Color? = null,
    enabled: Boolean = true,
    emphasis: GlassEmphasis = GlassEmphasis.Regular,
    navigationSurfaceAlphaMultiplier: Float = 1.25f,
    sampleBackdrop: Boolean = true,
    detailAction: Boolean = false,
    size: GlasmioButtonSize? = null,
    destructive: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalGlassColors.current
    val buttonColors = if (destructive) colors.copy(
        content = colors.destructive,
        prominentContainer = colors.destructive,
    ) else colors
    val height = GlasmioButtonDefaults.height(size, LocalGlassDimensions.current)
    val horizontalPadding = when (size) {
        GlasmioButtonSize.Small -> 10.dp
        GlasmioButtonSize.Medium -> 12.dp
        GlasmioButtonSize.Large -> 20.dp
        null -> 16.dp
    }
    val explicitTextStyle = size?.let {
        MaterialTheme.typography.labelLarge.copy(
            fontSize = if (it == GlasmioButtonSize.Small) 15.sp else 17.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
            fontWeight = FontWeight(510),
        )
    }
    CompositionLocalProvider(LocalGlassColors provides buttonColors) {
        GlassSurface(
            modifier = modifier.height(height),
            backdrop = backdrop,
            style = style,
            navigationSurfaceColor = navigationSurfaceColor,
            navigationSurfaceAlphaMultiplier = navigationSurfaceAlphaMultiplier,
            shape = Capsule(),
            emphasis = emphasis,
            enabled = enabled,
            sampleBackdrop = sampleBackdrop,
            onClick = onClick,
            detailAction = detailAction,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (explicitTextStyle != null) ProvideTextStyle(explicitTextStyle) { content() }
                else content()
            }
        }
    }
}

@Composable
fun GlasmioIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    style: GlassSurfaceStyle = LocalGlassSurfaceStyle.current,
    navigationSurfaceColor: Color? = null,
    navigationSurfaceAlphaMultiplier: Float = 1.25f,
    enabled: Boolean = true,
    emphasis: GlassEmphasis = GlassEmphasis.Regular,
    sampleBackdrop: Boolean = true,
    morphProgress: androidx.compose.runtime.State<Float>? = null,
    morphCaptureWidth: Dp? = null,
    groupSurfaceSeparation: (() -> Float)? = LocalToolbarActionSeparation.current,
    onLongClick: (() -> Unit)? = null,
    detailAction: Boolean = false,
    size: Dp? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier.size(size ?: LocalGlassDimensions.current.iconButtonSize),
        backdrop = backdrop,
        style = style,
        navigationSurfaceColor = navigationSurfaceColor,
        navigationSurfaceAlphaMultiplier = navigationSurfaceAlphaMultiplier,
        shape = CircleShape,
        emphasis = emphasis,
        enabled = enabled,
        sampleBackdrop = sampleBackdrop,
        morphProgress = morphProgress,
        morphCaptureWidth = morphCaptureWidth,
        groupSurfaceSeparation = groupSurfaceSeparation,
        onClick = onClick,
        onLongClick = onLongClick,
        detailAction = detailAction,
        content = content,
    )
}
