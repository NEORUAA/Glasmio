package io.github.neoruaa.glasmio.ui

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.*

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.effects.blur
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.first

/** Shared vertical allocation for page and primary-sidebar toolbars. */
object GlasmioToolbarDefaults {
    val Height = 62.dp
    val ControlHeight = 44.dp
    val VerticalPadding = (Height - ControlHeight) / 2
}


@Composable
fun <T> GlasmioScrollableTabRow(
    items: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.first.toString() }) { (value, label) ->
            val isSelected = value == selected
            Text(
                text = label,
                color = if (isSelected) Color.White else colors.content,
                style = IosTypography.subheadline,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .background(
                        if (isSelected) colors.prominentContainer.copy(alpha = 1f)
                        else colors.segmentedControlBackground,
                        Capsule(),
                    )
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                    ) { onSelected(value) }
                    .padding(horizontal = 15.dp, vertical = 7.dp),
            )
        }
    }
}

enum class GlasmioTopBarStyle { Default, CompactLargeTitle, LargeTitle, TwoLine, TwoLineLeading }

enum class GlasmioBottomToolbarStyle { Text, Symbols, Search }

private val IosTopToolbarActionGap = 8.dp

/**
 * A page-owned toolbar title that keeps the full string available to the layout before falling
 * back to marquee behavior. Edge fading is only enabled while the text overflows.
 */
@Composable
fun GlasmioToolbarTitle(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalGlassColors.current.content,
    textAlign: TextAlign = TextAlign.Center,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val textMeasurer = rememberTextMeasurer()
        val naturalWidth = remember(text, style, textMeasurer) {
            textMeasurer.measure(
                AnnotatedString(text), style = style, maxLines = 1, softWrap = false,
            ).size.width
        }
        val overflowing = naturalWidth > with(LocalDensity.current) { maxWidth.toPx() } + .5f
        val fadeModifier = if (overflowing) {
            Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val edge = minOf(14.dp.toPx(), size.width / 2f)
                    val width = size.width.coerceAtLeast(1f)
                    val start = (edge / width).coerceIn(0f, .5f)
                    val end = (1f - edge / width).coerceIn(.5f, 1f)
                    val mask = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        start to Color.White,
                        end to Color.White,
                        1f to Color.Transparent,
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(mask, blendMode = BlendMode.DstIn)
                    }
                }
        } else Modifier
        Box(Modifier.fillMaxWidth().then(fadeModifier)) {
            Text(
                text = text,
                style = style,
                color = color,
                textAlign = textAlign,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (overflowing) Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        repeatDelayMillis = 1_800,
                        initialDelayMillis = 650,
                    ) else Modifier),
            )
        }
    }
}

/** The five top-toolbar variants from Figma node 5661:41970. */
@Composable
fun GlasmioTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    style: GlasmioTopBarStyle = GlasmioTopBarStyle.Default,
    collapseProgress: Float = 1f,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    navigationStyle: GlasmioToolbarActionStyle = GlasmioToolbarActionStyle.Merged,
    actionsStyle: GlasmioToolbarActionStyle = GlasmioToolbarActionStyle.Merged,
    actionsSeparationProgress: Float? = null,
) {
    val progress = collapseProgress.coerceIn(0f, 1f)
    CompositionLocalProvider(
        LocalGlassSurfaceBrightness provides 1f,
        LocalGlassSurfaceStyle provides GlassSurfaceStyle.Navigation,
        LocalContentColor provides LocalGlassColors.current.content,
    ) {
        when (style) {
            GlasmioTopBarStyle.LargeTitle -> Column(modifier.padding(horizontal = IosPageHorizontalPadding, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        GlasmioToolbarActionGroup(style = navigationStyle) { navigation?.invoke() }
                    }
                    GlasmioToolbarActionGroup(
                        style = actionsStyle,
                        separationProgress = actionsSeparationProgress,
                        content = actions,
                    )
                }
                Column(Modifier.offset(y = (-10).dp)) {
                    Text(title, style = IosTypography.largeTitle, color = LocalGlassColors.current.content)
                    subtitle?.let { Text(it, style = IosTypography.subheadline, color = LocalGlassColors.current.secondaryContent) }
                }
            }
            GlasmioTopBarStyle.CompactLargeTitle -> Row(
                modifier.fillMaxWidth().height(54.dp).padding(horizontal = IosPageHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlasmioToolbarTitle(
                    text = title,
                    style = IosTypography.largeTitle,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start,
                )
                GlasmioToolbarActionGroup(
                    style = actionsStyle,
                    separationProgress = actionsSeparationProgress,
                    content = actions,
                )
            }
            else -> {
                var leadingWidth by remember { mutableIntStateOf(0) }
                var trailingWidth by remember { mutableIntStateOf(0) }
                val density = LocalDensity.current
                val leadingPad = with(density) { leadingWidth.toDp() }
                val trailingPad = with(density) { trailingWidth.toDp() }
                val leadingTitlePadding = leadingPad +
                    if (leadingWidth > 0) IosTopToolbarActionGap else 0.dp
                val trailingTitlePadding = trailingPad +
                    if (trailingWidth > 0) IosTopToolbarActionGap else 0.dp
                val startsAtLeading = style == GlasmioTopBarStyle.TwoLineLeading
                val titleStyle = if (subtitle == null) IosTypography.headline
                    else IosTypography.subheadline.copy(fontWeight = FontWeight.SemiBold)
                val subtitleStyle = IosTypography.caption
                val textMeasurer = rememberTextMeasurer()
                val titleWidth = remember(title, titleStyle, textMeasurer) {
                    textMeasurer.measure(AnnotatedString(title), titleStyle, maxLines = 1, softWrap = false).size.width
                }
                val subtitleWidth = subtitle?.let {
                    remember(it, subtitleStyle, textMeasurer) {
                        textMeasurer.measure(AnnotatedString(it), subtitleStyle, maxLines = 1, softWrap = false).size.width
                    }
                }
                BoxWithConstraints(modifier.fillMaxWidth().height(54.dp).padding(horizontal = IosPageHorizontalPadding)) {
                    val availableWidth = with(density) { maxWidth.toPx() }
                    val safeStart = with(density) { leadingTitlePadding.toPx() }
                    val safeEnd = availableWidth - with(density) { trailingTitlePadding.toPx() }
                    fun fitsCentered(width: Int?): Boolean {
                        if (width == null) return true
                        val left = (availableWidth - width) / 2f
                        return left >= safeStart && left + width <= safeEnd
                    }
                    val titleSqueezed = !startsAtLeading && !fitsCentered(titleWidth)
                    val subtitleSqueezed = !startsAtLeading && !fitsCentered(subtitleWidth)
                    val safeModifier = Modifier.fillMaxWidth().padding(
                        start = leadingTitlePadding,
                        end = trailingTitlePadding,
                    )
                    GlasmioToolbarActionGroup(
                        Modifier.align(Alignment.CenterStart).onSizeChanged { leadingWidth = it.width },
                        style = navigationStyle,
                    ) { navigation?.invoke() }
                    Column(
                        Modifier
                            .align(if (startsAtLeading) Alignment.CenterStart else Alignment.Center)
                            .fillMaxWidth()
                            .then(if (startsAtLeading) Modifier.padding(start = leadingPad, end = trailingPad) else Modifier)
                            .graphicsLayer {
                                alpha = progress
                                val scale = 0.92f + 0.08f * progress
                                scaleX = scale
                                scaleY = scale
                            }
                            .blur(8.dp * (1f - progress)),
                        horizontalAlignment = if (startsAtLeading) Alignment.Start else Alignment.CenterHorizontally,
                    ) {
                        GlasmioToolbarTitle(
                            text = title,
                            style = titleStyle,
                            modifier = if (titleSqueezed) safeModifier else Modifier.fillMaxWidth(),
                            textAlign = when {
                                startsAtLeading -> TextAlign.Start
                                titleSqueezed -> TextAlign.End
                                else -> TextAlign.Center
                            },
                        )
                        subtitle?.let {
                            Text(
                                it,
                                style = subtitleStyle,
                                color = LocalGlassColors.current.secondaryContent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = when {
                                    startsAtLeading -> Modifier
                                    subtitleSqueezed -> safeModifier
                                    else -> Modifier.fillMaxWidth()
                                },
                                textAlign = when {
                                    startsAtLeading -> TextAlign.Start
                                    subtitleSqueezed -> TextAlign.End
                                    else -> TextAlign.Center
                                },
                            )
                        }
                    }
                    GlasmioToolbarActionGroup(
                        Modifier.align(Alignment.CenterEnd).onSizeChanged { trailingWidth = it.width },
                        style = actionsStyle,
                        separationProgress = actionsSeparationProgress,
                        content = actions,
                    )
                }
            }
        }
    }
}

/** Compact toolbar for the [GlasmioBottomSheet] topBar slot, which owns the progressive blur.
 * The sheet supplies its body-only backdrop to the native navigation action material. */
@Composable
fun GlasmioSheetTopToolbar(
    title: String,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    GlasmioTopBar(
        title = title,
        modifier = modifier.offset(y = (-5).dp),
        navigation = navigation,
        actions = actions,
    )
}

@Composable
fun GlasmioSheetToolbarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    GlasmioIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = GlassSurfaceStyle.Navigation,
        onLongClick = onLongClick,
        content = content,
    )
}

/** Text actions share the sheet toolbar's sampled regular material and press physics. */
@Composable
fun GlasmioSheetButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    GlasmioButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = GlassSurfaceStyle.Navigation,
        content = content,
    )
}

/** Figma node 2517:14528. Floating bottom toolbar variants share the liquid capsule. */
@Composable
fun GlasmioBottomToolbar(
    modifier: Modifier = Modifier,
    style: GlasmioBottomToolbarStyle = GlasmioBottomToolbarStyle.Symbols,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    content: @Composable RowScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier.height(if (style == GlasmioBottomToolbarStyle.Search) 64.dp else 56.dp),
        backdrop = backdrop,
        shape = Capsule(),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Composable
fun GlasmioSearchToolbar(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    onCancel: () -> Unit,
    cancelLabel: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    style: GlassSurfaceStyle = GlassSurfaceStyle.Navigation,
    focusRequester: FocusRequester = remember { FocusRequester() },
    autoFocus: Boolean = true,
    morphProgress: Float = 1f,
    collapsedContent: (@Composable BoxScope.() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val colors = LocalGlassColors.current
    val backdrop = LocalGlassBackdrop.current
    val keyboard = LocalSoftwareKeyboardController.current
    val progress = morphProgress.coerceIn(0f, 1f)
    val contentReveal = ((progress - 0.25f) / 0.45f).coerceIn(0f, 1f)
    val cancelReveal = ((progress - 0.35f) / 0.45f).coerceIn(0f, 1f)
    Row(
        modifier.height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassSurface(
            modifier = Modifier.weight(1f).height(48.dp).testTag("GlasmioSearchFieldSurface"),
            backdrop = backdrop,
            shape = Capsule(),
            style = style,
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 12.dp).graphicsLayer {
                    alpha = if (collapsedContent == null) 1f else ((progress - 0.1f) / 0.35f).coerceIn(0f, 1f)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SfIcon("magnifyingglass", null, size = 21.dp, tint = colors.secondaryContent)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).graphicsLayer { alpha = contentReveal }) {
                    GlasmioEditableTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = IosTypography.body.copy(color = colors.content),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(query.text) }),
                        decorationBox = { inner ->
                            if (query.text.isEmpty()) {
                                Text(placeholder, style = IosTypography.body, color = colors.tertiaryContent)
                            }
                            inner()
                        },
                    )
                }
                if (query.text.isNotEmpty()) {
                    Box(
                        Modifier.size(32.dp).graphicsLayer { alpha = contentReveal }
                            .clickable(enabled = enabled) { onQueryChange(TextFieldValue()) },
                        contentAlignment = Alignment.Center,
                    ) {
                        SfIcon("xmark.circle", null, size = 18.dp, tint = colors.secondaryContent)
                    }
                }
            }
            if (collapsedContent != null && progress < 0.3f) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    alpha = (1f - progress / 0.3f).coerceIn(0f, 1f)
                }.clearAndSetSemantics {}, content = collapsedContent)
            }
        }
        Spacer(Modifier.width(8.dp * cancelReveal))
        Box(
            Modifier.width(48.dp * cancelReveal).height(48.dp)
                .graphicsLayer {
                    alpha = cancelReveal
                    val scale = 0.85f + 0.15f * cancelReveal
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center,
        ) {
            GlasmioIconButton(
                onClick = onCancel,
                backdrop = backdrop,
                style = style,
                modifier = Modifier.size(48.dp).testTag("GlasmioSearchCancelSurface"),
                enabled = enabled && cancelReveal > 0.5f,
            ) {
                SfIcon("xmark", cancelLabel, size = 20.dp, tint = colors.content)
            }
        }
    }
    LaunchedEffect(autoFocus, enabled) {
        if (autoFocus && enabled) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
}

/** Figma node 5661:33949: 52dp table text field with iOS separators. */
