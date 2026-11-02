package io.github.neoruaa.glasmio.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonRole
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec
import io.github.neoruaa.glasmio.ui.GlasmioAlertDialog
import io.github.neoruaa.glasmio.ui.GlasmioAlertFieldGroup
import io.github.neoruaa.glasmio.ui.GlasmioAlertTextField
import io.github.neoruaa.glasmio.ui.GlasmioButton
import io.github.neoruaa.glasmio.ui.GlasmioCascadingMenuItem
import io.github.neoruaa.glasmio.ui.GlasmioMessageBubble
import io.github.neoruaa.glasmio.ui.GlasmioMessageContextMenu
import io.github.neoruaa.glasmio.ui.GlasmioMessageReaction
import io.github.neoruaa.glasmio.ui.GlasmioMessageReactionArtwork
import io.github.neoruaa.glasmio.ui.MessageBubbleDefaults

private data class DemoMessage(
    val id: Int,
    val outgoing: Boolean,
    val text: String,
    val resource: Boolean = false,
    val replyTo: String? = null,
    val reaction: String? = null,
    val pinned: Boolean = false,
)

private fun initialMessages() = listOf(
    DemoMessage(1, false, "Are we still meeting at 6?"),
    DemoMessage(2, true, "Yes, see you there."),
    DemoMessage(3, true, "Here's the playlist for tonight."),
    DemoMessage(4, true, "Evening Mix", resource = true),
    DemoMessage(5, false, "Perfect — thank you!\nI'll bring the speakers and something to eat. Let's meet outside the station."),
    DemoMessage(6, true, "👍"),
)

private val MessageReactions = listOf(
    GlasmioMessageReaction("love", "Love", "❤️", GlasmioMessageReactionArtwork.Heart),
    GlasmioMessageReaction("like", "Like", "👍", GlasmioMessageReactionArtwork.ThumbsUp),
    GlasmioMessageReaction("dislike", "Dislike", "👎", GlasmioMessageReactionArtwork.ThumbsDown),
    GlasmioMessageReaction("laugh", "Laugh", "HA\nHA", GlasmioMessageReactionArtwork.Laugh),
    GlasmioMessageReaction("emphasize", "Emphasize", "‼️", GlasmioMessageReactionArtwork.Exclamation),
    GlasmioMessageReaction("question", "Question", "❓", GlasmioMessageReactionArtwork.Question),
    GlasmioMessageReaction("star", "Star", "🌟"),
)

@Composable
internal fun MessageBubblesDemo(
    state: LazyListState,
    padding: PaddingValues,
    header: @Composable () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val messages = remember { mutableStateListOf<DemoMessage>().apply { addAll(initialMessages()) } }
    val customReactions = remember { mutableStateMapOf<Int, GlasmioMessageReaction>() }
    var nextId by remember { mutableIntStateOf(7) }
    var expandedId by remember { mutableStateOf<Int?>(null) }
    var editorMessageId by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf(false) }
    var choosingReaction by remember { mutableStateOf(false) }
    var quotedMessage by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf(TextFieldValue()) }

    fun openEditor(message: DemoMessage, edit: Boolean) {
        editorMessageId = message.id
        editing = edit
        choosingReaction = false
        quotedMessage = message.text
        draft = TextFieldValue(if (edit) message.text else "")
    }

    fun updateMessage(id: Int, update: (DemoMessage) -> DemoMessage) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) messages[index] = update(messages[index])
    }

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { header() }
        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
            val showTail = messages.getOrNull(index + 1)?.outgoing != message.outgoing
            val actions = buildList {
                add(GlasmioCascadingMenuItem("Reply", "arrowshape.turn.up.left", onClick = { openEditor(message, false) }))
                if (message.outgoing && !message.resource) {
                    add(GlasmioCascadingMenuItem("Edit", "pencil", onClick = { openEditor(message, true) }))
                }
                add(GlasmioCascadingMenuItem("Copy", "document.on.clipboard", onClick = {
                    clipboard.setText(AnnotatedString(if (message.resource) "${message.text}\n12 songs" else message.text))
                }))
                add(GlasmioCascadingMenuItem(
                    if (message.pinned) "Unpin" else "Pin",
                    if (message.pinned) "pin.slash" else "pin",
                    onClick = { updateMessage(message.id) { it.copy(pinned = !it.pinned) } },
                ))
                add(GlasmioCascadingMenuItem("Delete", "trash", destructive = true, separatorBefore = true, onClick = {
                    messages.removeAll { it.id == message.id }
                    customReactions.remove(message.id)
                }))
            }
            DemoMessageRow(
                message = message,
                showTail = showTail,
                expanded = expandedId == message.id,
                onExpandedChange = { expanded ->
                    if (expanded) expandedId = message.id else if (expandedId == message.id) expandedId = null
                },
                actions = actions,
                reactions = MessageReactions.take(6) + (customReactions[message.id] ?: MessageReactions.last()),
                onMoreReactions = {
                    editorMessageId = message.id
                    editing = false
                    choosingReaction = true
                    draft = TextFieldValue(customReactions[message.id]?.emoji.orEmpty())
                },
                onReaction = { reaction ->
                    updateMessage(message.id) { it.copy(reaction = reaction.id.takeUnless { id -> id == it.reaction }) }
                },
            )
        }
        item {
            GlasmioButton(
                onClick = {
                    expandedId = null
                    messages.clear()
                    messages.addAll(initialMessages())
                    customReactions.clear()
                    nextId = 7
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text("Reset Messages") }
        }
    }
    GlasmioAlertDialog(
        visible = editorMessageId != null,
        onDismissRequest = { editorMessageId = null },
        title = if (choosingReaction) "Add Reaction" else if (editing) "Edit Message" else "Reply",
        message = quotedMessage.takeUnless { editing || choosingReaction },
        buttons = listOf(
            GlasmioAlertButtonSpec("Cancel", { editorMessageId = null }, GlasmioAlertButtonRole.Cancel),
            GlasmioAlertButtonSpec(if (choosingReaction) "Add" else if (editing) "Save" else "Send", {
                val index = messages.indexOfFirst { it.id == editorMessageId }
                if (index >= 0) {
                    if (choosingReaction) {
                        val emoji = draft.text.trim()
                        val reaction = GlasmioMessageReaction("custom:$emoji", emoji, emoji)
                        customReactions[messages[index].id] = reaction
                        messages[index] = messages[index].copy(reaction = reaction.id)
                    } else if (editing) messages[index] = messages[index].copy(text = draft.text)
                    else messages.add(DemoMessage(nextId++, true, draft.text, replyTo = messages[index].text))
                }
                editorMessageId = null
            }, enabled = draft.text.isNotBlank()),
        ),
    ) {
        GlasmioAlertFieldGroup {
            GlasmioAlertTextField(draft, { draft = it }, placeholder = if (choosingReaction) "Emoji" else "Message")
        }
    }
}

@Composable
private fun DemoMessageRow(
    message: DemoMessage,
    showTail: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: List<GlasmioCascadingMenuItem>,
    reactions: List<GlasmioMessageReaction>,
    onMoreReactions: () -> Unit,
    onReaction: (GlasmioMessageReaction) -> Unit,
) {
    val colors = LocalGlassColors.current
    val reaction = reactions.firstOrNull { it.id == message.reaction }
    val shape = MessageBubbleDefaults.shape(message.outgoing, showTail)
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            GlasmioMessageContextMenu(
                outgoing = message.outgoing,
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                actions = actions,
                reactions = reactions,
                onReaction = onReaction,
                onMoreReactions = onMoreReactions,
                selectedReaction = message.reaction,
                shape = shape,
                anchor = {
                    GlasmioMessageBubble(
                        outgoing = message.outgoing,
                        modifier = Modifier.widthIn(max = maxWidth * 0.80f),
                        showTail = showTail,
                    ) {
                        message.replyTo?.let { Text(it, style = IosTypography.caption, maxLines = 1) }
                        if (message.resource) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                SfIcon("music.note", null, size = 36.dp)
                                Column {
                                    Text(message.text, style = IosTypography.headline)
                                    Text("12 songs", style = IosTypography.subheadline)
                                }
                            }
                        } else Text(message.text)
                    }
                },
            )
        }
        val status = listOfNotNull("Pinned".takeIf { message.pinned }, reaction?.label).joinToString(" · ")
        if (status.isNotEmpty()) {
            Text(
                status,
                style = IosTypography.caption,
                color = colors.secondaryContent,
                modifier = Modifier.padding(horizontal = 12.dp).semantics { contentDescription = status },
            )
        }
    }
}
