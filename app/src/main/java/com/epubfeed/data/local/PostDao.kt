package com.epubfeed.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

private const val POST_SELECT = """
    SELECT p.id AS id,
           p.bookId AS bookId,
           p.chapterId AS chapterId,
           p.chapterIndex AS chapterIndex,
           p.indexInChapter AS indexInChapter,
           p.globalIndex AS globalIndex,
           p.blocksJson AS blocksJson,
           p.isChapterTitlePost AS isChapterTitlePost,
           p.plainText AS plainText,
           COALESCE(r.liked, 0) AS liked,
           COALESCE(r.bookmarked, 0) AS bookmarked
    FROM posts p
    LEFT JOIN reactions r ON r.postId = p.id
"""

@Dao
interface PostDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<PostEntity>)

    @Query("DELETE FROM posts WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)

    /** The whole chapter, in order: one chapter is one feed. */
    @Query("$POST_SELECT WHERE p.bookId = :bookId AND p.chapterIndex = :chapterIndex ORDER BY p.indexInChapter")
    fun observeChapter(bookId: String, chapterIndex: Int): Flow<List<PostRow>>

    @Query("$POST_SELECT WHERE p.bookId = :bookId AND r.bookmarked = 1 ORDER BY p.globalIndex")
    fun observeSaved(bookId: String): Flow<List<PostRow>>

    @Query("$POST_SELECT WHERE p.bookId = :bookId AND r.liked = 1 ORDER BY p.globalIndex")
    fun observeLiked(bookId: String): Flow<List<PostRow>>

    @Query("SELECT * FROM posts WHERE id = :id")
    suspend fun get(id: String): PostEntity?

    @Query("SELECT * FROM posts WHERE bookId = :bookId AND globalIndex = :globalIndex LIMIT 1")
    suspend fun getAt(bookId: String, globalIndex: Int): PostEntity?

    @Query("SELECT * FROM posts WHERE bookId = :bookId ORDER BY globalIndex")
    suspend fun allForBook(bookId: String): List<PostEntity>

    @Query("SELECT * FROM posts WHERE chapterId = :chapterId ORDER BY indexInChapter")
    suspend fun forChapter(chapterId: String): List<PostEntity>

    /** Posts that mention a word, for the "uses in this book" line. LIKE is only a coarse filter. */
    @Query(
        "SELECT * FROM posts WHERE bookId = :bookId AND plainText LIKE '%' || :term || '%' " +
            "ORDER BY globalIndex",
    )
    suspend fun matching(bookId: String, term: String): List<PostEntity>

    /** The whole book as plain text, for a full text search. */
    @Query(
        "SELECT id AS id, chapterIndex AS chapterIndex, indexInChapter AS indexInChapter, " +
            "globalIndex AS globalIndex, plainText AS plainText FROM posts " +
            "WHERE bookId = :bookId ORDER BY globalIndex",
    )
    suspend fun searchRows(bookId: String): List<SearchRow>

    @Query("SELECT COUNT(*) FROM posts WHERE bookId = :bookId")
    suspend fun countForBook(bookId: String): Int

    @Query("SELECT COUNT(*) FROM posts WHERE chapterId = :chapterId")
    suspend fun countForChapter(chapterId: String): Int

    @Query("SELECT MIN(globalIndex) FROM posts WHERE chapterId = :chapterId")
    suspend fun firstIndexForChapter(chapterId: String): Int?

    @Query("SELECT MIN(globalIndex) FROM posts WHERE bookId = :bookId")
    suspend fun firstIndexForBook(bookId: String): Int?

    @Query("SELECT MAX(globalIndex) FROM posts WHERE bookId = :bookId")
    suspend fun lastIndexForBook(bookId: String): Int?
}
