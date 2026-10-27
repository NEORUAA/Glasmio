package io.github.neoruaa.glasmio.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec
import io.github.neoruaa.glasmio.ui.GlasmioAlertDialog
import io.github.neoruaa.glasmio.ui.GlasmioAlertFieldGroup
import io.github.neoruaa.glasmio.ui.GlasmioAlertTextField
import io.github.neoruaa.glasmio.ui.GlasmioCascadingMenuItem
import io.github.neoruaa.glasmio.ui.GlasmioEditableText

@Composable
internal fun TextEditingDemo(value: MutableState<TextFieldValue>, padding: PaddingValues) {
    val colors = LocalGlassColors.current
    Box(Modifier.fillMaxSize().background(if (colors.isDark) Color.Black else Color.White)
        .padding(padding).consumeWindowInsets(padding).imePadding()) {
        GlasmioEditableText(
            value = value.value,
            onValueChange = { value.value = it },
            textStyle = IosTypography.body.copy(color = colors.content, lineHeight = 20.sp),
            singleLine = false,
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 16.dp)
                .semantics { contentDescription = "Editable text" },
        )
    }
}

internal class TextEditingDemoActions(
    val items: List<GlasmioCascadingMenuItem>,
    val dialog: @Composable () -> Unit,
)

/** Sample extensions edit the same selected range as Foundation's clipboard commands. */
@Composable
@Suppress("DEPRECATION")
internal fun textEditingDemoActions(value: MutableState<TextFieldValue>, backdrop: Backdrop): TextEditingDemoActions {
    val clipboard = LocalClipboardManager.current
    val accent = LocalGlassColors.current.accent
    var linkSelection by remember { mutableStateOf(TextRange.Zero) }
    var linkDialogVisible by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf(TextFieldValue("https://example.com")) }
    fun replace(replacement: String) {
        val current = value.value
        val start = current.selection.min
        val end = current.selection.max
        value.value = TextFieldValue(buildAnnotatedString {
            append(current.annotatedString.subSequence(0, start))
            append(replacement)
            append(current.annotatedString.subSequence(end, current.text.length))
        }, TextRange(start + replacement.length))
    }
    val actions = remember(value, clipboard) {
        fun selected() = value.value.let { it.text.substring(it.selection.min, it.selection.max) }
        listOf(
            GlasmioCascadingMenuItem("Paste and Match\nStyle", "document.on.clipboard", onClick = {
                clipboard.getText()?.text?.let(::replace)
            }),
            GlasmioCascadingMenuItem("Add Link", "link", onClick = {
                linkSelection = value.value.selection
                linkDialogVisible = true
            }),
            GlasmioCascadingMenuItem("Replace…", "character.cursor.ibeam", separatorBefore = true, children = listOf(
                GlasmioCascadingMenuItem("Hello", onClick = { replace("Hello") }),
                GlasmioCascadingMenuItem("Welcome", onClick = { replace("Welcome") }),
            )),
            GlasmioCascadingMenuItem("Writing Tools", "pencil.and.scribble", children = listOf(
                GlasmioCascadingMenuItem("UPPERCASE", onClick = { replace(selected().uppercase()) }),
                GlasmioCascadingMenuItem("lowercase", onClick = { replace(selected().lowercase()) }),
            )),
            GlasmioCascadingMenuItem("AutoFill", "keyboard", children = listOf(
                GlasmioCascadingMenuItem("Name", onClick = { replace("Alex") }),
                GlasmioCascadingMenuItem("Email", onClick = { replace("alex@example.com") }),
            )),
        )
    }
    val dialog: @Composable () -> Unit = {
        GlasmioAlertDialog(
            visible = linkDialogVisible,
            onDismissRequest = { linkDialogVisible = false },
            title = "Add Link",
            backdrop = backdrop,
            buttons = listOf(
                GlasmioAlertButtonSpec("Cancel", { linkDialogVisible = false }),
                GlasmioAlertButtonSpec("Done", {
                    val current = value.value
                    val target = linkSelection.min.coerceIn(0, current.text.length)..linkSelection.max.coerceIn(0, current.text.length)
                    value.value = current.copy(annotatedString = AnnotatedString.Builder(current.annotatedString).apply {
                        addStringAnnotation("URL", link.text, target.first, target.last)
                        addStyle(SpanStyle(color = accent, textDecoration = TextDecoration.Underline), target.first, target.last)
                    }.toAnnotatedString())
                    linkDialogVisible = false
                }),
            ),
        ) {
            GlasmioAlertFieldGroup {
                GlasmioAlertTextField(link, { link = it }, placeholder = "URL")
            }
        }
    }
    return TextEditingDemoActions(actions, dialog)
}
