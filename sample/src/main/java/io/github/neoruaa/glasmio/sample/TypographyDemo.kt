package io.github.neoruaa.glasmio.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.IosDynamicTypeSize
import io.github.neoruaa.glasmio.core.IosTextStyle
import io.github.neoruaa.glasmio.core.rememberGlasmioTextStyles
import io.github.neoruaa.glasmio.ui.GlasmioPopUpButton

@Composable
internal fun TypographyDemo(
    listState: LazyListState,
    contentPadding: PaddingValues,
    largeTitle: @Composable () -> Unit,
) {
    var size by rememberSaveable { mutableStateOf(IosDynamicTypeSize.Large) }
    val textStyles = rememberGlasmioTextStyles(dynamicTypeSize = size)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().consumeWindowInsets(contentPadding),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "sample-large-title") { largeTitle() }
        item(key = "dynamic-type-size") {
            GlasmioPopUpButton(
                selected = size,
                items = IosDynamicTypeSize.entries,
                onSelected = { size = it },
                label = { it.displayName },
                modifier = Modifier.padding(horizontal = 4.dp).semantics {
                    contentDescription = "Dynamic Type size"
                },
            )
        }
        items(IosTextStyle.entries, key = { it.name }) { role ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                role.variants.forEach { variant ->
                    Text(role.displayName, style = textStyles[role, variant])
                }
            }
        }
    }
}
