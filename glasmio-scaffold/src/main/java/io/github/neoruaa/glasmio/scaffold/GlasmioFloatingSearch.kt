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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.core.LocalGlassAppearance
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.GlassSurfaceStyle
import io.github.neoruaa.glasmio.glass.LocalBottomControlContact
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.icons.SfSymbol
import io.github.neoruaa.glasmio.ui.CompactBottomControlIconSize
import io.github.neoruaa.glasmio.ui.CompactBottomControlSize
import io.github.neoruaa.glasmio.ui.GlasmioIconButton
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
) {
    val value = progress.value
    if (!active && value <= 0f) return
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(toolbarPadding)) {
        Box(
            Modifier.align(Alignment.BottomEnd)
                .padding(horizontal = 8.dp)
                .graphicsLayer { alpha = value.coerceIn(0f, 1f) }
                .layout { measurable, constraints ->
                    val collapsed = 48.dp.roundToPx()
                    val maxWidth = constraints.maxWidth
                    val width = (collapsed + (maxWidth - collapsed) * value)
                        .roundToInt().coerceIn(0, maxWidth)
                    val height = 48.dp.roundToPx()
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(maxWidth, height) { placeable.place(maxWidth - width, 0) }
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

/** The navigation's adjacent search control, sharing the caller's compact progress. */
@Composable
fun GlasmioFloatingSearchButton(
    compactProgress: State<Float>,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalGlassBackdrop.current,
) {
    val contact = LocalBottomControlContact.current
    Box(
        modifier.size(NavigationBarSurfaceHeight)
            .then(contact?.takeIf { it.capturesLensSource.value }?.neighborRecordingModifier ?: Modifier),
        contentAlignment = Alignment.CenterEnd,
    ) {
        val progress = compactProgress.value
        val controlSize = NavigationBarSurfaceHeight -
            (NavigationBarSurfaceHeight - CompactBottomControlSize) * progress
        GlasmioIconButton(
            onClick = onClick,
            backdrop = backdrop,
            style = GlassSurfaceStyle.Navigation,
            modifier = Modifier.size(controlSize),
            morphProgress = compactProgress,
            morphCaptureWidth = NavigationBarSurfaceHeight,
        ) {
            SfIcon(
                SfSymbol.Search,
                contentDescription = contentDescription,
                tint = LocalGlassColors.current.content,
                size = 26.dp + (CompactBottomControlIconSize - 26.dp) * progress,
                weight = FontWeight.SemiBold,
            )
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
