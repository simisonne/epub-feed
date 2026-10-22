package com.epubfeed.data.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/**
 * PDFBox backed text source. Android's PdfRenderer can only draw bitmaps, so text
 * extraction goes through pdfbox-android instead.
 */
class PdfBoxTextSource private constructor(private val document: PDDocument) : PdfTextSource, AutoCloseable {

    override val pageCount: Int get() = document.numberOfPages

    override val title: String? by lazy {
        runCatching { document.documentInformation.title }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    override val author: String? by lazy {
        runCatching { document.documentInformation.author }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    override fun pageText(pageIndex: Int): String {
        val stripper = PDFTextStripper()
        stripper.sortByPosition = true
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.lineSeparator = "\n"
        return stripper.getText(document)
    }

    override fun outline(): List<PdfOutlineEntry> {
        val root: PDDocumentOutline = document.documentCatalog.documentOutline ?: return emptyList()
        val out = ArrayList<PdfOutlineEntry>()
        var child: PDOutlineItem? = runCatching { root.firstChild }.getOrNull()
        while (child != null) {
            collect(child, 0, out)
            child = runCatching { child!!.nextSibling }.getOrNull()
        }
        return out
    }

    private fun collect(item: PDOutlineItem, depth: Int, out: MutableList<PdfOutlineEntry>) {
        if (depth > 3 || out.size > 2000) return
        val title = runCatching { item.title }.getOrNull()?.trim().orEmpty()
        val page = runCatching { pageIndexOf(item) }.getOrDefault(-1)
        if (title.isNotEmpty() && page >= 0) {
            out += PdfOutlineEntry(title = title, pageIndex = page, depth = depth)
        }
        var child: PDOutlineItem? = runCatching { item.firstChild }.getOrNull()
        while (child != null) {
            collect(child, depth + 1, out)
            child = runCatching { child!!.nextSibling }.getOrNull()
        }
    }

    private fun pageIndexOf(item: PDOutlineItem): Int {
        val direct = item.destination
        if (direct != null) return pageNumberOf(direct)
        val action = item.action
        if (action is PDActionGoTo) return pageNumberOf(action.destination)
        return -1
    }

    private fun pageNumberOf(destination: Any?): Int = when (destination) {
        is PDPageDestination -> {
            val number = destination.retrievePageNumber()
            if (number >= 0) number else pageIndexOfPage(destination.page)
        }
        else -> -1
    }

    private fun pageIndexOfPage(page: PDPage?): Int {
        if (page == null) return -1
        for (index in 0 until document.numberOfPages) {
            if (document.getPage(index) === page) return index
        }
        return -1
    }

    override fun close() {
        runCatching { document.close() }
    }

    companion object {
        fun open(file: File): PdfBoxTextSource {
            val document = try {
                PDDocument.load(file)
            } catch (e: InvalidPasswordException) {
                throw com.epubfeed.data.epub.BookParseException(
                    "This PDF is password protected and cannot be opened.",
                    e,
                )
            } catch (e: java.io.IOException) {
                throw com.epubfeed.data.epub.BookParseException("This PDF is damaged or unreadable.", e)
            }
            return PdfBoxTextSource(document)
        }
    }
}
