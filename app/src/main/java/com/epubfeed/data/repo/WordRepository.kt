package com.epubfeed.data.repo

import android.content.Context
import android.net.Uri
import com.epubfeed.data.local.BookDao
import com.epubfeed.data.local.BookEntity
import com.epubfeed.data.local.PostDao
import com.epubfeed.data.local.WordDao
import com.epubfeed.data.local.WordEntity
import com.epubfeed.data.prefs.SettingsRepository
import com.epubfeed.domain.model.Word
import com.epubfeed.domain.model.WordOrder
import com.epubfeed.domain.thesaurus.LookupSource
import com.epubfeed.domain.thesaurus.Rarity
import com.epubfeed.domain.thesaurus.ThesaurusEntry
import com.epubfeed.domain.thesaurus.WordForms
import com.epubfeed.domain.thesaurus.WordUses
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The JSON shape of an exported word list. Versioned so a future format can migrate. */
@Serializable
data class WordExport(
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val words: List<ExportedWord> = emptyList(),
)

/** What an import did. */
data class ImportResult(val added: Int, val skipped: Int)

@Serializable
data class ExportedWord(
    val term: String,
    val definition: String,
    val partOfSpeech: String? = null,
    val synonyms: List<String> = emptyList(),
    val rarity: String? = null,
    val freqRank: Int? = null,
    val freqTotal: Int? = null,
    val source: String = "PACK",
    val packVersion: String? = null,
    val bookId: String? = null,
    val bookTitle: String? = null,
    val bookHue: Int? = null,
    val postId: String? = null,
    val chapterIndex: Int? = null,
    val excerpt: String? = null,
    val createdAt: Long = 0L,
)

/**
 * The saved word list.
 *
 * Two rules shape this class. A word outlives its book, so the book title and hue are copied into
 * the row and nothing here joins to `books` for display. And a saved definition is stored, not
 * looked up again, so the list renders with no dictionary installed and no network.
 */
@Singleton
class WordRepository @Inject constructor(
    private val wordDao: WordDao,
    private val postDao: PostDao,
    private val bookDao: BookDao,
    private val settings: SettingsRepository,
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) {

    fun observeWords(): Flow<List<Word>> =
        combine(wordDao.observeAll(), settings.settings) { rows, userSettings ->
            WordOrder.sort(rows.map(::toWord), userSettings.wordSort)
        }

    fun observeBooks(): Flow<List<BookEntity>> = bookDao.observeAll()

    suspend fun find(term: String, bookId: String?): Word? =
        wordDao.find(WordForms.normalize(term), bookId)?.let(::toWord)

    /**
     * Saves a dictionary answer. The same word from the same book updates the row it already has
     * instead of appearing twice, and the timestamp moves so it reads as freshly saved.
     */
    suspend fun save(
        term: String,
        entry: ThesaurusEntry,
        book: BookEntity?,
        postId: String?,
        chapterIndex: Int?,
        excerpt: String?,
    ): Word {
        val key = WordForms.normalize(term)
        val existing = wordDao.find(key, book?.id)
        val entity = WordEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            term = term.trim(),
            termKey = key,
            definition = entry.definition,
            partOfSpeech = entry.partOfSpeech,
            synonyms = entry.synonyms.take(5).joinToString(",").takeIf { it.isNotBlank() },
            rarity = entry.rarity?.name,
            freqRank = entry.frequency?.rank,
            freqTotal = entry.frequency?.total,
            source = entry.source.name,
            packVersion = entry.packVersion,
            bookId = book?.id,
            bookTitle = book?.title,
            bookHue = book?.hue,
            postId = postId,
            chapterIndex = chapterIndex,
            excerpt = excerpt,
            createdAt = System.currentTimeMillis(),
        )
        wordDao.upsert(entity)
        return toWord(entity)
    }

    /**
     * Saves a word the dictionary does not hold. It is still a word of the book it was found in:
     * it keeps the book, the post, the chapter and the sentence, so it reads like every other word
     * saved from a lookup instead of dropping into the custom pile that belongs to no book.
     */
    suspend fun saveWithoutEntry(
        term: String,
        book: BookEntity?,
        postId: String?,
        chapterIndex: Int?,
        excerpt: String?,
    ): Word? {
        val cleaned = term.trim().take(60)
        if (cleaned.isBlank()) return null
        val key = WordForms.normalize(cleaned)
        val existing = wordDao.find(key, book?.id)
        val entity = WordEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            term = cleaned,
            termKey = key,
            definition = "",
            source = LookupSource.NONE.name,
            bookId = book?.id,
            bookTitle = book?.title,
            bookHue = book?.hue,
            postId = postId,
            chapterIndex = chapterIndex,
            excerpt = excerpt,
            createdAt = System.currentTimeMillis(),
        )
        wordDao.upsert(entity)
        return toWord(entity)
    }

    /** A word the reader typed themselves, with or without a definition. */
    suspend fun addCustom(term: String, definition: String?): Word? {
        val cleaned = term.trim().take(60)
        if (cleaned.isBlank()) return null
        val key = WordForms.normalize(cleaned)
        val existing = wordDao.find(key, null)
        val entity = WordEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            term = cleaned,
            termKey = key,
            definition = definition?.trim().orEmpty(),
            source = LookupSource.NONE.name,
            createdAt = System.currentTimeMillis(),
        )
        wordDao.upsert(entity)
        return toWord(entity)
    }

    suspend fun remove(id: String) = wordDao.delete(id)

    /** Puts a removed word back exactly where it was, for the undo in the list. */
    suspend fun restore(word: Word) {
        wordDao.upsert(
            WordEntity(
                id = word.id,
                term = word.term,
                termKey = WordForms.normalize(word.term),
                definition = word.definition,
                partOfSpeech = word.partOfSpeech,
                synonyms = word.synonyms.take(5).joinToString(",").takeIf { it.isNotBlank() },
                rarity = word.rarity?.name,
                freqRank = word.freqRank,
                freqTotal = word.freqTotal,
                source = word.source.name,
                packVersion = null,
                bookId = word.bookId,
                bookTitle = word.bookTitle,
                bookHue = word.bookHue,
                postId = word.postId,
                chapterIndex = word.chapterIndex,
                excerpt = word.excerpt,
                createdAt = word.createdAt,
            ),
        )
    }

    /** Replaces a saved definition with a fresh answer, keeping the word where it sits in the list. */
    suspend fun refresh(word: Word, entry: ThesaurusEntry) {
        wordDao.byId(word.id)?.let { row ->
            wordDao.upsert(
                row.copy(
                    definition = entry.definition,
                    partOfSpeech = entry.partOfSpeech,
                    synonyms = entry.synonyms.take(5).joinToString(",").takeIf { it.isNotBlank() },
                    rarity = entry.rarity?.name,
                    freqRank = entry.frequency?.rank,
                    freqTotal = entry.frequency?.total,
                    // A word that had no entry when it was saved gets the real answer now.
                    source = entry.source.name,
                    packVersion = entry.packVersion,
                ),
            )
        }
    }

    /** Where the word appears in the book it was saved from, chapter numbers included. */
    suspend fun usesInBook(bookId: String, term: String): WordUses = withContext(Dispatchers.IO) {
        val posts = postDao.matching(bookId, WordForms.normalize(term))
        var total = 0
        val chapters = sortedSetOf<Int>()
        posts.forEach { post ->
            val hits = WordForms.countUses(post.plainText, term)
            if (hits > 0) {
                total += hits
                chapters += post.chapterIndex
            }
        }
        WordUses(chapters = chapters.toList(), count = total)
    }

    /** Writes the whole list as JSON to wherever the reader pointed the file picker. */
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val rows = wordDao.all()
        val export = WordExport(
            exportedAt = System.currentTimeMillis(),
            words = rows.sortedBy { it.createdAt }.map { row ->
                ExportedWord(
                    term = row.term,
                    definition = row.definition,
                    partOfSpeech = row.partOfSpeech,
                    synonyms = row.synonyms?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
                    rarity = row.rarity,
                    freqRank = row.freqRank,
                    freqTotal = row.freqTotal,
                    source = row.source,
                    packVersion = row.packVersion,
                    bookId = row.bookId,
                    bookTitle = row.bookTitle,
                    bookHue = row.bookHue,
                    postId = row.postId,
                    chapterIndex = row.chapterIndex,
                    excerpt = row.excerpt,
                    createdAt = row.createdAt,
                )
            },
        )
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(json.encodeToString(WordExport.serializer(), export).toByteArray())
        } ?: error("Could not open the file")
        rows.size
    }

    /**
     * Merges a file back in. Existing words win, so importing twice is harmless, and a word from a
     * book that no longer exists still arrives with the book title it was saved with.
     */
    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Could not read the file")
        val export = json.decodeFromString(WordExport.serializer(), text)
        val rows = export.words.mapNotNull { word ->
            val key = WordForms.normalize(word.term)
            if (key.isBlank()) return@mapNotNull null
            WordEntity(
                id = UUID.randomUUID().toString(),
                term = word.term,
                termKey = key,
                definition = word.definition,
                partOfSpeech = word.partOfSpeech,
                synonyms = word.synonyms.take(5).joinToString(",").takeIf { it.isNotBlank() },
                rarity = word.rarity,
                freqRank = word.freqRank,
                freqTotal = word.freqTotal,
                source = word.source,
                packVersion = word.packVersion,
                bookId = word.bookId,
                bookTitle = word.bookTitle,
                bookHue = word.bookHue,
                postId = word.postId,
                chapterIndex = word.chapterIndex,
                excerpt = word.excerpt,
                createdAt = if (word.createdAt > 0) word.createdAt else System.currentTimeMillis(),
            )
        }
        // SQLite treats NULL book ids as distinct in a unique index, so the merge is decided here:
        // a custom word that is already on the list must not arrive twice.
        val fresh = rows.filter { wordDao.find(it.termKey, it.bookId) == null }
        wordDao.insertMissing(fresh)
        ImportResult(added = fresh.size, skipped = rows.size - fresh.size)
    }

    private fun toWord(row: WordEntity): Word = Word(
        id = row.id,
        term = row.term,
        definition = row.definition,
        partOfSpeech = row.partOfSpeech,
        synonyms = row.synonyms?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
        rarity = Rarity.fromName(row.rarity),
        freqRank = row.freqRank,
        freqTotal = row.freqTotal,
        bookId = row.bookId,
        bookTitle = row.bookTitle,
        bookHue = row.bookHue,
        postId = row.postId,
        chapterIndex = row.chapterIndex,
        excerpt = row.excerpt,
        createdAt = row.createdAt,
        source = runCatching { LookupSource.valueOf(row.source) }.getOrDefault(LookupSource.NONE),
        // Custom means the reader typed it, which is exactly a word that belongs to no book.
        custom = row.bookId == null,
    )
}
