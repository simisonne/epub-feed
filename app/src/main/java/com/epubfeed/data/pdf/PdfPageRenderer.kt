package com.epubfeed.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Draws the first pages of a PDF as pictures.
 *
 * They serve two purposes: the first one becomes the book's cover (a PDF rarely ships one as an
 * image object), and all of them are the pages the reader can pick a profile photo from. Only the
 * first few are drawn, at a width that is plenty for an avatar, because this happens during the
 * import and must not turn into a second full render pass.
 */
object PdfPageRenderer {

    const val DEFAULT_PAGES = 5
    private const val TARGET_WIDTH = 900
    private const val JPEG_QUALITY = 88

    fun renderPages(file: File, count: Int = DEFAULT_PAGES): List<ByteArray> = runCatching {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = PdfRenderer(descriptor)
            try {
                val pages = minOf(count, renderer.pageCount)
                (0 until pages).mapNotNull { index ->
                    runCatching { renderPage(renderer, index) }.getOrNull()
                }
            } finally {
                renderer.close()
            }
        } finally {
            descriptor.close()
        }
    }.getOrDefault(emptyList())

    private fun renderPage(renderer: PdfRenderer, index: Int): ByteArray? {
        val page = renderer.openPage(index)
        try {
            val width = TARGET_WIDTH
            val height = (width * page.height.toFloat() / page.width.toFloat()).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // PdfRenderer leaves the untouched parts of the bitmap as they were, so a white
            // base keeps a page with a transparent background readable.
            android.graphics.Canvas(bitmap).drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val out = ByteArrayOutputStream()
            val written = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            return if (written) out.toByteArray() else null
        } finally {
            page.close()
        }
    }
}
