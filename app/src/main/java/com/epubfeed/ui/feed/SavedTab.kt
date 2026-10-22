package com.epubfeed.ui.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubfeed.domain.model.FeedPost
import com.epubfeed.ui.components.EmptyState
import com.epubfeed.ui.components.FeedIcons
import com.epubfeed.ui.components.PostCard
import com.epubfeed.ui.theme.LocalXColors

@Composable
fun SavedTab(
    posts: List<FeedPost>,
    displayName: String,
    handle: String,
    coverPath: String?,
    hue: Int,
    readingFontSize: TextUnit,
    readingFontFamily: FontFamily,
    totalPosts: Int,
    onLike: (FeedPost) -> Unit,
    onBookmark: (FeedPost) -> Unit,
    onShare: (FeedPost) -> Unit,
    onCopy: (FeedPost) -> Unit,
    onOpen: (FeedPost) -> Unit,
    modifier: Modifier = Modifier,
) {
    val x = LocalXColors.current
    if (posts.isEmpty()) {
        EmptyState(
            title = "No saved posts yet",
            message = "Tap the bookmark on any post and it will wait for you here, on this device only.",
            icon = FeedIcons.BookmarkOutline,
        )
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item(key = "saved-header") {
            Column {
                Text(
                    text = "${posts.size} saved ${if (posts.size == 1) "post" else "posts"}",
                    color = x.muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                )
            }
        }
        items(posts, key = { "saved-" + it.id }) { post ->
            PostCard(
                post = post,
                displayName = displayName,
                handle = handle,
                coverPath = coverPath,
                hue = hue,
                showThreadLine = false,
                showChapterDivider = false,
                readingFontSize = readingFontSize,
                readingFontFamily = readingFontFamily,
                viewsLabel = percentLabel(post.globalIndex, totalPosts),
                onLike = { onLike(post) },
                onBookmark = { onBookmark(post) },
                onShare = { onShare(post) },
                onCopy = { onCopy(post) },
                onOpen = { onOpen(post) },
            )
            HorizontalDivider(color = x.divider, thickness = 1.dp)
        }
        item(key = "saved-bottom") { Spacer(Modifier.height(96.dp)) }
    }
}

fun percentLabel(index: Int, total: Int): String {
    if (total <= 0) return "0%"
    return "${((index + 1) * 100 / total).coerceIn(0, 100)}%"
}
