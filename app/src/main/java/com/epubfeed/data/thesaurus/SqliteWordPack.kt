package com.epubfeed.data.thesaurus

import android.database.sqlite.SQLiteDatabase
import com.epubfeed.domain.thesaurus.Frequency
import java.io.File

/**
 * One installed language pack: a read only SQLite file built by `tools/build-thesaurus-pack.py`
 * (WordNet) or `tools/build-thesaurus-pack-wiktionary.py` (Wiktionary extracts).
 *
 * Senses are ordered by how often the part of speech was actually tagged in a corpus, so "ephemeral"
 * answers as an adjective instead of as its rare insect noun. Synonyms are the other words in the
 * same synset, with the looked up word and duplicates removed.
 */
class SqliteWordPack private constructor(
    private val db: SQLiteDatabase,
    override val version: String,
    override val language: String,
) : WordPack {

    private val frequencyTotal: Int by lazy {
        runCatching {
            db.rawQuery("SELECT rows FROM freq_total LIMIT 1", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }.getOrDefault(0)
    }

    override fun senses(termKey: String, limit: Int): List<PackSense> = runCatching {
        db.rawQuery(SENSES_SQL, arrayOf(termKey, limit.toString())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val pos = posName(cursor.getInt(0))
                    val lemmas = cursor.getString(1).orEmpty().split(' ')
                    val definition = cursor.getString(2).orEmpty().trim()
                    val synonyms = lemmas
                        .asSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.equals(termKey, ignoreCase = true) }
                        .distinct()
                        .take(5)
                        .toList()
                    if (definition.isNotEmpty()) {
                        add(PackSense(partOfSpeech = pos, definition = definition, synonyms = synonyms))
                    }
                }
            }
        }
    }.onFailure { android.util.Log.w("epubfeed-pack", "senses($termKey) failed: ${it.message}") }
        .getOrDefault(emptyList())

    override fun frequency(termKey: String): Frequency? = runCatching {
        val rank = db.rawQuery("SELECT rank FROM freq WHERE word = ? LIMIT 1", arrayOf(termKey))
            .use { if (it.moveToFirst()) it.getInt(0) else null }
        val total = frequencyTotal
        if (rank == null || total <= 0) null else Frequency(rank = rank, total = total)
    }.onFailure { android.util.Log.w("epubfeed-pack", "frequency($termKey) failed: ${it.message}") }
        .getOrNull()

    override fun close() {
        runCatching { db.close() }
    }

    companion object {
        /**
         * The part of speech tie break comes from `pos_tags`, which only holds lemmas whose senses
         * were actually tagged: an untagged lemma falls back to the pack's sense order.
         */
        private const val SENSES_SQL = """
            SELECT s.pos, s.lemmas, s.gloss
            FROM senses e
            JOIN synsets s ON s.id = e.synset
            LEFT JOIN pos_tags t ON t.lemma = e.lemma AND t.pos = s.pos
            WHERE e.lemma = ?
            GROUP BY e.synset
            ORDER BY COALESCE(t.tagged, 0) DESC, e.seq
            LIMIT ?
        """

        fun open(file: File): SqliteWordPack? = runCatching {
            require(file.isFile && file.length() > 0)
            val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            val meta = { key: String ->
                db.rawQuery("SELECT value FROM meta WHERE key = ? LIMIT 1", arrayOf(key))
                    .use { if (it.moveToFirst()) it.getString(0) else null }
            }
            SqliteWordPack(
                db = db,
                version = meta("version") ?: "unknown",
                language = meta("language") ?: "en",
            )
        }.getOrNull()
    }
}
