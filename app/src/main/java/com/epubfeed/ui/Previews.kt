package com.epubfeed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.epubfeed.domain.model.Block
import com.epubfeed.domain.model.BlockType
import com.epubfeed.domain.model.ChapterSummary
import com.epubfeed.domain.model.FeedPost
import com.epubfeed.domain.model.Note
import com.epubfeed.domain.model.ReadingFont
import com.epubfeed.domain.model.Run
import com.epubfeed.domain.model.ThemeMode
import com.epubfeed.ui.components.PostCard
import com.epubfeed.ui.feed.ChaptersTab
import com.epubfeed.ui.feed.FeedTopBar
import com.epubfeed.ui.theme.EpubFeedTheme
import com.epubfeed.ui.theme.LocalXColors
import com.epubfeed.ui.theme.readingFontFamily

private val samplePost = FeedPost(
    id = "preview-1",
    chapterIndex = 0,
    indexInChapter = 3,
    globalIndex = 4,
    isChapterTitlePost = false,
    blocks = listOf(
        Block(
            BlockType.PARAGRAPH,
            listOf(
                Run("\u201CMy dear Mr. Bennet,\u201D said his lady to him one day, \u201Chave you heard that "),
                Run("Netherfield Park", italic = true),
                Run(" is let at last?\u201D"),
            ),
        ),
        Block(BlockType.QUOTE, listOf(Run("\u201CIt is a truth universally acknowledged\u2026\u201D"))),
    ),
    liked = true,
    bookmarked = false,
    plainText = "Preview post body.",
)

private val sampleChapters = listOf(
    ChapterSummary("c1", 0, "Chapter I", 14, 0, 13),
    ChapterSummary("c2", 1, "Chapter II", 22, 14, 35),
    ChapterSummary("c3", 2, "Chapter III", 18, 36, 53),
)

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    val x = LocalXColors.current
    Column(Modifier.fillMaxWidth().background(x.background)) { content() }
}

@Preview(name = "Post - lights out", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PostLightsOutPreview() {
    EpubFeedTheme(ThemeMode.LIGHTS_OUT) {
        PreviewSurface {
            PostCard(
                post = samplePost,
                displayName = "Pride and Prejudice",
                handle = "jane_austen",
                avatarPath = null,
                hue = 205,
                showThreadLine = true,
                showChapterDivider = false,
                readingFontSize = 17.sp,
                readingFontFamily = readingFontFamily(ReadingFont.SANS),
                viewsLabel = "8%",
                notes = emptyList(),
                onLike = {},
                onBookmark = {},
                onComment = {},
                onShare = {},
                onCopy = {},
                onDeleteNote = {},
                onAvatarClick = {},
                onLongPress = {},
            )
        }
    }
}

@Preview(name = "Top bar", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FeedTopBarPreview() {
    EpubFeedTheme(ThemeMode.LIGHTS_OUT) {
        PreviewSurface {
            FeedTopBar(
                title = "Pride and Prejudice",
                author = "Jane Austen",
                avatarPath = null,
                hue = 205,
                progress = 0.34f,
                selectedTab = 0,
                onAvatarClick = {},
                onSettingsClick = {},
                onTabSelected = {},
            )
        }
    }
}

@Preview(name = "Chapters", showBackground = true, backgroundColor = 0xFF151515)
@Composable
private fun ChaptersPreview() {
    EpubFeedTheme(ThemeMode.DIM) {
        PreviewSurface {
            ChaptersTab(chapters = sampleChapters, lastPostIndex = 20, onChapterClick = {}, onUnmark = {})
        }
    }
}

@Preview(name = "Post - light", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun PostLightPreview() {
    EpubFeedTheme(ThemeMode.LIGHT) {
        PreviewSurface {
            PostCard(
                post = samplePost.copy(liked = false, bookmarked = true),
                displayName = "Pride and Prejudice",
                handle = "jane_austen",
                avatarPath = null,
                hue = 205,
                showThreadLine = false,
                showChapterDivider = true,
                readingFontSize = 20.sp,
                readingFontFamily = readingFontFamily(ReadingFont.SERIF),
                viewsLabel = "12%",
                notes = listOf(Note("n1", samplePost.id, "This is exactly how it feels.", System.currentTimeMillis() - 240_000)),
                onLike = {},
                onBookmark = {},
                onComment = {},
                onShare = {},
                onCopy = {},
                onDeleteNote = {},
                onAvatarClick = {},
                onLongPress = {},
            )
        }
    }
}
