package io.github.neoruaa.glasmio.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.kyant.capsule.ContinuousRoundedRectangle
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.icons.SfIcon
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt

/** A retained message preview, Tapback picker, and action menu in one modal window. */
@Composable
fun GlasmioMessageContextMenu(
    outgoing: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: List<GlasmioCascadingMenuItem>,
    modifier: Modifier = Modifier,
    reactions: List<GlasmioMessageReaction> = emptyList(),
    onReaction: ((GlasmioMessageReaction) -> Unit)? = null,
    selectedReaction: String? = null,
    onMoreReactions: (() -> Unit)? = null,
    shape: Shape = MessageBubbleDefaults.shape(outgoing),
    backdrop: Backdrop = LocalBlurBackdrop.current,
    anchor: @Composable (open: () -> Unit) -> Unit,
) {
    require(actions.none { it.children.isNotEmpty() }) { "Message context actions must be flat; open a cascading menu from an action callback." }
    val density = LocalDensity.current
    val tailOnRight = outgoing == (LocalLayoutDirection.current == LayoutDirection.Ltr)
    val colors = LocalGlassColors.current
    val currentExpandedChange by rememberUpdatedState(onExpandedChange)
    val sourceLayer = rememberGraphicsLayer()
    var source by remember { mutableStateOf<MessageSource?>(null) }
    var session by remember { mutableStateOf<MessageSource?>(null) }
    var retainedShape by remember { mutableStateOf(shape) }
    var retainedActions by remember { mutableStateOf(actions) }
    var retainedReactions by remember { mutableStateOf(reactions) }
    var alive by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val progress = remember { Animatable(0f) }
    val topInset = WindowInsets.safeDrawing.getTop(density)
    val bottomInset = maxOf(WindowInsets.safeDrawing.getBottom(density), WindowInsets.ime.getBottom(density))
    val leftInset = WindowInsets.safeDrawing.getLeft(density, LocalLayoutDirection.current)
    val rightInset = WindowInsets.safeDrawing.getRight(density, LocalLayoutDirection.current)
    val close: (() -> Unit) -> Unit = { action -> pendingAction = action; currentExpandedChange(false) }
    LaunchedEffect(expanded, source != null) {
        if (expanded && source != null) {
            pendingAction = null
            if (!alive) {
                session = source
                retainedShape = shape
                retainedActions = actions.toList()
                retainedReactions = if (onReaction != null) reactions.toList() else emptyList()
                pendingAction = null
                progress.snapTo(0f)
                alive = true
            }
            progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 385.5314f))
        } else if (alive) {
            val actionAtClose = pendingAction
            val started = withFrameNanos { it }
            // _UIRapidClickPresentationAssistant: action dismissal uses response .35;
            // outside/back dismissal uses response .4. Both have damping ratio .85.
            progress.animateTo(0f, spring(dampingRatio = 0.85f,
                stiffness = if (pendingAction != null) 322.2728f else 246.7401f))
            if (retainedReactions.isNotEmpty()) {
                val retainedNanos = MessageContextMotion.ExitRetentionMillis * 1_000_000
                while (withFrameNanos { it } - started < retainedNanos) { /* Keep the native tail channels mounted. */ }
            }
            alive = false
            session = null
            pendingAction = null
            actionAtClose?.invoke()
        }
    }
    Box(modifier
        .combinedClickable(interactionSource = null, indication = null, onClick = {}, onLongClick = {
            if (!alive && source != null) currentExpandedChange(true)
        })
        .onGloballyPositioned { coordinates ->
            val root = coordinates.findRootCoordinates()
            val next = MessageSource(
                Rect(coordinates.localToScreen(Offset.Zero), Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())),
                Rect(root.localToScreen(Offset.Zero), Size(root.size.width.toFloat(), root.size.height.toFloat())),
            )
            source = next
            if (alive && session != next) currentExpandedChange(false)
        }
        .drawWithContent {
            if (!alive) {
                sourceLayer.record { this@drawWithContent.drawContent() }
                drawLayer(sourceLayer)
            }
        },
    ) { anchor { if (!alive && source != null) currentExpandedChange(true) } }

    val captured = session
    if (alive && captured != null) {
        val px = density.density
        val actionScale = with(density) { IosTypography.body.fontSize.toPx() / 17.dp.toPx() }
        val viewport = Rect(
            captured.viewport.left + leftInset, captured.viewport.top + topInset,
            captured.viewport.right - rightInset, captured.viewport.bottom - bottomInset,
        )
        val pickerScale = density.fontScale.coerceIn(.88f, 1.3f)
        val placement = remember(captured, viewport, retainedActions, retainedReactions, tailOnRight, px, pickerScale, actionScale) {
            placeMessageContext(captured.bounds, viewport, retainedActions, retainedReactions.isNotEmpty(), tailOnRight, px, pickerScale, actionScale)
        }
        val popupBackdrop = rememberCrossWindowBackdrop(backdrop)
        var popupOrigin by remember { mutableStateOf<Offset?>(null) }
        val positionProvider = remember { object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
        } }
        Popup(positionProvider, onDismissRequest = { currentExpandedChange(false) }, properties = PopupProperties(
            focusable = expanded, dismissOnBackPress = expanded, dismissOnClickOutside = expanded,
            clippingEnabled = false,
        )) {
            val dimmingLayer = rememberLayerBackdrop()
            val materialBackdrop = rememberSharedGlassBackdrop(popupBackdrop, dimmingLayer)
            Box(Modifier.fillMaxSize().onGloballyPositioned { popupOrigin = it.localToScreen(Offset.Zero) }) {
                Box(Modifier.matchParentSize().testTag("messageContextScrim")
                    .layerBackdrop(dimmingLayer)
                    .drawWithContent {
                        drawContent()
                        // UIColor._alertControllerDimmingViewColor's default and dark traits.
                        drawRect(Color.Black.copy(alpha = (if (colors.isDark) 0.48f else 0.20f) * progress.value.coerceIn(0f, 1f)))
                    }
                    .clickable(interactionSource = null, indication = null) { currentExpandedChange(false) })
                val origin = popupOrigin
                if (origin != null) {
                    fun Modifier.at(rect: Rect): Modifier = absoluteOffset {
                        IntOffset((rect.left - origin.x).roundToInt(), (rect.top - origin.y).roundToInt())
                    }
                    val margin = 48.dp
                    val marginPx = with(density) { margin.toPx() }
                    // The unbounded shadow has its own transparent shell, inside the one full-screen window.
                    Box(Modifier.at(placement.preview.translate(Offset(-marginPx, -marginPx)))
                        .requiredSize(with(density) { captured.bounds.width.toDp() } + margin * 2,
                            with(density) { captured.bounds.height.toDp() } + margin * 2)
                        .graphicsLayer {
                            val p = progress.value
                            translationX = (captured.bounds.left - placement.preview.left) * (1f - p)
                            translationY = (captured.bounds.top - placement.preview.top) * (1f - p)
                        }) {
                        Box(Modifier.offset(margin, margin)
                            .requiredSize(with(density) { captured.bounds.width.toDp() }, with(density) { captured.bounds.height.toDp() })
                            .testTag("messageContextPreview")
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0f, 0f)
                                scaleX = 1f + (placement.previewScale - 1f) * progress.value
                                scaleY = scaleX
                            }
                            .dropShadow(retainedShape) {
                                radius = marginPx * 0.5f
                                offset = Offset(0f, 8.dp.toPx())
                                color = Color.Black
                                alpha = (if (colors.isDark) 0.48f else 0.28f) * progress.value.coerceIn(0f, 1f)
                            }
                            .drawWithContent { drawLayer(sourceLayer) })
                    }
                    val menu = placement.menu
                    Box(Modifier.at(menu.translate(Offset(-marginPx, -marginPx)))
                        .requiredSize(with(density) { menu.width.toDp() } + margin * 2, with(density) { menu.height.toDp() } + margin * 2)
                        .graphicsLayer {
                            alpha = progress.value.coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(if (tailOnRight) 1f else 0f, 0f)
                            scaleX = 0.2f + 0.8f * progress.value
                            scaleY = scaleX
                        }) {
                        GlassSurface(
                            modifier = Modifier.offset(margin, margin).requiredSize(with(density) { menu.width.toDp() }, with(density) { menu.height.toDp() }),
                            backdrop = materialBackdrop, shape = ContinuousRoundedRectangle(32.dp),
                            material = NavigationGlassMaterial.RegularMedium,
                            navigationSurfaceAlphaMultiplier = 1f,
                        ) {
                            Column(Modifier.fillMaxSize().padding(vertical = 10.dp).verticalScroll(rememberScrollState())) {
                                retainedActions.forEach { action ->
                                    if (action.separatorBefore) {
                                        HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 10.dp), color = LocalGlassColors.current.separator)
                                    }
                                    MessageActionRow(action, expanded) { close(action.onClick) }
                                }
                            }
                        }
                    }
                    placement.reactions?.let { bar ->
                        GlasmioMessageReactionBar(
                            reactions = retainedReactions, outgoing = outgoing, expanded = expanded,
                            onReaction = { reaction -> close { onReaction?.invoke(reaction) } },
                            selectedReaction = selectedReaction, backdrop = materialBackdrop,
                            onMoreReactions = onMoreReactions?.let { more -> { close(more) } },
                            tailCenterX = with(density) { ((if (tailOnRight) placement.preview.left - 28f * px else placement.preview.right + 28f * px) - bar.left).toDp() },
                            modifier = Modifier.at(bar).requiredWidth(with(density) { bar.width.toDp() }),
                        )
                    }
                }
            }
        }
    }
}

private data class MessageSource(val bounds: Rect, val viewport: Rect)
internal data class MessageContextPlacement(val preview: Rect, val menu: Rect, val reactions: Rect?, val previewScale: Float)

/** All inputs and results use the same screen coordinate space. */
internal fun placeMessageContext(source: Rect, viewport: Rect, actions: List<GlasmioCascadingMenuItem>, reactions: Boolean, tailOnRight: Boolean, density: Float, pickerScale: Float = 1f, actionScale: Float = 1f): MessageContextPlacement {
    val area = Rect(viewport.left + 8f * density, viewport.top + 8f * density, viewport.right - 8f * density, viewport.bottom - 8f * density)
    val width = area.width.coerceAtLeast(1f)
    val height = area.height.coerceAtLeast(1f)
    val requestedPickerHeight = MessageContextMotion.PickerHeight * density * pickerScale
    // A keyboard/compact window can leave less room than the accessory's fixed lobes.
    // Keep the action list reachable rather than placing an accessory outside the viewport.
    val showReactions = reactions && height >= requestedPickerHeight + 59f * density
    val pickerHeight = if (showReactions) requestedPickerHeight else 0f
    val pickerReserve = if (showReactions) pickerHeight + 5f * density else 0f
    val menuGap = 8f * density
    val requestedMenuHeight = (20f + actions.sumOf {
        (42 + (if ('\n' in it.title) 19 else 0) + (if (it.subtitle != null) 18 else 0)).toDouble()
    }.toFloat() * actionScale + actions.count { it.separatorBefore } * 21f) * density
    val available = (height - pickerReserve - menuGap).coerceAtLeast(1f)
    val tailFloor = if (showReactions) (MessageContextMotion.TailExtent - 5f) * density else 0f
    val menuHeight = min(min(requestedMenuHeight, 520f * density), min(available * 0.6f, available - tailFloor).coerceAtLeast(1f))
    val menuWidth = min(250f * density, width)
    val edge = max(source.width, source.height).coerceAtLeast(1f)
    val liftScale = min(1.15f, (edge + 26f * density) / edge)
    val previewScale = min(liftScale, min((width - if (showReactions) 61f * density else 0f).coerceAtLeast(1f) / source.width.coerceAtLeast(1f),
        (height - pickerReserve - menuHeight - menuGap).coerceAtLeast(1f) / source.height.coerceAtLeast(1f)))
    val previewWidth = source.width * previewScale
    val previewHeight = source.height * previewScale
    val stackHeight = max(previewHeight, tailFloor)
    val left = (if (tailOnRight) source.right - previewWidth else source.left).coerceIn(area.left, (area.right - previewWidth).coerceAtLeast(area.left))
    val top = source.top.coerceIn(area.top + pickerReserve, (area.bottom - menuHeight - menuGap - stackHeight).coerceAtLeast(area.top + pickerReserve))
    val preview = Rect(left, top, left + previewWidth, top + previewHeight)
    val menuLeft = (if (tailOnRight) preview.right - menuWidth else preview.left).coerceIn(area.left, (area.right - menuWidth).coerceAtLeast(area.left))
    val menu = Rect(menuLeft, preview.top + stackHeight + menuGap, menuLeft + menuWidth, preview.top + stackHeight + menuGap + menuHeight)
    val bar = if (showReactions) Rect(area.left, preview.top - 5f * density - pickerHeight, area.right, preview.top - 5f * density + MessageContextMotion.TailExtent * density) else null
    return MessageContextPlacement(preview, menu, bar, previewScale)
}

/** Glass LargeCellLayout: 27pt first baseline, 19pt baseline spacing, 15/14pt bottom. */
@Composable
private fun MessageActionRow(action: GlasmioCascadingMenuItem, expanded: Boolean, onClick: () -> Unit) {
    val colors = LocalGlassColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val foreground = if (action.destructive) colors.destructive else colors.content
    val scale = with(LocalDensity.current) { IosTypography.body.fontSize.toPx() / 17.dp.toPx() }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .background(Color.Black.copy(alpha = if (pressed) .12f else 0f), ContinuousRoundedRectangle(16.dp))
            .clickable(enabled = expanded && action.enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .graphicsLayer { alpha = if (action.enabled) 1f else .45f }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterStart) {
            action.systemName?.let { SfIcon(it, null, size = 24.dp, tint = foreground) }
        }
        Layout(content = {
            Text(action.title, style = IosTypography.body.copy(lineHeight = (19f * scale).sp / LocalDensity.current.fontScale),
                color = foreground, maxLines = if ('\n' in action.title) 2 else 1, overflow = TextOverflow.Ellipsis)
            action.subtitle?.let { Text(it, style = IosTypography.footnote, color = colors.secondaryContent, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }, modifier = Modifier.weight(1f)) { measurables, constraints ->
            val title = measurables[0].measure(constraints.copy(minHeight = 0))
            val subtitle = measurables.getOrNull(1)?.measure(constraints.copy(minHeight = 0))
            val first = (27.dp.toPx() * scale).roundToInt()
            val titleY = first - title[FirstBaseline]
            val last = titleY + title[LastBaseline]
            val subtitleY = subtitle?.let { last + (19.dp.toPx() * scale).roundToInt() - it[FirstBaseline] }
            val height = if (subtitle != null) subtitleY!! + subtitle[LastBaseline] + (14.dp.toPx() * scale).roundToInt()
                else last + (15.dp.toPx() * scale).roundToInt()
            layout(constraints.maxWidth, constraints.constrainHeight(height)) {
                title.placeRelative(0, titleY)
                if (subtitle != null) subtitle.placeRelative(0, subtitleY!!)
            }
        }
    }
}
