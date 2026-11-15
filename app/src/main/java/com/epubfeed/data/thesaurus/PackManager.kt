package com.epubfeed.data.thesaurus

import android.content.Context
import com.epubfeed.data.prefs.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** One downloadable dictionary. Language is data, so another language is another spec. */
data class PackSpec(
    val language: String,
    val label: String,
    val version: String,
    val fileName: String,
    val url: String,
    /** SHA-256 of the unpacked database, checked before it is allowed to replace anything. */
    val sha256: String,
    val downloadBytes: Long,
)

/** What one language row in the settings shows. */
data class LanguageStatus(
    val spec: PackSpec,
    val installed: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
)

/**
 * Owns the dictionary files. A pack is a plain SQLite file in `files/dictionaries`, downloaded on
 * demand from the project's GitHub release and verified by hash, so removing a language is deleting
 * a file and nothing in the app's own database is involved.
 *
 * Several languages can be installed at once. A lookup asks the reader's chosen language first and
 * the rest after it, which is what keeps a German word out of the English dictionary.
 */
@Singleton
class PackManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {

    private val directory = File(context.filesDir, "dictionaries")

    private val lock = Any()

    private val _languages = MutableStateFlow(readStatus(emptyList()))
    val languages: StateFlow<List<LanguageStatus>> = _languages.asStateFlow()

    private val cached = mutableMapOf<String, WordPack>()

    private val _networkBlocked = MutableStateFlow(false)

    /**
     * True once a download failed because the system refused the connection. Android 17 hands
     * `INTERNET` out as a permission the reader can turn off per app, and the request API can
     * report success while the socket layer still refuses, so the only trustworthy signal is a
     * download that could not even resolve the host.
     */
    val networkBlocked: StateFlow<Boolean> = _networkBlocked.asStateFlow()

    fun spec(language: String): PackSpec? = specs.firstOrNull { it.language == language }

    fun fileFor(spec: PackSpec): File = File(directory, spec.fileName)

    fun isInstalled(spec: PackSpec): Boolean =
        fileFor(spec).let { it.isFile && it.length() > MIN_PACK_BYTES }

    /** The language codes that are actually on the device, in spec order. */
    fun installedLanguages(): List<String> = specs.filter { isInstalled(it) }.map { it.language }

    /**
     * Opens every installed pack, the reader's language first, and keeps them open. The order is
     * the whole point: with English and German installed, "Gift" has to answer in German.
     */
    suspend fun openAll(): List<WordPack> {
        val preferred = settings.currentSettings().dictionaryLanguage
        val ordered = specs.sortedBy { if (it.language == preferred) 0 else 1 }
        // A lookup can arrive from the feed and the word list at once, and the cache is a plain map.
        return synchronized(lock) {
            ordered.mapNotNull { spec ->
                if (!isInstalled(spec)) return@mapNotNull null
                cached[spec.language]
                    ?: SqliteWordPack.open(fileFor(spec))?.also { cached[spec.language] = it }
            }
        }
    }

    suspend fun download(language: String): Result<Unit> = withContext(Dispatchers.IO) {
        android.util.Log.i("epubfeed-dict", "pack download begin: $language")
        val spec = spec(language) ?: return@withContext Result.failure(IllegalArgumentException("Unknown language"))
        if (_languages.value.any { it.downloading }) {
            return@withContext Result.failure(IllegalStateException("Already downloading"))
        }
        update(spec) { it.copy(downloading = true, progress = 0f, error = null) }
        android.util.Log.i("epubfeed-dict", "state now: " + _languages.value.joinToString { it.spec.language + ":" + it.downloading })
        directory.mkdirs()
        val temp = File(directory, spec.fileName + ".part")
        try {
            android.util.Log.i("epubfeed-dict", "connecting to " + spec.url)
            val connection = (URL(spec.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("Accept-Encoding", "identity")
            }
            android.util.Log.i("epubfeed-dict", "response " + connection.responseCode)
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Download failed (${connection.responseCode})")
            }
            val total = connection.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            GZIPInputStream(connection.inputStream.buffered(BUFFER)).use { input ->
                temp.outputStream().buffered(BUFFER).use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            update(spec) { it.copy(progress = (copied.toFloat() / total).coerceIn(0f, 1f)) }
                        }
                    }
                }
            }
            connection.disconnect()
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!hash.equals(spec.sha256, ignoreCase = true)) {
                throw IllegalStateException("The download did not match the expected file")
            }
            if (temp.length() < MIN_PACK_BYTES) throw IllegalStateException("The download was empty")

            // Close the old reader before its file is replaced, or the open handle keeps the old pack.
            synchronized(lock) { cached.remove(spec.language)?.close() }
            val target = fileFor(spec)
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) throw IllegalStateException("Could not store the dictionary")
            // readStatus carries the in flight flag over, so it has to be cleared by hand or the
            // row stays on "Downloading, 100%" and every later download is refused as already running.
            _languages.value = readStatus(_languages.value).map {
                if (it.spec.language == spec.language) {
                    it.copy(downloading = false, progress = 0f, error = null)
                } else {
                    it
                }
            }
            _networkBlocked.value = false
            Result.success(Unit)
        } catch (e: Throwable) {
            android.util.Log.w("epubfeed-dict", "download failed: " + e::class.java.simpleName + " " + e.message, e)
            temp.delete()
            val message = when (e) {
                is SecurityException -> "ePUB Feed is not allowed to use the network"
                is java.net.UnknownHostException -> "No connection to the dictionary host"
                else -> e.message ?: "Download failed"
            }
            if (e is SecurityException || e is java.net.UnknownHostException) _networkBlocked.value = true
            update(spec) { it.copy(downloading = false, progress = 0f, error = message) }
            Result.failure(e)
        }
    }

    fun remove(language: String) {
        val spec = spec(language) ?: return
        synchronized(lock) { cached.remove(spec.language)?.close() }
        fileFor(spec).delete()
        File(directory, spec.fileName + ".part").delete()
        _languages.value = readStatus(_languages.value)
    }

    /** Puts a message on one language row, for a download that never started. */
    fun showError(language: String, message: String) {
        val spec = spec(language) ?: return
        update(spec) { it.copy(downloading = false, progress = 0f, error = message) }
    }

    /** Re-reads the files, for a settings screen that may open after a file changed on disk. */
    fun refresh() {
        _languages.value = readStatus(_languages.value)
    }

    fun clearError(language: String) {
        val spec = spec(language) ?: return
        update(spec) { it.copy(error = null) }
    }

    private fun update(spec: PackSpec, change: (LanguageStatus) -> LanguageStatus) {
        _languages.value = _languages.value.map { if (it.spec.language == spec.language) change(it) else it }
    }

    /** Rebuilds the rows from the files, keeping any download that is still running. */
    private fun readStatus(previous: List<LanguageStatus>): List<LanguageStatus> = specs.map { spec ->
        val old = previous.firstOrNull { it.spec.language == spec.language }
        LanguageStatus(
            spec = spec,
            installed = isInstalled(spec),
            downloading = old?.downloading ?: false,
            progress = old?.progress ?: 0f,
            error = old?.error,
        )
    }

    companion object {
        private const val BUFFER = 64 * 1024
        private const val MIN_PACK_BYTES = 1_000_000L

        /**
         * Published with the dictionary releases. The hash is of the unpacked database, which is
         * what the app ends up reading.
         */
        val English = PackSpec(
            language = "en",
            label = "English",
            version = "en-v1",
            fileName = "en-v1.db",
            url = "https://github.com/simisonne/epub-feed/releases/download/thesaurus-en-v1/thesaurus-en-v1.db.gz",
            sha256 = "d85630d73ae678f4131714307ccada99d54973c70da118e6af9339b241eeb51f",
            downloadBytes = 13_929_552L,
        )

        val German = PackSpec(
            language = "de",
            label = "German",
            version = "de-v1",
            fileName = "de-v1.db",
            url = "https://github.com/simisonne/epub-feed/releases/download/thesaurus-de-v1/thesaurus-de-v1.db.gz",
            sha256 = "2ae8f5f88718e5766aa31f3f938412685839c653c92ed2cef9c656f34435afba",
            downloadBytes = 12_939_531L,
        )

        val Korean = PackSpec(
            language = "ko",
            label = "Korean",
            version = "ko-v1",
            fileName = "ko-v1.db",
            url = "https://github.com/simisonne/epub-feed/releases/download/thesaurus-ko-v1/thesaurus-ko-v1.db.gz",
            sha256 = "08d3bd6a7ce35e4e93dcf7e80cfa624610146957beb6148b07c75baf8fa06c9c",
            downloadBytes = 4_787_863L,
        )

        val specs: List<PackSpec> = listOf(English, German, Korean)
    }
}
