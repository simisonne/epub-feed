package com.epubfeed.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        PostEntity::class,
        ReactionEntity::class,
        NoteEntity::class,
        BookStatsEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun postDao(): PostDao
    abstract fun reactionDao(): ReactionDao
    abstract fun noteDao(): NoteDao
    abstract fun statsDao(): StatsDao

    companion object {
        /**
         * Adds notes, reading time and the extra book metadata. Everything the reader
         * created before this version (likes, bookmarks, position) is left untouched.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notes` (
                        `id` TEXT NOT NULL,
                        `postId` TEXT NOT NULL,
                        `bookId` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`postId`) REFERENCES `posts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_bookId` ON `notes` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_postId` ON `notes` (`postId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `book_stats` (
                        `bookId` TEXT NOT NULL,
                        `secondsRead` INTEGER NOT NULL,
                        PRIMARY KEY(`bookId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("ALTER TABLE `books` ADD COLUMN `publisher` TEXT")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `publishedYear` TEXT")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `wordCount` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Adds the editable identity (name, author, @), the chosen profile picture and the library
         * order. Existing books keep the order they were imported in: every row starts at 0 and the
         * tie break is `importedAt`, and the first drag writes real positions.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `books` ADD COLUMN `handle` TEXT")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `avatarPath` TEXT")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `libraryOrder` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
