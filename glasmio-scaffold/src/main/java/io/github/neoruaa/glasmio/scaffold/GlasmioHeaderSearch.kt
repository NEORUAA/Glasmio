package io.github.neoruaa.glasmio.scaffold

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.ui.GlasmioSearchToolbar
import io.github.neoruaa.glasmio.glass.GlasmioToolbarActionHost
import io.github.neoruaa.glasmio.glass.GlasmioToolbarActionStyle
import io.github.neoruaa.glasmio.icons.LocalSfToolbarSymbols
import kotlin.math.roundToInt

/** Entry-owned input and one geometry clock for an entire header action group's search morph. */
@Stable
class GlasmioHeaderSearchState internal constructor(
    initialQuery: TextFieldValue,
    internal val configuration: State<HeaderSearchConfiguration>,
    internal val transition: GlasmioSearchTransition,
) {
    val progress: State<Float> get() = transition.progressState
    internal var sourceBounds by mutableStateOf<Rect?>(null)
    var query by mutableStateOf(initialQuery)
        private set

    fun updateQuery(value: TextFieldValue) {
        // A shared header must acknowledge IME edits before the owning page recomposes.
        query = value
        configuration.value.onQueryChange(value)
    }
}

internal data class HeaderSearchConfiguration(
    val active: Boolean,
    val onQueryChange: (TextFieldValue) -> Unit,
    val onSearch: (String) -> Unit,
    val onDismiss: () -> Unit,
    val placeholder: String,
    val cancelLabel: String,
    val autoFocus: Boolean,
)

/**
 * Attach the returned state to [GlasmioHeaderGroup.search]; filtering stays with the page.
 * [initialQuery] seeds the editor once. Later edits use [GlasmioHeaderSearchState.updateQuery].
 */
@Composable
fun rememberGlasmioHeaderSearch(
    active: Boolean,
    initialQuery: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    placeholder: String,
    cancelLabel: String,
    autoFocus: Boolean = true,
): GlasmioHeaderSearchState {
    val configuration = rememberUpdatedState(HeaderSearchConfiguration(
        active, onQueryChange, onSearch, onDismiss, placeholder, cancelLabel, autoFocus,
    ))
    val transition = rememberGlasmioSearchTransition(active)
    return remember { GlasmioHeaderSearchState(initialQuery, configuration, transition) }
}

/** The measured source remains placed for rotation and interrupted reversals. */
@Composable
internal fun GlasmioHeaderSearchLayer(group: GlasmioHeaderGroup, originInWindow: Offset?) {
    val state = group.search ?: return
    val configuration = state.configuration.value
    val progress = state.progress.value.coerceIn(0f, 1f)
    if (!configuration.active && progress == 0f) return
    val source = state.sourceBounds ?: return
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val ime = WindowInsets.ime
    val imeVisible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    val dismiss = {
        focus.clearFocus()
        keyboard?.hide()
        state.updateQuery(TextFieldValue())
        configuration.onDismiss()
    }
    BackHandler(enabled = configuration.active && !imeVisible, onBack = dismiss)
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            GlasmioToolbarActionHost(
                modifier = Modifier.testTag("GlasmioHeaderSearchMorph")
                    .graphicsLayer { outsets = LayerOutsets(64.dp, 64.dp, 64.dp, 64.dp) },
                style = GlasmioToolbarActionStyle.Separated,
                separationProgress = cancelRevealProgress(progress),
            ) {
                GlasmioSearchToolbar(
                    query = state.query,
                    onQueryChange = state::updateQuery,
                    onSearch = {
                        focus.clearFocus()
                        keyboard?.hide()
                        configuration.onSearch(state.query.text)
                    },
                    onCancel = dismiss,
                    cancelLabel = configuration.cancelLabel,
                    placeholder = configuration.placeholder,
                    autoFocus = configuration.active && configuration.autoFocus && progress >= 0.85f,
                    morphProgress = progress,
                    enabled = configuration.active,
                    modifier = Modifier.fillMaxSize(),
                    collapsedContent = {
                        CompositionLocalProvider(LocalSfToolbarSymbols provides true) {
                            Row(
                                Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                group.actions.forEach { action ->
                                    Box(
                                        Modifier.defaultMinSize(minWidth = 44.dp).height(44.dp).padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { action.content() }
                                }
                            }
                        }
                    },
                )
            }
        },
    ) { measurables, constraints ->
        val localSource = source.translate(-(originInWindow ?: source.topLeft))
        val width = (localSource.width + (constraints.maxWidth - localSource.width) * progress)
            .roundToInt().coerceIn(1, constraints.maxWidth.coerceAtLeast(1))
        val height = (localSource.height + (48.dp.toPx() - localSource.height) * progress)
            .roundToInt().coerceAtLeast(1)
        val x = (localSource.left * (1f - progress)).roundToInt()
        val y = (localSource.top + (GlasmioAdaptiveNavigationDefaults.SearchTopPadding.toPx() - localSource.top) * progress)
            .roundToInt()
        val toolbar = measurables.single().measure(Constraints.fixed(width, height))
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (originInWindow != null) toolbar.place(x, y)
        }
    }
}
