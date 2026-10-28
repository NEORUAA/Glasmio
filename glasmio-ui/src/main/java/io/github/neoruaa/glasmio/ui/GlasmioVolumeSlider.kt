package io.github.neoruaa.glasmio.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.segmentedControlBackground
import io.github.neoruaa.glasmio.core.swipeBackExcluded
import io.github.neoruaa.glasmio.icons.SfIcon
import kotlin.math.abs

/**
 * A filled iOS volume capsule with a normalized 0..1 value and immediate local drag feedback.
 * The caller owns [value] and supplies a localized accessibility label through [modifier].
 */
@Composable
fun GlasmioVolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    require(value.isFinite()) { "Slider value must be finite." }
    val colors = GlasmioTheme.colors
    val appearance = GlasmioTheme.appearance
    val spacing = GlasmioTheme.spacing
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val track = colors.segmentedControlBackground.let {
        if (appearance.reduceTransparency) it.compositeOver(colors.elevatedBackground) else it
    }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentValue by rememberUpdatedState(value.coerceIn(0f, 1f))
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    var dragging by remember(enabled) { mutableStateOf(false) }
    var dragValue by remember(enabled) { mutableFloatStateOf(currentValue) }
    var focused by remember { mutableStateOf(false) }
    val displayedValue = if (dragging) dragValue else currentValue
    val updateValue by rememberUpdatedState<(Float) -> Boolean> { requested ->
        val next = requested.coerceIn(0f, 1f)
        val previous = if (dragging) dragValue else currentValue
        if (next == previous) {
            false
        } else {
            if (dragging) dragValue = next
            currentOnValueChange(next)
            true
        }
    }
    val finishGesture by rememberUpdatedState {
        if (dragging && currentEnabled) currentOnValueChangeFinished?.invoke()
        dragging = false
    }
    val symbol = when {
        displayedValue <= 0f -> "speaker.slash.fill"
        displayedValue < 1f / 3f -> "speaker.wave.1.fill"
        displayedValue < 2f / 3f -> "speaker.wave.2.fill"
        else -> "speaker.wave.3.fill"
    }

    Box(
        modifier
            .swipeBackExcluded(enabled)
            .fillMaxWidth()
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .progressSemantics(displayedValue, 0f..1f)
            .semantics {
                if (!enabled) disabled()
                setProgress { requested ->
                    if (!currentEnabled || !requested.isFinite() || !updateValue(requested)) {
                        false
                    } else {
                        currentOnValueChangeFinished?.invoke()
                        true
                    }
                }
            }
            .onKeyEvent { event ->
                if (!currentEnabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val next = when (event.key) {
                    Key.DirectionLeft -> displayedValue + if (ltr) -0.05f else 0.05f
                    Key.DirectionRight -> displayedValue + if (ltr) 0.05f else -0.05f
                    Key.DirectionUp -> displayedValue + 0.05f
                    Key.DirectionDown -> displayedValue - 0.05f
                    Key.MoveHome -> 0f
                    Key.MoveEnd -> 1f
                    else -> return@onKeyEvent false
                }
                if (updateValue(next)) currentOnValueChangeFinished?.invoke()
                true
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = enabled)
            .pointerInput(enabled, ltr) {
                if (!enabled) return@pointerInput
                fun updateFromPosition(x: Float) {
                    val fraction = (x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    updateValue(if (ltr) fraction else 1f - fraction)
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var verticalIntent = false
                    try {
                        val start = awaitTouchSlopOrCancellation(down.id) { change, overSlop ->
                            if (!verticalIntent && abs(overSlop.x) > abs(overSlop.y)) {
                                if (currentEnabled) {
                                    change.consume()
                                    dragValue = currentValue
                                    dragging = true
                                    updateFromPosition(change.position.x)
                                }
                            } else {
                                // Once vertical intent wins, leave this gesture to the page.
                                verticalIntent = true
                            }
                        }
                        if (start != null && dragging) {
                            val ended = drag(start.id) { change ->
                                if (currentEnabled) {
                                    change.consume()
                                    updateFromPosition(change.position.x)
                                }
                            }
                            if (ended) {
                                currentEvent.changes.forEach { if (!it.pressed) it.consume() }
                            }
                        } else if (!verticalIntent && currentEnabled) {
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up?.changedToUpIgnoreConsumed() == true && !up.isConsumed) {
                                up.consume()
                                updateFromPosition(up.position.x)
                                currentOnValueChangeFinished?.invoke()
                            }
                        }
                    } finally {
                        if (dragging) finishGesture()
                    }
                }
            }
            .clip(Capsule())
            .drawBehind {
                drawRect(track)
                val width = size.width * displayedValue
                drawRect(
                    color = Color.White,
                    topLeft = Offset(if (ltr) 0f else size.width - width, 0f),
                    size = Size(width, size.height),
                )
            }
            .then(if (enabled && focused) Modifier.border(2.dp, colors.accent, Capsule()) else Modifier),
    ) {
        SfIcon(
            symbol,
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = spacing.regular),
            tint = colors.content,
            size = 24.dp,
        )
        // Keep the speaker legible on both the white fill and the dark unfilled track.
        Box(
            Modifier.matchParentSize().drawWithContent {
                val width = size.width * displayedValue
                clipRect(
                    left = if (ltr) 0f else size.width - width,
                    right = if (ltr) width else size.width,
                ) { this@drawWithContent.drawContent() }
            },
        ) {
            SfIcon(
                symbol,
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = spacing.regular),
                tint = Color.Black,
                size = 24.dp,
            )
        }
    }
}
