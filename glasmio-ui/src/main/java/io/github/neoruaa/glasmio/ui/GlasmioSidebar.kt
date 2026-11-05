package io.github.neoruaa.glasmio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.GlasmioBackdropProvider
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.drawPlainBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCombinedBackdrop
import io.github.neoruaa.glasmio.glass.sidebarGlass
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.icons.SfSymbol
import com.kyant.shapes.Capsule

/** UIKit's planar sidebar endpoint; content extension remains a separate backdrop source. */
@Composable
internal fun Modifier.sidebarMaterialBackground(
    backdrop: Backdrop,
    shape: () -> Shape,
): Modifier {
    val colors = LocalGlassColors.current
    val appearance = LocalGlassAppearance.current
    return if (!appearance.usesBackdrop) drawWithContent {
        drawOutline(shape().createOutline(size, layoutDirection, this), colors.elevatedBackground)
        drawContent()
    }
    else drawPlainBackdrop(
        backdrop = backdrop,
        shape = shape,
        samplingScale = { _, _ -> .25f },
        effects = { sidebarGlass(backdrop, colors.isDark) },
    )
}

/** Regular-width primary column; its toolbar controls use the shared navigation glass recipe. */
@Composable
fun GlasmioSidebar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    backgroundAlpha: () -> Float = { 1f },
    contentAlpha: () -> Float = { 1f },
    onContentPreview: ((GraphicsLayer, IntSize) -> Unit)? = null,
    drawContentInPlace: Boolean = true,
    content: @Composable () -> Unit,
) {
    val materialBackdrop = rememberLayerBackdrop()
    val controlsBackdrop = rememberCombinedBackdrop(backdrop, materialBackdrop)
    val surface = Modifier.sidebarMaterialBackground(backdrop) { RectangleShape }
    val preview = rememberGraphicsLayer()
    Box(modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = backgroundAlpha().coerceIn(0f, 1f) }
            .layerBackdrop(materialBackdrop, reuseContent = true).then(surface))
        GlasmioBackdropProvider(controlsBackdrop) {
            Box(Modifier.fillMaxSize()
                .graphicsLayer { alpha = contentAlpha().coerceIn(0f, 1f) }
                .drawWithContent {
                    if (onContentPreview != null) {
                        val endpointSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                        clipRect(0f, 0f, 0f, 0f) { this@drawWithContent.drawContent() }
                        onContentPreview(preview, endpointSize)
                        if (drawContentInPlace) drawLayer(preview)
                    } else drawContent()
                }
                .layout { measurable, constraints ->
                    val child = measurable.measure(constraints)
                    layout(child.width, child.height) { child.placeWithLayer(0, 0, preview) }
                }
                .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))) { content() }
        }
    }
}

@Composable
fun GlasmioSidebarToolbar(
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().height(GlasmioToolbarDefaults.Height).padding(vertical = GlasmioToolbarDefaults.VerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, content = leading)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
fun GlasmioSidebarRow(
    title: String,
    symbol: SfSymbol,
    selected: Boolean = false,
    detail: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp).clip(Capsule())
        .background(if (selected) colors.content.copy(alpha = .1f) else Color.Transparent)
        .selectable(selected, interactionSource = null, indication = null, role = Role.Tab, onClick = onClick)
        .padding(start = 10.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
            SfIcon(symbol, null, size = 20.dp, tint = colors.accent)
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = IosTypography.body, color = colors.content, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = IosTypography.body, color = colors.secondaryContent,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun GlasmioSidebarSectionHeader(
    title: String,
    expanded: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    Row(modifier.fillMaxWidth().heightIn(min = 54.dp)
        .then(if (onExpandedChange != null) Modifier.clickable(interactionSource = null, indication = null) {
            onExpandedChange(!expanded)
        } else Modifier)
        .padding(start = 12.dp, end = 16.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = IosTypography.subheadline, color = colors.secondaryContent, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = IosTypography.subheadline, color = colors.secondaryContent,
            modifier = Modifier.padding(start = 8.dp))
        if (onExpandedChange != null) SfIcon(if (expanded) "chevron.down" else "chevron.forward", null,
            size = 12.dp, tint = colors.content, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
fun GlasmioSidebarSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (() -> Unit)? = null,
    placeholder: String = "Search",
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    Row(modifier.fillMaxWidth().padding(bottom = 20.dp).heightIn(min = 44.dp)
        .clip(Capsule()).background(colors.segmentedControlBackground).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SfIcon(SfSymbol.Search, null, size = 17.dp, tint = colors.secondaryContent)
        Spacer(Modifier.width(8.dp))
        BasicTextField(query, onQueryChange, Modifier.weight(1f),
            textStyle = IosTypography.body.copy(color = colors.content),
            singleLine = true, cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
            decorationBox = { field ->
                Box {
                    if (query.isEmpty()) Text(placeholder, style = IosTypography.body, color = colors.secondaryContent)
                    field()
                }
            },
        )
    }
}
