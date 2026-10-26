package com.epubfeed.data.image

/**
 * Reads the pixel size out of an encoded picture without decoding it.
 *
 * The parsers run on the JVM in unit tests, where [android.graphics.BitmapFactory] is a stub that
 * reports nothing, so the header bytes are the only reliable source. Every reader is defensive: a
 * damaged or unknown format reports null and the caller treats the picture as usable.
 */
object ImageSize {

    data class Px(val width: Int, val height: Int) {
        val shortestSide: Int get() = minOf(width, height)
    }

    fun read(bytes: ByteArray): Px? = runCatching {
        when {
            isPng(bytes) -> png(bytes)
            isJpeg(bytes) -> jpeg(bytes)
            isGif(bytes) -> gif(bytes)
            isBmp(bytes) -> bmp(bytes)
            isWebp(bytes) -> webp(bytes)
            else -> null
        }
    }.getOrNull()?.takeIf { it.width > 0 && it.height > 0 }

    private fun u8(bytes: ByteArray, index: Int): Int = bytes[index].toInt() and 0xFF

    private fun be16(bytes: ByteArray, index: Int): Int = (u8(bytes, index) shl 8) or u8(bytes, index + 1)

    private fun be32(bytes: ByteArray, index: Int): Int =
        (u8(bytes, index) shl 24) or (u8(bytes, index + 1) shl 16) or
            (u8(bytes, index + 2) shl 8) or u8(bytes, index + 3)

    private fun le16(bytes: ByteArray, index: Int): Int = u8(bytes, index) or (u8(bytes, index + 1) shl 8)

    private fun le32(bytes: ByteArray, index: Int): Int =
        u8(bytes, index) or (u8(bytes, index + 1) shl 8) or
            (u8(bytes, index + 2) shl 16) or (u8(bytes, index + 3) shl 24)

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size > 24 && u8(bytes, 0) == 0x89 && u8(bytes, 1) == 0x50 && u8(bytes, 2) == 0x4E && u8(bytes, 3) == 0x47

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size > 4 && u8(bytes, 0) == 0xFF && u8(bytes, 1) == 0xD8

    private fun isGif(bytes: ByteArray): Boolean =
        bytes.size > 10 && bytes[0].toInt().toChar() == 'G' && bytes[1].toInt().toChar() == 'I' &&
            bytes[2].toInt().toChar() == 'F'

    private fun isBmp(bytes: ByteArray): Boolean =
        bytes.size > 26 && bytes[0].toInt().toChar() == 'B' && bytes[1].toInt().toChar() == 'M'

    private fun isWebp(bytes: ByteArray): Boolean =
        bytes.size > 30 && String(bytes, 0, 4, Charsets.ISO_8859_1) == "RIFF" &&
            String(bytes, 8, 4, Charsets.ISO_8859_1) == "WEBP"

    /** PNG: the IHDR chunk always sits first, width and height are big endian at 16 and 20. */
    private fun png(bytes: ByteArray): Px = Px(be32(bytes, 16), be32(bytes, 20))

    /** GIF: the logical screen descriptor right after the signature, little endian. */
    private fun gif(bytes: ByteArray): Px = Px(le16(bytes, 6), le16(bytes, 8))

    private fun bmp(bytes: ByteArray): Px = Px(le32(bytes, 18), kotlin.math.abs(le32(bytes, 22)))

    /**
     * JPEG needs a walk: the size lives in a start-of-frame marker (SOF0..SOF15, minus the
     * markers that share the range but carry something else), after any number of other segments.
     */
    private fun jpeg(bytes: ByteArray): Px? {
        var index = 2
        while (index + 9 < bytes.size) {
            if (u8(bytes, index) != 0xFF) {
                index++
                continue
            }
            var marker = u8(bytes, index + 1)
            while (marker == 0xFF && index + 2 < bytes.size) {
                index++
                marker = u8(bytes, index + 1)
            }
            val isFrame = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
            if (isFrame) {
                return Px(be16(bytes, index + 7), be16(bytes, index + 5))
            }
            if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) {
                index += 2
                continue
            }
            val length = be16(bytes, index + 2)
            if (length < 2) return null
            index += 2 + length
        }
        return null
    }

    /** WebP: VP8X carries canvas size minus one as 24 bit, VP8 and VP8L hold it in the bitstream. */
    private fun webp(bytes: ByteArray): Px? {
        val chunk = String(bytes, 12, 4, Charsets.ISO_8859_1)
        return when (chunk) {
            "VP8X" -> Px(
                (u8(bytes, 24) or (u8(bytes, 25) shl 8) or (u8(bytes, 26) shl 16)) + 1,
                (u8(bytes, 27) or (u8(bytes, 28) shl 8) or (u8(bytes, 29) shl 16)) + 1,
            )
            "VP8 " -> Px(le16(bytes, 26) and 0x3FFF, le16(bytes, 28) and 0x3FFF)
            "VP8L" -> {
                val bits = le32(bytes, 21)
                Px((bits and 0x3FFF) + 1, ((bits shr 14) and 0x3FFF) + 1)
            }
            else -> null
        }
    }
}
