package io.github.neoruaa.glasmio.ui

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.first


@Composable
fun GlasmioTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var owned by remember { mutableStateOf(TextFieldValue(value)) }
    var lastText by remember(value) { mutableStateOf(value) }
    val current = owned.copy(text = value)
    SideEffect { if (owned.selection != current.selection || owned.composition != current.composition) owned = current }
    GlasmioTextField(
        value = current,
        onValueChange = { next ->
            owned = next
            if (next.text != lastText) { lastText = next.text; onValueChange(next.text) }
        },
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        trailing = trailing,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}

@Composable
fun GlasmioTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = LocalGlassColors.current
    val fieldTextStyle = IosTypography.body.copy(fontWeight = FontWeight(510))
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .glasmioGroupedListSeparator(colors.separator, horizontalInset = 16.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlasmioEditableTextField(
            value,
            onValueChange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            textStyle = fieldTextStyle.copy(color = colors.content),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { inner ->
                if (value.text.isEmpty()) Text(placeholder, style = fieldTextStyle, color = colors.tertiaryContent)
                inner()
            },
        )
        trailing?.let { trailingContent ->
            CompositionLocalProvider(
                LocalGroupedListIconColor provides colors.content,
                LocalContentColor provides colors.content,
            ) {
                trailingContent()
            }
        }
    }
}


@Composable
fun GlasmioStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
    enabled: Boolean = true,
    decreaseContentDescription: String = "Decrease",
    increaseContentDescription: String = "Increase",
) {
    val colors = LocalGlassColors.current
    val fill = if (colors.isDark) Color.White.copy(alpha = 0.12f) else Color(0x14747480)
    Row(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .height(32.dp)
            .clip(Capsule())
            .background(fill)
            .drawWithContent {
                drawContent()
                val lineWidth = 1.dp.toPx()
                drawRoundRect(
                    colors.separator,
                    topLeft = Offset((size.width - lineWidth) / 2f, 4.dp.toPx()),
                    size = Size(lineWidth, 24.dp.toPx()),
                    cornerRadius = CornerRadius(lineWidth / 2f),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 46.dp, height = 32.dp)
                .semantics { contentDescription = decreaseContentDescription }
                .clickable(enabled = enabled && value > range.first, role = Role.Button) { onValueChange(value - 1) },
            contentAlignment = Alignment.Center,
        ) {
            SfIcon("minus", null, size = 17.dp, tint = colors.content)
        }
        Box(
            Modifier.size(width = 46.dp, height = 32.dp)
                .semantics { contentDescription = increaseContentDescription }
                .clickable(enabled = enabled && value < range.last, role = Role.Button) { onValueChange(value + 1) },
            contentAlignment = Alignment.Center,
        ) {
            SfIcon("plus", null, size = 17.dp, tint = colors.content)
        }
    }
}
