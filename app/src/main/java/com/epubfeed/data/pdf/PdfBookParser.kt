package com.epubfeed.data.pdf

import com.epubfeed.data.epub.BookParseException
import com.epubfeed.data.epub.ParsedBook
import com.epubfeed.data.epub.ParsedChapter
import com.epubfeed.data.image.PictureFilter
import com.epubfeed.domain.model.Block
import com.epubfeed.domain.model.BlockType

/**
 * Builds chapters out of a PDF: the outline when it has usable page targets,
 * otherwise fixed size page chunks. Text is sanitized into paragraphs and the
 * pictures drawn on a page follow that page's text, so a figure lands in the feed
 * right after the paragraph that explains it.
 */
object PdfBookParser {

    const val DEFAULT_PAGES_PER_CHAPTER = 10
    private const val MIN_MEANINGFUL_CHARS = 40
    private const val MAX_PICTURES_PER_BOOK = 400

    fun parse(
        source: PdfTextSource,
        pagesPerChapter: Int = DEFAULT_PAGES_PER_CHAPTER,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ParsedBook {
        val pageCount = source.pageCount
        if (pageCount <= 0) throw BookParseException("This PDF has no pages.")

        val pageTexts = ArrayList<String>(pageCount)
        val pagePictures = ArrayList<List<PdfPageImage>>(pageCount)
        var meaningfulChars = 0
        for (page in 0 until pageCount) {
            val text = runCatching { source.pageText(page) }.getOrDefault("")
            pageTexts += text
            pagePictures += runCatching { source.pageImages(page) }.getOrDefault(emptyList())
            meaningfulChars += text.count { !it.isWhitespace() }
            onProgress(page + 1, pageCount)
        }

        if (meaningfulChars < MIN_MEANINGFUL_CHARS) {
            throw BookParseException(
                "This PDF looks like a scan of printed pages. It has no text layer, so there is nothing to read as a feed. OCR is not supported.",
            )
        }

        val outline = source.outline()
            .filter { it.pageIndex in 0 until pageCount }
            .distinctBy { it.pageIndex }
            .sortedBy { it.pageIndex }
            .filter { it.title.isNotBlank() }

        val ranges: List<Triple<String, Int, Int>> = if (outline.size >= 2) {
            outline.mapIndexedNotNull { index, entry ->
                val end = outline.getOrNull(index + 1)?.pageIndex ?: pageCount
                if (end <= entry.pageIndex) null else Triple(cleanTitle(entry.title), entry.pageIndex, end)
            }
        } else {
            (0 until pageCount step pagesPerChapter.coerceAtLeast(1)).map { start ->
                val end = (start + pagesPerChapter).coerceAtMost(pageCount)
                Triple("Pages ${start + 1}\u2013$end", start, end)
            }
        }

        // One picture, one entry: a border or a logo repeated on every page is kept once.
        val illustrations = LinkedHashMap<String, ByteArray>()
        val chapters = ranges.mapNotNull { (title, start, end) ->
            val blocks = ArrayList<Block>()
            for (page in start until end) {
                blocks += PdfTextSanitizer.toBlocks(pageTexts[page])
                for (picture in pagePictures[page]) {
                    if (illustrations.size >= MAX_PICTURES_PER_BOOK) break
                    if (!PictureFilter.qualifies(picture.width, picture.height)) continue
                    if (illustrations.containsKey(picture.key)) continue
                    illustrations[picture.key] = picture.bytes
                    blocks += Block(type = BlockType.IMAGE, imageRef = picture.key)
                }
            }
            val cleaned = com.epubfeed.data.epub.ChapterBlocks.stripTitleHeading(blocks, title)
            if (cleaned.isEmpty()) null else ParsedChapter(title = title, blocks = cleaned)
        }

        if (chapters.isEmpty()) {
            throw BookParseException("No readable text was found in this PDF.")
        }

        return ParsedBook(
            title = source.title?.trim().orEmpty(),
            author = source.author?.trim().orEmpty(),
            chapters = chapters,
            coverBytes = null,
            illustrations = illustrations,
        )
    }

    private fun cleanTitle(raw: String): String =
        raw.replace(Regex("""\s+"""), " ").trim().take(120).ifBlank { "Untitled section" }
}
