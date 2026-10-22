package com.epubfeed.data.local

/** A post joined with its reaction, ready for the feed. */
data class PostRow(
    val id: String,
    val bookId: String,
    val chapterId: String,
    val chapterIndex: Int,
    val indexInChapter: Int,
    val globalIndex: Int,
    val blocksJson: String,
    val isChapterTitlePost: Boolean,
    val plainText: String,
    val liked: Boolean,
    val bookmarked: Boolean,
)

/** One row of the Chapters tab. */
data class ChapterRow(
    val id: String,
    val title: String,
    val chapterIndex: Int,
    val postCount: Int,
    val firstIndex: Int,
    val lastIndex: Int,
)

/** A reaction plus the block anchor of its post, used when regenerating posts. */
data class ReactionAnchor(
    val postId: String,
    val chapterId: String,
    val firstBlock: Int,
    val liked: Boolean,
    val bookmarked: Boolean,
    val updatedAt: Long,
)
