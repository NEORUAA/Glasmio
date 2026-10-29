package io.github.neoruaa.glasmio.ui

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.effects.blur
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.first


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
                Text(
                    title,
                    style = IosTypography.largeTitle,
                    color = LocalGlassColors.current.content,
                    modifier = Modifier.weight(1f),
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
                val centeredTitleSidePadding = maxOf(leadingPad, trailingPad) +
                    if (leadingWidth > 0 || trailingWidth > 0) IosTopToolbarActionGap else 0.dp
                Box(modifier.fillMaxWidth().height(54.dp).padding(horizontal = IosPageHorizontalPadding)) {
                    GlasmioToolbarActionGroup(
                        Modifier.align(Alignment.CenterStart).onSizeChanged { leadingWidth = it.width },
                        style = navigationStyle,
                    ) { navigation?.invoke() }
                    Column(
                        Modifier
                            .align(if (style == GlasmioTopBarStyle.TwoLineLeading) Alignment.CenterStart else Alignment.Center)
                            .fillMaxWidth()
                            .padding(
                                // Centered styles stay optically centered: reserve the wider
                                // side on both edges, plus the same gap used between actions.
                                // The leading style just clears the sides.
                                start = if (style == GlasmioTopBarStyle.TwoLineLeading) leadingPad
                                else centeredTitleSidePadding,
                                end = if (style == GlasmioTopBarStyle.TwoLineLeading) trailingPad
                                else centeredTitleSidePadding,
                            )
                            .graphicsLayer {
                                alpha = progress
                                val scale = 0.92f + 0.08f * progress
                                scaleX = scale
                                scaleY = scale
                            }
                            .blur(8.dp * (1f - progress)),
                        horizontalAlignment = if (style == GlasmioTopBarStyle.TwoLineLeading) Alignment.Start else Alignment.CenterHorizontally,
                    ) {
                        Text(
                            title,
                            style = if (subtitle == null) IosTypography.headline
                            else IosTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                            color = LocalGlassColors.current.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        subtitle?.let {
                            Text(
                                it,
                                style = IosTypography.caption,
                                color = LocalGlassColors.current.secondaryContent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
) {
    val colors = LocalGlassColors.current
    val backdrop = LocalGlassBackdrop.current
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
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SfIcon("magnifyingglass", null, size = 21.dp, tint = colors.secondaryContent)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).graphicsLayer { alpha = contentReveal }) {
                    GlasmioEditableTextField(
                        value = query,
                        onValueChange = onQueryChange,
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
                            .clickable { onQueryChange(TextFieldValue()) },
                        contentAlignment = Alignment.Center,
                    ) {
                        SfIcon("xmark.circle", null, size = 18.dp, tint = colors.secondaryContent)
                    }
                }
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
                enabled = cancelReveal > 0.5f,
            ) {
                SfIcon("xmark", cancelLabel, size = 20.dp, tint = colors.content)
            }
        }
    }
    LaunchedEffect(autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }
}

/** Figma node 5661:33949: 52dp table text field with iOS separators. */
