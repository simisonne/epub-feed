package com.epubfeed.data.thesaurus

import com.epubfeed.domain.thesaurus.LookupOutcome
import com.epubfeed.domain.thesaurus.LookupSource
import com.epubfeed.domain.thesaurus.Rarity
import com.epubfeed.domain.thesaurus.ThesaurusEntry
import com.epubfeed.domain.thesaurus.WordForms
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers a lookup from the installed dictionaries, the reader's language first and the rest after
 * it. Everything here is a file on the phone: reading happens on trains and planes, and a feature
 * that needs the network is a feature that fails exactly when a reader is inside a book.
 */
@Singleton
class ThesaurusRepository @Inject constructor(
    private val packs: PackManager,
) {

    suspend fun lookup(rawTerm: String): LookupOutcome {
        val display = rawTerm.trim()
        if (display.isBlank()) return LookupOutcome.Missing(display)
        val candidates = WordForms.candidates(display)
        if (candidates.isEmpty()) return LookupOutcome.Missing(display)

        val installed = packs.openAll()
        for (pack in installed) {
            for (candidate in candidates) {
                val first = pack.senses(candidate, limit = 2).firstOrNull() ?: continue
                val frequency = pack.frequency(candidate)
                return LookupOutcome.Found(
                    ThesaurusEntry(
                        term = display,
                        partOfSpeech = first.partOfSpeech,
                        definition = first.definition,
                        synonyms = first.synonyms,
                        rarity = frequency?.let { Rarity.fromRank(it.rank, it.total) } ?: Rarity.notListed(),
                        source = LookupSource.PACK,
                        packVersion = pack.version,
                        frequency = frequency,
                    ),
                )
            }
        }

        return LookupOutcome.Missing(display, noDictionary = installed.isEmpty())
    }
}
