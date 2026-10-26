package com.epubfeed.data.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.epubfeed.data.image.PictureFilter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * PDFBox backed text source. Android's PdfRenderer can only draw bitmaps, so text
 * extraction goes through pdfbox-android instead, and pictures come out of the page's
 * own image objects.
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

    /**
     * The pictures drawn on one page. Marks and ornaments are dropped by size, huge images are
     * skipped so a print resolution plate cannot exhaust the heap, and everything kept is scaled
     * down and re-encoded, which also flattens transparency onto white.
     */
    override fun pageImages(pageIndex: Int): List<PdfPageImage> {
        val page: PDPage = runCatching { document.getPage(pageIndex) }.getOrNull() ?: return emptyList()
        val resources = runCatching { page.resources }.getOrNull() ?: return emptyList()
        val names = runCatching { resources.xObjectNames }.getOrNull() ?: return emptyList()

        val out = ArrayList<PdfPageImage>()
        var slot = 0
        for (name in names) {
            if (out.size >= MAX_PICTURES_PER_PAGE) break
            val xObject = runCatching { resources.getXObject(name) }.getOrNull() ?: continue
            if (xObject !is PDImageXObject) continue
            val width = runCatching { xObject.width }.getOrDefault(0)
            val height = runCatching { xObject.height }.getOrDefault(0)
            if (!PictureFilter.qualifies(width, height)) continue
            if (width.toLong() * height > MAX_SOURCE_PIXELS) continue
            val bitmap = runCatching { xObject.image }.getOrNull() ?: continue
            val encoded = runCatching { encodeJpeg(bitmap) }.getOrNull() ?: continue
            out += PdfPageImage(
                width = encoded.first,
                height = encoded.second,
                key = "pdf-page${pageIndex + 1}-$slot.jpg",
                bytes = encoded.third,
            )
            slot++
        }
        return out
    }

    /** Scales the picture down to a sane width and writes it as JPEG on white. */
    private fun encodeJpeg(source: Bitmap): Triple<Int, Int, ByteArray>? {
        val scale = if (source.width > MAX_STORED_WIDTH) MAX_STORED_WIDTH.toFloat() / source.width else 1f
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val flat = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, null, android.graphics.Rect(0, 0, width, height), null)
        }
        val out = ByteArrayOutputStream()
        if (!flat.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) return null
        return Triple(width, height, out.toByteArray())
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
        private const val MAX_PICTURES_PER_PAGE = 20
        private const val MAX_SOURCE_PIXELS = 24_000_000L
        private const val MAX_STORED_WIDTH = 1600
        private const val JPEG_QUALITY = 85

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
