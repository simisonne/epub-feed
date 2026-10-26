package com.epubfeed.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    /** The library order the reader dragged into place, with the import date as the tie break. */
    @Query("SELECT * FROM books ORDER BY libraryOrder ASC, importedAt ASC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY libraryOrder ASC, importedAt ASC")
    suspend fun all(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): BookEntity?

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(libraryOrder), -1) FROM books")
    suspend fun maxLibraryOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE books SET wordCount = :words WHERE id = :id")
    suspend fun updateWordCount(id: String, words: Int)

    @Query("UPDATE books SET lastPostIndex = :index WHERE id = :id")
    suspend fun updateLastPostIndex(id: String, index: Int)

    @Query("UPDATE books SET postCount = :posts, chapterCount = :chapters WHERE id = :id")
    suspend fun updateCounts(id: String, posts: Int, chapters: Int)

    @Query(
        "UPDATE books SET postCount = :posts, chapterCount = :chapters, wordCount = :words, " +
            "lastPostIndex = :position WHERE id = :id",
    )
    suspend fun updateStructure(id: String, posts: Int, chapters: Int, words: Int, position: Int)

    /** The profile edits: the name, the author and the @ the reader wants to see. */
    @Query("UPDATE books SET title = :title, author = :author, handle = :handle WHERE id = :id")
    suspend fun updateIdentity(id: String, title: String, author: String, handle: String?)

    @Query("UPDATE books SET avatarPath = :path WHERE id = :id")
    suspend fun updateAvatar(id: String, path: String?)

    @Query("UPDATE books SET libraryOrder = :order WHERE id = :id")
    suspend fun updateLibraryOrder(id: String, order: Int)
}
