package io.github.neoruaa.glasmio.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassAppearance
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.swipeBackExcluded
import io.github.neoruaa.glasmio.glass.DampedDragAnimation
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.NativeControlLensStyle
import io.github.neoruaa.glasmio.glass.InteractiveHighlight
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import io.github.neoruaa.glasmio.glass.navigationGlassBackground
import io.github.neoruaa.glasmio.glass.rememberNavigationInnerShadows
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.rememberGlassMaterialContext
import io.github.neoruaa.glasmio.glass.nativeControlLens
import io.github.neoruaa.glasmio.glass.render.backdrops.emptyBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCombinedBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.drawPlainBackdrop
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.icons.SfSymbol
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** Keeps top tabs composed while the adaptive host morphs their backplate into a sidebar. */
@Stable
interface GlasmioTabBarPresentation {
    val backgroundVisible: () -> Boolean
    val contentAlpha: () -> Float
    val interactive: Boolean
    val capturesPreview: Boolean get() = false
    val drawsContentInPlace: Boolean get() = true
    fun onPreviewLayer(layer: GraphicsLayer, size: IntSize) {}
    fun onMaterialEndpoint(colors: io.github.neoruaa.glasmio.core.GlassColors, appearance: io.github.neoruaa.glasmio.core.GlassAppearance) {}
}

private object DefaultTabBarPresentation : GlasmioTabBarPresentation {
    override val backgroundVisible: () -> Boolean = { true }
    override val contentAlpha: () -> Float = { 1f }
    override val interactive: Boolean = true
}

val LocalGlasmioTabBarPresentation = staticCompositionLocalOf<GlasmioTabBarPresentation> {
    DefaultTabBarPresentation
}

/** iPad-style top tab geometry, excluding the enclosing toolbar's safe-area insets. */
object GlasmioTabBarDefaults {
    val Height = 44.dp
    val ContentPadding = 4.dp
    val IndicatorHeight = 36.dp
    val ActionWidth = 54.dp

    /** The bar hugs this width; a caller's smaller maximum width compresses its cells. */
    @Composable
    fun <T> preferredWidth(items: List<GlasmioTabItem<T>>, showsSidebar: Boolean = false): Dp =
        tabBarNaturalWidths(items).fold(ContentPadding * 2) { total, width -> total + width } +
            if (showsSidebar) ActionWidth else 0.dp
}

@Composable
private fun <T> tabBarNaturalWidths(items: List<GlasmioTabItem<T>>): List<Dp> {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = IosTypography.body
    return remember(items, density, textMeasurer, labelStyle) {
        items.map { item ->
            val width = maxOf(
                textMeasurer.measure(AnnotatedString(item.label), labelStyle.copy(fontWeight = FontWeight.SemiBold), maxLines = 1).size.width,
                textMeasurer.measure(AnnotatedString(item.label), labelStyle.copy(fontWeight = FontWeight.Bold), maxLines = 1).size.width,
            )
            with(density) { width.toDp() } + 32.dp
        } + GlasmioTabBarDefaults.ActionWidth
    }
}

/**
 * Content-sized top tabs with navigation glass, native press optics and a draggable indicator.
 * The optional leading sidebar action is independent of selection and indicator dragging.
 * Keep [searchKey] distinct from every item key; search is the trailing selectable action.
 */
@Composable
fun <T> GlasmioTabBar(
    items: List<GlasmioTabItem<T>>,
    selectedKey: T,
    onSelected: (T) -> Unit,
    onSearchClick: () -> Unit,
    searchKey: T,
    modifier: Modifier = Modifier,
    searchLabel: String = "Search",
    onSidebarToggle: (() -> Unit)? = null,
    sidebarToggleLabel: String = "Show Sidebar",
    backdrop: Backdrop = LocalGlassBackdrop.current,
    onSearchBoundsChanged: ((Rect) -> Unit)? = null,
) {
    require(items.none { it.key == searchKey }) { "The search key must not duplicate a tab key" }
    require(items.map { it.key }.distinct().size == items.size) { "Duplicate tab keys" }
    val colors = LocalGlassColors.current
    val appearance = LocalGlassAppearance.current
    val presentation = LocalGlasmioTabBarPresentation.current
    SideEffect { presentation.onMaterialEndpoint(colors, appearance) }
    val previewLayer = rememberGraphicsLayer()
    val previewDisplayLayer = rememberGraphicsLayer()
    val labelsBackdrop = rememberLayerBackdrop()
    val trackBackdrop = rememberLayerBackdrop()
    val indicatorBackdrop = rememberCombinedBackdrop(backdrop, trackBackdrop)
    val materialContext = rememberGlassMaterialContext()
    val innerShadows = rememberNavigationInnerShadows(appearance.usesBackdrop, renderScale = .5f)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val tabs = items + GlasmioTabItem(searchKey, searchLabel, SfSymbol.Search)
    val selectedIndex = tabs.indexOfFirst { it.key == selectedKey }
    val currentItems by rememberUpdatedState(tabs)
    val currentSelection by rememberUpdatedState(selectedIndex)
    val select by rememberUpdatedState<(T) -> Unit> { key ->
        if (key == searchKey) onSearchClick() else onSelected(key)
    }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val labelStyle = IosTypography.body.copy(fontWeight = FontWeight.SemiBold)
    val naturalWidths = tabBarNaturalWidths(items)
    val sidebarWidth = if (onSidebarToggle != null) GlasmioTabBarDefaults.ActionWidth else 0.dp
    val naturalWidth = naturalWidths.fold(GlasmioTabBarDefaults.ContentPadding * 2 + sidebarWidth) { total, width -> total + width }

    BoxWithConstraints(
        modifier.width(naturalWidth).swipeBackExcluded().height(GlasmioTabBarDefaults.Height)
            .graphicsLayer {
                // The hidden source must still draw while exporting its unattenuated preview.
                alpha = if (presentation.capturesPreview) 1f else presentation.contentAlpha().coerceIn(0f, 1f)
            }
            .drawWithContent {
                if (presentation.capturesPreview) {
                    clipRect(0f, 0f, 0f, 0f) { this@drawWithContent.drawContent() }
                    presentation.onPreviewLayer(previewLayer, size.toIntSize())
                    if (presentation.drawsContentInPlace) {
                        val opacity = presentation.contentAlpha().coerceIn(0f, 1f)
                        if (opacity >= 1f) drawLayer(previewLayer)
                        else if (opacity > 0f) {
                            // Apply opacity to a display wrapper, never to the exported layer.
                            previewDisplayLayer.record { drawLayer(previewLayer) }
                            previewDisplayLayer.alpha = opacity
                            drawLayer(previewDisplayLayer)
                        }
                    }
                } else if (presentation.drawsContentInPlace) drawContent()
            }
            .then(if (presentation.interactive) Modifier.selectableGroup() else Modifier.clearAndSetSemantics { })
            .layout { measurable, constraints ->
                val child = measurable.measure(constraints)
                layout(child.width, child.height) {
                    if (presentation.capturesPreview || presentation.interactive || presentation.contentAlpha() > 0f) {
                        child.placeWithLayer(0, 0, previewLayer)
                    }
                }
            }
            .onGloballyPositioned { coordinates = it },
    ) {
        val surfaceWidthPx = constraints.maxWidth.toFloat()
        val paddingPx = with(density) { GlasmioTabBarDefaults.ContentPadding.toPx() }
        val totalWidthPx = (surfaceWidthPx - paddingPx * 2f).coerceAtLeast(1f)
        val actionCount = if (onSidebarToggle != null) 2 else 1
        val actionWidthPx = minOf(with(density) { GlasmioTabBarDefaults.ActionWidth.toPx() }, totalWidthPx / actionCount)
        val sidebarWidthPx = if (onSidebarToggle != null) actionWidthPx else 0f
        val labelWidthsPx = naturalWidths.dropLast(1).map { with(density) { it.toPx() } }
        val labelSpace = (totalWidthPx - actionWidthPx * actionCount).coerceAtLeast(.001f)
        val labelRatio = labelSpace / labelWidthsPx.sum().coerceAtLeast(1f)
        val widthsPx = labelWidthsPx.map { (it * labelRatio).coerceAtLeast(.001f) } + actionWidthPx
        val lefts = widthsPx.runningFold(sidebarWidthPx, Float::plus).dropLast(1)
        val centers = widthsPx.indices.map { lefts[it] + widthsPx[it] / 2f }
        fun interpolate(values: List<Float>, index: Float): Float {
            val position = index.coerceIn(0f, tabs.lastIndex.toFloat())
            val lower = position.toInt()
            val upper = (lower + 1).coerceAtMost(tabs.lastIndex)
            return values[lower] + (values[upper] - values[lower]) * (position - lower)
        }
        fun centerFor(index: Float) = interpolate(centers, index)
        fun indexFor(center: Float): Float {
            if (center <= centers.first()) return 0f
            for (upper in 1..tabs.lastIndex) {
                if (center <= centers[upper]) return upper - 1f +
                    (center - centers[upper - 1]) / (centers[upper] - centers[upper - 1])
            }
            return tabs.lastIndex.toFloat()
        }
        val currentCenterFor by rememberUpdatedState(::centerFor)
        val currentIndexFor by rememberUpdatedState(::indexFor)
        val currentPressedIndex by rememberUpdatedState<(Offset) -> Int> { position ->
            val logicalX = if (isLtr) position.x else totalWidthPx - sidebarWidthPx - position.x
            lefts.indexOfLast { it <= logicalX + sidebarWidthPx }.coerceAtLeast(0)
        }
        val dragOffset = remember { Animatable(0f) }
        val animation = remember(scope, tabs.size, isLtr) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = selectedIndex.coerceAtLeast(0).toFloat(),
                valueRange = 0f..tabs.lastIndex.coerceAtLeast(1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                dragCoordinateSpace = { coordinates },
                lensStyle = NativeControlLensStyle.TabBarIndicator,
                onDragStarted = { position -> updateValue(currentPressedIndex(position).toFloat()) },
                onDragStopped = {
                    val index = targetValue.roundToInt().coerceIn(0, currentItems.lastIndex)
                    if (index != currentSelection) select(currentItems[index].key)
                    animateToValue(index.toFloat())
                    scope.launch { dragOffset.animateTo(0f, spring(1f, 300f, .5f)) }
                },
                onDragCancelled = {
                    updateValue(currentSelection.coerceAtLeast(0).toFloat())
                    scope.launch { dragOffset.animateTo(0f, spring(1f, 300f, .5f)) }
                },
                onDrag = { _, amount ->
                    updateValue(currentIndexFor(currentCenterFor(targetValue) + amount.x * if (isLtr) 1f else -1f)
                        .coerceIn(0f, currentItems.lastIndex.toFloat()))
                    scope.launch { dragOffset.snapTo(dragOffset.value + amount.x) }
                },
            )
        }
        LaunchedEffect(selectedIndex, animation) {
            if (selectedIndex >= 0 && animation.targetValue != selectedIndex.toFloat()) {
                animation.animateToValue(selectedIndex.toFloat(), animatePress = false)
            }
        }
        val onUserSelected: (T) -> Unit = remember(animation) {
            { key -> animation.press(); select(key); animation.release() }
        }
        fun indicatorLeft(): Float {
            val left = interpolate(lefts, animation.value)
            val width = interpolate(widthsPx, animation.value)
            return if (isLtr) left else totalWidthPx - left - width
        }

        fun panelOffset(): Float {
            val fraction = (dragOffset.value / surfaceWidthPx).coerceIn(-1f, 1f)
            return paddingPx * fraction.sign * EaseOut.transform(abs(fraction))
        }
        val highlight = remember(scope, animation, surfaceWidthPx, isLtr) {
            InteractiveHighlight(scope) { size, _ ->
                val center = currentCenterFor(animation.value)
                Offset((if (isLtr) paddingPx + center else surfaceWidthPx - paddingPx - center) + panelOffset(),
                    size.height / 2f)
            }
        }
        LaunchedEffect(presentation.interactive) {
            if (!presentation.interactive) {
                highlight.cancelPress()
                animation.updateValue(currentSelection.coerceAtLeast(0).toFloat())
                animation.release()
            }
        }
        val puff = remember { Animatable(0f, .001f) }
        val longDimension = with(density) { surfaceWidthPx.toDp().value }.coerceAtLeast(44f)
        val springProgress = if (longDimension < 120f) 0f else ((longDimension - 44f) / 116f).coerceIn(0f, 1f)
        LaunchedEffect(highlight.isPressed, springProgress) {
            val pressed = highlight.isPressed
            val response = if (pressed) .262 + (.314 - .262) * springProgress
                else .4 + (.36 - .4) * springProgress
            val damping = if (pressed) .625f else .375f + .225f * springProgress
            puff.animateTo(if (pressed) 1f else 0f,
                spring(damping, (4.0 * PI * PI / (response * response)).toFloat(), .001f))
        }
        fun panelScale() = 1f + 16f * puff.value / longDimension
        val panelTransform = Modifier.graphicsLayer {
            translationX = panelOffset()
            scaleX = panelScale()
            scaleY = panelScale()
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationX = panelOffset()
            alpha = if (presentation.backgroundVisible()) 1f else 0f
        }
            .then(if (appearance.usesBackdrop) Modifier.layerBackdrop(trackBackdrop) else Modifier)
            .navigationGlassBackground(
                backdrop = backdrop, shape = { Capsule() }, containerColor = colors.container,
                navigationMaterial = appearance.usesBackdrop, innerShadows = innerShadows,
                isDark = colors.isDark, appearance = appearance, materialContext = materialContext,
                material = NavigationGlassMaterial.RegularLarge,
                shadowAlpha = .5f,
                pressProgressState = animation.pressProgressState,
                layerBlock = { scaleX = panelScale(); scaleY = panelScale() },
            ).then(highlight.modifier))

        @Composable
        fun RowScope.Labels(interactive: Boolean) {
            if (onSidebarToggle != null) Box(
                Modifier.weight(sidebarWidthPx).fillMaxHeight().clip(Capsule())
                    .then(if (interactive && presentation.interactive) Modifier
                        .semantics { contentDescription = sidebarToggleLabel }
                        .clickable(interactionSource = null, indication = null, role = Role.Button,
                            onClick = onSidebarToggle) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                SfIcon("sidebar.left", null, modifier = Modifier.width(21.dp), size = 16.dp,
                    inkHeight = 16.dp, weight = FontWeight.SemiBold, fitGlyphToBounds = false,
                    tint = colors.content, mirrored = true)
            }
            tabs.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                Box(
                    Modifier.weight(widthsPx[index]).fillMaxHeight().clip(Capsule())
                        .then(if (interactive) Modifier else Modifier.graphicsLayer {
                            val scale = 1f + .16f * animation.pressProgress
                            scaleX = scale
                            scaleY = scale
                        })
                        .then(if (interactive && item.key == searchKey && onSearchBoundsChanged != null) Modifier
                            .onGloballyPositioned { onSearchBoundsChanged(it.boundsInWindow()) } else Modifier)
                        .then(if (interactive && presentation.interactive) Modifier
                            .semantics { contentDescription = item.contentDescription }
                            .selectable(selected, interactionSource = null, indication = null, role = Role.Tab,
                                onClick = { onUserSelected(item.key) }) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.key == searchKey) SfIcon(SfSymbol.Search, null, size = 17.dp,
                        inkHeight = 17.dp, weight = FontWeight.SemiBold, fitGlyphToBounds = false,
                        tint = if (!interactive) colors.accent else if (selected)
                            androidx.compose.ui.graphics.lerp(colors.accent, colors.content, animation.pressProgress)
                            else colors.content)
                    else Text(item.label, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = if (!interactive) colors.accent else if (selected)
                            androidx.compose.ui.graphics.lerp(colors.accent, colors.content, animation.pressProgress)
                            else colors.content,
                        fontWeight = if (interactive) FontWeight.SemiBold else FontWeight.Bold)
                }
            }
        }
        Row(Modifier.fillMaxSize()
            .padding(GlasmioTabBarDefaults.ContentPadding).clip(Capsule())
            .then(panelTransform)) { Labels(interactive = true) }

        if (selectedIndex >= 0 && appearance.usesBackdrop) Row(
            Modifier.fillMaxSize().clearAndSetSemantics { }.alpha(0f)
                .layerBackdrop(labelsBackdrop).drawWithContent {
                    if (drawContext.graphicsLayer === labelsBackdrop.graphicsLayer) drawContent()
                }.then(panelTransform).padding(GlasmioTabBarDefaults.ContentPadding),
        ) { Labels(interactive = false) }

        if (selectedIndex >= 0) Box(
            Modifier.align(AbsoluteAlignment.TopLeft).graphicsLayer {
                val width = interpolate(widthsPx, animation.value)
                val left = paddingPx + indicatorLeft()
                translationX = left + (left + width / 2f - surfaceWidthPx / 2f) * (panelScale() - 1f) + panelOffset()
                translationY = GlasmioTabBarDefaults.ContentPadding.toPx()
            }.height(GlasmioTabBarDefaults.IndicatorHeight).clearAndSetSemantics { }
                .layout { measurable, constraints ->
                    val width = interpolate(widthsPx, animation.value).roundToInt().coerceAtLeast(1)
                    val child = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(child.width, child.height) { child.place(0, 0) }
                }.testTag("LandscapeNavigationIndicator"),
        ) {
            Box(Modifier.fillMaxSize().layout { measurable, constraints ->
                val progress = if (appearance.usesRefraction) animation.pressProgress else 0f
                val width = (constraints.maxWidth + 16.dp.toPx() * progress).roundToInt().coerceAtLeast(1)
                val height = (constraints.maxHeight + 16.dp.toPx() * progress).roundToInt().coerceAtLeast(1)
                val child = measurable.measure(Constraints.fixed(width, height))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    child.place((constraints.maxWidth - width) / 2, (constraints.maxHeight - height) / 2)
                }
            }.testTag("LandscapeNavigationLiftedLens")
                .drawPlainBackdrop(
                    backdrop = if (appearance.usesBackdrop) indicatorBackdrop else emptyBackdrop(),
                    shape = { Capsule() }, effects = {
                        if (appearance.usesBackdrop) nativeControlLens(
                            style = NativeControlLensStyle.TabBarIndicator,
                            liftProgress = if (appearance.usesRefraction) animation.pressProgress else 0f,
                            isDark = colors.isDark, liftedContent = labelsBackdrop,
                        )
                    }, layerBlock = {
                        val velocity = animation.velocity / 10f
                        scaleX = panelScale() / (1f - (velocity * .75f).coerceIn(-.2f, .2f))
                        scaleY = panelScale() * (1f - (velocity * .25f).coerceIn(-.2f, .2f))
                    }, onDrawSurface = {
                        if (!appearance.usesBackdrop) drawRect(colors.content.copy(alpha = .1f))
                    },
                ))
        }
        if (presentation.interactive) Box(
            Modifier.fillMaxSize()
                .padding(
                    start = with(density) { (paddingPx + sidebarWidthPx).toDp() },
                    end = GlasmioTabBarDefaults.ContentPadding,
                )
                .then(highlight.gestureModifier)
                .then(animation.modifier),
        )
    }
}
