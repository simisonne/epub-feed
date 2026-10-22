package com.epubfeed.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reaction: ReactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reactions: List<ReactionEntity>)

    @Query("SELECT * FROM reactions WHERE postId = :postId")
    suspend fun get(postId: String): ReactionEntity?

    @Query("SELECT * FROM reactions WHERE bookId = :bookId AND (liked = 1 OR bookmarked = 1)")
    suspend fun forBook(bookId: String): List<ReactionEntity>

    @Query(
        """
        SELECT r.postId AS postId,
               p.chapterId AS chapterId,
               p.firstBlock AS firstBlock,
               r.liked AS liked,
               r.bookmarked AS bookmarked,
               r.updatedAt AS updatedAt
        FROM reactions r
        JOIN posts p ON p.id = r.postId
        WHERE r.bookId = :bookId AND (r.liked = 1 OR r.bookmarked = 1)
        """,
    )
    suspend fun anchorsForBook(bookId: String): List<ReactionAnchor>

    @Query("DELETE FROM reactions WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}
