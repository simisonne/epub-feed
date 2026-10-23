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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubfeed.domain.model.FeedPost
import com.epubfeed.domain.model.Note
import com.epubfeed.ui.components.EmptyState
import com.epubfeed.ui.components.FeedIcons
import com.epubfeed.ui.components.PostCard
import com.epubfeed.ui.theme.LocalXColors

/**
 * The Likes and Saved tabs are the same list with a different query behind it, so one
 * composable serves both. Tapping a post jumps the feed back to it.
 */
@Composable
fun PostCollectionTab(
    posts: List<FeedPost>,
    notes: Map<String, List<Note>>,
    headerText: String,
    emptyTitle: String,
    emptyMessage: String,
    emptyIcon: ImageVector,
    displayName: String,
    handle: String,
    coverPath: String?,
    hue: Int,
    readingFontSize: TextUnit,
    readingFontFamily: FontFamily,
    totalPosts: Int,
    onLike: (FeedPost) -> Unit,
    onBookmark: (FeedPost) -> Unit,
    onComment: (FeedPost) -> Unit,
    onShare: (FeedPost) -> Unit,
    onCopy: (FeedPost) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onAvatarClick: () -> Unit,
    onLongPress: (FeedPost) -> Unit,
    onOpen: (FeedPost) -> Unit,
    modifier: Modifier = Modifier,
) {
    val x = LocalXColors.current
    if (posts.isEmpty()) {
        EmptyState(title = emptyTitle, message = emptyMessage, icon = emptyIcon)
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item(key = "collection-header") {
            Column {
                Text(
                    text = headerText,
                    color = x.muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                )
            }
        }
        items(posts, key = { "collection-" + it.id }) { post ->
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
                notes = notes[post.id].orEmpty(),
                onLike = { onLike(post) },
                onBookmark = { onBookmark(post) },
                onComment = { onComment(post) },
                onShare = { onShare(post) },
                onCopy = { onCopy(post) },
                onDeleteNote = onDeleteNote,
                onAvatarClick = onAvatarClick,
                onLongPress = { onLongPress(post) },
                onOpen = { onOpen(post) },
            )
            HorizontalDivider(color = x.divider, thickness = 1.dp)
        }
        item(key = "collection-bottom") { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
fun LikedTab(
    posts: List<FeedPost>,
    notes: Map<String, List<Note>>,
    displayName: String,
    handle: String,
    coverPath: String?,
    hue: Int,
    readingFontSize: TextUnit,
    readingFontFamily: FontFamily,
    totalPosts: Int,
    onLike: (FeedPost) -> Unit,
    onBookmark: (FeedPost) -> Unit,
    onComment: (FeedPost) -> Unit,
    onShare: (FeedPost) -> Unit,
    onCopy: (FeedPost) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onAvatarClick: () -> Unit,
    onLongPress: (FeedPost) -> Unit,
    onOpen: (FeedPost) -> Unit,
) = PostCollectionTab(
    posts = posts,
    notes = notes,
    headerText = "${posts.size} liked ${if (posts.size == 1) "post" else "posts"}",
    emptyTitle = "No liked posts yet",
    emptyMessage = "Tap the heart on any post and the ones you loved collect here, on this device only.",
    emptyIcon = FeedIcons.LikeOutline,
    displayName = displayName,
    handle = handle,
    coverPath = coverPath,
    hue = hue,
    readingFontSize = readingFontSize,
    readingFontFamily = readingFontFamily,
    totalPosts = totalPosts,
    onLike = onLike,
    onBookmark = onBookmark,
    onComment = onComment,
    onShare = onShare,
    onCopy = onCopy,
    onDeleteNote = onDeleteNote,
    onAvatarClick = onAvatarClick,
    onLongPress = onLongPress,
    onOpen = onOpen,
)

@Composable
fun SavedTab(
    posts: List<FeedPost>,
    notes: Map<String, List<Note>>,
    displayName: String,
    handle: String,
    coverPath: String?,
    hue: Int,
    readingFontSize: TextUnit,
    readingFontFamily: FontFamily,
    totalPosts: Int,
    onLike: (FeedPost) -> Unit,
    onBookmark: (FeedPost) -> Unit,
    onComment: (FeedPost) -> Unit,
    onShare: (FeedPost) -> Unit,
    onCopy: (FeedPost) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onAvatarClick: () -> Unit,
    onLongPress: (FeedPost) -> Unit,
    onOpen: (FeedPost) -> Unit,
) = PostCollectionTab(
    posts = posts,
    notes = notes,
    headerText = "${posts.size} saved ${if (posts.size == 1) "post" else "posts"}",
    emptyTitle = "No saved posts yet",
    emptyMessage = "Tap the bookmark on any post and it will wait for you here, on this device only.",
    emptyIcon = FeedIcons.BookmarkOutline,
    displayName = displayName,
    handle = handle,
    coverPath = coverPath,
    hue = hue,
    readingFontSize = readingFontSize,
    readingFontFamily = readingFontFamily,
    totalPosts = totalPosts,
    onLike = onLike,
    onBookmark = onBookmark,
    onComment = onComment,
    onShare = onShare,
    onCopy = onCopy,
    onDeleteNote = onDeleteNote,
    onAvatarClick = onAvatarClick,
    onLongPress = onLongPress,
    onOpen = onOpen,
)

fun percentLabel(index: Int, total: Int): String {
    if (total <= 0) return "0%"
    return "${((index + 1) * 100 / total).coerceIn(0, 100)}%"
}
