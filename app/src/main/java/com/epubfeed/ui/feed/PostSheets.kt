package com.epubfeed.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.epubfeed.domain.model.FeedPost
import com.epubfeed.domain.model.Note
import com.epubfeed.ui.components.BookAvatar
import com.epubfeed.ui.components.FeedIcons
import com.epubfeed.ui.components.relativeTime
import com.epubfeed.ui.theme.LocalXColors

private const val NOTE_LIMIT = 280

/**
 * The note composer: X's reply sheet, with the post you are replying to above the field,
 * a 280 character budget and the notes you already wrote underneath.
 */
@Composable
fun NoteComposer(
    post: FeedPost,
    displayName: String,
    avatarPath: String?,
    hue: Int,
    existing: List<Note>,
    onDismiss: () -> Unit,
    onPost: (String) -> Unit,
) {
    val x = LocalXColors.current
    var value by remember { mutableStateOf(TextFieldValue("")) }
    val focus = remember { FocusRequester() }
    val remaining = NOTE_LIMIT - value.text.length

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(x.background)
                .statusBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cancel note",
                        tint = x.text,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Add a note",
                    color = x.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                val enabled = value.text.isNotBlank() && remaining >= 0
                Box(
                    modifier = Modifier
                        .background(
                            if (enabled) x.accent else x.accent.copy(alpha = 0.4f),
                            RoundedCornerShape(999.dp),
                        )
                        .clickable(enabled = enabled) { onPost(value.text) }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Post",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            HorizontalDivider(color = x.divider, thickness = 1.dp)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row {
                    BookAvatar(
                        avatarPath = avatarPath,
                        title = displayName,
                        hue = hue,
                        size = 40.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayName,
                                color = x.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Text(
                                text = " \u00B7 Ch. ${post.chapterIndex + 1}",
                                color = x.muted,
                                fontSize = 14.sp,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = post.plainText,
                            color = x.muted,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            maxLines = 6,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = "Post your note",
                            color = x.muted,
                            fontSize = 17.sp,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { next ->
                            value = if (next.text.length > NOTE_LIMIT) {
                                TextFieldValue(next.text.take(NOTE_LIMIT), TextRange(next.text.take(NOTE_LIMIT).length))
                            } else {
                                next
                            }
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = x.text,
                            fontSize = 17.sp,
                            lineHeight = 23.sp,
                        ),
                        cursorBrush = SolidColor(x.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = if (remaining < 0) "$remaining" else "$remaining left",
                        color = if (remaining < 40) x.like else x.muted,
                        fontSize = 13.sp,
                        fontWeight = if (remaining < 40) FontWeight.Bold else FontWeight.Normal,
                    )
                }

                if (existing.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = x.divider, thickness = 1.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (existing.size == 1) "1 note on this post" else "${existing.size} notes on this post",
                        color = x.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    existing.forEach { note ->
                        Column(Modifier.padding(top = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("You", color = x.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(" \u00B7 ${relativeTime(note.createdAt)}", color = x.muted, fontSize = 12.sp)
                            }
                            Text(
                                text = note.text,
                                color = x.text,
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Long press on a post: the chapter tools, a note shortcut and copy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostActionsSheet(
    post: FeedPost,
    notes: List<Note>,
    chapterStart: Boolean,
    canUnmark: Boolean,
    onMark: () -> Unit,
    onUnmark: () -> Unit,
    onCopy: () -> Unit,
    onNote: () -> Unit,
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
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = post.plainText.take(90) + if (post.plainText.length > 90) "\u2026" else "",
                color = x.muted,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            HorizontalDivider(color = x.divider, thickness = 1.dp)
            if (chapterStart) {
                SheetAction(
                    title = "Unmark as chapter",
                    subtitle = if (canUnmark) {
                        "Merge this chapter back into the one before it"
                    } else {
                        "This is the first chapter of the book"
                    },
                    icon = FeedIcons.BookmarkOutline,
                    enabled = canUnmark,
                    onClick = onUnmark,
                )
            } else {
                SheetAction(
                    title = "Mark as chapter",
                    subtitle = "Start a new chapter at this post",
                    icon = FeedIcons.BookmarkFilled,
                    enabled = true,
                    onClick = onMark,
                )
            }
            SheetAction(
                title = if (notes.isEmpty()) "Add a note" else "Add another note",
                subtitle = "Write up to 280 characters under this post",
                icon = FeedIcons.Reply,
                enabled = true,
                onClick = onNote,
            )
            SheetAction(
                title = "Copy text",
                subtitle = "Copy this post with its attribution",
                icon = FeedIcons.Share,
                enabled = true,
                onClick = onCopy,
            )
        }
    }
}

@Composable
private fun SheetAction(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val x = LocalXColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) x.text else x.muted.copy(alpha = 0.5f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                color = if (enabled) x.text else x.muted.copy(alpha = 0.6f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = x.muted,
                fontSize = 13.sp,
            )
        }
    }
}
