package com.epubfeed

import com.epubfeed.domain.model.Word
import com.epubfeed.domain.model.WordOrder
import com.epubfeed.domain.model.WordSort
import com.epubfeed.domain.thesaurus.LookupSource
import com.epubfeed.domain.thesaurus.Rarity
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WordOrderTest {

    private fun word(
        term: String,
        rarity: Rarity? = null,
        rank: Int? = null,
        total: Int? = null,
        book: String? = null,
        createdAt: Long = 0,
    ) = Word(
        id = term,
        term = term,
        definition = "",
        partOfSpeech = null,
        synonyms = emptyList(),
        rarity = rarity,
        bookId = book,
        bookTitle = book,
        bookHue = null,
        postId = null,
        chapterIndex = null,
        excerpt = null,
        createdAt = createdAt,
        source = LookupSource.PACK,
        custom = book == null,
        freqRank = rank,
        freqTotal = total,
    )

    private val collator = java.text.Collator.getInstance(Locale.US)

    @Test
    fun `alphabetical is the default order`() {
        val words = listOf(word("gloaming"), word("ephemeral"), word("house"))
        assertEquals(
            listOf("ephemeral", "gloaming", "house"),
            WordOrder.sort(words, WordSort.ALPHABETICAL, collator).map { it.term },
        )
    }

    @Test
    fun `rarity puts the rarest first and the unknown last`() {
        val words = listOf(
            word("common", Rarity.COMMON, rank = 100, total = 100_000),
            word("unknown"),
            word("rare", Rarity.RARE, rank = 40_000, total = 100_000),
            word("very", Rarity.VERY_RARE, rank = 90_000, total = 100_000),
        )
        assertEquals(
            listOf("very", "rare", "common", "unknown"),
            WordOrder.sort(words, WordSort.RARITY, collator).map { it.term },
        )
    }

    @Test
    fun `within one band the deeper rank comes first`() {
        val words = listOf(
            word("shallower", Rarity.RARE, rank = 30_000, total = 100_000),
            word("deeper", Rarity.RARE, rank = 55_000, total = 100_000),
        )
        assertEquals(
            listOf("deeper", "shallower"),
            WordOrder.sort(words, WordSort.RARITY, collator).map { it.term },
        )
    }

    @Test
    fun `the rank is compared on its own scale, not as a raw number`() {
        // 30,000 of 50,000 is deeper in its list than 30,000 of 200,000.
        val short = word("shortlist", Rarity.RARE, rank = 30_000, total = 50_000)
        val long = word("longlist", Rarity.RARE, rank = 30_000, total = 200_000)
        assertEquals(
            listOf("shortlist", "longlist"),
            WordOrder.sort(listOf(long, short), WordSort.RARITY, collator).map { it.term },
        )
    }

    @Test
    fun `newest reads by save time`() {
        val words = listOf(
            word("old", createdAt = 1),
            word("new", createdAt = 3),
            word("middle", createdAt = 2),
        )
        assertEquals(
            listOf("new", "middle", "old"),
            WordOrder.sort(words, WordSort.NEWEST, collator).map { it.term },
        )
    }

    @Test
    fun `by book keeps a book together and puts the custom words last`() {
        val words = listOf(
            word("beta", book = "A Book"),
            word("alpha", book = "A Book"),
            word("mine"),
            word("gamma", book = "Z Book"),
        )
        assertEquals(
            listOf("alpha", "beta", "gamma", "mine"),
            WordOrder.sort(words, WordSort.BY_BOOK, collator).map { it.term },
        )
    }
}
