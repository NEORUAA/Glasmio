package io.github.neoruaa.glasmio.ui

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.*

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.roundToInt

private fun submenuSpring(dampingRatio: Float, responseSeconds: Float) = spring<Float>(
    dampingRatio = dampingRatio,
    stiffness = (2f * PI.toFloat() / responseSeconds).let { it * it },
    visibilityThreshold = 0.001f,
)

// UIKit's stacked hierarchy uses distinct spring behaviors for these properties.
private val SubmenuOpenGeometry = submenuSpring(dampingRatio = 0.85f, responseSeconds = 0.40f)
private val SubmenuCloseGeometry = submenuSpring(dampingRatio = 1f, responseSeconds = 0.40f)
private val SubmenuOpenAppearance = submenuSpring(dampingRatio = 1f, responseSeconds = 0.20f)
private val SubmenuCloseAppearance = submenuSpring(dampingRatio = 1f, responseSeconds = 0.50f)

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
    val icon: (@Composable () -> Unit)? = null,
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
    var open by remember { mutableStateOf(true) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var childExpanded by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val childTarget = if (childExpanded) 1f else 0f
    val geometry by animateFloatAsState(
        childTarget,
        if (childExpanded) SubmenuOpenGeometry else SubmenuCloseGeometry,
        visibilityThreshold = 0.001f,
        label = "Submenu geometry",
    )
    val appearance by animateFloatAsState(
        childTarget,
        if (childExpanded) SubmenuOpenAppearance else SubmenuCloseAppearance,
        visibilityThreshold = 0.001f,
        label = "Submenu appearance",
    )
    val openingPivot = remember { Animatable(1f) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex == null) openingPivot.snapTo(1f)
        else openingPivot.animateTo(0f, SubmenuOpenGeometry)
    }
    val menuScale = remember {
        MenuScaleMotion(
            scale = { 1f - 0.03f * geometry.coerceIn(0f, 1f) },
            isSettled = {
                val target = if (childExpanded) 1f else 0f
                geometry == target && appearance == target
            },
        )
    }
    fun parentContentAlpha() = 1f - 0.5f * geometry.coerceIn(0f, 1f)
    var menuBounds by remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current
    val hostView = LocalView.current
    val safeViewport = menuSafeViewport(viewportBounds)
    val menuScroll = rememberScrollState()
    val colors = LocalGlassColors.current
    val headerRows = if (headerActions.isEmpty()) 0 else 2
    val headerHeightPx = with(density) {
        if (headerContent != null) headerContentHeight.roundToPx()
        else if (headerActions.isEmpty()) 0 else 76.dp.roundToPx() + separatorHeight.roundToPx()
    }
    val itemHeights = remember(items, itemHeight) { items.map { menuItemHeight(itemHeight, it.title, it.subtitle) } }
    val menuHeight = with(density) {
        paddedMenuHeight(headerHeightPx + itemHeights.sumOf { it.roundToPx() } +
            items.count { it.separatorBefore } * separatorHeight.roundToPx())
    }
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

    LaunchedEffect(childExpanded) {
        if (!childExpanded) {
            snapshotFlow { geometry == 0f && appearance == 0f }.first { it }
            if (!childExpanded) {
                selectedIndex = null
            }
        }
    }

    fun close(action: (() -> Unit)? = null) {
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
        heightOverride = menuHeight,
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
            if (!open) {
                onDismiss()
                pendingAction?.invoke()
            }
        },
        // Keep the child in the existing full-screen Popup. Its origin is measured
        // before selection so the parent row and child header hand off in one frame.
        overlayContent = { menuAlpha ->
            var popupScreenOrigin by remember { mutableStateOf<Offset?>(null) }
            var hostWindowScreenOrigin by remember { mutableStateOf(Offset.Zero) }
            val hostScreenLocation = remember { IntArray(2) }
            val hostWindowLocation = remember { IntArray(2) }
            Box(
                Modifier.fillMaxSize().onGloballyPositioned {
                    hostView.getLocationOnScreen(hostScreenLocation)
                    hostView.getLocationInWindow(hostWindowLocation)
                    hostWindowScreenOrigin = Offset(
                        (hostScreenLocation[0] - hostWindowLocation[0]).toFloat(),
                        (hostScreenLocation[1] - hostWindowLocation[1]).toFloat(),
                    )
                    popupScreenOrigin = it.localToScreen(Offset.Zero)
                }.semantics { paneTitle = title },
                contentAlignment = AbsoluteAlignment.TopLeft,
            ) {
                val index = selectedIndex ?: return@Box
                val selected = items.getOrNull(index) ?: return@Box
                val parent = menuBounds ?: return@Box
                val origin = popupScreenOrigin?.takeIf { it.isFinite } ?: return@Box
                val popupOriginInHost = origin - hostWindowScreenOrigin
                val p = geometry
                val revealProgress = p.coerceIn(0f, 1f)
                val selectedRowHeight = itemHeights[index]
                // Keep the source in the parent's resting layout if its window or viewport moves.
                // Compose rounds each measured row and separator separately.
                val bounds = with(density) {
                    val inset = 10.dp.roundToPx()
                    val previousRows = (0 until index).sumOf { itemHeights[it].roundToPx() }
                    val separators = (0..index).count { items[it].separatorBefore }
                    val top = parent.top + inset + headerHeightPx +
                        previousRows + separators * separatorHeight.roundToPx() - menuScroll.value
                    Rect(parent.left + inset, top, parent.right - inset, top + selectedRowHeight.roundToPx())
                }
                val contentInset = with(density) { 10.dp.roundToPx().toFloat() }
                val source = bounds.intersect(parent.deflate(contentInset))
                if (source.isEmpty) {
                    LaunchedEffect(Unit) { childExpanded = false }
                    return@Box
                }
                val appearanceProgress = appearance.coerceIn(0f, 1f)
                val onePixel = with(density) { 1.toDp() }
                val viewportHeight = with(density) { safeViewport.height.toDp() }
                val viewportWidth = with(density) { safeViewport.width.toDp() }
                val verticalMargin = minOf(12.dp, (viewportHeight - onePixel).coerceAtLeast(0.dp) / 2)
                val topLimit = with(density) { (safeViewport.top - popupOriginInHost.y).toDp() } + verticalMargin
                val bottomLimit = with(density) { (safeViewport.bottom - popupOriginInHost.y).toDp() } - verticalMargin
                val sourceHeight = with(density) { source.height.toDp() }
                val childRows = selected.children
                val childHeight = remember(density, childRows, selectedRowHeight, itemHeight, separatorHeight) {
                    with(density) {
                        val rows = childRows.sumOf { menuItemHeight(itemHeight, it.title, it.subtitle).roundToPx() }
                        val separators = 1 + childRows.count { it.separatorBefore }
                        paddedMenuHeight(selectedRowHeight.roundToPx() + rows + separators * separatorHeight.roundToPx())
                    }
                }
                val targetHeight = minOf(childHeight, (bottomLimit - topLimit).coerceAtLeast(onePixel))
                val sourceTop = with(density) { (source.top - origin.y).toDp() }
                val targetTop = (sourceTop - 10.dp).coerceIn(topLimit, (bottomLimit - targetHeight).coerceAtLeast(topLimit))
                val sourceLeft = with(density) { (source.left - origin.x).toDp() }
                val sourceWidth = with(density) { source.width.toDp() }
                val targetWidth = (with(density) { bounds.width.toDp() } + 20.dp)
                    .coerceIn(onePixel, viewportWidth.coerceAtLeast(onePixel))
                val horizontalMargin = minOf(12.dp, (viewportWidth - targetWidth).coerceAtLeast(0.dp) / 2)
                val leftLimit = with(density) { (safeViewport.left - popupOriginInHost.x).toDp() } + horizontalMargin
                val rightLimit = with(density) { (safeViewport.right - popupOriginInHost.x).toDp() } - horizontalMargin
                val targetLeft = (sourceLeft - 10.dp).coerceIn(leftLimit, (rightLimit - targetWidth).coerceAtLeast(leftLimit))
                // UIKit transfers the anchor pivot to the top center as the list grows.
                val anchorCenter = anchorBounds?.center
                val pivotX = anchorCenter?.let { ((it.x - parent.left) / parent.width).coerceIn(0f, 1f) } ?: 0.5f
                val pivotY = anchorCenter?.let { ((it.y - parent.top) / parent.height).coerceIn(0f, 1f) } ?: 0f
                val movingPivot = openingPivot.value.coerceIn(0f, 1f) * p
                val top = sourceTop + (targetTop - sourceTop) * p -
                    (targetHeight - sourceHeight) * (pivotY * movingPivot)
                val left = sourceLeft + (targetLeft - sourceLeft) * p -
                    (targetWidth - sourceWidth) * ((pivotX - 0.5f) * movingPivot)
                val surfaceOutset = 10.dp * (1f - maxOf(revealProgress, appearanceProgress))
                val headerOffset = with(density) { (bounds.top - source.top).toDp() } * (1f - revealProgress)
                Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { childExpanded = false })
                CompositionLocalProvider(
                    LocalMenuSurfaceReveal provides MenuSurfaceReveal(
                        progress = revealProgress,
                        cornerRadius = 32.dp,
                        contentWidth = (targetWidth - 20.dp).coerceAtLeast(onePixel),
                        surfaceOutset = surfaceOutset,
                    ),
                ) {
                    GlasmioContextMenu(
                        visible = true,
                        modifier = Modifier.absoluteOffset(left, top).graphicsLayer { alpha = menuAlpha() },
                        backdrop = backdrop,
                        exportChildBackdrop = false,
                        compact = true,
                        menuWidth = (sourceWidth + (targetWidth - sourceWidth) * p).coerceAtLeast(onePixel),
                        heightOverride = (sourceHeight + (targetHeight - sourceHeight) * p).coerceAtLeast(onePixel),
                        shadowAlpha = appearanceProgress,
                        highlightAlpha = appearanceProgress,
                        itemCount = selected.children.size + 1,
                    ) { childBackdrop ->
                        CompositionLocalProvider(LocalMenuItemDragSelectionEnabled provides false) {
                            GlasmioMenuItem(
                                title = selected.title,
                                modifier = Modifier
                                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                    .offset(y = headerOffset)
                                    .graphicsLayer { alpha = appearanceProgress },
                                subtitle = selected.subtitle,
                                rowHeight = selectedRowHeight,
                                systemName = selected.systemName,
                                icon = selected.icon,
                                iconTint = if (selected.destructive) colors.destructive else if (useAccentIcons) colors.accent else colors.content,
                                destructive = selected.destructive,
                                fontWeight = FontWeight((400 + 200 * appearanceProgress).roundToInt()),
                                backdrop = childBackdrop,
                                enabled = selected.enabled,
                                trailing = {
                                    MenuChevron(
                                        90f * appearanceProgress,
                                        if (childExpanded) expandedDescription else collapsedDescription,
                                    )
                                },
                                onClick = { childExpanded = !childExpanded },
                            )
                        }
                        val childScroll = rememberScrollState()
                        Column(
                            Modifier.weight(1f)
                                .graphicsLayer { alpha = appearanceProgress }
                                .verticalScroll(childScroll, enabled = childScroll.maxValue > 0),
                        ) {
                            GlasmioMenuSeparator(height = separatorHeight, horizontalInset = 12.dp)
                            CompositionLocalProvider(
                                LocalIosPopupMenuInteractive provides (
                                    LocalIosPopupMenuInteractive.current && childExpanded && p > 0.95f
                                ),
                            ) {
                                selected.children.forEach { item ->
                                    if (item.separatorBefore) {
                                        GlasmioMenuSeparator(height = separatorHeight, horizontalInset = 12.dp)
                                    }
                                    GlasmioMenuItem(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        rowHeight = menuItemHeight(itemHeight, item.title, item.subtitle),
                                        systemName = item.systemName,
                                        icon = item.icon,
                                        iconTint = if (item.destructive) colors.destructive else if (useAccentIcons) colors.accent else colors.content,
                                        backdrop = childBackdrop,
                                        enabled = item.enabled,
                                        onClick = { close(item.onClick) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        anchor = { Spacer(Modifier.size(1.dp)) },
    ) { childBackdrop, _ ->
        val parentInteractive = LocalIosPopupMenuInteractive.current && selectedIndex == null
        Column(Modifier.fillMaxSize().verticalScroll(menuScroll, enabled = parentInteractive && menuScroll.maxValue > 0)) {
            if (headerContent != null) {
                Column(Modifier.graphicsLayer {
                    alpha = parentContentAlpha()
                }.then(if (selectedIndex != null) Modifier.semantics { hideFromAccessibility() } else Modifier)) {
                    headerContent { action -> if (parentInteractive) close(action) }
                }
            } else if (headerActions.isNotEmpty()) {
                Column(Modifier.graphicsLayer {
                    alpha = parentContentAlpha()
                }.then(if (selectedIndex != null) Modifier.semantics { hideFromAccessibility() } else Modifier)) {
                    Row(Modifier.fillMaxWidth().height(76.dp)) {
                        headerActions.forEach { action ->
                            MenuHeaderAction(
                                action = action,
                                iconTint = if (action.destructive) colors.destructive else if (useAccentIcons) colors.accent else colors.content,
                                enabled = action.enabled && parentInteractive,
                                modifier = Modifier.weight(1f),
                                onClick = { close(action.onClick) },
                            )
                        }
                    }
                    GlasmioMenuSeparator(height = separatorHeight)
                }
            }
            items.forEachIndexed { index, item ->
                if (item.separatorBefore) {
                    GlasmioMenuSeparator(Modifier.graphicsLayer {
                        alpha = parentContentAlpha()
                    }, height = separatorHeight)
                }
                val menuItem: @Composable () -> Unit = {
                    GlasmioMenuItem(
                        title = item.title,
                        subtitle = item.subtitle,
                        rowHeight = itemHeights[index],
                        systemName = item.systemName,
                        icon = item.icon,
                        destructive = item.destructive,
                        backdrop = childBackdrop,
                        iconTint = if (item.destructive) colors.destructive else if (useAccentIcons) colors.accent else colors.content,
                        enabled = item.enabled,
                        modifier = Modifier.graphicsLayer {
                            alpha = if (selectedIndex == index) 1f - appearance.coerceIn(0f, 1f) else parentContentAlpha()
                        }.then(if (selectedIndex != null) Modifier.semantics { hideFromAccessibility() } else Modifier),
                        trailing = if (item.children.isEmpty()) null else {
                            { MenuChevron(0f, collapsedDescription) }
                        },
                        onClick = {
                            if (item.children.isEmpty()) close(item.onClick)
                            else if (menuBounds != null) {
                                selectedIndex = index
                                childExpanded = true
                            }
                        },
                    )
                }
                CompositionLocalProvider(
                    LocalIosPopupMenuInteractive provides parentInteractive,
                    LocalMenuItemAutoNavigationDelayMillis provides if (item.children.isEmpty()) null else 500L,
                    content = menuItem,
                )
            }
        }
    }

}

@Composable
internal fun GlasmioMenuSeparator(
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    horizontalInset: Dp = 16.dp,
) {
    val color = LocalGlassColors.current.content.copy(alpha = 0.16f)
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        HorizontalDivider(
            Modifier.padding(horizontal = horizontalInset).graphicsLayer {
                blendMode = BlendMode.Luminosity
                compositingStrategy = CompositingStrategy.Offscreen
            },
            thickness = 0.5.dp,
            color = color,
        )
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
            .menuDragTarget(enabled && LocalIosPopupMenuInteractive.current, onClick, shape) {
                highlight.pressProgress
            }
            .then(if (enabled) Modifier.clickable(
                interactionSource = null, indication = null, role = Role.Button, onClick = onClick,
            ).then(highlight.gestureModifier) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        if (action.icon != null) {
            Box(Modifier.size(24.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                action.icon.invoke()
            }
        } else {
            action.systemName?.let { SfIcon(it, null, size = 24.dp, tint = iconTint) }
        }
        Spacer(Modifier.height(6.dp))
        Text(action.title, style = IosTypography.subheadline, color = colors.content,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
