package io.github.neoruaa.glasmio.ui

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.*

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.rememberCoroutineScope
import com.kyant.capsule.ContinuousRoundedRectangle
import io.github.neoruaa.glasmio.glass.InteractiveHighlight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

/** Data adapter for the shared popup components; it does not own another menu style. */
data class GlasmioCascadingMenuItem(
    val title: String,
    val systemName: String? = null,
    val destructive: Boolean = false,
    val separatorBefore: Boolean = false,
    val children: List<GlasmioCascadingMenuItem> = emptyList(),
    val enabled: Boolean = true,
    val onClick: () -> Unit = {},
    val subtitle: String? = null,
)

/**
 * A dropdown menu sharing the popup lifecycle, glass surface, and interactive rows.
 * Transfers a toolbar or standalone anchor into the menu by default.
 * Disable [morphFromAnchor] for a host that does not use source-preview morphing.
 */
@Composable
fun GlasmioCascadingMenu(
    anchorBounds: Rect?,
    items: List<GlasmioCascadingMenuItem>,
    title: String,
    expandedDescription: String,
    collapsedDescription: String,
    onDismiss: () -> Unit,
    backdrop: Backdrop = LocalBlurBackdrop.current,
    headerActions: List<GlasmioCascadingMenuItem> = emptyList(),
    onTriggerAlphaChanged: ((Float) -> Unit)? = null,
    menuAnchorState: GlasmioMenuAnchorState? = null,
    useAccentIcons: Boolean = true,
    centerHorizontally: Boolean = false,
    expandFromAnchor: Alignment.Horizontal? = null,
    forceBelowAnchor: Boolean = false,
    morphFromAnchor: Boolean = true,
    headerContentHeight: Dp = 0.dp,
    headerContent: (@Composable (execute: (() -> Unit) -> Unit) -> Unit)? = null,
    viewportBounds: Rect? = null,
    itemHeight: Dp = 44.dp,
    separatorHeight: Dp = 12.dp,
) {
    var open by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var childExpanded by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val child = remember { Animatable(0f) }
    val menuScale = remember(child) {
        { 1f - 0.04f * child.value.coerceIn(0f, 1f) }
    }
    var menuBounds by remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current
    val menuScroll = rememberScrollState()
    var selectedBounds by remember { mutableStateOf<Rect?>(null) }
    val colors = LocalGlassColors.current
    val separatorColor = colors.content.copy(alpha = 0.16f)
    val separatorModifier = Modifier.graphicsLayer {
        blendMode = BlendMode.Luminosity
        compositingStrategy = CompositingStrategy.Offscreen
    }
    val headerRows = if (headerActions.isEmpty()) 0 else 2
    val resolvedHeaderHeight = if (headerContent != null) headerContentHeight
        else if (headerActions.isEmpty()) 0.dp else 76.dp + separatorHeight
    val itemHeights = remember(items, itemHeight) { items.map { menuItemHeight(itemHeight, it.title, it.subtitle) } }
    val textMeasurer = rememberTextMeasurer()
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val menuWidth = if (headerActions.isEmpty()) {
        val longestRowWidth = items.maxOfOrNull {
            textMeasurer.measure(it.title, style = IosTypography.body, maxLines = if ('\n' in it.title) 2 else 1).size.width
        } ?: 0
        maxOf(if (headerContent != null) 250.dp else 238.dp, with(density) { longestRowWidth.toDp() } + 104.dp)
            .coerceAtMost((windowWidth - 24.dp).coerceAtLeast(1.dp))
    } else {
        val longestActionWidth = headerActions.maxOfOrNull {
            textMeasurer.measure(it.title, style = IosTypography.subheadline, maxLines = 1).size.width
        } ?: 0
        // Include both cells' inner padding and the shared menu's outer content padding.
        val requiredWidth = (with(density) { longestActionWidth.toDp() } + 32.dp) * headerActions.size + 20.dp
        maxOf(280.dp, requiredWidth + 4.dp).coerceAtMost((windowWidth - 24.dp).coerceAtLeast(1.dp))
    }

    LaunchedEffect(Unit) { started = true; open = true }
    LaunchedEffect(childExpanded) {
        child.animateTo(if (childExpanded) 1f else 0f, spring(dampingRatio = 0.78f, stiffness = 240f))
        if (!childExpanded) {
            selectedIndex = null
            selectedBounds = null
        }
    }

    fun close(action: (() -> Unit)? = null) {
        android.util.Log.d("GlasmioTextTrace", "cascade close open=$open action=${action != null}")
        if (!open) return
        pendingAction = action
        childExpanded = false
        open = false
    }

    GlasmioMenu(
        expanded = open,
        onExpandedChange = {
            if (!it) {
                if (childExpanded) childExpanded = false else close()
            }
        },
        itemCount = items.size + headerRows,
        heightOverride = 20.dp + resolvedHeaderHeight + itemHeights.fold(0.dp) { total, height -> total + height } +
            separatorHeight * items.count { it.separatorBefore },
        menuWidth = menuWidth,
        backdrop = backdrop,
        exportChildBackdrop = false,
        externalAnchorBounds = anchorBounds,
        keepAnchorVisible = true,
        fullScreen = true,
        centerHorizontally = centerHorizontally,
        expandFromAnchor = expandFromAnchor,
        forceBelowAnchor = forceBelowAnchor,
        menuScale = menuScale,
        onMenuBoundsChanged = { menuBounds = it },
        onMenuAlphaChanged = { onTriggerAlphaChanged?.invoke(1f - it) },
        menuAnchorState = menuAnchorState,
        morphFromAnchor = morphFromAnchor,
        viewportBounds = viewportBounds,
        onClosed = {
            android.util.Log.d("GlasmioTextTrace", "cascade onClosed started=$started open=$open action=${pendingAction != null}")
            if (started && !open) {
                onDismiss()
                pendingAction?.invoke()
            }
        },
        // Keep the child in the existing full-screen Popup. Its origin is measured
        // before selection so the parent row and child header hand off in one frame.
        overlayContent = { menuAlpha ->
            var windowOrigin by remember { mutableStateOf<Offset?>(null) }
            BoxWithConstraints(
                Modifier.fillMaxSize().onGloballyPositioned {
                    windowOrigin = it.localToScreen(Offset.Zero)
                }.semantics { paneTitle = title },
                contentAlignment = AbsoluteAlignment.TopLeft,
            ) {
                val selected = selectedIndex?.let(items::getOrNull) ?: return@BoxWithConstraints
                val bounds = selectedBounds ?: return@BoxWithConstraints
                val origin = windowOrigin ?: return@BoxWithConstraints
                val density = LocalDensity.current
                val p = child.value.coerceIn(0f, 1f)
                val topInset = with(density) {
                    (viewportBounds?.top?.minus(origin.y)?.coerceAtLeast(0f)
                        ?: androidx.compose.foundation.layout.WindowInsets.safeDrawing.getTop(this).toFloat()).toDp()
                } + 12.dp
                val bottomInset = with(density) {
                    (viewportBounds?.bottom?.let { constraints.maxHeight - (it - origin.y) }?.coerceAtLeast(0f)
                        ?: androidx.compose.foundation.layout.WindowInsets.safeDrawing.getBottom(this).toFloat()).toDp()
                } + 12.dp
                val selectedRowHeight = menuItemHeight(itemHeight, selected.title, selected.subtitle)
                val collapsedHeight = 20.dp + selectedRowHeight
                val targetHeight = minOf(
                    collapsedHeight + selected.children.fold(0.dp) { total, item -> total + menuItemHeight(itemHeight, item.title, item.subtitle) } +
                        separatorHeight * (1 + selected.children.count { it.separatorBefore }),
                    maxHeight - topInset - bottomInset,
                )
                val rowTop = with(density) { (bounds.top - origin.y).toDp() } - 10.dp
                val targetTop = rowTop.coerceIn(topInset, (maxHeight - bottomInset - targetHeight).coerceAtLeast(topInset))
                val top = rowTop + (targetTop - rowTop) * p
                val left = with(density) { (bounds.left - origin.x).toDp() } - 10.dp
                Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { childExpanded = false })
                GlasmioContextMenu(
                    visible = true,
                    modifier = Modifier.absoluteOffset(left, top).graphicsLayer { alpha = menuAlpha() },
                    backdrop = backdrop,
                    exportChildBackdrop = false,
                    compact = true,
                    menuWidth = menuWidth,
                    heightOverride = collapsedHeight + (targetHeight - collapsedHeight) * p,
                    shadowAlpha = p,
                    highlightAlpha = p,
                    itemCount = selected.children.size + 1,
                ) { childBackdrop ->
                    GlasmioMenuItem(
                        title = selected.title,
                        subtitle = selected.subtitle,
                        rowHeight = selectedRowHeight,
                        systemName = selected.systemName,
                        iconTint = if (selected.destructive) colors.destructive else if (useAccentIcons) colors.accent else colors.content,
                        destructive = selected.destructive,
                        fontWeight = FontWeight((400 + 200 * p).roundToInt()),
                        backdrop = childBackdrop,
                        enabled = selected.enabled && open,
                        trailing = { MenuChevron(90f * p, if (childExpanded) expandedDescription else collapsedDescription) },
                        onClick = { childExpanded = !childExpanded },
                    )
                    Column(Modifier.weight(1f).graphicsLayer {
                        alpha = p
                        translationY = -separatorHeight.toPx() * (1f - p)
                    }.verticalScroll(rememberScrollState())) {
                        Box(Modifier.fillMaxWidth().height(separatorHeight), contentAlignment = Alignment.Center) {
                            HorizontalDivider(Modifier.padding(horizontal = 12.dp).then(separatorModifier), thickness = 0.5.dp, color = separatorColor)
                        }
                        selected.children.forEach { item ->
                            if (item.separatorBefore) {
                                Box(Modifier.fillMaxWidth().height(separatorHeight), contentAlignment = Alignment.Center) {
                                    HorizontalDivider(Modifier.padding(horizontal = 12.dp).then(separatorModifier), thickness = 0.5.dp, color = separatorColor)
                                }
                            }
                            GlasmioMenuItem(
                                title = item.title,
                                subtitle = item.subtitle,
                                rowHeight = menuItemHeight(itemHeight, item.title, item.subtitle),
                                backdrop = childBackdrop,
                                enabled = item.enabled && open && childExpanded && p > 0.95f,
                                onClick = { close(item.onClick) },
                            )
                        }
                    }
                }
            }
        },
        anchor = { Spacer(Modifier.size(1.dp)) },
    ) { childBackdrop, _ ->
        Column(Modifier.fillMaxSize().verticalScroll(menuScroll)) {
            if (headerContent != null) {
                Column(Modifier.graphicsLayer {
                    alpha = 1f - 0.58f * child.value.coerceIn(0f, 1f)
                }.then(if (selectedIndex != null) Modifier.semantics { hideFromAccessibility() } else Modifier)) {
                    headerContent { action -> close(action) }
                }
            } else if (headerActions.isNotEmpty()) {
                Column(Modifier.graphicsLayer {
                    alpha = 1f - 0.58f * child.value.coerceIn(0f, 1f)
                }.then(if (selectedIndex != null) Modifier.semantics { hideFromAccessibility() } else Modifier)) {
                    Row(Modifier.fillMaxWidth().height(76.dp)) {
                        headerActions.forEach { action ->
                            MenuHeaderAction(
                                action = action,
                                iconTint = if (action.destructive) colors.destructive else if (useAccentIcons) colors.accent else colors.content,
                                enabled = action.enabled && open && selectedIndex == null,
                                modifier = Modifier.weight(1f),
                                onClick = { close(action.onClick) },
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(separatorHeight), contentAlignment = Alignment.Center) {
                        HorizontalDivider(
                            Modifier.padding(horizontal = 16.dp).then(separatorModifier),
                            thickness = 0.5.dp,
                            color = separatorColor,
                        )
                    }
                }
            }
            items.forEachIndexed { index, item ->
                if (item.separatorBefore) {
                    Box(Modifier.fillMaxWidth().height(separatorHeight).graphicsLayer {
                        alpha = 1f - 0.58f * child.value.coerceIn(0f, 1f)
                    }, contentAlignment = Alignment.Center) {
                        HorizontalDivider(
                            Modifier.padding(horizontal = 16.dp).then(separatorModifier),
                            thickness = 0.5.dp,
                            color = separatorColor,
                        )
                    }
                }
                GlasmioMenuItem(
                    title = item.title,
                    subtitle = item.subtitle,
                    rowHeight = itemHeights[index],
                    systemName = item.systemName,
                    destructive = item.destructive,
                    backdrop = childBackdrop,
                    iconTint = if (item.destructive) colors.destructive else if (useAccentIcons) colors.accent else colors.content,
                    enabled = item.enabled && selectedIndex == null,
                    modifier = Modifier.graphicsLayer {
                        alpha = if (selectedIndex == index) 0f else 1f - 0.58f * child.value.coerceIn(0f, 1f)
                    }.then(if (selectedIndex != null) Modifier.semantics { hideFromAccessibility() } else Modifier),
                    trailing = if (item.children.isEmpty()) null else {
                        { MenuChevron(0f, collapsedDescription) }
                    },
                    onClick = {
                        if (item.children.isEmpty()) close(item.onClick)
                        else {
                            menuBounds?.let { bounds ->
                                // Use the resting menu layout and the shared row metrics, never
                                // the pressed row's transformed screen coordinates.
                                val inset = with(density) { 10.dp.toPx() }
                                val rowHeight = with(density) { itemHeights[index].toPx() }
                                val previousRowsHeight = with(density) {
                                    resolvedHeaderHeight.toPx() + itemHeights.take(index).fold(0.dp) { total, height -> total + height }.toPx()
                                }
                                val top = bounds.top + inset + previousRowsHeight - menuScroll.value +
                                    with(density) { separatorHeight.toPx() } * items.take(index + 1).count { it.separatorBefore }
                                selectedBounds = Rect(
                                    bounds.left + inset, top, bounds.right - inset, top + rowHeight,
                                )
                                selectedIndex = index
                                childExpanded = true
                            }
                        }
                    },
                )
            }
        }
    }

}

@Composable
private fun MenuChevron(rotation: Float, state: String) {
    SfIcon(
        "chevron.right", null, size = 14.dp, tint = LocalGlassColors.current.content,
        modifier = Modifier.semantics { stateDescription = state }.graphicsLayer { rotationZ = rotation },
    )
}

/** The active shape is inset from the 34dp menu radius by its 10dp content padding. */
@Composable
private fun MenuHeaderAction(
    action: GlasmioCascadingMenuItem,
    iconTint: Color,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalGlassColors.current
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(scope) }
    val shape = ContinuousRoundedRectangle(34.dp - 10.dp)
    Column(
        modifier.height(76.dp).clip(shape)
            .background(Color.Black.copy(alpha = 0.15f * highlight.pressProgress), shape)
            .then(if (enabled) Modifier.clickable(
                interactionSource = null, indication = null, role = Role.Button, onClick = onClick,
            ).then(highlight.gestureModifier) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        action.systemName?.let { SfIcon(it, null, size = 24.dp, tint = iconTint) }
        Spacer(Modifier.height(6.dp))
        Text(action.title, style = IosTypography.subheadline, color = colors.content,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
