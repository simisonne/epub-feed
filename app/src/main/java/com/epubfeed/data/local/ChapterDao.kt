package com.epubfeed.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex")
    suspend fun listForBook(bookId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun get(id: String): ChapterEntity?

    @Query(
        """
        SELECT c.id AS id,
               c.title AS title,
               c.chapterIndex AS chapterIndex,
               c.postCount AS postCount,
               COALESCE(MIN(p.globalIndex), 0) AS firstIndex,
               COALESCE(MAX(p.globalIndex), 0) AS lastIndex
        FROM chapters c
        LEFT JOIN posts p ON p.chapterId = c.id
        WHERE c.bookId = :bookId
        GROUP BY c.id
        ORDER BY c.chapterIndex
        """,
    )
    fun observeRows(bookId: String): Flow<List<ChapterRow>>

    @Query("UPDATE chapters SET postCount = :count WHERE id = :id")
    suspend fun updatePostCount(id: String, count: Int)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}
