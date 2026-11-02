package io.github.neoruaa.glasmio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.LocalGlassContentColor
import io.github.neoruaa.glasmio.core.systemGray5
import kotlin.math.ceil

/** A ChatKit-style balloon surface. The host owns message grouping, labels, and actions. */
@Composable
fun GlasmioMessageBubble(
    outgoing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    showTail: Boolean = true,
    contentPadding: PaddingValues = MessageBubbleDefaults.contentPadding(outgoing, showTail),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalGlassColors.current
    val fill = if (color != Color.Unspecified) color else if (outgoing) colors.accent else colors.systemGray5
    val foreground = if (contentColor != Color.Unspecified) contentColor else if (outgoing) Color.White else colors.content
    val shape = MessageBubbleDefaults.shape(outgoing, showTail)
    CompositionLocalProvider(LocalContentColor provides foreground, LocalGlassContentColor provides foreground) {
        ProvideTextStyle(IosTypography.body.copy(color = foreground)) {
            Column(
                modifier.defaultMinSize(
                    minWidth = MessageBubbleDefaults.pillHeight,
                    minHeight = MessageBubbleDefaults.minimumHeight(showTail),
                )
                    .drawWithCache {
                        val outline = shape.createOutline(size, layoutDirection, this)
                        val clip = when (outline) {
                            is Outline.Generic -> outline.path
                            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                        }
                        // Keep the silhouette and content in the outer recording. A clip
                        // GraphicsLayer would leave a child RenderNode owned by the source
                        // window, which disappears when a lifted preview hides that source.
                        onDrawWithContent {
                            drawOutline(outline, fill)
                            clipPath(clip) { this@onDrawWithContent.drawContent() }
                        }
                    }.padding(contentPadding),
                content = content,
            )
        }
    }
}

object MessageBubbleDefaults {
    // CKTextBalloonView measures its text first. Use the host's body line box and the
    // native 10pt text insets so both shape and lifted shadow track Dynamic Type.
    internal val pillHeight: Dp
        @Composable get() {
            val style = IosTypography.body
            return with(LocalDensity.current) {
                ceil((if (style.lineHeight.isSpecified) style.lineHeight else style.fontSize).toPx()).toFloat().toDp()
            } + 20.dp
        }

    @Composable
    fun minimumHeight(showTail: Boolean = true): Dp = pillHeight * if (showTail) 1.169625f else 1f

    @Composable
    fun shape(outgoing: Boolean, showTail: Boolean = true): MessageBubbleShape {
        val radius = pillHeight / 2f
        return remember(outgoing, showTail, radius) { MessageBubbleShape(outgoing, showTail, radius) }
    }

    /** iOS 27 round tails reserve only their native vertical inset inside the outline. */
    @Composable
    fun contentPadding(outgoing: Boolean, showTail: Boolean = true): PaddingValues {
        val tail = if (showTail) pillHeight * 0.169625f else 0.dp
        return PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 10.dp,
            bottom = 10.dp + tail,
        )
    }
}
