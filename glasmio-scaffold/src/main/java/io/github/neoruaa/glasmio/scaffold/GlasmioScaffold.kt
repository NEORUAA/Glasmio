package io.github.neoruaa.glasmio.scaffold

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.LocalGlassColors

/**
 * Measures caller-owned bars and supplies their occupied space to the body.
 * Apply [content]'s padding once, for example with [GlasmioListPage].
 */
@Composable
fun GlasmioScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    containerColor: Color = LocalGlassColors.current.groupedBackground,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        contentWindowInsets = contentWindowInsets,
        containerColor = containerColor,
        contentColor = LocalGlassColors.current.content,
        content = content,
    )
}

/** A lazy body for [GlasmioScaffold], including bounce for empty or short content. */
@Composable
fun GlasmioListPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    GlasmioOverscrollHost(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().consumeWindowInsets(contentPadding),
            state = state,
            contentPadding = contentPadding,
            content = content,
        )
    }
}
