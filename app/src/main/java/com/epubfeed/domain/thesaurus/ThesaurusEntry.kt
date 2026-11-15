package com.epubfeed.domain.thesaurus

import androidx.compose.runtime.Immutable

/**
 * How common a word is, as a band instead of a number. A percentage or a "1 in 3,900" line would
 * be invented precision: the frequency data is a ranking of a few hundred thousand words and the
 * useful signal is "is this word worth learning", not a decimal.
 */
enum class Rarity(val label: String) {
    COMMON("common"),
    UNCOMMON("uncommon"),
    RARE("rare"),
    VERY_RARE("very rare"),
    ;

    companion object {
        /** Bands from the rank inside the pack's frequency list, rank 1 being the commonest word. */
        fun fromRank(rank: Int?, total: Int): Rarity? {
            if (rank == null || rank <= 0 || total <= 0) return null
            val position = rank.toDouble() / total
            return when {
                position < 0.02 -> COMMON
                position < 0.20 -> UNCOMMON
                position < 0.60 -> RARE
                else -> VERY_RARE
            }
        }

        /** Words outside the frequency list are at least as rare as the end of it. */
        fun notListed(): Rarity = RARE

        fun fromName(name: String?): Rarity? = entries.firstOrNull { it.name == name }
    }
}

/** Which side answered the lookup. ONLINE is only kept so words saved by older versions read back. */
enum class LookupSource { PACK, ONLINE, NONE }

/**
 * Where a word sits in the pack's frequency list. It is kept next to the band because the band is
 * only four steps wide: two words both "rare" still differ by tens of thousands of places, and that
 * is the value the word list sorts on.
 */
@Immutable
data class Frequency(val rank: Int, val total: Int) {
    val position: Double get() = if (rank > 0 && total > 0) rank.toDouble() / total else 1.0
}

/** One dictionary answer, ready to show and ready to save. */
@Immutable
data class ThesaurusEntry(
    val term: String,
    val partOfSpeech: String?,
    val definition: String,
    val synonyms: List<String>,
    val rarity: Rarity?,
    val source: LookupSource,
    val packVersion: String?,
    val frequency: Frequency? = null,
)

sealed interface LookupOutcome {

    data class Found(val entry: ThesaurusEntry) : LookupOutcome

    /** Nothing answered. [noDictionary] is true when not even a dictionary is installed. */
    data class Missing(val term: String, val noDictionary: Boolean = false) : LookupOutcome
}

/** Where a word is used inside one book. */
@Immutable
data class WordUses(val chapters: List<Int>, val count: Int) {
    val label: String
        get() = when {
            count <= 0 -> ""
            count == 1 -> "1 use in this book"
            chapters.isEmpty() -> "$count uses in this book"
            else -> "$count uses, " + chapterList(chapters)
        }

    /** "chapter 4", "chapters 2 and 7", "chapters 1, 7 and 8", then just a count. */
    private fun chapterList(chapters: List<Int>): String {
        val shown = chapters.take(MAX_CHAPTERS).sorted()
        val rest = chapters.size - shown.size
        val numbers = shown.map { (it + 1).toString() }
        return when {
            numbers.size == 1 -> "chapter ${numbers.first()}"
            rest > 0 -> "chapters " + numbers.joinToString(", ") + " and $rest more"
            else -> "chapters " + numbers.dropLast(1).joinToString(", ") + " and " + numbers.last()
        }
    }

    private companion object {
        const val MAX_CHAPTERS = 3
    }
}
