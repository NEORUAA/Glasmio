package io.github.neoruaa.glasmio.ui

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.glass.InteractiveHighlight


val GlasmioModalSheetShape = ContinuousRoundedRectangle(
    topStart = 34.dp,
    topEnd = 34.dp,
    bottomStart = 58.dp,
    bottomEnd = 58.dp,
)


@Composable
fun GlasmioModalOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!visible) return
    Box(modifier.fillMaxSize().background(Color(0x3B29293A)), contentAlignment = Alignment.Center, content = content)
}

/** Figma large-sheet surface 10525:1640 in 10525:1632; inner rows remain ordinary content. */
@Composable
fun GlasmioSheetSurface(
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalBlurBackdrop.current,
    shape: Shape = ContinuousRoundedRectangle(38.dp),
    interactiveHighlight: InteractiveHighlight? = null,
    applyDragScale: Boolean = true,
    interactive: Boolean = true,
    glassFraction: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalGlassColors.current
    val appearance = LocalGlassAppearance.current
    val inheritedMaterial = LocalIosSheetMaterialContext.current
        ?: IosSheetMaterialContext(colors.isDark, if (glassFraction >= 0.5f) 1f else 0f)
    val contentMaterial = if (appearance.usesBackdrop) inheritedMaterial else inheritedMaterial.opaque()
    val materialBackdrop = rememberLayerBackdrop()
    val glass = glassFraction.coerceIn(0f, 1f)
    val innerShadows = rememberNavigationInnerShadows(true)
    val animationScope = rememberCoroutineScope()
    val activeInteractiveHighlight = interactiveHighlight ?: remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val pressProgress = remember(activeInteractiveHighlight) {
        derivedStateOf { activeInteractiveHighlight.pressProgress }
    }
    val sheetLayerBlock: (GraphicsLayerScope.() -> Unit)? = if (applyDragScale) {
        {
            applyGlassDragScale(
                pressProgress = activeInteractiveHighlight.pressProgress,
                offset = activeInteractiveHighlight.offset,
            )
        }
    } else {
        null
    }
    // Modal sheet surfaces render in a separate dialog window; the shared backdrop
    // lives in the app window, so sample it with window-space coordinates. The library's
    // localPositionOf mapping cannot cross compose owners and silently sampled nothing.
    val backdropRenderer = if (appearance.usesBackdrop) {
        val samplingBackdrop = rememberCrossWindowBackdrop(backdrop)
        rememberSheetBackdropRenderer(samplingBackdrop, NavigationGlassMaterial.RegularLarge.frostRadius)
    } else {
        emptyBackdrop()
    }
    Box(
        modifier
            .fillMaxWidth()
                .navigationGlassBackground(
                    backdrop = backdropRenderer,
                    shape = { shape },
                    containerColor = colors.container,
                    navigationMaterial = true,
                    innerShadows = innerShadows,
                    isDark = colors.isDark,
                    // The quarter-resolution backend already supplies the scene blur.
                    blurRadius = 0.dp,
                    appearance = appearance,
                    material = NavigationGlassMaterial.RegularLarge,
                    exportedBackdrop = materialBackdrop,
                    pressProgressState = pressProgress,
                    highlightAlphaMultiplier = glass,
                    innerShadowAlpha = glass,
                    preserveBackdropColors = true,
                    // Android mapping calibrated against the physical regular/sheet backdrop.
                    // This attenuates only the paint passes, preserving the native-sized optics.
                    materialPaintAlpha = 0f,
                    surfaceOverlay = {
                        val systemBackground = if (colors.isDark) Color(0xFF1C1C1E) else Color.White
                        if (!appearance.usesBackdrop) {
                            drawRect(systemBackground)
                        } else {
                            if (colors.isDark) {
                                drawRect(Color(0xFF111111).copy(alpha = 0.43f))
                            } else {
                                drawRect(Color.White.copy(alpha = 0.232f))
                                drawRect(Color.White.copy(alpha = 0.065f), blendMode = BlendMode.Plus)
                            }
                            if (glass < 1f) drawRect(systemBackground.copy(alpha = 1f - glass))
                        }
                    },
                    layerBlock = sheetLayerBlock,
                )
                .then(if (interactive) activeInteractiveHighlight.modifier else Modifier)
                .then(if (interactive) activeInteractiveHighlight.gestureModifier else Modifier),
            content = {
                CompositionLocalProvider(
                    LocalContentColor provides colors.content,
                    LocalGlassContentColor provides colors.content,
                    LocalGroupedListBackgroundAlpha provides SheetGroupedListBackgroundAlpha,
                    LocalIosSheetMaterialContext provides contentMaterial,
                    LocalIosSheetBackdrop provides materialBackdrop,
                    LocalGlassColors provides colors.copy(
                        groupedBackground = contentMaterial.tableColor,
                        elevatedBackground = contentMaterial.cellColor,
                        separator = contentMaterial.separatorColor,
                        secondaryContent = contentMaterial.secondaryLabelColor,
                    ),
                ) {
                    content()
                }
            },
    )
}

/** Figma node 754:62559: title, optional message, and a vertically grouped action list. */
@Composable
fun GlasmioActionSheet(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    backdrop: Backdrop = LocalBlurBackdrop.current,
    shape: Shape = GlasmioModalSheetShape,
    showHandle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlasmioSheetSurface(modifier, backdrop, shape) {
        GlasmioActionSheetContent(
            title = title,
            message = message,
            showHandle = showHandle,
            content = content,
        )
    }
}

@Composable
fun GlasmioActionSheetContent(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    showHandle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalGlassColors.current
    CompositionLocalProvider(LocalContentColor provides colors.content) {
        Column(
            modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(
                top = if (showHandle) 4.dp else 18.dp,
                bottom = 18.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showHandle) {
                Box(Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.TopCenter) {
                    Box(
                        Modifier
                            .size(width = 58.dp, height = 4.dp)
                            .background(LocalGlassColors.current.tertiaryContent.copy(alpha = 0.55f), Capsule()),
                    )
                }
            }
            Column(Modifier.padding(horizontal = 8.dp)) {
                Text(title, style = IosTypography.headline, color = colors.content)
                message?.let {
                    Text(
                        it,
                        style = IosTypography.subheadline,
                        color = LocalGlassColors.current.secondaryContent,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            GlasmioGroupedList(content = content)
        }
    }
}
