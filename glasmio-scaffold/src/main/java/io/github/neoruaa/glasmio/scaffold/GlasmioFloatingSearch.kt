package io.github.neoruaa.glasmio.scaffold

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCombinedBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.core.LocalGlassAppearance
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.GlassSurfaceStyle
import io.github.neoruaa.glasmio.glass.BottomNavigationContact
import io.github.neoruaa.glasmio.glass.LocalBottomControlContact
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.icons.SfSymbol
import io.github.neoruaa.glasmio.ui.CompactBottomControlIconSize
import io.github.neoruaa.glasmio.ui.CompactBottomControlSize
import io.github.neoruaa.glasmio.ui.GlasmioIconButton
import io.github.neoruaa.glasmio.ui.GlasmioNavigationBar
import io.github.neoruaa.glasmio.ui.GlasmioTabItem
import io.github.neoruaa.glasmio.ui.GlasmioSearchToolbar
import io.github.neoruaa.glasmio.ui.NavigationBarSurfaceHeight
import kotlin.math.roundToInt

/**
 * Coordinates a caller-owned search route, results overlay and floating input toolbar.
 * [content] stays composed, so an empty query reveals the host's search landing page.
 * Apply the [results] padding once, for example as a lazy list's content padding.
 *
 * Route changes and the entry-focus flag remain owned by the host. By default its navigation
 * dispatcher handles Back; [handleBack] opts into dismissal only while the IME is absent.
 * Leaving an active search clears its input and restores the window's soft-input policy.
 * [sourceBoundsInWindow] optionally supplies the measured collapsed endpoint in window pixels.
 */
@Composable
fun GlasmioFloatingSearch(
    active: Boolean,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    placeholder: String,
    cancelLabel: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    extraBottomSpacing: Dp = 0.dp,
    resultsBottomSpacing: Dp = 56.dp,
    handleBack: Boolean = false,
    transition: GlasmioSearchTransition? = null,
    sourceBoundsInWindow: Rect? = null,
    results: @Composable BoxScope.(PaddingValues) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val window = remember(view) {
        (view.parent as? DialogWindowProvider)?.window ?: view.context.findActivity()?.window
    }
    val currentQuery by rememberUpdatedState(query)
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnSearch by rememberUpdatedState(onSearch)
    val clearInput = remember(focusManager, keyboard) {
        {
            focusManager.clearFocus()
            keyboard?.hide()
            if (currentQuery != TextFieldValue()) currentOnQueryChange(TextFieldValue())
        }
    }
    val dismiss = remember(clearInput) {
        {
            clearInput()
            currentOnDismiss()
        }
    }
    DisposableEffect(active, window, clearInput) {
        val previousMode = window?.attributes?.softInputMode
        if (active && previousMode != null) {
            window?.setSoftInputMode(
                (previousMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
            )
        }
        onDispose {
            if (active) {
                clearInput()
                if (previousMode != null) window?.setSoftInputMode(previousMode)
            }
        }
    }

    val navigationInsets = WindowInsets.navigationBars
    val imeInsets = WindowInsets.ime
    val topInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top)
    val bottomControlInsets = remember(navigationInsets, extraBottomSpacing) {
        navigationInsets.only(WindowInsetsSides.Bottom)
            .union(WindowInsets(bottom = 20.dp))
            .add(WindowInsets(bottom = extraBottomSpacing))
    }
    val searchBottomInsets = remember(imeInsets, bottomControlInsets, extraBottomSpacing) {
        bottomControlInsets.union(
            imeInsets.add(WindowInsets(bottom = 8.dp + extraBottomSpacing)),
        ).only(WindowInsetsSides.Bottom)
    }
    val toolbarPadding = remember(searchBottomInsets, density) {
        searchBottomInsets.asPaddingValues(density)
    }
    val resultsPadding = remember(topInsets, searchBottomInsets, resultsBottomSpacing, density) {
        topInsets.add(searchBottomInsets)
            .add(WindowInsets(bottom = resultsBottomSpacing))
            .asPaddingValues(density)
    }
    // Only the IME's visibility boundary affects Back routing; animated inset values are
    // otherwise read by padding during measurement, without recomposing the search body.
    val imeVisible by remember(imeInsets, density) {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }
    BackHandler(enabled = active && handleBack && !imeVisible, onBack = dismiss)

    val resultsBackdrop = rememberLayerBackdrop()
    val toolbarBackdrop = rememberCombinedBackdrop(backdrop, resultsBackdrop)
    val usesBackdrop = LocalGlassAppearance.current.usesBackdrop
    val morph = transition ?: rememberGlasmioSearchTransition(active)
    var openingSourceBounds by remember { mutableStateOf(sourceBoundsInWindow) }
    LaunchedEffect(active) {
        if (active) openingSourceBounds = sourceBoundsInWindow
    }
    LaunchedEffect(sourceBoundsInWindow) {
        if (active && openingSourceBounds == null) openingSourceBounds = sourceBoundsInWindow
    }
    Box(modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().then(
            if (active && query.text.isNotEmpty()) Modifier.clearAndSetSemantics {} else Modifier,
        )) { content() }
        // Record the fading results alone. The toolbar remains a sibling and can never
        // feed its own glass pixels back into the sampled source.
        Box(
            Modifier.matchParentSize().then(
                if (usesBackdrop) Modifier.layerBackdrop(resultsBackdrop).trackBackdropPosition(resultsBackdrop)
                else Modifier,
            ),
        ) {
            AnimatedVisibility(visible = active, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier.fillMaxSize().background(
                        if (query.text.isEmpty()) Color.Transparent else MaterialTheme.colorScheme.background,
                    ),
                ) {
                    if (query.text.isNotEmpty()) results(resultsPadding)
                }
            }
        }
        SearchBarLayer(
            active = active,
            progress = morph.progressState,
            toolbarPadding = toolbarPadding,
            backdrop = toolbarBackdrop,
            query = query,
            onQueryChange = onQueryChange,
            onSearch = {
                focusManager.clearFocus()
                keyboard?.hide()
                currentOnSearch(it)
            },
            onCancel = dismiss,
            cancelLabel = cancelLabel,
            placeholder = placeholder,
            autoFocus = active && autoFocus,
            sourceBoundsInWindow = openingSourceBounds,
        )
    }
}

/**
 * Grows the search capsule leftwards from the trailing search button. The navigation bar and
 * this capsule share [GlasmioSearchTransition], so both sides of the swap follow one spring.
 */
@Composable
private fun BoxScope.SearchBarLayer(
    active: Boolean,
    progress: State<Float>,
    toolbarPadding: PaddingValues,
    backdrop: Backdrop,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    onCancel: () -> Unit,
    cancelLabel: String,
    placeholder: String,
    autoFocus: Boolean,
    sourceBoundsInWindow: Rect?,
) {
    val value = progress.value
    if (!active && value <= 0f) return
    val direction = LocalLayoutDirection.current
    var toolbarOriginInWindow by remember { mutableStateOf<Offset?>(null) }
    val source = sourceBoundsInWindow?.takeIf {
        it.width > 0f && it.height > 0f &&
            it.left.isFinite() && it.top.isFinite() && it.right.isFinite() && it.bottom.isFinite()
    }
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(toolbarPadding)) {
        Box(
            Modifier.align(Alignment.BottomEnd)
                .padding(horizontal = 8.dp)
                .onGloballyPositioned { toolbarOriginInWindow = it.positionInWindow() }
                .graphicsLayer { alpha = value.coerceIn(0f, 1f) }
                .layout { measurable, constraints ->
                    val collapsed = 48.dp.roundToPx()
                    val maxWidth = constraints.maxWidth
                    val startWidth = source?.width ?: collapsed.toFloat()
                    val startHeight = source?.height ?: collapsed.toFloat()
                    val fraction = value.coerceIn(0f, 1f)
                    val width = (startWidth + (maxWidth - startWidth) * fraction)
                        .roundToInt().coerceIn(1, maxWidth.coerceAtLeast(1))
                    val height = (startHeight + (collapsed - startHeight) * fraction).roundToInt().coerceAtLeast(1)
                    val envelopeHeight = maxOf(collapsed, startHeight.roundToInt())
                    val origin = toolbarOriginInWindow
                    val startX = if (source != null && origin != null) source.left - origin.x
                    else if (direction == LayoutDirection.Ltr) maxWidth - startWidth else 0f
                    val startBottom = if (source != null && origin != null) source.bottom - origin.y
                    else envelopeHeight.toFloat()
                    val x = (startX * (1f - fraction)).roundToInt()
                    val bottom = (startBottom + (envelopeHeight - startBottom) * fraction).roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(maxWidth, envelopeHeight) { placeable.place(x, bottom - height) }
                },
        ) {
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                GlasmioSearchToolbar(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    onCancel = onCancel,
                    cancelLabel = cancelLabel,
                    placeholder = placeholder,
                    autoFocus = autoFocus,
                    morphProgress = value,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * A navigation item can share the main capsule or occupy its own trailing capsule.
 * Selection and actions keep the same keys in both presentations. The host owns routes,
 * search state and outer window insets; [onItemBoundsChanged] reports actual window pixels.
 */
@Composable
fun <T> GlasmioNavigationBarWithTrailingItem(
    items: List<GlasmioTabItem<T>>,
    trailingItem: GlasmioTabItem<T>,
    selectedKey: T,
    onSelected: (T) -> Unit,
    onExpand: () -> Unit,
    compactProgress: State<Float>,
    modifier: Modifier = Modifier,
    detachTrailingItem: Boolean = true,
    compactSize: Dp = CompactBottomControlSize,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    onItemBoundsChanged: ((T, Rect) -> Unit)? = null,
) {
    require(items.isNotEmpty()) { "At least one main navigation item is required." }
    require(items.none { it.key == trailingItem.key }) { "The trailing item key must be unique." }
    val mainItems = remember(items, trailingItem, detachTrailingItem) {
        if (detachTrailingItem) items else items + trailingItem
    }
    BottomNavigationContact(backdrop = backdrop, modifier = modifier) {
        GlasmioNavigationBar(
            items = mainItems,
            selectedKey = selectedKey,
            onSelected = onSelected,
            onExpand = onExpand,
            compactProgress = compactProgress,
            compactSize = compactSize,
            backdrop = backdrop,
            modifier = Modifier.weight(1f).zIndex(1f),
            onItemBoundsChanged = onItemBoundsChanged,
        )
        if (detachTrailingItem) {
            Spacer(Modifier.size(width = 8.dp, height = 1.dp))
            FloatingNavigationControl(
                compactProgress = compactProgress,
                onClick = { onSelected(trailingItem.key) },
                backdrop = backdrop,
                compactSize = compactSize,
                itemSelected = trailingItem.key == selectedKey,
                itemContentDescription = trailingItem.contentDescription,
                onBoundsChanged = onItemBoundsChanged?.let { callback ->
                    { bounds -> callback(trailingItem.key, bounds) }
                },
            ) { _ ->
                SfIcon(
                    trailingItem.symbol,
                    contentDescription = trailingItem.contentDescription,
                    tint = LocalGlassColors.current.let { if (trailingItem.key == selectedKey) it.accent else it.content },
                    size = CompactBottomControlIconSize,
                    fontSize = 18.sp,
                    weight = FontWeight.Medium,
                    fontFeatureSettings = "'ss16' 1",
                    fitGlyphToBounds = false,
                )
            }
        }
    }
}

/** The navigation's adjacent search control, sharing the caller's compact progress. */
@Composable
fun GlasmioFloatingSearchButton(
    compactProgress: State<Float>,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalGlassBackdrop.current,
) {
    FloatingNavigationControl(compactProgress, onClick, modifier, backdrop) { _ ->
        SfIcon(
            SfSymbol.Search,
            contentDescription = contentDescription,
            tint = LocalGlassColors.current.content,
            size = CompactBottomControlIconSize,
            fontSize = 18.sp,
            weight = FontWeight.Medium,
            fontFeatureSettings = "'ss16' 1",
            fitGlyphToBounds = false,
        )
    }
}

@Composable
private fun FloatingNavigationControl(
    compactProgress: State<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    compactSize: Dp = CompactBottomControlSize,
    itemSelected: Boolean? = null,
    itemContentDescription: String? = null,
    onBoundsChanged: ((Rect) -> Unit)? = null,
    content: @Composable (Float) -> Unit,
) {
    val contact = LocalBottomControlContact.current
    Box(
        modifier.size(NavigationBarSurfaceHeight)
            .then(contact?.takeIf { it.capturesLensSource.value }?.neighborRecordingModifier ?: Modifier)
            .then(if (itemSelected != null) Modifier.clearAndSetSemantics {
                role = Role.Tab
                selected = itemSelected
                itemContentDescription?.let { contentDescription = it }
                onClick { onClick(); true }
            } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val progress = compactProgress.value
        val controlSize = NavigationBarSurfaceHeight -
            (NavigationBarSurfaceHeight - compactSize) * progress
        GlasmioIconButton(
            onClick = onClick,
            backdrop = backdrop,
            style = GlassSurfaceStyle.Navigation,
            nativeNavigationFlex = true,
            modifier = Modifier.size(controlSize)
                .onGloballyPositioned { coordinates ->
                    onBoundsChanged?.invoke(coordinates.boundsInWindow())
                },
            morphProgress = compactProgress,
            morphCaptureWidth = NavigationBarSurfaceHeight,
        ) {
            content(progress)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        val base = context.baseContext
        if (base === context) return null
        context = base
    }
    return context as? Activity
}
