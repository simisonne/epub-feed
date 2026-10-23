package com.epubfeed.domain.model

import androidx.compose.runtime.Immutable

/** A post ready to render. */
@Immutable
data class FeedPost(
    val id: String,
    val chapterIndex: Int,
    val indexInChapter: Int,
    val globalIndex: Int,
    val isChapterTitlePost: Boolean,
    val blocks: List<Block>,
    val liked: Boolean,
    val bookmarked: Boolean,
    val plainText: String,
)

/** A chapter as shown in the Chapters tab. */
@Immutable
data class ChapterSummary(
    val id: String,
    val index: Int,
    val title: String,
    val postCount: Int,
    val firstIndex: Int,
    val lastIndex: Int,
)

/** A note the reader wrote under a post. */
@Immutable
data class Note(
    val id: String,
    val postId: String,
    val text: String,
    val createdAt: Long,
)
