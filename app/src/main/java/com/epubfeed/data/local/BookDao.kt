package com.epubfeed.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY importedAt ASC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY importedAt ASC")
    suspend fun all(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): BookEntity?

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE books SET lastPostIndex = :index WHERE id = :id")
    suspend fun updateLastPostIndex(id: String, index: Int)

    @Query("UPDATE books SET postCount = :posts, chapterCount = :chapters WHERE id = :id")
    suspend fun updateCounts(id: String, posts: Int, chapters: Int)
}
