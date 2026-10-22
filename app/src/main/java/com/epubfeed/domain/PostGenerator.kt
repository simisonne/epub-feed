package com.epubfeed.domain

import com.epubfeed.domain.model.Block
import com.epubfeed.domain.model.BlockType
import com.epubfeed.domain.model.PostLength
import com.epubfeed.domain.model.Run
import javax.inject.Inject
import javax.inject.Singleton

/** A generated post, ready to be written to Room. */
data class GeneratedPost(
    val indexInChapter: Int,
    val isChapterTitlePost: Boolean,
    val blocks: List<Block>,
    val firstBlock: Int,
    val lastBlock: Int,
    val plainText: String,
)

/**
 * Turns a chapter's blocks into a thread of posts that feel like an X timeline.
 *
 * Rules:
 *  - the chapter always opens with a dedicated title post (the thread opener),
 *  - blocks are grouped greedily until the character budget is reached,
 *  - blocks are never cut in half: an oversized paragraph is split on sentence
 *    boundaries only, and a paragraph with no usable sentence boundary stays whole.
 */
@Singleton
class PostGenerator @Inject constructor() {

    fun generate(
        chapterTitle: String,
        blocks: List<Block>,
        length: PostLength,
    ): List<GeneratedPost> {
        val budget = length.budget
        val units = expandBlocks(blocks, budget)
        val out = mutableListOf<GeneratedPost>()

        val opener = GeneratedPost(
            indexInChapter = 0,
            isChapterTitlePost = true,
            blocks = listOf(
                Block(
                    type = BlockType.HEADING,
                    runs = listOf(Run(text = chapterTitle.trim(), bold = true)),
                ),
            ),
            firstBlock = -1,
            lastBlock = -1,
            plainText = chapterTitle.trim(),
        )
        out += opener

        var current = mutableListOf<Unit>()
        var currentChars = 0

        fun flush() {
            if (current.isEmpty()) return
            val idx = out.size
            out += GeneratedPost(
                indexInChapter = idx,
                isChapterTitlePost = false,
                blocks = current.map { it.block },
                firstBlock = current.first().sourceIndex,
                lastBlock = current.last().sourceIndex,
                plainText = plainTextOf(current.map { it.block }),
            )
            current = mutableListOf()
            currentChars = 0
        }

        for (unit in units) {
            val cost = unit.length
            if (current.isNotEmpty() && currentChars + cost > budget) flush()
            current += unit
            currentChars += cost
        }
        flush()
        return out
    }

    private data class Unit(val block: Block, val sourceIndex: Int) {
        val length: Int get() = block.text.length
    }

    /**
     * Splits blocks that are far over budget into sentence sized blocks so a very
     * long paragraph still produces a couple of readable posts.
     */
    private fun expandBlocks(blocks: List<Block>, budget: Int): List<Unit> {
        val out = ArrayList<Unit>(blocks.size * 2)
        val hardLimit = budget * 2
        blocks.forEachIndexed { index, block ->
            val text = block.text
            if (block.type == BlockType.QUOTE || text.length <= hardLimit) {
                out += Unit(block, index)
                return@forEachIndexed
            }
            val sentences = SentenceSplitter.split(text)
            if (sentences.size <= 1) {
                out += Unit(block, index)
                return@forEachIndexed
            }
            var buffer = StringBuilder()
            sentences.forEach { sentence ->
                if (buffer.isNotEmpty() && buffer.length + sentence.length + 1 > budget) {
                    out += Unit(Block(block.type, listOf(Run(buffer.toString().trim()))), index)
                    buffer = StringBuilder()
                }
                if (buffer.isNotEmpty()) buffer.append(' ')
                buffer.append(sentence)
            }
            if (buffer.isNotEmpty()) {
                out += Unit(Block(block.type, listOf(Run(buffer.toString().trim()))), index)
            }
        }
        return out
    }

    private fun plainTextOf(blocks: List<Block>): String =
        blocks.joinToString("\n\n") { it.text.trim() }.trim()
}

/**
 * Conservative sentence splitter. It only reports a split when the punctuation really
 * ends a sentence: common abbreviations and initials are ignored, and a lowercase
 * continuation after the punctuation never counts as a boundary.
 */
object SentenceSplitter {

    private val ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "mt", "vs", "etc", "e.g", "i.e",
        "no", "vol", "pp", "ch", "fig", "inc", "ltd", "co", "capt", "col", "gen", "lt", "sgt",
        "rev", "hon", "messrs", "mm", "mme", "mlle", "fol", "ed", "eds", "op", "cf", "al",
    )

    private val CLOSERS = charArrayOf('"', '\'', '\u201D', '\u2019', ')', ']', '\u00BB', '.', '!', '?')

    fun split(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val out = ArrayList<String>()
        var start = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '.' || c == '!' || c == '?') {
                var j = i + 1
                while (j < text.length && text[j] in CLOSERS) j++
                val end = j
                val followedByBreak = end >= text.length || text[end].isWhitespace()
                if (followedByBreak && isBoundary(text, i, end)) {
                    val piece = text.substring(start, end).trim()
                    if (piece.isNotEmpty()) out += piece
                    start = end
                    i = end
                    continue
                }
                i = end
                continue
            }
            i++
        }
        if (start < text.length) {
            val tail = text.substring(start).trim()
            if (tail.isNotEmpty()) out += tail
        }
        return out
    }

    private fun isBoundary(text: String, punctIndex: Int, end: Int): Boolean {
        if (text[punctIndex] != '.') {
            return nextStartsSentence(text, end)
        }
        // Word directly before the dot, ignoring an immediately preceding single letter (initials).
        var k = punctIndex - 1
        while (k >= 0 && (text[k].isLetter() || text[k] == '.')) k--
        val word = text.substring(k + 1, punctIndex).lowercase().trim('.')
        if (word.isEmpty()) return false
        if (word.length == 1) return false // initial, as in "J. Austen"
        if (word in ABBREVIATIONS) return false
        return nextStartsSentence(text, end)
    }

    private fun nextStartsSentence(text: String, from: Int): Boolean {
        var k = from
        while (k < text.length && text[k].isWhitespace()) k++
        if (k >= text.length) return true
        val next = text[k]
        return !next.isLowerCase()
    }
}
