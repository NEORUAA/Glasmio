package io.github.neoruaa.glasmio.preference

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.capsule.ContinuousRoundedRectangle
import io.github.neoruaa.glasmio.ui.*

@Composable
internal fun PreferenceListDialog(
    onDismiss: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp / 2
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlasmioCard(Modifier.padding(24.dp)) {
            LazyColumn(Modifier.heightIn(max = maxHeight), content = content)
        }
    }
}

@Composable
internal fun PreferenceTextFieldDialog(
    initialTextFieldValue: TextFieldValue,
    singleLine: Boolean,
    isInputValid: (String) -> Boolean,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initialTextFieldValue) }
    val focus = remember { FocusRequester() }
    fun submit() {
        if (isInputValid(value.text)) {
            onDone(value.text)
            onDismiss()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlasmioCard(Modifier.padding(24.dp)) {
            Column(Modifier.padding(16.dp)) {
                TextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = singleLine,
                    shape = ContinuousRoundedRectangle(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlasmioAlertButton(stringResource(android.R.string.cancel), onDismiss, Modifier.weight(1f), role = GlasmioAlertButtonRole.Cancel)
                    GlasmioAlertButton(stringResource(android.R.string.ok), ::submit, Modifier.weight(1f), enabled = isInputValid(value.text))
                }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
