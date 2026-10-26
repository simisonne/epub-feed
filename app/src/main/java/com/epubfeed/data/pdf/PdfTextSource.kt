package com.epubfeed.data.pdf

/** One bookmark from the PDF outline. [pageIndex] is 0 based. */
data class PdfOutlineEntry(val title: String, val pageIndex: Int, val depth: Int = 0)

/**
 * A picture drawn on a page, already turned into a file format the app can store.
 *
 * [key] is unique inside the book and doubles as the illustration key, so the same picture used on
 * several pages is only ever kept once.
 */
data class PdfPageImage(
    val width: Int,
    val height: Int,
    val key: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PdfPageImage) return false
        return key == other.key && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * key.hashCode() + bytes.contentHashCode()
}

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

    /**
     * The pictures on one page, in the order they are drawn. Sources that cannot read pictures
     * report none, which is what keeps the text only fakes in the tests working.
     */
    fun pageImages(pageIndex: Int): List<PdfPageImage> = emptyList()
}
