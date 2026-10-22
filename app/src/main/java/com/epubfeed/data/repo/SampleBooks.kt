package com.epubfeed.data.repo

import android.content.Context
import com.epubfeed.data.epub.EpubParser
import com.epubfeed.data.prefs.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds the bundled public domain sample on first launch. The sample is a real,
 * valid EPUB so it exercises the same parser path as a user import.
 */
@Singleton
class SampleBooks @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: BookRepository,
    private val settings: SettingsRepository,
) {

    suspend fun ensureSample() {
        if (settings.isSeedDone()) return
        if (repository.anyBooks()) {
            settings.setSeedDone()
            return
        }
        runCatching {
            withContext(Dispatchers.IO) {
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
        settings.setSeedDone()
    }

    companion object {
        const val SAMPLE_ASSET = "sample-pride-and-prejudice.epub"
    }
}
