package com.epubfeed.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {

    @Query("SELECT * FROM book_stats WHERE bookId = :bookId")
    fun observe(bookId: String): Flow<BookStatsEntity?>

    @Query("SELECT secondsRead FROM book_stats WHERE bookId = :bookId")
    suspend fun secondsFor(bookId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: BookStatsEntity)

    @Query("UPDATE book_stats SET secondsRead = secondsRead + :seconds WHERE bookId = :bookId")
    suspend fun addSeconds(bookId: String, seconds: Long)

    @Query("DELETE FROM book_stats WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}
