package io.github.neoruaa.glasmio.ui

import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuData
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.swipeBackExcluded
import io.github.neoruaa.glasmio.glass.rememberCrossWindowBackdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val LocalGlasmioTextEditingHost = staticCompositionLocalOf<GlasmioTextEditingHost?> { null }

/** Window-local editing chrome. Native text input, selection and IME ownership stay in Foundation. */
internal class GlasmioTextEditingHost(val windowView: View, val recording: LayerBackdrop) {
    var coordinates by mutableStateOf<LayoutCoordinates?>(null)
    var active by mutableStateOf<GlasmioTextEditor?>(null)
    var menuOwnsFocus by mutableStateOf(false)
    val magnifying: Boolean
        get() = active?.let {
            it.chromeVisible && (it.magnifierSource != null || it.magnifierRetainedSource != null)
        } == true

    fun activate(editor: GlasmioTextEditor) {
        if (active !== editor) active?.dismiss()
        active = editor
    }
}

@Composable
internal fun rememberGlasmioTextEditingHost(): GlasmioTextEditingHost {
    val windowView = LocalView.current.rootView
    val recording = rememberLayerBackdrop()
    return remember(windowView, recording) { GlasmioTextEditingHost(windowView, recording) }
}

/** A modal must own its chrome in its own window, rather than using the activity overlay. */
@Composable
internal fun GlasmioTextEditingWindow(backdrop: Backdrop, content: @Composable () -> Unit) {
    if (LocalGlasmioTextEditingHost.current == null) content()
    else GlasmioTextSelectionHost(backdrop = rememberCrossWindowBackdrop(backdrop), content = content)
}

/**
 * A plain editable text surface with window-local iOS editing chrome inside
 * [GlasmioTextSelectionHost]. Single-line fields scroll horizontally; multiline fields wrap
 * and scroll vertically within their height constraints and [maxLines]. Without a matching
 * host, Foundation renders its native editing chrome.
 */
@Composable
fun GlasmioEditableText(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = IosTypography.body,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    cursorBrush: Brush = SolidColor(LocalGlassColors.current.accent),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit = { it() },
) {
    require(maxLines > 0) { "maxLines must be positive." }
    val inputModifier = modifier.swipeBackExcluded(enabled)
    val host = LocalGlasmioTextEditingHost.current
    val view = LocalView.current
    if (host == null || host.windowView !== view.rootView) {
        BasicTextField(value, onValueChange, inputModifier, enabled, readOnly, textStyle,
            keyboardOptions, keyboardActions, singleLine, maxLines,
            visualTransformation = visualTransformation, onTextLayout = onTextLayout,
            cursorBrush = cursorBrush, decorationBox = decorationBox)
        return
    }
    val realWindow = LocalWindowInfo.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val bringIntoView = remember { BringIntoViewRequester() }
    val toolbar = LocalTextToolbar.current
    val modern = LocalTextContextMenuToolbarProvider.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val editor = remember(host, scroll, toolbar, modern, clipboard) {
        GlasmioTextEditor(host, scope, scroll, toolbar, modern, clipboard, value)
    }
    val hiddenNativeChrome = remember(realWindow) {
        object : WindowInfo by realWindow { override val isWindowFocused: Boolean get() = false }
    }
    val offsetMapping = remember(value.annotatedString, visualTransformation) {
        visualTransformation.filter(value.annotatedString).offsetMapping
    }
    val colors = LocalGlassColors.current
    SideEffect {
        if (editor.value.text != value.text) {
            editor.showHandles = false
            editor.dismissMenu()
        }
        editor.value = value
        editor.change = onValueChange
        editor.mapping = offsetMapping
        editor.enabled = enabled
        editor.readOnly = readOnly
        editor.password = visualTransformation is PasswordVisualTransformation
        editor.realWindowFocused = realWindow.isWindowFocused
        editor.density = density
        editor.singleLine = singleLine
        editor.reverseScroll = singleLine && layoutDirection == LayoutDirection.Rtl
    }
    DisposableEffect(editor) {
        onDispose {
            editor.dismiss()
            if (host.active === editor) host.active = null
        }
    }
    LaunchedEffect(editor, enabled, editor.focused, realWindow.isWindowFocused, host.menuOwnsFocus) {
        val ownedMenu = host.menuOwnsFocus && host.active === editor
        if (!enabled) editor.dismiss()
        else if (!ownedMenu && (!editor.focused || !realWindow.isWindowFocused)) {
            // Closing our popup and restoring its owner window can land in the same frame.
            withFrameNanos { }
            if (!editor.chromeVisible) editor.dismiss()
        }
    }
    LaunchedEffect(editor, singleLine) {
        editor.dismiss()
        scroll.scrollTo(0)
    }
    LaunchedEffect(editor, value.text, value.selection, editor.focused) {
        editor.blink = true
        while (isActive && editor.focused) {
            delay(500)
            editor.blink = !editor.blink
        }
    }
    LaunchedEffect(editor, value.text, value.selection, editor.layout, editor.focused, editor.viewportSize, singleLine) {
        if (editor.focused && enabled && editor.pointerScreen == null) {
            withFrameNanos { }
            editor.cursorRect(value.selection.end)?.let { bringIntoView.bringIntoView(it) }
        }
    }
    LaunchedEffect(editor, editor.draggingHandle) {
        while (isActive && editor.draggingHandle != null && editor.chromeVisible) {
            withFrameNanos { }
            editor.scrollAtEdge()
        }
    }
    val fieldModifier = inputModifier.onFocusChanged {
        editor.focused = it.isFocused
        if (it.isFocused) host.activate(editor) else if (!host.menuOwnsFocus) editor.dismiss()
    }.onGloballyPositioned { editor.decoration = it }
    .pointerInput(editor) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!editor.enabled || down.type == PointerType.Mouse) return@awaitEachGesture
            editor.beginTouch()
            var finishedNormally = false
            try {
                var point = down.position
                var movedBeforeLongPress = false
                val completed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                        point = change.position
                        if (!change.pressed) return@withTimeoutOrNull true
                        if ((point - down.position).getDistance() > viewConfiguration.touchSlop) {
                            movedBeforeLongPress = true
                            return@withTimeoutOrNull true
                        }
                    }
                    @Suppress("UNREACHABLE_CODE") true
                }
                if (completed == null) {
                    editor.decoration?.let { editor.updatePointer(it.localToScreen(point)) }
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        editor.decoration?.let { editor.updatePointer(it.localToScreen(change.position)) }
                    }
                }
                editor.pointerScreen = null
                editor.magnifierSource = null
                editor.finishTouch(showHandles = !movedBeforeLongPress, showMenu = completed == null)
                finishedNormally = true
            } finally {
                if (!finishedNormally) editor.cancelTouch()
            }
        }
    }
    CompositionLocalProvider(
        LocalWindowInfo provides hiddenNativeChrome,
        LocalTextToolbar provides editor.legacyAdapter,
        LocalTextContextMenuToolbarProvider provides editor.modernAdapter,
        LocalTextSelectionColors provides TextSelectionColors(colors.accent, colors.accent.copy(alpha = 0.22f)),
    ) {
        BasicTextField(
            value = value,
            onValueChange = { next ->
                val textEdited = editor.value.text != next.text
                editor.value = next
                if (!editor.touching && editor.draggingHandle == null) {
                    if (textEdited) {
                        editor.showHandles = false
                        editor.dismissMenu()
                    } else {
                        editor.showHandles = !next.selection.collapsed
                    }
                }
                onValueChange(next)
            },
            modifier = fieldModifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            // The decoration owns scrolling and must see the entire text layout.
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            visualTransformation = visualTransformation,
            onTextLayout = { editor.layout = it; onTextLayout(it) },
            cursorBrush = SolidColor(Color.Transparent),
            decorationBox = { nativeInner ->
                decorationBox {
                    val maximumHeight = if (!singleLine && maxLines != Int.MAX_VALUE) {
                        // Match Foundation's reference-font line cap without clipping its inner layout.
                        val paragraph = rememberTextMeasurer().measure(
                            AnnotatedString("HHHHHHHHHH\nHHHHHHHHHH\nHHHHHHHHHH"),
                            style = textStyle,
                        ).multiParagraph
                        val height = paragraph.getLineHeight(0) +
                            paragraph.getLineHeight(1) * maxOf(maxLines - 2, 0) +
                            paragraph.getLineHeight(2) * minOf(maxLines - 1, 1)
                        with(density) { height.roundToInt().toDp() }
                    } else Dp.Infinity
                    BoxWithConstraints(Modifier.heightIn(max = maximumHeight), propagateMinConstraints = true) {
                        val minimumWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
                        val minimumHeight = constraints.minHeight
                        val scrolling = if (singleLine) Modifier.horizontalScroll(scroll) else Modifier.verticalScroll(scroll)
                        Box(Modifier.onGloballyPositioned(editor::updateViewportCoordinates)
                            .then(scrolling), propagateMinConstraints = true) {
                            Box(
                                Modifier.layout { measurable, incoming ->
                                    val contentConstraints = if (singleLine) {
                                        incoming.copy(minWidth = minimumWidth, maxWidth = Constraints.Infinity)
                                    } else {
                                        incoming.copy(minWidth = minimumWidth, minHeight = minimumHeight, maxHeight = Constraints.Infinity)
                                    }
                                    val placeable = measurable.measure(contentConstraints)
                                    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                                }
                                    .onGloballyPositioned(editor::updateContentCoordinates)
                                    .bringIntoViewRequester(bringIntoView)
                                    .drawWithContent {
                                        drawContent()
                                        if (editor.chromeVisible && !readOnly && value.selection.collapsed && (editor.blink || editor.touching)) {
                                            editor.cursorRect(value.selection.end)?.let { rect ->
                                                drawRect(cursorBrush, Offset(rect.left, rect.top),
                                                    androidx.compose.ui.geometry.Size(2.dp.toPx(), rect.height))
                                            }
                                        }
                                    },
                            ) { nativeInner() }
                        }
                    }
                }
            },
        )
    }
}

@Composable
internal fun GlasmioEditableTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = IosTypography.body,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    cursorBrush: Brush = SolidColor(LocalGlassColors.current.accent),
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit = { it() },
) = GlasmioEditableText(value, onValueChange, modifier, enabled, readOnly, textStyle,
    singleLine, maxLines, visualTransformation, keyboardOptions, keyboardActions, cursorBrush,
    decorationBox = decorationBox)

@Composable
internal fun GlasmioEditableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = IosTypography.body,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    cursorBrush: Brush = SolidColor(LocalGlassColors.current.accent),
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit = { it() },
) {
    var owned by remember { mutableStateOf(TextFieldValue(value)) }
    var lastText by remember(value) { mutableStateOf(value) }
    val current = owned.copy(text = value)
    SideEffect { if (owned.selection != current.selection || owned.composition != current.composition) owned = current }
    GlasmioEditableText(current, { next ->
        owned = next
        if (next.text != lastText) { lastText = next.text; onValueChange(next.text) }
    }, modifier = modifier, enabled = enabled, textStyle = textStyle, singleLine = singleLine,
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions, cursorBrush = cursorBrush, decorationBox = decorationBox)
}

@Suppress("DEPRECATION")
internal class GlasmioTextEditor(
    val host: GlasmioTextEditingHost,
    private val scope: CoroutineScope,
    private val scroll: ScrollState,
    private val toolbar: TextToolbar,
    private val modern: TextContextMenuProvider?,
    private val clipboard: ClipboardManager,
    initial: TextFieldValue,
) {
    var value by mutableStateOf(initial)
    var layout by mutableStateOf<TextLayoutResult?>(null)
    var coordinates: LayoutCoordinates? = null
    var decoration: LayoutCoordinates? = null
    var viewport: LayoutCoordinates? = null
    var viewportSize by mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
    var positionVersion by mutableIntStateOf(0)
    private var contentScreenBounds: Rect? = null
    private var viewportScreenBounds: Rect? = null
    var focused by mutableStateOf(false)
    var realWindowFocused by mutableStateOf(true)
    var enabled = true
    var readOnly = false
    var password = false
    var density: Density = Density(1f)
    var singleLine = true
    var reverseScroll = false
    var mapping: OffsetMapping = OffsetMapping.Identity
    var change: (TextFieldValue) -> Unit = {}
    var touching = false
    var blink by mutableStateOf(true)
    var showHandles by mutableStateOf(false)
    var pointerScreen by mutableStateOf<Offset?>(null)
    var magnifierSource by mutableStateOf<Offset?>(null)
    /** Last magnified point, retained while the dismissal spring runs after the gesture ends. */
    var magnifierRetainedSource by mutableStateOf<Offset?>(null)
    var draggingHandle by mutableStateOf<Boolean?>(null)
    private var magnifierOrigin: Size? = null
    private var fixedHandleOffset: Int? = null
    val draggingSelection: Boolean get() = fixedHandleOffset != null
    private var nativeProvider: TextContextMenuDataProvider? = null
    private var nativeLegacy: (() -> Unit)? = null
    private var menuJob: Job? = null
    private var settleJob: Job? = null
    private var pendingMenuJob: Job? = null
    private var interactionGeneration = 0L
    val chromeVisible: Boolean get() = enabled &&
        ((focused && realWindowFocused) || (host.menuOwnsFocus && host.active === this))

    fun updateContentCoordinates(node: LayoutCoordinates) {
        coordinates = node
        val bounds = Rect(node.localToScreen(Offset.Zero),
            node.localToScreen(Offset(node.size.width.toFloat(), node.size.height.toFloat())))
        if (contentScreenBounds != bounds) {
            contentScreenBounds = bounds
            positionVersion++
        }
    }

    fun updateViewportCoordinates(node: LayoutCoordinates) {
        viewport = node
        viewportSize = node.size
        val bounds = node.boundsInWindow().translate(node.localToScreen(Offset.Zero) - node.localToWindow(Offset.Zero))
        // Layout callbacks also run when only the selection overlay changes. Invalidating
        // that overlay again at the same geometry creates a composition/layout feedback loop.
        if (viewportScreenBounds != bounds) {
            viewportScreenBounds = bounds
            positionVersion++
        }
    }

    val legacyAdapter = object : TextToolbar {
        override val status: TextToolbarStatus get() = toolbar.status
        override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) =
            showMenu(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested, null)
        override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?, onAutofillRequested: (() -> Unit)?) {
            val selectAll: (() -> Unit)? = if (onSelectAllRequested != null) ({ this@GlasmioTextEditor.selectAll() }) else null
            nativeLegacy = { toolbar.showMenu(rect, onCopyRequested, onPasteRequested, onCutRequested, selectAll, onAutofillRequested) }
            if (chromeVisible) nativeLegacy?.invoke()
        }
        // The deliberately unfocused native chrome hides itself after layout. Real dismissal is
        // handled by this field's focus, touch, editing and disposal lifecycle instead.
        override fun hide() = Unit
    }
    val modernAdapter = object : TextContextMenuProvider {
        override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
            nativeProvider = object : TextContextMenuDataProvider by dataProvider {
                override fun data() = TextContextMenuData(dataProvider.data().components.map { component ->
                    if (component is TextContextMenuItem && component.key == TextContextMenuKeys.SelectAllKey) {
                        TextContextMenuItem(component.key, component.label, component.leadingIcon) { this@GlasmioTextEditor.selectAll() }
                    } else component
                })
            }
            if (chromeVisible) showNativeMenu()
            // The native request can be cancelled by false WindowInfo without cancelling the
            // independently owned menu coroutine or its live data provider.
            awaitCancellation()
        }
    }

    fun cursorRect(offset: Int): Rect? {
        val result = layout ?: return null
        val transformed = mapping.originalToTransformed(offset.coerceIn(0, value.text.length))
            .coerceIn(0, result.layoutInput.text.length)
        return result.getCursorRect(transformed)
    }

    fun viewportBounds(): Rect? {
        positionVersion
        val view = viewport?.takeIf { it.isAttached } ?: return null
        return view.boundsInWindow().translate(view.localToScreen(Offset.Zero) - view.localToWindow(Offset.Zero))
    }

    fun handleRect(start: Boolean): Rect? {
        positionVersion
        val node = coordinates?.takeIf { it.isAttached } ?: return null
        val rect = cursorRect(if (start) value.selection.start else value.selection.end) ?: return null
        return Rect(node.localToScreen(rect.topLeft), node.localToScreen(rect.bottomRight))
    }

    fun selectionBounds(): Rect? {
        val start = handleRect(true) ?: return null
        val end = handleRect(false) ?: return null
        val viewport = viewportBounds() ?: return null
        val bounds = if (!singleLine && !value.selection.collapsed) {
            val result = layout ?: return null
            val node = coordinates?.takeIf { it.isAttached } ?: return null
            val firstLine = result.getLineForVerticalPosition(node.screenToLocal(viewport.topLeft).y)
            val lastLine = result.getLineForVerticalPosition(node.screenToLocal(viewport.bottomRight).y)
            val from = maxOf(mapping.originalToTransformed(value.selection.min), result.getLineStart(firstLine))
            val to = minOf(mapping.originalToTransformed(value.selection.max), result.getLineEnd(lastLine))
            if (from >= to) return null
            val local = result.getPathForRange(from, to).getBounds()
            Rect(node.localToScreen(local.topLeft), node.localToScreen(local.bottomRight)).intersect(viewport)
        } else {
            Rect(minOf(start.left, end.left), minOf(start.top, end.top),
                maxOf(start.right, end.right), maxOf(start.bottom, end.bottom))
        }
        val pop = if (showHandles && !value.selection.collapsed) with(density) { 16.dp.toPx() } else 0f
        return Rect(bounds.left, bounds.top - pop, bounds.right, bounds.bottom + pop)
    }

    fun updatePointer(screen: Offset) {
        pointerScreen = screen
        val node = coordinates?.takeIf { it.isAttached } ?: return
        val viewport = viewportBounds() ?: return
        val point = Offset(screen.x.coerceIn(viewport.left, viewport.right), screen.y.coerceIn(viewport.top, viewport.bottom))
        val local = node.screenToLocal(point)
        val result = layout ?: return
        val offset = result.getOffsetForPosition(local)
        val cursor = result.getCursorRect(offset.coerceIn(0, result.layoutInput.text.length))
        magnifierSource = node.localToScreen(Offset(local.x, cursor.center.y)).copy(
            x = point.x,
            y = node.localToScreen(cursor.center).y.coerceIn(viewport.top, viewport.bottom),
        )
        magnifierOrigin = cursor.size
        magnifierRetainedSource = magnifierSource
    }

    /**
     * Size of the shape the lens grows out of: the caret rect, as UIKit's `originShape` does. The
     * layout reports a zero-width caret because only the drawn stroke has width, so the shape keeps
     * the 2 dp the cursor is painted with.
     */
    fun magnifierOriginSize(): Size? {
        val size = magnifierOrigin ?: return null
        return with(density) {
            Size(
                maxOf(size.width, 2.dp.toPx()).toDp().value,
                maxOf(size.height, 1.dp.toPx()).toDp().value,
            )
        }
    }

    /** UIKit refuses to open a loupe whose selection widget is taller than the lens body. */
    fun allowsMagnifier(): Boolean {
        val size = magnifierOrigin ?: return false
        return with(density) { size.height.toDp() } <= GlasmioLoupeBodyHeight
    }

    /** Clear the retained point once the dismissal spring has landed. */
    fun clearRetainedMagnifier() {
        if (magnifierSource == null) magnifierRetainedSource = null
    }

    fun dragHandle(start: Boolean, screen: Offset) {
        val node = coordinates?.takeIf { it.isAttached } ?: return
        val result = layout ?: return
        updatePointer(screen)
        val viewport = viewportBounds() ?: return
        val point = Offset(screen.x.coerceIn(viewport.left, viewport.right), screen.y.coerceIn(viewport.top, viewport.bottom))
        val transformed = result.getOffsetForPosition(node.screenToLocal(point))
        val original = mapping.transformedToOriginal(transformed.coerceIn(0, result.layoutInput.text.length))
            .coerceIn(0, value.text.length)
        val selection = if (draggingHandle != null) {
            val fixed = fixedHandleOffset
            if (fixed == null) TextRange(original)
            else if (start) TextRange(original, fixed) else TextRange(fixed, original)
        } else if (value.selection.collapsed) TextRange(original)
        else if (start) TextRange(original, value.selection.end) else TextRange(value.selection.start, original)
        val next = value.copy(selection = selection)
        value = next
        change(next)
        blink = true
    }

    fun beginHandle(start: Boolean) {
        dismissMenu()
        fixedHandleOffset = if (value.selection.collapsed) null
            else if (start) value.selection.end else value.selection.start
        draggingHandle = start
    }

    fun endHandle(released: Boolean) {
        if (released) draggingHandle?.let { handle -> pointerScreen?.let { dragHandle(handle, it) } }
        draggingHandle = null
        fixedHandleOffset = null
        pointerScreen = null
        magnifierSource = null
        if (released && chromeVisible) {
            showHandles = true
            showMenuAfterGesture(fromHandle = true)
        }
    }

    fun beginTouch() {
        dismissMenu()
        touching = true
    }

    fun cancelTouch() {
        touching = false
        pointerScreen = null
        magnifierSource = null
        dismissMenu()
    }

    fun finishTouch(showHandles: Boolean, showMenu: Boolean) {
        val generation = interactionGeneration
        settleJob?.cancel()
        settleJob = scope.launch {
            // Initial-pass observation sees UP before Foundation handles it on Main.
            withFrameNanos { }
            if (generation != interactionGeneration || !chromeVisible) return@launch
            touching = false
            this@GlasmioTextEditor.showHandles = showHandles
            if (showMenu) showMenuAfterGesture()
        }
    }

    suspend fun scrollAtEdge() {
        val handle = draggingHandle ?: return
        val pointer = pointerScreen ?: return
        // Placement catches up with the preceding scroll on this frame, including at the limit.
        dragHandle(handle, pointer)
        val bounds = viewportBounds() ?: return
        val edge = with(density) { 24.dp.toPx() }
        val position = if (singleLine) pointer.x else pointer.y
        val minimum = if (singleLine) bounds.left else bounds.top
        val maximum = if (singleLine) bounds.right else bounds.bottom
        val speed = with(density) { 12.dp.toPx() }
        val delta = when {
            position < minimum + edge -> -((minimum + edge - position) / edge).coerceIn(0f, 1f) * speed
            position > maximum - edge -> ((position - maximum + edge) / edge).coerceIn(0f, 1f) * speed
            else -> 0f
        }
        if (delta != 0f) scroll.scrollBy(if (reverseScroll) -delta else delta)
    }

    fun dismissMenu() {
        interactionGeneration++
        settleJob?.cancel()
        settleJob = null
        pendingMenuJob?.cancel()
        pendingMenuJob = null
        menuJob?.cancel()
        menuJob = null
        toolbar.hide()
    }
    fun dismiss() { dismissMenu(); touching = false; pointerScreen = null; magnifierSource = null; magnifierRetainedSource = null; showHandles = false; draggingHandle = null; fixedHandleOffset = null }

    fun showMenuAfterGesture(fromHandle: Boolean = false) {
        val generation = interactionGeneration
        pendingMenuJob?.cancel()
        pendingMenuJob = scope.launch {
            withFrameNanos { }
            if (generation != interactionGeneration || !chromeVisible || touching || draggingHandle != null) return@launch
            if (nativeProvider != null) showNativeMenu()
            else if (!fromHandle && nativeLegacy != null) nativeLegacy?.invoke()
            else showFallbackMenu()
        }
    }

    private fun showNativeMenu() {
        val provider = nativeProvider ?: return
        val target = modern ?: return
        if (menuJob?.isActive == true) return
        menuJob = scope.launch { target.showTextContextMenu(provider) }
    }

    private fun selectAll() {
        // Selection belongs to this editor; native requests can outlive the gesture that
        // produced them while our menu closes. Read the current value and callback here.
        val next = value.copy(selection = TextRange(0, value.text.length))
        value = next
        change(next)
        showHandles = true
        showMenuAfterGesture(fromHandle = true)
    }

    private fun showFallbackMenu() {
        val bounds = handleRect(false) ?: return
        val root = coordinates?.takeIf { it.isAttached }?.let { it.findRootCoordinates() } ?: return
        val rect = Rect(root.screenToLocal(bounds.topLeft), root.screenToLocal(bounds.bottomRight))
        val selection = value.selection
        val hasSelection = !selection.collapsed
        fun selectedText() = value.text.substring(value.selection.min, value.selection.max)
        fun replace(text: String) {
            val start = value.selection.min
            val next = TextFieldValue(value.text.replaceRange(start, value.selection.max, text), TextRange(start + text.length))
            value = next
            change(next)
            showHandles = false
        }
        toolbar.showMenu(rect,
            onCopyRequested = if (hasSelection && !password) ({ clipboard.setText(AnnotatedString(selectedText())); dismissMenu() }) else null,
            onPasteRequested = if (!readOnly && clipboard.hasText()) ({ clipboard.getText()?.let { replace(it.text) }; dismissMenu() }) else null,
            onCutRequested = if (!readOnly && hasSelection && !password) ({ clipboard.setText(AnnotatedString(selectedText())); replace(""); dismissMenu() }) else null,
            onSelectAllRequested = if (value.text.isNotEmpty() && selection.length < value.text.length) ::selectAll else null,
        )
    }
}

@Composable
internal fun GlasmioTextEditingOverlay(host: GlasmioTextEditingHost, backdrop: Backdrop) {
    val editor = host.active ?: return
    val root = host.coordinates?.takeIf { it.isAttached } ?: return
    if (!editor.chromeVisible) return
    val viewport = editor.viewportBounds() ?: return
    val popOffset = with(LocalDensity.current) { 8.dp.toPx() }
    // UIKit has no selection widget for a collapsed caret: the caret is its own grab point and the
    // loupe magnifies the caret itself, so only a range owns the two lollipops.
    val handles = if (editor.showHandles && (!editor.value.selection.collapsed || editor.draggingSelection) && editor.value.text.isNotEmpty()) {
        listOf(true, false).mapNotNull { editingHandle(editor, root, viewport, popOffset, it) }
    } else emptyList()
    val handlesBackdrop = rememberLayerBackdrop()
    // Record the actual widgets at their laid-out positions. The loupe samples this layer with the
    // same coordinate mapping as the text, so both stay attached through all lens transforms.
    Box(Modifier.fillMaxSize().layerBackdrop(handlesBackdrop, reuseContent = true)) {
        handles.forEach { handle ->
            key(editor, handle.start) { GlasmioEditingHandle(editor, handle.start, handle.anchor, handle.lineHeight) }
        }
    }

    val liveSource = editor.magnifierSource
    val source = liveSource ?: editor.magnifierRetainedSource ?: return
    val originSize = editor.magnifierOriginSize() ?: return
    if (!editor.allowsMagnifier()) return
    val combined = remember(backdrop, host.recording, handlesBackdrop) {
        object : Backdrop {
            override val isCoordinatesDependent: Boolean
                get() = backdrop.isCoordinatesDependent || host.recording.isCoordinatesDependent ||
                    handlesBackdrop.isCoordinatesDependent
            override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
                with(backdrop) { drawBackdrop(density, coordinates, layerBlock) }
                with(host.recording) { drawBackdrop(density, coordinates, layerBlock) }
                with(handlesBackdrop) { drawBackdrop(density, coordinates, layerBlock) }
            }
        }
    }
    GlasmioMagnifier(
        visible = liveSource != null,
        sourcePosition = root.screenToLocal(source),
        backdrop = combined,
        originSize = DpSize(originSize.width.dp, originSize.height.dp),
        // A caret and a selection widget replay the opening passes when they swap mid-gesture.
        identity = editor.value.selection.collapsed,
        tracking = liveSource != null,
        onDismissed = editor::clearRetainedMagnifier,
    )
}

@Composable
private fun GlasmioEditingHandle(editor: GlasmioTextEditor, start: Boolean, anchor: Offset, lineHeight: Float) {
    val density = LocalDensity.current
    val color = LocalGlassColors.current.accent
    val halfTouch = with(density) { 22.dp.toPx() }
    Canvas(
        Modifier.absoluteOffset { IntOffset((anchor.x - halfTouch).roundToInt(), (anchor.y - halfTouch).roundToInt()) }
            .size(44.dp)
            .semantics { contentDescription = if (start) "Selection start" else "Selection end" }
            .pointerInput(editor, start) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    editor.beginHandle(start)
                    var released = false
                    try {
                        val rect = editor.handleRect(start) ?: return@awaitEachGesture
                        val original = Offset(rect.left, rect.center.y)
                        var point = original
                        editor.updatePointer(point)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) { released = true; break }
                            point += change.positionChange()
                            editor.dragHandle(start, point)
                            change.consume()
                        }
                    } finally {
                        editor.endHandle(released)
                    }
                }
            },
    ) {
        drawEditingHandle(Offset(size.width / 2f, size.height / 2f), lineHeight, start != editor.value.selection.reversed, color)
    }
}

/**
 * One iOS selection widget: an 8 dp dot 8 dp off the line, joined to it by a 2 dp stick.
 * [center] is the dot in the handle's own 44 dp touch box. The loupe samples the recorded widget.
 */
private fun DrawScope.drawEditingHandle(center: Offset, lineHeight: Float, start: Boolean, color: Color) {
    val stickLength = lineHeight + 11.dp.toPx()
    drawLine(color, center, center + Offset(0f, if (start) stickLength else -stickLength), 2.dp.toPx())
    drawCircle(color, 8.dp.toPx(), center)
}

/** A drawable selection widget, in overlay pixels. */
private class EditingHandle(val start: Boolean, val anchor: Offset, val lineHeight: Float)

/**
 * Where one widget sits, or null when UIKit would not show it. The widget the finger holds stays
 * drawn outside the field; the idle one is only shown while its line is still on screen.
 */
private fun editingHandle(
    editor: GlasmioTextEditor,
    root: LayoutCoordinates,
    viewport: Rect,
    popOffset: Float,
    start: Boolean,
): EditingHandle? {
    val rect = editor.handleRect(start) ?: return null
    val leading = start != editor.value.selection.reversed
    val edge = if (leading) rect.top else rect.bottom
    val popDirection = if (leading) -popOffset else popOffset
    val anchor = Offset(rect.left, edge + popDirection)
    val dragging = editor.draggingHandle == start
    if (!dragging && !(anchor.x in viewport.left..viewport.right && edge in (viewport.top - 1f)..(viewport.bottom + 1f))) return null
    val visibleAnchor = if (dragging) {
        Offset(anchor.x.coerceIn(viewport.left, viewport.right), edge.coerceIn(viewport.top, viewport.bottom) + popDirection)
    } else anchor
    return EditingHandle(start, root.screenToLocal(visibleAnchor), rect.height)
}
