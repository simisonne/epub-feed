package com.epubfeed.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubfeed.domain.model.Block
import com.epubfeed.domain.model.BlockType
import com.epubfeed.domain.model.FeedPost
import com.epubfeed.ui.theme.LocalXColors
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private val AvatarSize = 40.dp
private val Gutter = 12.dp

/**
 * One post in the timeline. Matches X's reply thread layout: avatar column on the
 * left (with the 2dp thread line), header, body and the action row underneath.
 */
@Composable
fun PostCard(
    post: FeedPost,
    displayName: String,
    handle: String,
    coverPath: String?,
    hue: Int,
    showThreadLine: Boolean,
    showChapterDivider: Boolean,
    readingFontSize: TextUnit,
    readingFontFamily: FontFamily,
    viewsLabel: String,
    onLike: () -> Unit,
    onBookmark: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onOpen: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val x = LocalXColors.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val likeScale = remember { Animatable(1f) }

    Column(modifier.fillMaxWidth()) {
        if (showChapterDivider) {
            HorizontalDivider(color = x.divider, thickness = 1.dp)
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .pointerInput(post.id, onOpen) {
                    detectTapGestures(
                        onTap = { onOpen?.invoke() },
                        onLongPress = { onCopy() },
                    )
                },
        ) {
            Column(
                modifier = Modifier
                    .width(AvatarSize)
                    .fillMaxHeight()
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BookAvatar(
                    coverPath = coverPath,
                    title = displayName,
                    hue = hue,
                    size = AvatarSize,
                    contentDescription = null,
                )
                if (showThreadLine) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .width(2.dp)
                            .background(x.threadLine),
                    )
                }
            }

            Spacer(Modifier.width(Gutter))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 12.dp, end = 16.dp, bottom = 4.dp),
            ) {
                Header(
                    displayName = displayName,
                    handle = handle,
                    position = "Ch. ${post.chapterIndex + 1} \u00B7 ${post.indexInChapter + 1}",
                )
                Spacer(Modifier.height(4.dp))
                PostBody(
                    blocks = post.blocks,
                    fontSize = readingFontSize,
                    fontFamily = readingFontFamily,
                    isChapterTitlePost = post.isChapterTitlePost,
                )
                Spacer(Modifier.height(8.dp))
                ActionRow(
                    replyCount = (post.indexInChapter + 1).toString(),
                    viewsLabel = viewsLabel,
                    liked = post.liked,
                    bookmarked = post.bookmarked,
                    likeScale = { likeScale.value },
                    onLike = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLike()
                        scope.launch {
                            likeScale.animateTo(1.3f, tween(90))
                            likeScale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
                        }
                    },
                    onBookmark = onBookmark,
                    onShare = onShare,
                )
            }
        }
    }
}

@Composable
private fun Header(displayName: String, handle: String, position: String) {
    val x = LocalXColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = displayName,
            color = x.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = FeedIcons.Verified,
            contentDescription = "Verified book",
            tint = x.accent,
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(15.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "@$handle",
            color = x.muted,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(2f, fill = false),
        )
        Text(
            text = " \u00B7 $position",
            color = x.muted,
            fontSize = 15.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun PostBody(
    blocks: List<Block>,
    fontSize: TextUnit,
    fontFamily: FontFamily,
    isChapterTitlePost: Boolean,
) {
    val x = LocalXColors.current
    val lineHeight = fontSize * 1.38f

    Column {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            when (block.type) {
                BlockType.QUOTE -> QuoteCard(block, fontFamily, fontSize * 0.95f, lineHeight)
                BlockType.HEADING -> Text(
                    text = block.annotated(),
                    color = x.text,
                    fontSize = fontSize * 1.12f,
                    lineHeight = lineHeight * 1.12f,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                )
                BlockType.PARAGRAPH -> Text(
                    text = block.annotated(),
                    color = x.text,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    fontWeight = if (isChapterTitlePost) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = fontFamily,
                )
            }
        }
    }
}

@Composable
private fun QuoteCard(block: Block, fontFamily: FontFamily, fontSize: TextUnit, lineHeight: TextUnit) {
    val x = LocalXColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, x.divider, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = block.annotated(),
            color = x.text,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontFamily = fontFamily,
            fontStyle = FontStyle.Italic,
        )
    }
}

private fun Block.annotated(): AnnotatedString = buildAnnotatedString {
    runs.forEach { run ->
        val style = SpanStyle(
            fontStyle = if (run.italic) FontStyle.Italic else null,
            fontWeight = if (run.bold) FontWeight.Bold else null,
        )
        withStyle(style) { append(run.text) }
    }
}

@Composable
private fun ActionRow(
    replyCount: String,
    viewsLabel: String,
    liked: Boolean,
    bookmarked: Boolean,
    likeScale: () -> Float,
    onLike: () -> Unit,
    onBookmark: () -> Unit,
    onShare: () -> Unit,
) {
    val x = LocalXColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionCell(
            icon = FeedIcons.Reply,
            label = replyCount,
            contentDescription = "Replies, post $replyCount in chapter",
            tint = x.muted,
            onClick = {},
        )
        ActionCell(
            icon = FeedIcons.Repost,
            label = null,
            contentDescription = "Repost",
            tint = x.muted,
            onClick = {},
        )
        ActionCell(
            icon = if (liked) FeedIcons.LikeFilled else FeedIcons.LikeOutline,
            label = null,
            contentDescription = if (liked) "Unlike post" else "Like post",
            tint = if (liked) x.like else x.muted,
            onClick = onLike,
            iconModifier = Modifier.graphicsLayer {
                scaleX = likeScale()
                scaleY = likeScale()
            },
        )
        ActionCell(
            icon = FeedIcons.Views,
            label = viewsLabel,
            contentDescription = "Views, $viewsLabel of the book read",
            tint = x.muted,
            onClick = {},
        )
        ActionCell(
            icon = if (bookmarked) FeedIcons.BookmarkFilled else FeedIcons.BookmarkOutline,
            label = null,
            contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark post",
            tint = if (bookmarked) x.accent else x.muted,
            onClick = onBookmark,
        )
        ActionCell(
            icon = FeedIcons.Share,
            label = null,
            contentDescription = "Share post",
            tint = x.muted,
            onClick = onShare,
            alignEnd = true,
        )
    }
}

@Composable
private fun RowScope.ActionCell(
    icon: ImageVector,
    label: String?,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    iconModifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(18.dp)
                .then(iconModifier),
        )
        if (label != null) {
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                color = tint,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
    }
}
