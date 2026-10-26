package com.epubfeed.data.repo

import android.content.Context
import com.epubfeed.data.epub.EpubParser
import com.epubfeed.data.prefs.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds the bundled onboarding book on first launch. It is a real, valid EPUB so it
 * exercises the same parser path as a user import.
 *
 * The seed carries a version: bumping [SEED_VERSION] re-runs it for existing installs too,
 * which is how the old Pride and Prejudice sample gets replaced by the tutorial.
 */
@Singleton
class SampleBooks @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: BookRepository,
    private val settings: SettingsRepository,
) {

    suspend fun ensureSample() {
        if (settings.seedVersion() >= SEED_VERSION) return
        runCatching {
            withContext(Dispatchers.IO) {
                // The bundled sample is the app's own book, never the reader's, so a seed
                // version bump replaces it outright. That is how new tutorial content ships.
                repository.observeBooks().first()
                    .filter { it.isSample }
                    .forEach { repository.deleteBook(it.id) }

                val dir = File(context.cacheDir, "sample").apply { mkdirs() }
                val target = File(dir, SAMPLE_ASSET)
                context.assets.open(SAMPLE_ASSET).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                val parsed = EpubParser.parse(target)
                val length = settings.currentSettings().postLength
                val bookId = repository.persistParsedBook(
                    parsed = parsed,
                    format = "EPUB",
                    isSample = true,
                    length = length,
                )
                settings.setLastBookId(bookId)
                target.delete()
            }
        }
        settings.setSeedVersion(SEED_VERSION)
    }

    companion object {
        const val SAMPLE_ASSET = "sample-epub-feed-basics.epub"
        const val SAMPLE_TITLE = "ePUB Feed Basics"

        /** 1 = Pride and Prejudice, 2 = first ePUB Feed Basics, 3 = expanded tutorial, 4 = picture post. */
        private const val SEED_VERSION = 4
    }
}
