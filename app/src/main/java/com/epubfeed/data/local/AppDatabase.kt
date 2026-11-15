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
        WordEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun postDao(): PostDao
    abstract fun reactionDao(): ReactionDao
    abstract fun noteDao(): NoteDao
    abstract fun statsDao(): StatsDao

    abstract fun wordDao(): WordDao

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

        /**
         * Adds the saved word list. The table has no foreign key to `books` on purpose: a word has
         * to survive its book being deleted, or the sample book being reseeded on a version bump.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `words` (
                        `id` TEXT NOT NULL,
                        `term` TEXT NOT NULL,
                        `termKey` TEXT NOT NULL,
                        `definition` TEXT NOT NULL,
                        `partOfSpeech` TEXT,
                        `synonyms` TEXT,
                        `rarity` TEXT,
                        `source` TEXT NOT NULL,
                        `packVersion` TEXT,
                        `bookId` TEXT,
                        `bookTitle` TEXT,
                        `bookHue` INTEGER,
                        `postId` TEXT,
                        `chapterIndex` INTEGER,
                        `excerpt` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_words_termKey_bookId` " +
                        "ON `words` (`termKey`, `bookId`)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_words_bookId` ON `words` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_words_createdAt` ON `words` (`createdAt`)")
            }
        }

        /**
         * Adds the frequency position to a saved word, so the list can sort by rarity with the
         * rank as the tie break instead of falling back to the alphabet after four bands. Words
         * saved before this have no rank and sort last until they are looked up again.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `words` ADD COLUMN `freqRank` INTEGER")
                db.execSQL("ALTER TABLE `words` ADD COLUMN `freqTotal` INTEGER")
            }
        }
    }
}
