package com.epubfeed

import com.epubfeed.data.image.ImageSize
import com.epubfeed.data.image.PictureFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picture filter needs pixel sizes on the JVM, where BitmapFactory reports nothing, so the
 * sizes come out of the file headers. These tests pin the header readers down.
 */
class ImageSizeTest {

    @Test
    fun `png size comes from the IHDR chunk`() {
        val size = ImageSize.read(TestEpubs.png(400, 600))
        assertEquals(400, size?.width)
        assertEquals(600, size?.height)
    }

    @Test
    fun `jpeg size comes from the start of frame marker`() {
        val size = ImageSize.read(jpeg(200, 100))
        assertEquals(200, size?.width)
        assertEquals(100, size?.height)
    }

    @Test
    fun `gif size comes from the screen descriptor`() {
        val size = ImageSize.read(gif(320, 240))
        assertEquals(320, size?.width)
        assertEquals(240, size?.height)
    }

    @Test
    fun `a picture whose size cannot be read is not treated as small`() {
        assertNull(ImageSize.read(byteArrayOf(1, 2, 3, 4)))
        assertTrue(PictureFilter.qualifies(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun `the shortest side decides`() {
        assertTrue(PictureFilter.qualifies(1000, 200))
        assertFalse(PictureFilter.qualifies(199, 1000))
        assertTrue(PictureFilter.qualifies(0, 0))
    }

    private fun jpeg(width: Int, height: Int): ByteArray {
        val app0 = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
        )
        val sof = byteArrayOf(
            0xFF.toByte(), 0xC0.toByte(), 0x00, 0x11, 0x08,
            (height ushr 8).toByte(), height.toByte(),
            (width ushr 8).toByte(), width.toByte(),
            0x03, 0x01, 0x22, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
        )
        return app0 + sof + byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    }

    private fun gif(width: Int, height: Int): ByteArray = byteArrayOf(
        'G'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), '8'.code.toByte(), '9'.code.toByte(), 'a'.code.toByte(),
        (width and 0xFF).toByte(), (width ushr 8).toByte(),
        (height and 0xFF).toByte(), (height ushr 8).toByte(),
        0x00, 0x00, 0x00,
    )
}
