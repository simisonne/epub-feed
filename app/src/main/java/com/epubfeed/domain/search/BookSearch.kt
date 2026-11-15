package com.epubfeed.domain.search

import com.epubfeed.domain.thesaurus.Span
import com.epubfeed.domain.thesaurus.WordForms
import java.util.Locale

/** One post as the search sees it: where it sits in the book, and the text to search. */
data class SearchText(
    val postId: String,
    val chapterIndex: Int,
    val indexInChapter: Int,
    val globalIndex: Int,
    val text: String,
)

/** A post that holds the query, with the passage to show and the words to mark inside it. */
data class SearchHit(
    val postId: String,
    val chapterIndex: Int,
    val indexInChapter: Int,
    val globalIndex: Int,
    val excerpt: String,
    /** Matched ranges of [excerpt], ready to highlight. */
    val marks: List<Span>,
    val occurrences: Int,
    val phrase: Boolean,
)

/** What one query found. */
data class SearchResult(
    val hits: List<SearchHit> = emptyList(),
    /** The words of the query, in the order they were typed. */
    val terms: List<String> = emptyList(),
    /** True when the hits hold every word. False means the loose pass ran: some words only. */
    val everyTerm: Boolean = true,
    /** Every post that matched, including the ones past the cap, so the count is the real one. */
    val total: Int = 0,
    /** True when there were more hits than the cap. */
    val capped: Boolean = false,
)

/**
 * Search inside one book.
 *
 * The rules that make it good at finding a passage rather than at matching bytes:
 *
 * - Every word of the query must appear in the post, as a whole word. "art" never matches
 *   "earth", and "glass bead" does not match a post that only says "glass".
 * - Whole words are matched through the same inflection guesses the lookup uses, so searching
 *   "houses" still finds "house" and "ran" finds "run". An exact hit always outranks a guess.
 * - A query of several adjacent words that appears in that order earns the biggest bonus, so the
 *   post that says "the glass bead game" beats the one that mentions the three words far apart.
 * - Ties break by where the post sits in the book, so results read in reading order.
 * - When no post holds every word, the same pass runs again with "any word" and the result says
 *   so, instead of showing an empty screen while a word is clearly in the book.
 */
object BookSearch {

    /** Longer queries are cut: nobody types more, and a thousand regexes per post would crawl. */
    private const val MAX_TERMS = 8
    private const val VARIANTS_PER_TERM = 4
    private const val EXCERPT_CHARS = 220
    private const val SENTENCE_CHARS = 420
    const val DEFAULT_LIMIT = 200

    private val SEPARATORS = Regex("[^\\p{L}\\p{N}'\\u2019-]+")
    private val STOPS = charArrayOf('.', '!', '?', '\u2026', '\n', '\r')

    /** The query split into lowercase words, in order, without repeats. */
    fun tokenize(query: String): List<String> = query
        .split(SEPARATORS)
        .map { it.trim('\'', '\u2019', '-', ' ') }
        .filter { it.isNotEmpty() }
        .map { it.lowercase(Locale.ROOT) }
        .distinct()
        .take(MAX_TERMS)

    fun run(posts: List<SearchText>, query: String, limit: Int = DEFAULT_LIMIT): SearchResult {
        val terms = tokenize(query)
        if (terms.isEmpty()) return SearchResult()
        val plans = terms.map { TermPlan.build(it) }
        val phrase = phrasePattern(terms)

        val strict = ArrayList<Scored>()
        val loose = ArrayList<Scored>()
        for (post in posts) {
            if (post.text.isBlank()) continue
            // Lowercase once per post, then match lowercase forms. Every pattern is anchored to
            // word boundaries, so the regexes are cheap and never match inside a longer word.
            val text = post.text.lowercase(Locale.ROOT)
            val matched = ArrayList<List<Span>>(plans.size)
            var exactTerms = 0
            var any = false
            for (plan in plans) {
                val hits = plan.locate(text)
                if (hits.isNotEmpty()) {
                    any = true
                    if (plan.exactHit) exactTerms++
                }
                matched += hits
            }
            if (!any) continue
            val covered = matched.count { it.isNotEmpty() }
            if (covered < plans.size) {
                loose += Scored(post, matched, covered, exactTerms, phrase = false)
            } else {
                strict += Scored(
                    post = post,
                    hitsByTerm = matched,
                    covered = covered,
                    exactTerms = exactTerms,
                    phrase = phrase != null && phrase.containsMatchIn(text),
                )
            }
        }

        // False only when the hits on screen are a loose match. A book with nothing at all is not
        // a relaxed answer, so an empty result stays "every term" and the screen says nothing found.
        val everyTerm = strict.isNotEmpty() || loose.isEmpty()
        val pool = if (strict.isNotEmpty()) strict else loose
        val ranked = pool.sortedWith(
            compareByDescending<Scored> { it.score }.thenBy { it.post.globalIndex },
        )
        return SearchResult(
            hits = ranked.take(limit).map { it.toHit() },
            terms = terms,
            everyTerm = everyTerm,
            total = ranked.size,
            capped = ranked.size > limit,
        )
    }

    /** One form of one query word, with its word boundary pattern compiled once per query. */
    private class Variant(val form: String, val exact: Boolean, val pattern: Regex)

    /** One word of the query: its exact form, then the inflections a dictionary would hold. */
    private class TermPlan(private val variants: List<Variant>) {

        var exactHit: Boolean = false
            private set

        fun locate(text: String): List<Span> {
            exactHit = false
            val spans = ArrayList<Span>()
            for (variant in variants) {
                // A plain contains check first: most words are not in a given post, and a failed
                // contains is far cheaper than running the regex.
                if (!text.contains(variant.form)) continue
                val found = variant.pattern.findAll(text)
                    .map { Span(it.range.first, it.range.last + 1) }
                    .toList()
                if (found.isNotEmpty() && variant.exact) exactHit = true
                spans += found
            }
            return if (spans.size <= 1) spans else merge(spans)
        }

        companion object {
            fun build(term: String): TermPlan {
                val forms = LinkedHashSet<String>().apply {
                    // The exact word must be first: that order is what makes it outrank a guess.
                    add(term)
                    addAll(
                        WordForms.candidates(term, limit = VARIANTS_PER_TERM)
                            .ifEmpty { listOf(term) }
                            .map { it.lowercase(Locale.ROOT) },
                    )
                }
                return TermPlan(
                    forms.map { form -> Variant(form, form == term, wordPattern(form)) },
                )
            }
        }
    }

    private class Scored(
        val post: SearchText,
        val hitsByTerm: List<List<Span>>,
        val covered: Int,
        val exactTerms: Int,
        val phrase: Boolean,
    ) {
        private val occurrences = hitsByTerm.sumOf { it.size }

        /** Phrase first, then how many words were exact, then how often the words appear. */
        val score: Int =
            (if (phrase) 4_000 else 0) +
                exactTerms * 400 +
                covered * 200 +
                minOf(occurrences, 12) * 20

        fun toHit(): SearchHit {
            val spans = hitsByTerm.flatten().sortedBy { it.start }
            val (excerpt, marks) = excerptOf(post.text, spans)
            return SearchHit(
                postId = post.postId,
                chapterIndex = post.chapterIndex,
                indexInChapter = post.indexInChapter,
                globalIndex = post.globalIndex,
                excerpt = excerpt,
                marks = marks,
                occurrences = occurrences,
                phrase = phrase,
            )
        }
    }

    /**
     * A word is matched whole, so "art" never matches "earth". Korean is the exception: particles
     * are glued on, so searching 사랑 has to find 사랑은, 사랑이 and 사랑을 or most of a Korean book
     * is unreachable. The start boundary still holds.
     */
    private fun wordPattern(form: String): Regex = if (WordForms.isHangul(form)) {
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(form))
    } else {
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(form) + "(?![\\p{L}\\p{N}])")
    }

    /** Overlapping hits from two variants of one word become one highlight. */
    private fun merge(spans: List<Span>): List<Span> {
        val sorted = spans.sortedBy { it.start }
        val merged = ArrayList<Span>(sorted.size)
        for (span in sorted) {
            val last = merged.lastOrNull()
            if (last != null && span.start <= last.end) {
                if (span.end > last.end) merged[merged.size - 1] = Span(last.start, span.end)
            } else {
                merged += span
            }
        }
        return merged
    }

    private fun phrasePattern(terms: List<String>): Regex? {
        if (terms.size < 2) return null
        val body = terms.joinToString("\\s+") { Regex.escape(it) }
        return Regex("(?<![\\p{L}\\p{N}])$body(?![\\p{L}\\p{N}])")
    }

    /**
     * The sentence that holds the most matches, cut to a card sized window that still contains
     * one of them. The marks come back already shifted onto the excerpt.
     */
    private fun excerptOf(text: String, spans: List<Span>): Pair<String, List<Span>> {
        if (spans.isEmpty()) return text.take(EXCERPT_CHARS) to emptyList()
        var window = bestSentence(text, spans) ?: Span(0, text.length)
        if (window.length > EXCERPT_CHARS) {
            val anchor = spans.firstOrNull { it.start >= window.start }?.start ?: window.start
            val start = (anchor - EXCERPT_CHARS / 3)
                .coerceIn(window.start, (window.end - EXCERPT_CHARS).coerceAtLeast(window.start))
            window = Span(start, minOf(window.end, start + EXCERPT_CHARS))
        }
        var start = window.start
        var end = window.end
        if (start > 0) while (start < end && WordForms.isWordChar(text[start])) start++
        if (end < text.length) while (end > start && WordForms.isWordChar(text[end - 1])) end--
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--

        val lead = if (start > 0) 1 else 0
        val excerpt = buildString {
            if (lead > 0) append('\u2026')
            append(text, start, end)
            if (end < text.length) append('\u2026')
        }
        val marks = spans.mapNotNull { span ->
            val from = maxOf(span.start, start)
            val to = minOf(span.end, end)
            if (to <= from) null else Span(from - start + lead, to - start + lead)
        }
        return excerpt to marks
    }

    /** Sentences long enough to hold a paragraph are cut so one hit cannot drag in the whole page. */
    private fun bestSentence(text: String, spans: List<Span>): Span? {
        var best: Span? = null
        var bestScore = -1
        var start = 0
        var i = 0
        while (i < text.length) {
            val atStop = text[i] in STOPS
            val atLimit = i - start >= SENTENCE_CHARS && text[i] == ' '
            if (!atStop && !atLimit) {
                i++
                continue
            }
            val end = i + 1
            val score = spans.count { it.start >= start && it.end <= end }
            if (score > bestScore) {
                bestScore = score
                best = Span(start, end)
            }
            start = end
            i = end
        }
        if (start < text.length) {
            val score = spans.count { it.start >= start }
            if (score > bestScore) best = Span(start, text.length)
        }
        return best
    }
}
