package com.epubfeed.ui.words

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubfeed.data.local.BookEntity
import com.epubfeed.domain.model.Word
import com.epubfeed.domain.model.WordOrder
import com.epubfeed.ui.components.BookAvatar
import com.epubfeed.ui.components.EpubFeedAvatar
import com.epubfeed.ui.components.FeedIcons
import com.epubfeed.ui.theme.LocalXColors

/** Typing a word in by hand, with an optional definition of your own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWordSheet(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    val x = LocalXColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var term by remember { mutableStateOf("") }
    var definition by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = x.background,
        contentColor = x.text,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = x.divider) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(text = "Add a word", color = x.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            FieldLabel("Word")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(x.elevated, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (term.isEmpty()) {
                    Text("Word or phrase", color = x.muted, fontSize = 16.sp)
                }
                BasicTextField(
                    value = term,
                    onValueChange = { term = it.take(60) },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = x.text,
                        fontSize = 16.sp,
                    ),
                    cursorBrush = SolidColor(x.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }

            Spacer(Modifier.height(14.dp))
            FieldLabel("Definition, optional")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(x.elevated, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (definition.isEmpty()) {
                    Text("What the word means to you", color = x.muted, fontSize = 16.sp)
                }
                BasicTextField(
                    value = definition,
                    onValueChange = { definition = it.take(300) },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = x.text,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                    ),
                    cursorBrush = SolidColor(x.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .background(
                        if (term.isBlank()) x.accent.copy(alpha = 0.4f) else x.accent,
                        RoundedCornerShape(999.dp),
                    )
                    .clickable(enabled = term.isNotBlank()) { onSave(term, definition) },
                contentAlignment = Alignment.Center,
            ) {
                Text("Save", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Everything known about one saved word, and the three things you can do with it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordDetailSheet(
    word: Word,
    book: BookEntity?,
    onOpenBook: () -> Unit,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val x = LocalXColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = x.background,
        contentColor = x.text,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = x.divider) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = word.term,
                    color = x.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                word.rarity?.let { Text(text = it.label, color = x.muted, fontSize = 13.sp) }
            }

            if (word.definition.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = listOfNotNull(word.partOfSpeech, word.definition).joinToString(". "),
                    color = x.text,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
            }

            if (word.synonyms.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    word.synonyms.forEach { synonym ->
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

            val source = word.bookTitle
            if (source != null || word.chapterIndex != null) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (word.bookId == null) {
                        EpubFeedAvatar(size = 26.dp)
                    } else {
                        BookAvatar(
                            avatarPath = book?.avatarSource,
                            title = source ?: WordOrder.CUSTOM_GROUP,
                            hue = word.bookHue ?: 210,
                            size = 26.dp,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = buildString {
                            append(source ?: WordOrder.CUSTOM_GROUP)
                            word.chapterIndex?.let { append(", chapter ${it + 1}") }
                        },
                        color = x.muted,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            word.excerpt?.takeIf { it.isNotBlank() }?.let { excerpt ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "\u201C$excerpt\u201D",
                    color = x.muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = x.divider, thickness = 1.dp)

            if (word.bookId != null) {
                SheetRow(
                    title = "Open the book",
                    subtitle = "Continue where you left off",
                    icon = FeedIcons.Share,
                    onClick = onOpenBook,
                )
            }
            SheetRow(
                title = "Look up again",
                subtitle = "Ask the dictionary for a fresh definition",
                icon = FeedIcons.Reply,
                onClick = onRefresh,
            )
            SheetRow(
                title = "Remove",
                subtitle = "Take this word off the list",
                icon = FeedIcons.BookmarkOutline,
                danger = true,
                onClick = onRemove,
            )
        }
    }
}

@Composable
private fun SheetRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val x = LocalXColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (danger) x.like else x.text,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                color = if (danger) x.like else x.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, color = x.muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    val x = LocalXColors.current
    Text(
        text = text,
        color = x.muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}
