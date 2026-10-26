package com.epubfeed.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubfeed.domain.model.ChapterSummary
import com.epubfeed.ui.components.FeedIcons
import com.epubfeed.ui.theme.LocalXColors

/** Chapter list with post counts and how far the reader has got inside each chapter. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChaptersTab(
    chapters: List<ChapterSummary>,
    lastPostIndex: Int,
    onChapterClick: (Int) -> Unit,
    onUnmark: (ChapterSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val x = LocalXColors.current
    var menuTarget by remember { mutableStateOf<ChapterSummary?>(null) }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item(key = "chapters-header") {
            Text(
                text = if (chapters.size == 1) "1 chapter" else "${chapters.size} chapters",
                color = x.muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
            )
        }
        items(chapters, key = { it.id }) { chapter ->
            val fraction = chapterProgress(chapter, lastPostIndex)
            val read = fraction >= 0.999f
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onChapterClick(chapter.firstIndex) },
                        onLongClick = { menuTarget = chapter },
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (chapter.index + 1).toString(),
                        color = x.muted,
                        fontSize = 13.sp,
                        modifier = Modifier.width(26.dp),
                    )
                    Text(
                        text = chapter.title,
                        color = x.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (read) "Read" else "${(fraction * 100).toInt()}%",
                        color = if (read) x.accent else x.muted,
                        fontSize = 13.sp,
                        fontWeight = if (read) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(x.divider),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(3.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (read) x.accent else x.accent.copy(alpha = 0.75f)),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${chapter.postCount} posts",
                        color = x.muted,
                        fontSize = 13.sp,
                        modifier = Modifier.width(62.dp),
                    )
                }
            }
            HorizontalDivider(color = x.divider, thickness = 1.dp)
        }
        item(key = "chapters-bottom") { Spacer(Modifier.height(96.dp)) }
    }

    menuTarget?.let { chapter ->
        ChapterActionsSheet(
            chapter = chapter,
            canUnmark = chapter.index > 0,
            onOpen = {
                menuTarget = null
                onChapterClick(chapter.firstIndex)
            },
            onUnmark = {
                menuTarget = null
                onUnmark(chapter)
            },
            onDismiss = { menuTarget = null },
        )
    }
}

/** Long press on a chapter: jump into it or fold it back into the chapter before it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterActionsSheet(
    chapter: ChapterSummary,
    canUnmark: Boolean,
    onOpen: () -> Unit,
    onUnmark: () -> Unit,
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
                text = chapter.title,
                color = x.muted,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            HorizontalDivider(color = x.divider, thickness = 1.dp)
            ChapterSheetAction(
                title = "Unmark chapter",
                subtitle = if (canUnmark) {
                    "Merge this chapter back into the one before it"
                } else {
                    "The first chapter of a book has nothing to merge into"
                },
                icon = FeedIcons.BookmarkOutline,
                enabled = canUnmark,
                onClick = onUnmark,
            )
            ChapterSheetAction(
                title = "Open chapter",
                subtitle = "Jump the feed to the start of this chapter",
                icon = FeedIcons.BookmarkFilled,
                enabled = true,
                onClick = onOpen,
            )
        }
    }
}

@Composable
private fun ChapterSheetAction(
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

fun chapterProgress(chapter: ChapterSummary, lastPostIndex: Int): Float {
    if (chapter.lastIndex < chapter.firstIndex) return 0f
    if (lastPostIndex >= chapter.lastIndex) return 1f
    if (lastPostIndex <= chapter.firstIndex) return 0f
    val span = (chapter.lastIndex - chapter.firstIndex).coerceAtLeast(1)
    return ((lastPostIndex - chapter.firstIndex).toFloat() / span).coerceIn(0f, 1f)
}
