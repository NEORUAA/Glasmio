package io.github.neoruaa.glasmio.preference

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonRole
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec
import io.github.neoruaa.glasmio.ui.GlasmioAlertDialog
import io.github.neoruaa.glasmio.ui.GlasmioAlertFieldGroup
import io.github.neoruaa.glasmio.ui.GlasmioAlertTextField

@Composable
internal fun PreferenceListDialog(
    visible: Boolean,
    title: @Composable () -> Unit,
    onDismiss: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp / 2
    GlasmioAlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = "",
        titleContent = title,
        buttons = listOf(
            GlasmioAlertButtonSpec(
                stringResource(android.R.string.cancel),
                onDismiss,
                GlasmioAlertButtonRole.Cancel,
            ),
        ),
    ) {
        LazyColumn(Modifier.heightIn(max = maxHeight), content = content)
    }
}

@Composable
internal fun PreferenceTextFieldDialog(
    visible: Boolean,
    title: @Composable () -> Unit,
    initialTextFieldValue: TextFieldValue,
    singleLine: Boolean,
    isInputValid: (String) -> Boolean,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // The caller's persisted value initializes a separate draft for this dialog session.
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(initialTextFieldValue)
    }
    var submitted by remember { mutableStateOf(false) }
    var sessionWasVisible by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    SideEffect {
        if (!visible) sessionWasVisible = false
    }
    fun submit() {
        if (visible && !submitted && isInputValid(value.text)) {
            submitted = true
            onDone(value.text)
            onDismiss()
        }
    }
    GlasmioAlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = "",
        titleContent = title,
        buttons = listOf(
            GlasmioAlertButtonSpec(
                stringResource(android.R.string.cancel),
                onDismiss,
                GlasmioAlertButtonRole.Cancel,
            ),
            GlasmioAlertButtonSpec(
                stringResource(android.R.string.ok),
                ::submit,
                enabled = isInputValid(value.text) && (!visible || !submitted),
            ),
        ),
    ) {
        GlasmioAlertFieldGroup {
            GlasmioAlertTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.focusRequester(focus),
                singleLine = singleLine,
                keyboardOptions = KeyboardOptions(
                    imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
        }
        LaunchedEffect(visible) {
            if (visible) {
                if (!sessionWasVisible) {
                    value = initialTextFieldValue
                    submitted = false
                    sessionWasVisible = true
                }
                focus.requestFocus()
            }
        }
    }
}
