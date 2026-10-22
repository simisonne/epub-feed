package com.epubfeed.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubfeed.domain.model.ChapterSummary
import com.epubfeed.ui.theme.LocalXColors

/** Chapter list with post counts and how far the reader has got inside each chapter. */
@Composable
fun ChaptersTab(
    chapters: List<ChapterSummary>,
    lastPostIndex: Int,
    onChapterClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val x = LocalXColors.current
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
                    .clickable { onChapterClick(chapter.firstIndex) }
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
}

fun chapterProgress(chapter: ChapterSummary, lastPostIndex: Int): Float {
    if (chapter.lastIndex < chapter.firstIndex) return 0f
    if (lastPostIndex >= chapter.lastIndex) return 1f
    if (lastPostIndex <= chapter.firstIndex) return 0f
    val span = (chapter.lastIndex - chapter.firstIndex).coerceAtLeast(1)
    return ((lastPostIndex - chapter.firstIndex).toFloat() / span).coerceIn(0f, 1f)
}
