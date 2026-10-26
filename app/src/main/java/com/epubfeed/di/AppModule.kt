package com.epubfeed.di

import android.content.Context
import androidx.room.Room
import com.epubfeed.data.local.AppDatabase
import com.epubfeed.data.local.BookDao
import com.epubfeed.data.local.ChapterDao
import com.epubfeed.data.local.NoteDao
import com.epubfeed.data.local.PostDao
import com.epubfeed.data.local.ReactionDao
import com.epubfeed.data.local.StatsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "epub-feed.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideBookDao(db: AppDatabase): BookDao = db.bookDao()

    @Provides
    fun provideChapterDao(db: AppDatabase): ChapterDao = db.chapterDao()

    @Provides
    fun providePostDao(db: AppDatabase): PostDao = db.postDao()

    @Provides
    fun provideReactionDao(db: AppDatabase): ReactionDao = db.reactionDao()

    @Provides
    fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideStatsDao(db: AppDatabase): StatsDao = db.statsDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
