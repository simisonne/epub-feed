package com.epubfeed

import com.epubfeed.domain.model.Block
import com.epubfeed.domain.model.FeedPost
import com.epubfeed.ui.feed.restoreListIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestoreIndexTest {

    private fun posts(chapterIndex: Int, firstGlobal: Int, count: Int): List<FeedPost> =
        (0 until count).map { i ->
            FeedPost(
                id = "c$chapterIndex-p$i",
                chapterIndex = chapterIndex,
                indexInChapter = i,
                globalIndex = firstGlobal + i,
                isChapterTitlePost = i == 0,
                blocks = listOf(Block()),
                liked = false,
                bookmarked = false,
                plainText = "post $i",
            )
        }

    @Test
    fun `a position mid chapter parks on its own post`() {
        val chapter = posts(chapterIndex = 6, firstGlobal = 149, count = 70)
        assertEquals(11, restoreListIndex(chapter, target = 160, leading = 0))
    }

    @Test
    fun `the pinned welcome post offsets every other post by one`() {
        val chapter = posts(chapterIndex = 0, firstGlobal = 0, count = 3)
        assertEquals(3, restoreListIndex(chapter, target = 2, leading = 1))
    }

    @Test
    fun `the first post of a chapter parks on the welcome post`() {
        val chapter = posts(chapterIndex = 0, firstGlobal = 0, count = 3)
        assertEquals(0, restoreListIndex(chapter, target = 0, leading = 1))
    }

    @Test
    fun `a position past the chapter shows the last post`() {
        // A place written before the book was re-sliced: the target sits in a later chapter.
        val chapter = posts(chapterIndex = 0, firstGlobal = 0, count = 40)
        assertEquals(40, restoreListIndex(chapter, target = 160, leading = 1))
    }

    @Test
    fun `a position before the chapter parks on its first post`() {
        val chapter = posts(chapterIndex = 6, firstGlobal = 149, count = 70)
        assertEquals(0, restoreListIndex(chapter, target = 3, leading = 0))
    }

    @Test
    fun `nothing to scroll to`() {
        assertNull(restoreListIndex(emptyList(), target = 12, leading = 0))
    }
}
