package com.epubfeed.data.pdf

/** One bookmark from the PDF outline. [pageIndex] is 0 based. */
data class PdfOutlineEntry(val title: String, val pageIndex: Int, val depth: Int = 0)

/**
 * Everything the PDF book builder needs from a PDF document.
 *
 * Kept as an interface so the chapter splitting logic can be unit tested on the
 * JVM without the Android PDFBox port.
 */
interface PdfTextSource {
    val pageCount: Int
    val title: String?
    val author: String?
    fun pageText(pageIndex: Int): String
    fun outline(): List<PdfOutlineEntry>
}
