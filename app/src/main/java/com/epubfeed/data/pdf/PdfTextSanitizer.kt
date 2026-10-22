package com.epubfeed.data.pdf

import com.epubfeed.domain.model.Block
import com.epubfeed.domain.model.BlockType
import com.epubfeed.domain.model.Run

/**
 * Turns raw PDF text (hard wrapped lines, page numbers, hyphenation) into readable
 * paragraphs. PDF extraction gives no structure, so paragraphs are inferred from
 * blank lines and from lines that look like they end a sentence.
 */
object PdfTextSanitizer {

    private val PAGE_NUMBER = Regex("""^\s*(?:[-\u2013\u2014\u00B7]\s*)?\p{Nd}{1,4}(?:\s*[-\u2013\u2014\u00B7])?\s*$""")
    private val SENTENCE_END = Regex("""[.!?\u2026]["\u201D\u2019)\]]?$""")
    private val CHAPTER_LIKE = Regex("""^(chapter|part|book|section|prologue|epilogue|preface|introduction|contents)\b""", RegexOption.IGNORE_CASE)
    private val ALL_CAPS = Regex("""^[\p{Lu}\p{Nd}\p{Punct}\s]{4,}$""")

    fun sanitize(rawText: String): String = paragraphs(rawText).joinToString("\n\n")

    fun paragraphs(rawText: String): List<String> {
        val normalized = rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u00A0', ' ')
            .replace("\u00AD", "")
            .replace('\u000C', '\n')
            .replace(Regex("""[ \t]+"""), " ")
        val lines = normalized.split('\n').map { it.trim() }
        val widths = lines.filter { it.isNotEmpty() }.map { it.length }.sorted()
        val medianWidth: Float = if (widths.isEmpty()) 0f else widths[widths.size / 2].toFloat()

        val paragraphs = ArrayList<String>()
        val buffer = StringBuilder()
        var lastLineLength = 0

        fun flush() {
            if (buffer.isNotEmpty()) {
                paragraphs += buffer.toString().trim()
                buffer.setLength(0)
            }
        }

        for (line in lines) {
            if (line.isEmpty()) {
                flush()
                continue
            }
            if (PAGE_NUMBER.matches(line)) {
                continue
            }
            if (buffer.isEmpty()) {
                buffer.append(line)
                lastLineLength = line.length
                continue
            }
            val previous = buffer.toString()
            if (previous.endsWith("-") && line.firstOrNull()?.isLowerCase() == true && previous.length > 1) {
                buffer.setLength(buffer.length - 1)
                buffer.append(line)
                lastLineLength = line.length
                continue
            }
            val previousEndsSentence = SENTENCE_END.containsMatchIn(previous)
            val previousShort = medianWidth > 0 && lastLineLength < medianWidth * 0.86f
            val lineStartsSentence = line.firstOrNull()?.let { it.isUpperCase() || it.isDigit() || it in "\"'\u201C\u2018(" } == true
            if (previousEndsSentence && previousShort && lineStartsSentence) {
                flush()
                buffer.append(line)
                lastLineLength = line.length
                continue
            }
            buffer.append(' ').append(line)
            lastLineLength = line.length
        }
        flush()
        return paragraphs.filter { it.isNotBlank() }
    }

    fun toBlocks(rawText: String): List<Block> {
        val paragraphs = paragraphs(rawText)
        val blocks = ArrayList<Block>(paragraphs.size)
        for (paragraph in paragraphs) {
            val text = paragraph.replace(Regex("""\s+"""), " ").trim()
            if (text.isEmpty()) continue
            val looksLikeHeading = (text.length <= 90 &&
                (ALL_CAPS.matches(text) || CHAPTER_LIKE.containsMatchIn(text))) &&
                !SENTENCE_END.containsMatchIn(text.takeLast(1))
            blocks += Block(
                type = if (looksLikeHeading) BlockType.HEADING else BlockType.PARAGRAPH,
                runs = listOf(Run(text)),
            )
        }
        return blocks
    }
}
