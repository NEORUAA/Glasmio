package io.github.neoruaa.glasmio.sample

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import io.github.neoruaa.glasmio.scaffold.*

/** State belongs to a back-stack instance, including repeated visits to the same route. */
internal class SampleHeaderEntry {
    var scroll by mutableStateOf<GlasmioHeaderScrollState?>(null)
    var actions by mutableStateOf<List<GlasmioHeaderAction>?>(null)
}
internal val LocalSampleHeaderEntry = staticCompositionLocalOf<SampleHeaderEntry> { error("Missing sample header entry") }
internal val LocalSampleBodyPadding = staticCompositionLocalOf { PaddingValues() }
internal fun sampleEntryKey(route: String) = "$route#${java.util.UUID.randomUUID()}"
internal fun String.sampleRouteName() = substringBefore('#')
