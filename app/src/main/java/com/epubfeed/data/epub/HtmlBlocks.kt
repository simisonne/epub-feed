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
 * Scripts, navigation and footnote apparatus are dropped; whitespace is normalized; italics and
 * bold survive as styled runs. Pictures survive as [BlockType.IMAGE] blocks, in reading order, so
 * a chapter that is only a plate still produces a post.
 */
object HtmlBlocks {

    private val BLOCK_TAGS = arrayOf(
        "p", "div", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6", "li", "pre", "td", "section", "article",
        "figure", "figcaption",
    )
    private val SKIP_TAGS = setOf(
        "script", "style", "svg", "nav", "aside", "header", "footer", "iframe", "audio", "video", "form",
        "button", "noscript", "sup",
    )
    private val NOISE_QUERY = SKIP_TAGS.joinToString(",")
    private val FOOTNOTE_CLASSES = setOf(
        "noteref", "footnote", "footnotes", "endnote", "endnotes", "fnhref", "fnref", "footnote-ref", "note-ref",
    )
    private val BRACKET_NUMBER = Regex("""\[\d{1,4}]""")

    /**
     * @param resolveImage maps an `img` src to the ZIP entry that holds it, or null when the
     *   picture cannot be found (a data URI, a missing entry, a format the app cannot draw).
     */
    fun extract(doc: Element, resolveImage: (String) -> String? = { null }): Pair<List<Block>, String?> {
        doc.select(NOISE_QUERY).remove()
        doc.select("[epub\\:type=noteref], [epub\\:type=footnote]").remove()

        // Drop inline footnote references by class name.
        doc.getAllElements().toList().forEach { el ->
            if (el.parent() == null) return@forEach
            val classes = el.classNames().map { it.lowercase() }
            if (classes.any { it in FOOTNOTE_CLASSES }) el.remove()
        }

        val emitter = Emitter(resolveImage)
        emitter.visit(doc.getElementsByTag("body").firstOrNull() ?: doc)

        if (emitter.blocks.isEmpty()) {
            // Some books ship their text without any block level markup.
            val fallback = doc.text().trim()
            if (fallback.isNotEmpty()) {
                emitter.blocks += Block(BlockType.PARAGRAPH, listOf(Run(cleanInline(fallback))))
            }
        }
        return emitter.blocks to emitter.heading
    }

    /**
     * Walks the document in reading order. A block element that only holds inline content is
     * emitted as one or more blocks; a block element that wraps other blocks is descended into,
     * which is what keeps a `<figure><img/><figcaption/></figure>` in the right order.
     */
    private class Emitter(private val resolveImage: (String) -> String?) {
        val blocks = mutableListOf<Block>()
        var heading: String? = null
        private var lastImageRef: String? = null

        fun visit(el: Element) {
            for (node in el.childNodes()) {
                if (node !is Element) continue
                val tag = node.tagName().lowercase()
                when {
                    tag in SKIP_TAGS -> Unit
                    tag == "img" -> emitImage(node)
                    tag in BLOCK_TAGS -> if (isLeafBlock(node)) emitElement(node) else visit(node)
                    // An inline wrapper can still hold pictures or nested blocks.
                    else -> visit(node)
                }
            }
        }

        private fun emitElement(el: Element) {
            val type = when (el.tagName().lowercase()) {
                "blockquote" -> BlockType.QUOTE
                "h1", "h2", "h3", "h4", "h5", "h6" -> BlockType.HEADING
                else -> BlockType.PARAGRAPH
            }
            var pending = ArrayList<Run>()
            fun flush() {
                if (pending.isEmpty()) return
                val runs = mergeRuns(trimRuns(pending))
                pending = ArrayList()
                if (runs.none { it.text.isNotBlank() }) return
                val text = runs.joinToString("") { it.text }.trim()
                if (heading == null && type == BlockType.HEADING && text.length <= 120) heading = text
                lastImageRef = null
                blocks += Block(type = type, runs = runs)
            }
            walkContent(
                el,
                onRun = { pending += it },
                onImage = { img ->
                    flush()
                    emitImage(img)
                },
            )
            flush()
        }

        private fun emitImage(img: Element) {
            val src = img.attr("src").ifBlank { img.attr("data-src") }.trim()
            if (src.isEmpty()) return
            val ref = resolveImage(src) ?: return
            if (ref == lastImageRef) return
            lastImageRef = ref
            blocks += Block(
                type = BlockType.IMAGE,
                imageRef = ref,
                imageAlt = img.attr("alt").trim().ifEmpty { null },
            )
        }
    }

    private fun isLeafBlock(el: Element): Boolean {
        var child = el.children().firstOrNull()
        while (child != null) {
            if (child.tagName().lowercase() in BLOCK_TAGS) return false
            child = child.nextElementSibling()
        }
        return true
    }

    /** Reports text runs and pictures of one block element, in the order they appear. */
    private fun walkContent(root: Element, onRun: (Run) -> Unit, onImage: (Element) -> Unit) {
        fun walk(node: Node, italic: Boolean, bold: Boolean) {
            when (node) {
                is TextNode -> {
                    val text = cleanInline(node.wholeText)
                    if (text.isNotEmpty()) onRun(Run(text, italic, bold))
                }
                is Element -> {
                    when (node.tagName().lowercase()) {
                        "br" -> onRun(Run("\n"))
                        "img" -> onImage(node)
                        "i", "em", "cite" -> node.childNodes().forEach { walk(it, true, bold) }
                        "b", "strong" -> node.childNodes().forEach { walk(it, italic, true) }
                        "script", "style", "svg" -> Unit
                        else -> node.childNodes().forEach { walk(it, italic, bold) }
                    }
                }
                else -> Unit
            }
        }
        walk(root, false, false)
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
