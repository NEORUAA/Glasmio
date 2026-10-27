package io.github.neoruaa.glasmio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSeparator
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.InteractiveHighlight
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import io.github.neoruaa.glasmio.glass.glasmioMenuAnchor
import io.github.neoruaa.glasmio.glass.rememberGlasmioMenuAnchorState
import io.github.neoruaa.glasmio.glass.glassBackdropSource
import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import io.github.neoruaa.glasmio.icons.SfIcon
import kotlinx.coroutines.CompletableDeferred
import kotlin.math.roundToInt

@Immutable
data class GlasmioTextSelectionLabels(
    val cut: String = "Cut",
    val copy: String = "Copy",
    val paste: String = "Paste",
    val selectAll: String = "Select All",
    val autofill: String = "AutoFill",
    val more: String = "More",
    val expanded: String = "Expanded",
    val collapsed: String = "Collapsed",
)

/**
 * Apple's compact Edit Menu and its expanded contextual menu. Place this overlay at
 * the window root and supply selection bounds in screen pixels. Actions keep their
 * original callbacks; the owner decides whether an action ends the selection session.
 * Additional entries may contain real editing actions or cascading submenus.
 */
@Composable
fun GlasmioTextSelectionMenu(
    selectionBounds: Rect,
    primaryActions: List<GlasmioCascadingMenuItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    additionalItems: List<GlasmioCascadingMenuItem> = emptyList(),
    labels: GlasmioTextSelectionLabels = GlasmioTextSelectionLabels(),
    backdrop: Backdrop = LocalGlassBackdrop.current,
    onToolbarBoundsChanged: ((Rect) -> Unit)? = null,
) {
    if (primaryActions.isEmpty() && additionalItems.isEmpty()) return
    val density = LocalDensity.current
    val colors = LocalGlassColors.current
    val menuAnchor = rememberGlasmioMenuAnchorState()
    var moreExpanded by remember { mutableStateOf(false) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy()) }
    var toolbarBounds by remember { mutableStateOf<Rect?>(null) }
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
    val editingHost = LocalGlasmioTextEditingHost.current
    val safeInsets = WindowInsets.safeDrawing.union(WindowInsets.ime)
    val layoutDirection = LocalLayoutDirection.current
    val windowSize = LocalWindowInfo.current.containerSize
    DisposableEffect(moreExpanded) {
        editingHost?.menuOwnsFocus = moreExpanded
        onDispose { if (moreExpanded) editingHost?.menuOwnsFocus = false }
    }
    BackHandler(enabled = !moreExpanded, onBack = onDismiss)
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { rootCoordinates = it }) {
        val root = rootCoordinates?.takeIf { it.isAttached } ?: return@BoxWithConstraints
        val topLeft = root.screenToLocal(selectionBounds.topLeft)
        val bottomRight = root.screenToLocal(selectionBounds.bottomRight)
        val bounds = Rect(topLeft, bottomRight)
        val inset = with(density) { 12.dp.toPx() }
        val width = toolbarSize.width.toFloat()
        val height = with(density) { 44.dp.toPx() }
        val windowWidth = with(density) { maxWidth.toPx() }
        val windowHeight = with(density) { maxHeight.toPx() }
        val safeViewport = Rect(
            root.windowToLocal(Offset(safeInsets.getLeft(density, layoutDirection).toFloat(), safeInsets.getTop(density).toFloat())),
            root.windowToLocal(Offset((windowSize.width - safeInsets.getRight(density, layoutDirection)).toFloat(),
                (windowSize.height - safeInsets.getBottom(density)).toFloat())),
        ).intersect(Rect(0f, 0f, windowWidth, windowHeight))
        val viewport = Rect(safeViewport.left + inset, safeViewport.top + inset,
            (safeViewport.right - inset).coerceAtLeast(safeViewport.left + inset),
            (safeViewport.bottom - inset).coerceAtLeast(safeViewport.top + inset))
        val left = bounds.left.coerceIn(viewport.left, (viewport.right - width).coerceAtLeast(viewport.left))
        val top = editingOverlayTop(bounds, viewport, height, with(density) { 8.dp.toPx() }, preferAbove = false)
        val position by animateOffsetAsState(Offset(left, top),
            spring(dampingRatio = 0.85f, stiffness = 420f), label = "TextSelectionMenuPosition")
        GlassSurface(
            modifier = Modifier.absoluteOffset {
                IntOffset(position.x.coerceIn(viewport.left, (viewport.right - width).coerceAtLeast(viewport.left)).roundToInt(),
                    position.y.coerceIn(viewport.top, (viewport.bottom - height).coerceAtLeast(viewport.top)).roundToInt())
            }
                .onSizeChanged { toolbarSize = it }
                .onGloballyPositioned {
                    toolbarBounds = Rect(it.localToWindow(Offset.Zero), androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat()))
                    onToolbarBoundsChanged?.invoke(Rect(it.localToScreen(Offset.Zero), androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat())))
                }
                .glasmioMenuAnchor(menuAnchor),
            backdrop = backdrop,
            shape = ContinuousRoundedRectangle(34.dp),
            material = NavigationGlassMaterial.RegularMedium,
        ) {
            Row(
                Modifier.height(44.dp).padding(start = 20.dp, end = if (additionalItems.isEmpty()) 20.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                primaryActions.forEachIndexed { index, action ->
                    if (index > 0) Box(Modifier.width(1.dp).height(18.dp).background(colors.content.copy(alpha = 0.18f)))
                    Box(
                        Modifier.height(44.dp).clip(ContinuousRoundedRectangle(20.dp))
                            .clickable(enabled = action.enabled, interactionSource = null, indication = null, role = Role.Button, onClick = action.onClick)
                            .padding(start = if (index > 0) 16.dp else 0.dp, end = if (additionalItems.isNotEmpty() || index < primaryActions.lastIndex) 16.dp else 0.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(action.title, style = IosTypography.subheadline.copy(fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = (-0.6).sp),
                            color = colors.content.copy(alpha = if (action.enabled) 1f else 0.45f), maxLines = 1)
                    }
                }
                if (additionalItems.isNotEmpty()) {
                    Box(
                        Modifier.size(36.dp).clip(ContinuousRoundedRectangle(18.dp))
                            .background(if (colors.isDark) Color(0xFF373737) else Color(0xFFEDEDED))
                            .clickable(interactionSource = null, indication = null, role = Role.Button) {
                                editingHost?.menuOwnsFocus = true
                                moreExpanded = true
                            },
                        contentAlignment = Alignment.Center,
                    ) { SfIcon("chevron.right", labels.more, size = 16.dp, tint = colors.content) }
                }
            }
        }
        if (moreExpanded && toolbarBounds != null) {
            GlasmioCascadingMenu(
                anchorBounds = toolbarBounds,
                items = additionalItems.mapIndexed { index, item ->
                    if (index == 0 && primaryActions.isNotEmpty()) item.copy(separatorBefore = false) else item
                },
                title = labels.more,
                expandedDescription = labels.expanded,
                collapsedDescription = labels.collapsed,
                onDismiss = { moreExpanded = false },
                backdrop = backdrop,
                menuAnchorState = menuAnchor,
                useAccentIcons = false,
                expandFromAnchor = Alignment.Start,
                viewportBounds = Rect(root.localToWindow(safeViewport.topLeft), root.localToWindow(safeViewport.bottomRight)),
                itemHeight = 40.dp,
                separatorHeight = 21.dp,
                headerContentHeight = if (primaryActions.isEmpty()) 0.dp else 77.dp,
                headerContent = if (primaryActions.isEmpty()) null else { execute ->
                    TextSelectionQuickActions(primaryActions) { execute(it.onClick) }
                    Box(Modifier.fillMaxWidth().height(21.dp), contentAlignment = Alignment.Center) {
                        HorizontalDivider(Modifier.padding(horizontal = 6.dp), thickness = 1.dp, color = colors.content.copy(alpha = 0.1f))
                    }
                },
            )
        }
    }
}

@Composable
private fun TextSelectionQuickActions(actions: List<GlasmioCascadingMenuItem>, onClick: (GlasmioCascadingMenuItem) -> Unit) {
    val colors = LocalGlassColors.current
    Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        actions.forEach { action ->
            val scope = rememberCoroutineScope()
            val highlight = remember(scope) { InteractiveHighlight(scope) }
            val shape = ContinuousRoundedRectangle(20.dp)
            Column(
                Modifier.weight(1f).height(56.dp).clip(shape)
                    .background(Color.Black.copy(alpha = 0.15f * highlight.pressProgress), shape)
                    .clickable(enabled = action.enabled, interactionSource = null, indication = null, role = Role.Button) { onClick(action) }
                    .then(if (action.enabled) highlight.gestureModifier else Modifier)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
                    action.systemName?.let { SfIcon(it, null, size = 22.dp, fontSize = 13.sp, tint = colors.content) }
                }
                Text(action.title, style = IosTypography.caption1.copy(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight(510)),
                    color = colors.content.copy(alpha = if (action.enabled) 1f else 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Install once around a window's content. Both Foundation's current text context-menu
 * provider and its legacy LocalTextToolbar use the same editing menu and real callbacks.
 * Value-based BasicTextField, state-based fields and SelectionContainer are supported.
 */
@Composable
fun GlasmioTextSelectionHost(
    modifier: Modifier = Modifier,
    labels: GlasmioTextSelectionLabels = GlasmioTextSelectionLabels(),
    additionalItems: List<GlasmioCascadingMenuItem> = emptyList(),
    backdrop: Backdrop = LocalGlassBackdrop.current,
    content: @Composable () -> Unit,
) {
    val toolbar = remember { GlasmioSelectionToolbar() }
    val editingHost = rememberGlasmioTextEditingHost()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy()) }
    var toolbarBounds by remember { mutableStateOf<Rect?>(null) }
    val latestCoordinates by rememberUpdatedState(coordinates)
    val latestToolbarBounds by rememberUpdatedState(toolbarBounds)
    DisposableEffect(toolbar) { onDispose { toolbar.hide() } }
    CompositionLocalProvider(
        LocalTextToolbar provides toolbar,
        LocalTextContextMenuToolbarProvider provides toolbar,
        LocalGlasmioTextEditingHost provides editingHost,
    ) {
        Box(
            modifier.fillMaxSize().onGloballyPositioned { coordinates = it; editingHost.coordinates = it }
                .pointerInput(toolbar) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val point = latestCoordinates?.takeIf { it.isAttached }?.localToScreen(down.position)
                        if (point != null && toolbar.status == TextToolbarStatus.Shown && latestToolbarBounds?.contains(point) != true) toolbar.hide()
                    }
                },
        ) {
            Box(Modifier.matchParentSize().then(
                if (editingHost.magnifying) Modifier.glassBackdropSource(editingHost.recording)
                    .trackBackdropPosition(editingHost.recording) else Modifier,
            )) { content() }
            GlasmioTextEditingOverlay(editingHost, backdrop)
            val root = coordinates?.takeIf { it.isAttached }
            val session = toolbar.session
            val legacy = toolbar.legacy
            if (root != null && (session != null || legacy != null)) {
                val editingBounds = editingHost.active?.takeIf { it.chromeVisible }?.selectionBounds()
                val screenBounds = editingBounds ?: if (session != null) {
                    val bounds = session.dataProvider.contentBounds(root)
                    Rect(root.localToScreen(bounds.topLeft), root.localToScreen(bounds.bottomRight))
                } else {
                    val bounds = requireNotNull(legacy).bounds
                    val owner = root.findRootCoordinates()
                    Rect(owner.localToScreen(bounds.topLeft), owner.localToScreen(bounds.bottomRight))
                }
                val actions = if (session != null) modernTextActions(session) else legacyTextActions(requireNotNull(legacy), labels, toolbar::hide)
                val clipboard = listOf(TextContextMenuKeys.CutKey, TextContextMenuKeys.CopyKey, TextContextMenuKeys.PasteKey)
                    .mapNotNull { key -> actions.firstOrNull { it.first == key } }
                val primary = clipboard.ifEmpty { actions.take(3) }
                val remaining = actions.filter { candidate -> primary.none { it.first == candidate.first } }.map { it.second }
                key(session ?: legacy?.identity) {
                    GlasmioTextSelectionMenu(
                        selectionBounds = screenBounds,
                        primaryActions = primary.map { it.second },
                        additionalItems = remaining + additionalItems,
                        onDismiss = toolbar::hide,
                        labels = labels,
                        backdrop = backdrop,
                        onToolbarBoundsChanged = { toolbarBounds = it },
                    )
                }
            }
        }
    }
}

private class SelectionMenuSession(val dataProvider: TextContextMenuDataProvider) : TextContextMenuSession {
    val closed = CompletableDeferred<Unit>()
    override fun close() { closed.complete(Unit) }
}

private data class LegacySelectionMenu(
    val bounds: Rect,
    val copy: (() -> Unit)?,
    val paste: (() -> Unit)?,
    val cut: (() -> Unit)?,
    val selectAll: (() -> Unit)?,
    val autofill: (() -> Unit)?,
    val identity: Any,
)

private class GlasmioSelectionToolbar : TextToolbar, TextContextMenuProvider {
    private val mutator = MutatorMutex()
    var session by mutableStateOf<SelectionMenuSession?>(null)
        private set
    var legacy by mutableStateOf<LegacySelectionMenu?>(null)
        private set
    override val status: TextToolbarStatus
        get() = if (session != null || legacy != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        mutator.mutate {
            val request = SelectionMenuSession(dataProvider)
            legacy = null
            session = request
            try { request.closed.await() } finally { if (session === request) session = null }
        }
    }

    override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
        showMenu(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested, null)
    }

    override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?, onAutofillRequested: (() -> Unit)?) {
        session?.close()
        session = null
        legacy = LegacySelectionMenu(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested, onAutofillRequested, legacy?.identity ?: Any())
    }

    override fun hide() {
        session?.close()
        session = null
        legacy = null
    }
}

private fun modernTextActions(session: SelectionMenuSession): List<Pair<Any, GlasmioCascadingMenuItem>> {
    var separated = false
    return buildList {
        session.dataProvider.data().components.forEach { component ->
            when (component) {
                TextContextMenuSeparator -> separated = true
                is TextContextMenuItem -> {
                    add(component.key to GlasmioCascadingMenuItem(
                        title = component.label,
                        systemName = textActionSymbol(component.key),
                        separatorBefore = separated,
                        onClick = { component.onClick(session) },
                    ))
                    separated = false
                }
                else -> Unit
            }
        }
    }
}

private fun legacyTextActions(request: LegacySelectionMenu, labels: GlasmioTextSelectionLabels, hide: () -> Unit): List<Pair<Any, GlasmioCascadingMenuItem>> = buildList {
    fun addAction(key: Any, label: String, callback: (() -> Unit)?, close: Boolean = true) {
        if (callback != null) add(key to GlasmioCascadingMenuItem(label, textActionSymbol(key), onClick = {
            callback()
            if (close) hide()
        }))
    }
    addAction(TextContextMenuKeys.CutKey, labels.cut, request.cut)
    addAction(TextContextMenuKeys.CopyKey, labels.copy, request.copy)
    addAction(TextContextMenuKeys.PasteKey, labels.paste, request.paste)
    addAction(TextContextMenuKeys.SelectAllKey, labels.selectAll, request.selectAll, close = false)
    addAction(TextContextMenuKeys.AutofillKey, labels.autofill, request.autofill)
}

private fun textActionSymbol(key: Any): String? = when (key) {
    TextContextMenuKeys.CutKey -> "scissors"
    TextContextMenuKeys.CopyKey -> "document.on.document"
    TextContextMenuKeys.PasteKey -> "document.on.clipboard"
    TextContextMenuKeys.SelectAllKey -> "square.dashed"
    TextContextMenuKeys.AutofillKey -> "character"
    else -> null
}
