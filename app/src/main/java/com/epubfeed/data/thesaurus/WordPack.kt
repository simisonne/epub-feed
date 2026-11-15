package com.epubfeed.data.thesaurus

import com.epubfeed.domain.thesaurus.Frequency
import com.epubfeed.domain.thesaurus.Rarity

/** One dictionary sense as the pack stores it. */
data class PackSense(
    val partOfSpeech: String,
    val definition: String,
    val synonyms: List<String>,
)

/**
 * A dictionary that can answer a lookup. Every language pack implements this, so nothing above
 * this line knows which file answered or what language it holds.
 */
interface WordPack {

    val version: String

    /** The language the pack holds, matching a [PackSpec] language code. */
    val language: String

    /** Senses of [termKey], most common sense first. */
    fun senses(termKey: String, limit: Int = 3): List<PackSense>

    /** The word's place in the pack's frequency list, or null when the list does not hold it. */
    fun frequency(termKey: String): Frequency?

    fun close()
}

/** Words outside the frequency list are at least as rare as the end of it. */
internal fun WordPack.rarityOf(termKey: String): Rarity? =
    frequency(termKey)?.let { Rarity.fromRank(it.rank, it.total) } ?: Rarity.notListed()

internal fun posName(code: Int): String = when (code) {
    0 -> "noun"
    1 -> "verb"
    2 -> "adjective"
    3 -> "adverb"
    else -> "word"
}
