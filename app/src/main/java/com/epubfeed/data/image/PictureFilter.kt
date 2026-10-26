package com.epubfeed.data.image

/**
 * Decides which pictures from a book are worth a post of their own.
 *
 * Books are full of marks that are not illustrations: bullets drawn as bitmaps, tiny chapter
 * ornaments, publisher logos and the 16 pixel rules that separate sections. In a feed every one of
 * them would become a photo post that carries nothing, so a picture has to be big enough to be
 * looked at.
 *
 * The threshold is on the shortest side so a wide figure stays even when it is short, and an
 * unreadable size counts as big enough: it is better to show a picture than to silently drop one.
 */
object PictureFilter {

    /** Roughly the smallest picture that still reads as an illustration rather than a mark. */
    const val MIN_SHORT_SIDE_PX = 200

    fun qualifies(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return true
        return minOf(width, height) >= MIN_SHORT_SIDE_PX
    }

    fun qualifies(bytes: ByteArray): Boolean {
        val size = ImageSize.read(bytes) ?: return true
        return qualifies(size.width, size.height)
    }
}
