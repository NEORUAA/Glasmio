package io.github.neoruaa.glasmio.glass.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** A material region's latest completed pre-coating luminance sample. */
@Stable
class BackdropLuminance internal constructor() {
    /** Null until a sample succeeds. Missing samples preserve the last completed value. */
    var value: Float? by mutableStateOf(null)
        private set

    internal fun publish(sample: Float) {
        if (!sample.isFinite() || sample < 0f) return
        // The chooser handles noise; small raw changes may cross an appearance boundary.
        if (value != sample) value = sample
    }
}

/** Sampling is enabled only when this state is passed to a backdrop draw modifier. */
@Composable
fun rememberBackdropLuminance(): BackdropLuminance = remember { BackdropLuminance() }
