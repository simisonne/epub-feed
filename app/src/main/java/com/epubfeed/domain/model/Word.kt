package com.epubfeed.domain.model

import androidx.compose.runtime.Immutable
import com.epubfeed.domain.thesaurus.LookupSource
import com.epubfeed.domain.thesaurus.Rarity
import java.text.Collator

/** A saved word, ready to list. */
@Immutable
data class Word(
    val id: String,
    val term: String,
    val definition: String,
    val partOfSpeech: String?,
    val synonyms: List<String>,
    val rarity: Rarity?,
    val bookId: String?,
    val bookTitle: String?,
    val bookHue: Int?,
    val postId: String?,
    val chapterIndex: Int?,
    val excerpt: String?,
    val createdAt: Long,
    val source: LookupSource,
    val custom: Boolean,
    /** Position in the dictionary's frequency list; rank 1 is the commonest word. */
    val freqRank: Int? = null,
    /** How many words that list spans, so two packs on different scales still compare. */
    val freqTotal: Int? = null,
) {
    /**
     * Where the word sits in its own list, 0 = the commonest word, 1 = the rarest. This is the
     * fine grained tie break under the rarity band, which can only be as fine as four steps.
     */
    val rarityPosition: Double
        get() {
            val rank = freqRank ?: return -1.0
            val total = freqTotal ?: return -1.0
            return if (rank > 0 && total > 0) rank.toDouble() / total else -1.0
        }
}

/** How the word list reads. */
enum class WordSort(val label: String) {
    ALPHABETICAL("A to Z"),
    RARITY("Rarity"),
    NEWEST("Newest"),
    BY_BOOK("By book"),
}

/**
 * The one place that decides the order of the word list.
 *
 * It lives in the domain so it can be tested without a database, and so no screen sorts on its
 * own: the list, the book filter and any future list all read the same order.
 */
object WordOrder {

    /** Group heading for words the reader added by hand, which belong to no book. */
    const val CUSTOM_GROUP = "Custom"

    fun sort(words: List<Word>, order: WordSort, collator: Collator = Collator.getInstance()): List<Word> =
        when (order) {
            WordSort.ALPHABETICAL -> words.sortedWith { a, b -> collator.compare(a.term, b.term) }
            WordSort.RARITY -> words.sortedWith(
                // Rarest first: the list is a record of what was worth stopping for.
                compareBy<Word> { bandOrder(it.rarity) }
                    .thenByDescending { it.rarityPosition }
                    .thenComparator { a, b -> collator.compare(a.term, b.term) },
            )

            WordSort.NEWEST -> words.sortedWith(
                compareByDescending<Word> { it.createdAt }
                    .thenComparator { a, b -> collator.compare(a.term, b.term) },
            )

            WordSort.BY_BOOK -> words.sortedWith(
                // Books alphabetically, and the words the reader typed by hand after all of them:
                // a group called "Custom" belongs at the end, not between B and D.
                compareBy<Word> { if (it.bookId == null) 1 else 0 }
                    .thenComparator { a, b -> collator.compare(a.bookTitle.orEmpty(), b.bookTitle.orEmpty()) }
                    .thenComparator { a, b -> collator.compare(a.term, b.term) },
            )
        }

    /** The four bands, rarest first. A word with no frequency data goes last: nothing is known. */
    private fun bandOrder(rarity: Rarity?): Int = when (rarity) {
        Rarity.VERY_RARE -> 0
        Rarity.RARE -> 1
        Rarity.UNCOMMON -> 2
        Rarity.COMMON -> 3
        null -> 4
    }
}
