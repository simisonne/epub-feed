package com.epubfeed.data.epub

import com.epubfeed.domain.model.Block
import com.epubfeed.domain.model.BlockType
import com.epubfeed.domain.model.Run
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Converts XHTML chapter markup into the block/run model the feed renders.
 *
 * Images, scripts, navigation and footnote apparatus are dropped; whitespace is
 * normalized; italics and bold survive as styled runs.
 */
object HtmlBlocks {

    private val BLOCK_TAGS = arrayOf(
        "p", "div", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6", "li", "pre", "td", "section", "article",
    )
    private val BLOCK_SELECTOR = BLOCK_TAGS.joinToString(",")
    private val NOISE_QUERY = "script,style,nav,svg,img,figure,figcaption,aside,header,footer,iframe,audio,video,form,button,noscript,sup"
    private val FOOTNOTE_CLASSES = setOf(
        "noteref", "footnote", "footnotes", "endnote", "endnotes", "fnhref", "fnref", "footnote-ref", "note-ref",
    )
    private val BRACKET_NUMBER = Regex("""\[\d{1,4}]""")

    fun extract(doc: Element): Pair<List<Block>, String?> {
        doc.select(NOISE_QUERY).remove()
        doc.select("[epub\\:type=noteref], [epub\\:type=footnote]").remove()

        // Drop inline footnote references by class name.
        doc.getAllElements().toList().forEach { el ->
            if (el.parent() == null) return@forEach
            val classes = el.classNames().map { it.lowercase() }
            if (classes.any { it in FOOTNOTE_CLASSES }) el.remove()
        }

        val blocks = mutableListOf<Block>()
        val candidates = doc.select(BLOCK_SELECTOR)
        var heading: String? = null

        for (el in candidates) {
            if (!isLeafBlock(el)) continue
            val text = el.text().trim()
            if (text.isEmpty()) continue
            val type = when (el.tagName().lowercase()) {
                "blockquote" -> BlockType.QUOTE
                "h1", "h2", "h3", "h4", "h5", "h6" -> BlockType.HEADING
                else -> BlockType.PARAGRAPH
            }
            if (heading == null && type == BlockType.HEADING && text.length <= 120) {
                heading = text
            }
            val runs = runsOf(el)
            if (runs.none { it.text.isNotBlank() }) continue
            blocks += Block(type = type, runs = runs)
        }

        if (blocks.isEmpty()) {
            // Some books ship their text without any block level markup.
            val fallback = doc.text().trim()
            if (fallback.isNotEmpty()) {
                blocks += Block(BlockType.PARAGRAPH, listOf(Run(cleanInline(fallback))))
            }
        }
        return blocks to heading
    }

    private fun isLeafBlock(el: Element): Boolean {
        var child = el.children().firstOrNull()
        while (child != null) {
            if (child.tagName().lowercase() in BLOCK_TAGS) return false
            child = child.nextElementSibling()
        }
        return true
    }

    private fun runsOf(root: Element): List<Run> {
        val out = ArrayList<Run>()
        fun walk(node: Node, italic: Boolean, bold: Boolean) {
            when (node) {
                is TextNode -> {
                    val text = cleanInline(node.wholeText)
                    if (text.isNotEmpty()) out += Run(text, italic, bold)
                }
                is Element -> {
                    when (node.tagName().lowercase()) {
                        "br" -> out += Run("\n")
                        "i", "em", "cite" -> node.childNodes().forEach { walk(it, true, bold) }
                        "b", "strong" -> node.childNodes().forEach { walk(it, italic, true) }
                        "script", "style" -> Unit
                        else -> node.childNodes().forEach { walk(it, italic, bold) }
                    }
                }
                else -> Unit
            }
        }
        walk(root, false, false)
        return mergeRuns(trimRuns(out))
    }

    /** Normalizes whitespace, strips bracketed footnote numbers and joins edge spaces. */
    private fun cleanInline(raw: String): String {
        if (raw.isEmpty()) return raw
        val withoutRefs = BRACKET_NUMBER.replace(raw, "")
        return withoutRefs
            .replace('\u00A0', ' ')
            .replace(Regex("""\s+"""), " ")
    }

    private fun trimRuns(runs: List<Run>): List<Run> {
        val out = ArrayList<Run>(runs.size)
        runs.forEachIndexed { index, run ->
            var text = run.text
            if (index == 0) text = text.trimStart()
            if (index == runs.lastIndex) text = text.trimEnd()
            if (text.isNotEmpty()) out += run.copy(text = text)
        }
        return out
    }

    private fun mergeRuns(runs: List<Run>): List<Run> {
        val out = ArrayList<Run>(runs.size)
        for (run in runs) {
            val last = out.lastOrNull()
            if (last != null && last.italic == run.italic && last.bold == run.bold) {
                out[out.lastIndex] = last.copy(text = last.text + run.text)
            } else {
                out += run
            }
        }
        return out.filter { it.text.isNotEmpty() }
    }
}
