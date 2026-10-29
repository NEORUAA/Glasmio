package io.github.neoruaa.glasmio.glass.render.highlight

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Key/fill edge coverage; the defaults use the native one-point highlight height. */
@Immutable
data class Highlight(
    val width: Dp = 1.dp,
    val blurRadius: Dp = width / 2f,
    val alpha: Float = 1f,
    val style: HighlightStyle = HighlightStyle.Default,
) {
    companion object {
        @Stable val Default = Highlight()
        @Stable val Ambient = Highlight(style = HighlightStyle.Ambient)
        @Stable val Plain = Highlight(style = HighlightStyle.Plain)
    }
}
