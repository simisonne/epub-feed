package com.epubfeed.data.epub

import com.epubfeed.domain.model.Block
import com.epubfeed.domain.model.BlockType

/**
 * Some books repeat the chapter title inside the chapter body (an `<h2>Chapter I</h2>`
 * or a heading line in a PDF page). The feed already renders the title as the thread
 * opener, so the duplicate is removed before posts are generated.
 */
object ChapterBlocks {

    fun stripTitleHeading(blocks: List<Block>, title: String): List<Block> {
        val normalizedTitle = normalize(title)
        if (normalizedTitle.isEmpty()) return blocks
        val out = blocks.toMutableList()
        while (out.isNotEmpty() && out.first().type == BlockType.HEADING &&
            normalize(out.first().text) == normalizedTitle
        ) {
            out.removeAt(0)
        }
        if (out.isNotEmpty() && out.first().type == BlockType.PARAGRAPH &&
            normalize(out.first().text) == normalizedTitle
        ) {
            out.removeAt(0)
        }
        return out
    }

    private fun normalize(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }
}
