package com.epubfeed.domain.thesaurus

import java.util.Locale

/** A span of characters in a piece of text. */
data class Span(val start: Int, val end: Int) {
    val length: Int get() = end - start
}

/**
 * Everything about words that is independent of the dictionary and of the UI: which characters
 * count as part of a word, how a selection snaps to whole words, the sentence around it, and the
 * cheap morphology that turns what a reader highlighted ("houses", "ran", "gloaming") into
 * something a dictionary will actually have ("house", "run", "gloam").
 */
object WordForms {

    private val APOSTROPHES = charArrayOf('\'', '\u2019')
    private val INNER = charArrayOf('\'', '\u2019', '-')
    private val WHITESPACE = Regex("\\s+")

    /** Hyphens and apostrophes live inside words, everything else that is not a letter does not. */
    fun isWordChar(c: Char): Boolean = c.isLetter() || c in INNER

    fun normalize(raw: String): String {
        val trimmed = raw.trim().trim { !it.isLetter() && it !in APOSTROPHES }
        return trimmed.replace(WHITESPACE, " ").lowercase(Locale.ROOT)
    }

    /**
     * Grows a selection out to whole words. A handle that lands in the middle of "gloaming" looks
     * up the whole word, never "gloa".
     */
    fun snap(text: String, start: Int, end: Int): Span? {
        var s = start.coerceIn(0, text.length)
        var e = end.coerceIn(0, text.length)
        if (s > e) {
            val swap = s
            s = e
            e = swap
        }
        while (s > 0 && isWordChar(text[s - 1])) s--
        while (e < text.length && isWordChar(text[e])) e++
        while (s < e && !text[s].isLetter()) s++
        while (e > s && !text[e - 1].isLetter()) e--
        return if (e > s) Span(s, e) else null
    }

    fun wordAt(text: String, offset: Int): Span? = snap(text, offset, offset)

    /**
     * The sentence the word sits in, trimmed to something that fits a card. Used as the reminder
     * of where the word came from.
     */
    fun sentenceAt(text: String, span: Span): String {
        val stops = charArrayOf('.', '!', '?', '\n')
        var start = span.start
        while (start > 0 && text[start - 1] !in stops) start--
        var end = span.end
        while (end < text.length && text[end] !in stops) end++
        if (end < text.length && text[end] == '.') end++
        var sentence = text.substring(start, end).replace(WHITESPACE, " ").trim()
        if (sentence.length > 220) {
            val from = (span.start - start - 60).coerceAtLeast(0)
            val to = (from + 220).coerceAtMost(sentence.length)
            sentence = "\u2026" + sentence.substring(from, to).trim() + "\u2026"
        }
        return sentence
    }

    fun countUses(text: String, term: String): Int {
        if (term.isBlank()) return 0
        return termPattern(term).findAll(text).count()
    }

    fun containsWord(text: String, term: String): Boolean = termPattern(term).containsMatchIn(text)

    /**
     * Korean has no spaces before its particles, so the end of a word is not a boundary there:
     * "사랑" is used by every "사랑은", "사랑이" and "사랑을". Every other language matches whole
     * words only, or "art" would count inside "earth".
     */
    private fun termPattern(term: String): Regex = if (isHangul(term)) {
        Regex("(?<![\\p{L}])" + Regex.escape(term), RegexOption.IGNORE_CASE)
    } else {
        Regex("(?<![\\p{L}])" + Regex.escape(term) + "(?![\\p{L}])", RegexOption.IGNORE_CASE)
    }

    private val IRREGULAR = mapOf(
        "ran" to "run", "went" to "go", "gone" to "go", "better" to "good", "best" to "good",
        "was" to "be", "were" to "be", "been" to "be", "is" to "be", "are" to "be", "am" to "be",
        "had" to "have", "has" to "have", "said" to "say", "made" to "make", "took" to "take",
        "taken" to "take", "came" to "come", "saw" to "see", "seen" to "see", "thought" to "think",
        "knew" to "know", "known" to "know", "wrote" to "write", "written" to "write",
        "spoke" to "speak", "spoken" to "speak", "brought" to "bring", "bought" to "buy",
        "felt" to "feel", "kept" to "keep", "left" to "leave", "lost" to "lose", "met" to "meet",
        "paid" to "pay", "sold" to "sell", "sent" to "send", "sat" to "sit", "stood" to "stand",
        "told" to "tell", "understood" to "understand", "wore" to "wear", "worn" to "wear",
        "won" to "win", "began" to "begin", "begun" to "begin", "drank" to "drink",
        "drove" to "drive", "driven" to "drive", "ate" to "eat", "eaten" to "eat",
        "fell" to "fall", "fallen" to "fall", "gave" to "give", "given" to "give",
        "grew" to "grow", "grown" to "grow", "heard" to "hear", "held" to "hold",
        "led" to "lead", "meant" to "mean", "rode" to "ride", "ridden" to "ride",
        "rose" to "rise", "risen" to "rise", "sang" to "sing", "sung" to "sing",
        "slept" to "sleep", "spent" to "spend", "swam" to "swim", "swum" to "swim",
        "threw" to "throw", "thrown" to "throw", "woke" to "wake", "woken" to "wake",
        "men" to "man", "women" to "woman", "feet" to "foot", "teeth" to "tooth",
        "mice" to "mouse", "children" to "child", "people" to "person", "lives" to "life",
        "wives" to "wife", "knives" to "knife", "leaves" to "leaf", "halves" to "half",
        "wolves" to "wolf", "selves" to "self", "dice" to "die", "geese" to "goose",
    )

    /**
     * Korean writes its particles and endings onto the word, so what a reader selects is almost
     * never what the dictionary holds: "사랑은" has to be looked up as "사랑". Longer suffixes come
     * first, or "에서" would be cut down to "에" and miss the noun.
     */
    private val KOREAN_SUFFIXES = listOf(
        "이라고", "이라는", "에서는", "에게서", "으로서", "으로써", "입니다",
        "에서", "에게", "으로", "부터", "까지", "하고", "한다", "했다", "이다", "였다",
        "았다", "었다", "은", "는", "이", "가", "을", "를", "의", "에", "도", "만",
        "와", "과", "로", "나", "랑", "께", "다", "요", "고", "서", "며", "면", "지", "게", "기",
    ).sortedByDescending { it.length }

    fun isHangul(text: String): Boolean = text.any { it in '\uAC00'..'\uD7A3' }

    /**
     * What a selection should be called once it is looked up.
     *
     * Korean glues its particle onto the word, so a reader who selects "사랑은" is asking about
     * "사랑". The entry that answered is that word, and the card, the saved row and the list all
     * say it. English keeps what was selected, because "houses" is a word a reader chose to keep.
     */
    fun shownTerm(raw: String, matched: String): String {
        val key = normalize(raw)
        val selected = raw.trim()
        if (key.isEmpty() || !isHangul(key)) return selected
        if (matched == key || !key.startsWith(matched)) return selected
        return matched
    }

    /**
     * What the dictionary might actually hold, most likely first. A phrase falls back to its words
     * so a two word selection still produces an answer.
     */
    fun candidates(raw: String, limit: Int = 6): List<String> {
        val key = normalize(raw)
        if (key.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        out += key
        IRREGULAR[key]?.let { out += it }
        val parts = key.split(' ').filter { it.isNotBlank() }
        if (parts.size > 1) parts.forEach { out += it }

        fun add(base: String) {
            if (base.length >= 2) out += base
        }

        if (key.length > 4 && key.endsWith("ies")) add(key.dropLast(3) + "y")
        if (key.length > 4 && key.endsWith("es")) add(key.dropLast(2))
        if (key.length > 3 && key.endsWith("s") && !key.endsWith("ss") && !key.endsWith("us")) {
            add(key.dropLast(1))
        }
        if (key.length > 5 && key.endsWith("ing")) {
            val base = key.dropLast(3)
            add(base)
            add(base + "e")
            if (base.length > 2 && base[base.length - 1] == base[base.length - 2]) add(base.dropLast(1))
        }
        if (key.length > 4 && key.endsWith("ed")) {
            val base = key.dropLast(2)
            add(base)
            add(key.dropLast(1))
            if (base.length > 2 && base[base.length - 1] == base[base.length - 2]) add(base.dropLast(1))
        }
        if (key.length > 5 && key.endsWith("ly")) {
            add(key.dropLast(2))
            if (key.endsWith("ily")) add(key.dropLast(3) + "y")
        }
        if (key.length > 5 && key.endsWith("ness")) add(key.dropLast(4))
        if (key.length > 5 && key.endsWith("ment")) add(key.dropLast(4))

        if (isHangul(key)) {
            for (suffix in KOREAN_SUFFIXES) {
                if (key.length > suffix.length + 1 && key.endsWith(suffix)) {
                    add(key.dropLast(suffix.length))
                }
            }
        }
        return out.take(limit)
    }
}
