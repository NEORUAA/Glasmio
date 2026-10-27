package io.github.neoruaa.glasmio.preference

import io.github.neoruaa.glasmio.ui.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.icons.SfIcon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
@Composable
fun GlasmioPreferenceEntry(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    content: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    isEnabled: Boolean = true,
    showTopSeparator: Boolean = true,
) {
    val colors = LocalGlassColors.current
    val minimumHeight = if (description != null || content != null) 68.dp else 52.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = isEnabled && onClick != null,
                onClick = onClick ?: {}
            )
            .padding(horizontal = 16.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.alpha(if (isEnabled) 1f else 0.5f).padding(horizontal = 4.dp)
            ) {
                icon()
            }

            Spacer(Modifier.width(12.dp))
        }

        Row(
            modifier = Modifier.weight(1f)
                .heightIn(min = minimumHeight)
                .glasmioGroupedListSeparator(colors.separator, visible = showTopSeparator)
                .alpha(if (isEnabled) 1f else 0.5f)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                ProvideTextStyle(IosTypography.body) {
                    title()
                }

                if (description != null) {
                    Text(
                        text = description,
                        style = IosTypography.subheadline,
                        color = colors.secondaryContent
                    )
                }

                content?.invoke()
            }

            if (trailingContent != null) {
                Spacer(Modifier.width(12.dp))

                trailingContent()
            }
        }
    }
}

@Composable
fun <T> GlasmioListPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    selectedValue: T,
    values: List<T>,
    valueText: @Composable (T) -> String,
    onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
    showTopSeparator: Boolean = true,
) {
    var showDialog by rememberSaveable {
        mutableStateOf(false)
    }
    PreferenceListDialog(
        visible = showDialog,
        title = title,
        onDismiss = { showDialog = false }
    ) {
        itemsIndexed(values) { index, value ->
            val selected = value == selectedValue
            GlasmioListRow(
                title = valueText(value),
                modifier = Modifier.selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = {
                        if (showDialog) {
                            onValueSelected(value)
                            showDialog = false
                        }
                    },
                ),
                showTopSeparator = index > 0,
                trailing = {
                    if (selected) {
                        SfIcon("checkmark", null, size = 17.dp, tint = LocalGlassColors.current.accent)
                    }
                },
            )
        }
    }

    GlasmioPreferenceEntry(
        modifier = modifier,
        title = title,
        description = description ?: valueText(selectedValue),
        icon = icon,
        onClick = { showDialog = true },
        isEnabled = isEnabled,
        showTopSeparator = showTopSeparator,
    )
}

@Composable
inline fun <reified T : Enum<T>> GlasmioEnumListPreference(
    modifier: Modifier = Modifier,
    noinline title: @Composable () -> Unit,
    noinline icon: (@Composable () -> Unit)?,
    selectedValue: T,
    noinline valueText: @Composable (T) -> String,
    noinline onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
    showTopSeparator: Boolean = true,
) {
    GlasmioListPreference(
        modifier = modifier,
        title = title,
        icon = icon,
        selectedValue = selectedValue,
        values = enumValues<T>().toList(),
        valueText = valueText,
        onValueSelected = onValueSelected,
        isEnabled = isEnabled,
        showTopSeparator = showTopSeparator,
    )
}

@Composable
fun GlasmioSwitchPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isEnabled: Boolean = true,
    showTopSeparator: Boolean = true,
) {
    GlasmioPreferenceEntry(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        trailingContent = {
            GlasmioSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = isEnabled,
            )
        },
        onClick = { onCheckedChange(!checked) },
        isEnabled = isEnabled,
        showTopSeparator = showTopSeparator,
    )
}

@Composable
fun GlasmioEditTextPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    isInputValid: (String) -> Boolean = { it.isNotEmpty() },
    isEnabled: Boolean = true,
    showTopSeparator: Boolean = true,
) {
    var showDialog by rememberSaveable {
        mutableStateOf(false)
    }

    PreferenceTextFieldDialog(
        visible = showDialog,
        title = title,
        initialTextFieldValue = TextFieldValue(
            text = value,
            selection = TextRange(value.length)
        ),
        singleLine = singleLine,
        isInputValid = isInputValid,
        onDone = onValueChange,
        onDismiss = { showDialog = false }
    )

    GlasmioPreferenceEntry(
        modifier = modifier,
        title = title,
        description = value,
        icon = icon,
        onClick = { showDialog = true },
        isEnabled = isEnabled,
        showTopSeparator = showTopSeparator,
    )
}

@Composable
fun GlasmioPreferenceGroupTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    GlasmioSectionTitle(title, modifier)
}
