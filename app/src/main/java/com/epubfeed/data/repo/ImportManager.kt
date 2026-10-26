package com.epubfeed.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.epubfeed.data.epub.BookParseException
import com.epubfeed.data.epub.EpubParser
import com.epubfeed.data.epub.ParsedBook
import com.epubfeed.data.pdf.PdfBookParser
import com.epubfeed.data.pdf.PdfBoxTextSource
import com.epubfeed.data.pdf.PdfPageRenderer
import com.epubfeed.data.prefs.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ImportState {
    data object Idle : ImportState
    data class Working(val step: String, val fraction: Float?) : ImportState
    data class Failure(val message: String) : ImportState
    data class Success(val bookId: String, val title: String) : ImportState
}

/**
 * Copies a picked document into private storage, parses it off the main thread and
 * writes the result into Room. The original URI is never touched again afterwards.
 */
@Singleton
class ImportManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: BookRepository,
    private val settings: SettingsRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    fun dismiss() {
        _state.value = ImportState.Idle
    }

    fun import(uri: Uri) {
        if (_state.value is ImportState.Working) return
        _state.value = ImportState.Working("Reading file", null)
        scope.launch { runImport(uri) }
    }

    private suspend fun runImport(uri: Uri) {
        var temp: File? = null
        try {
            val displayName = queryDisplayName(uri) ?: "book"
            val length = settings.currentSettings().postLength
            temp = withContext(Dispatchers.IO) { copyToCache(uri) }
            val bytes = temp.length()
            if (bytes <= 0) throw BookParseException("That file is empty.")

            _state.value = ImportState.Working("Detecting format", null)
            val format = detectFormat(uri, temp)

            _state.value = ImportState.Working(if (format == Format.EPUB) "Unpacking EPUB" else "Opening PDF", null)
            val parsed: ParsedBook = withContext(Dispatchers.Default) {
                when (format) {
                    Format.EPUB -> EpubParser.parse(temp)
                    Format.PDF -> {
                        val source = PdfBoxTextSource.open(temp)
                        source.use {
                            PdfBookParser.parse(it) { done, total ->
                                val fraction = if (total <= 0) null else done / total.toFloat()
                                _state.value = ImportState.Working("Extracting text (page $done of $total)", fraction)
                            }
                        }
                    }
                }
            }

            _state.value = ImportState.Working("Building the feed", null)
            var title = parsed.title.ifBlank { displayName.substringBeforeLast('.').ifBlank { "Untitled" } }
            var author = parsed.author.ifBlank { "Unknown author" }
            if (format == Format.PDF) {
                if (title.isBlank()) title = displayName.substringBeforeLast('.').ifBlank { "Untitled" }
                if (author.isBlank()) author = "PDF document"
            }
            val normalized = parsed.copy(title = title, author = author)

            // A PDF has no cover image object, so its first page is drawn and becomes the cover,
            // which is also the photo the reader can pick on the profile.
            val pageImages = if (format == Format.PDF) {
                _state.value = ImportState.Working("Drawing the cover", null)
                withContext(Dispatchers.IO) { PdfPageRenderer.renderPages(temp, count = 1) }
            } else {
                emptyList()
            }

            val bookId = withContext(Dispatchers.IO) {
                repository.persistParsedBook(
                    parsed = normalized,
                    format = format.name,
                    isSample = false,
                    length = length,
                    pageImages = pageImages,
                )
            }
            _state.value = ImportState.Success(bookId, normalized.title)
        } catch (e: BookParseException) {
            _state.value = ImportState.Failure(e.userMessage)
        } catch (e: OutOfMemoryError) {
            _state.value = ImportState.Failure("This file is too large to open on this device.")
        } catch (e: SecurityException) {
            _state.value = ImportState.Failure("The app is not allowed to read that file. Try picking it again.")
        } catch (e: IOException) {
            _state.value = ImportState.Failure("That file could not be read. It may have been moved or deleted.")
        } catch (e: Exception) {
            _state.value = ImportState.Failure(
                "Something went wrong while reading that file. (${e.javaClass.simpleName})",
            )
        } finally {
            runCatching { temp?.delete() }
        }
    }

    private fun copyToCache(uri: Uri): File {
        val dir = File(context.cacheDir, "imports").apply { mkdirs() }
        val target = File(dir, "import-${System.currentTimeMillis()}.bin")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("The document could not be opened.")
        input.use { source ->
            target.outputStream().use { sink ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_IMPORT_BYTES) {
                        throw BookParseException("This file is larger than ${MAX_IMPORT_BYTES / (1024 * 1024)} MB.")
                    }
                    sink.write(buffer, 0, read)
                }
            }
        }
        return target
    }

    private fun detectFormat(uri: Uri, file: File): Format {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()?.lowercase()
        val name = queryDisplayName(uri)?.lowercase().orEmpty()
        val head = ByteArray(8)
        val read = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(0)
        val magic = if (read >= 4) String(head, 0, 4, Charsets.ISO_8859_1) else ""

        return when {
            magic.startsWith("%PDF") -> Format.PDF
            magic.startsWith("PK") -> Format.EPUB
            mime == "application/pdf" || name.endsWith(".pdf") -> Format.PDF
            mime == "application/epub+zip" || name.endsWith(".epub") -> Format.EPUB
            else -> throw BookParseException("That file is not an EPUB or a PDF. Import an .epub or .pdf file.")
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
    }.getOrNull()

    enum class Format { EPUB, PDF }

    companion object {
        const val MAX_IMPORT_BYTES = 250L * 1024 * 1024
    }
}
