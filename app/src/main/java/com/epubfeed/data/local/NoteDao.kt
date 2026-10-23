package com.epubfeed.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<NoteEntity>)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM notes WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)

    @Query("SELECT * FROM notes WHERE bookId = :bookId ORDER BY createdAt ASC")
    fun observeForBook(bookId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE bookId = :bookId ORDER BY createdAt ASC")
    suspend fun forBook(bookId: String): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes WHERE bookId = :bookId")
    suspend fun countForBook(bookId: String): Int
}
