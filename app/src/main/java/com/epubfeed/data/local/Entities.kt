package com.epubfeed.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    /** "EPUB" or "PDF". */
    val format: String,
    val coverPath: String?,
    /** Stable hue (0..359) used for the fallback avatar colour. */
    val hue: Int,
    val importedAt: Long,
    /** Global index of the first post the reader is sitting on. */
    val lastPostIndex: Int,
    val isSample: Boolean,
    val postCount: Int,
    val chapterCount: Int,
    /** From the EPUB package metadata, when present. */
    val publisher: String? = null,
    val publishedYear: String? = null,
    /** Total words in the book, used for the reading time and page estimates. */
    val wordCount: Int = 0,
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId"), Index(value = ["bookId", "chapterIndex"], unique = true)],
)
data class ChapterEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    @ColumnInfo(name = "chapterIndex") val index: Int,
    val title: String,
    /** Serialized List<Block>: the source paragraphs, used to regenerate posts. */
    val blocksJson: String,
    /** Denormalized for the Chapters tab; rewritten when posts are regenerated. */
    var postCount: Int,
)

@Entity(
    tableName = "posts",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["bookId", "globalIndex"]), Index("chapterId"), Index("bookId")],
)
data class PostEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterId: String,
    val chapterIndex: Int,
    val indexInChapter: Int,
    val globalIndex: Int,
    /** Serialized List<Block> for this post. */
    val blocksJson: String,
    val isChapterTitlePost: Boolean,
    val plainText: String,
    /** Inclusive source block range inside the chapter, used to carry reactions across regenerations. */
    val firstBlock: Int,
    val lastBlock: Int,
)

@Entity(
    tableName = "reactions",
    foreignKeys = [
        ForeignKey(
            entity = PostEntity::class,
            parentColumns = ["id"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId"), Index(value = ["bookId", "bookmarked"])],
)
data class ReactionEntity(
    @PrimaryKey val postId: String,
    val bookId: String,
    val liked: Boolean,
    val bookmarked: Boolean,
    val updatedAt: Long,
)

/** A note the reader wrote under a post, the feed's version of a reply. */
@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = PostEntity::class,
            parentColumns = ["id"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId"), Index("postId")],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val postId: String,
    val bookId: String,
    val text: String,
    val createdAt: Long,
)

/** Time actually spent reading a book, accumulated with an idle timeout. */
@Entity(tableName = "book_stats")
data class BookStatsEntity(
    @PrimaryKey val bookId: String,
    val secondsRead: Long,
)
