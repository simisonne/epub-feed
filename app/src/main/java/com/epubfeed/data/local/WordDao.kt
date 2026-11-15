package com.epubfeed.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {

    @Query("SELECT * FROM words")
    fun observeAll(): Flow<List<WordEntity>>

    @Query("SELECT * FROM words")
    suspend fun all(): List<WordEntity>

    /**
     * The same word saved twice from the same book is one row. A null book (a custom entry) needs
     * its own comparison because SQLite treats NULLs as distinct in unique indexes.
     */
    @Query(
        "SELECT * FROM words WHERE termKey = :termKey AND " +
            "(bookId = :bookId OR (bookId IS NULL AND :bookId IS NULL)) LIMIT 1",
    )
    suspend fun find(termKey: String, bookId: String?): WordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(word: WordEntity)

    /** Imported rows keep the ones already on the device: the import merges, never replaces. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(words: List<WordEntity>)

    @Query("DELETE FROM words WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun byId(id: String): WordEntity?
}
