package com.epubfeed.ui.lookup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.epubfeed.data.local.BookEntity
import com.epubfeed.domain.model.Block
import com.epubfeed.domain.model.FeedPost
import com.epubfeed.domain.thesaurus.LookupOutcome
import com.epubfeed.domain.thesaurus.WordForms
import com.epubfeed.ui.theme.LocalXColors

/**
 * The lookup: the post rendered as a passage you can select in, and one panel at the bottom that
 * goes from "pick a word" to a definition to "saved".
 *
 * The selection is the confirmation. There is no separate edit screen and no confirm dialog:
 * highlighting a word arms one button, and pressing it is the whole decision.
 */
@Composable
fun LookupScreen(
    post: FeedPost,
    book: BookEntity?,
    fontSize: TextUnit,
    fontFamily: FontFamily,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LookupViewModel = hiltViewModel(),
) {
    val x = LocalXColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val passage = remember(post.id) { passageOf(post) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(x.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close the lookup",
                        tint = x.text,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Look up a word",
                        color = x.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = book?.title?.let { "Chapter ${post.chapterIndex + 1} of $it" }
                            ?: "Chapter ${post.chapterIndex + 1}",
                        color = x.muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(color = x.divider, thickness = 1.dp)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                SelectablePassage(
                    text = passage,
                    fontSize = fontSize,
                    fontFamily = fontFamily,
                    onSelection = { term, span ->
                        val excerpt = span?.let { WordForms.sentenceAt(passage, it) }
                        viewModel.select(term, excerpt)
                    },
                )
                Spacer(Modifier.height(24.dp))
            }

            LookupPanel(
                state = state,
                onLookUp = { viewModel.lookUp(post, book) },
                onSave = { viewModel.save(post, book) },
                onSaveWithoutEntry = { viewModel.saveWithoutEntry(post, book) },
                onClear = { viewModel.select("", null) },
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

/**
 * A read only text field instead of a `SelectionContainer`: it is the only way to read back what
 * is selected, and every selection is grown to whole words so the lookup never queries "gloa".
 */
@Composable
private fun SelectablePassage(
    text: String,
    fontSize: TextUnit,
    fontFamily: FontFamily,
    onSelection: (String, com.epubfeed.domain.thesaurus.Span?) -> Unit,
) {
    val x = LocalXColors.current
    var value by remember(text) { mutableStateOf(TextFieldValue(text = text, selection = TextRange.Zero)) }

    BasicTextField(
        value = value,
        onValueChange = { next ->
            val span = if (next.selection.collapsed) {
                null
            } else {
                WordForms.snap(next.text, next.selection.start, next.selection.end)
            }
            val snapped = if (span != null) {
                next.copy(selection = TextRange(span.start, span.end))
            } else {
                next
            }
            value = snapped
            onSelection(span?.let { next.text.substring(it.start, it.end) }.orEmpty(), span)
        },
        readOnly = true,
        textStyle = TextStyle(
            color = x.text,
            fontSize = fontSize,
            lineHeight = fontSize * 1.5f,
            fontFamily = fontFamily,
        ),
        cursorBrush = SolidColor(Color.Transparent),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

@Composable
private fun LookupPanel(
    state: LookupViewModel.UiState,
    onLookUp: () -> Unit,
    onSave: () -> Unit,
    onSaveWithoutEntry: () -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val x = LocalXColors.current
    val outcome = state.outcome
    val nothingYet = outcome == null && !state.loading

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(x.elevated)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        when {
            state.selection.isBlank() && nothingYet -> {
                Text(
                    text = "Press and hold a word in the text to select it.",
                    color = x.muted,
                    fontSize = 14.sp,
                )
            }

            nothingYet -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.selection,
                    color = x.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Pill(label = "Look up", enabled = true, onClick = onLookUp)
            }

            state.loading -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.selection,
                    color = x.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(text = "Looking up\u2026", color = x.muted, fontSize = 14.sp)
            }

            outcome is LookupOutcome.Found -> EntryCard(
                state = state,
                onSave = onSave,
                onClear = onClear,
            )

            outcome is LookupOutcome.Missing && outcome.noDictionary -> MissingCard(
                title = "No dictionary installed yet",
                message = "Download a dictionary in Reading settings, and lookups work offline.",
                action = "Keep the word anyway",
                saved = state.saved,
                onAction = onSaveWithoutEntry,
                onOpenSettings = onOpenSettings,
                onClear = onClear,
            )

            else -> MissingCard(
                title = "No entry for \u201C${(outcome as? LookupOutcome.Missing)?.term.orEmpty()}\u201D",
                message = "The installed dictionary does not hold it.",
                action = "Keep the word anyway",
                saved = state.saved,
                onAction = onSaveWithoutEntry,
                onOpenSettings = null,
                onClear = onClear,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryCard(
    state: LookupViewModel.UiState,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    val x = LocalXColors.current
    val entry = (state.outcome as? LookupOutcome.Found)?.entry ?: return

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.term,
                color = x.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            entry.rarity?.let {
                Text(text = it.label, color = x.muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = listOfNotNull(entry.partOfSpeech, entry.definition).joinToString(". "),
            color = x.text,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
        if (entry.synonyms.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                entry.synonyms.forEach { synonym ->
                    Text(
                        text = synonym,
                        color = x.accent,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .border(1.dp, x.divider, RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
        }
        val uses = state.uses
        if (uses != null && uses.count > 1) {
            Spacer(Modifier.height(10.dp))
            Text(text = uses.label, color = x.muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.saved) {
                Text(text = "Saved to your word list", color = x.muted, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                TextButton(label = "Close", onClick = onClear)
            } else {
                Pill(
                    label = "Save to word list",
                    enabled = true,
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                TextButton(label = "Cancel", onClick = onClear)
            }
        }
    }
}

@Composable
private fun MissingCard(
    title: String,
    message: String,
    action: String,
    saved: Boolean,
    onAction: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    onClear: () -> Unit,
) {
    val x = LocalXColors.current
    Column(Modifier.fillMaxWidth()) {
        Text(text = title, color = x.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(text = message, color = x.muted, fontSize = 14.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Saving is one way: once the word is on the list the button must not stay there and
            // invite a second press.
            if (saved) {
                Text(text = "Saved to your word list", color = x.muted, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
            } else {
                Pill(label = action, enabled = true, onClick = onAction, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
            }
            TextButton(label = "Close", onClick = onClear)
        }
        if (onOpenSettings != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Reading settings",
                color = x.accent,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(onClick = onOpenSettings)
                    .padding(vertical = 6.dp, horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun Pill(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val x = LocalXColors.current
    Box(
        modifier = modifier
            .height(44.dp)
            .background(
                if (enabled) x.accent else x.accent.copy(alpha = 0.4f),
                RoundedCornerShape(999.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TextButton(label: String, onClick: () -> Unit) {
    val x = LocalXColors.current
    Box(
        modifier = Modifier
            .height(44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = x.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The post as one passage: paragraphs separated by a blank line, pictures left out. */
private fun passageOf(post: FeedPost): String = buildString {
    post.blocks.forEach { block ->
        if (block.type != com.epubfeed.domain.model.BlockType.IMAGE && block.text.isNotBlank()) {
            if (isNotEmpty()) append("\n\n")
            append(block.text.trim())
        }
    }
    if (isEmpty()) append(post.plainText)
}
