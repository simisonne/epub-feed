package com.epubfeed

import com.epubfeed.data.epub.EpubParser
import com.epubfeed.data.repo.SampleBooks
import com.epubfeed.domain.model.FeedPost
import com.epubfeed.ui.feed.percentLabel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the book the app ships with. It has to parse like a real import, carry the cover the
 * library shows on first launch, and keep the house writing style the user insists on.
 */
class SampleBookTest {

    private fun asset(): File {
        val candidates = listOf(
            File("src/main/assets/${SampleBooks.SAMPLE_ASSET}"),
            File("app/src/main/assets/${SampleBooks.SAMPLE_ASSET}"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("bundled sample is missing, looked in ${candidates.map { it.absolutePath }}")
    }

    private fun paragraphs(): List<String> =
        EpubParser.parse(asset()).chapters.flatMap { chapter -> chapter.blocks.map { it.text } }

    @Test
    fun `bundled sample parses as the onboarding book`() {
        val parsed = EpubParser.parse(asset())
        assertEquals(SampleBooks.SAMPLE_TITLE, parsed.title)
        assertEquals("ePUB Feed", parsed.author)
        assertEquals(
            listOf("Welcome", "Reading", "Likes, saves and notes", "Chapters", "Settings", "The profile"),
            parsed.chapters.map { it.title },
        )
        assertNotNull("the sample must carry the app icon as its cover", parsed.coverBytes)
        assertTrue(parsed.chapters.all { it.blocks.size >= 3 })
    }

    @Test
    fun `bundled sample follows the house writing style`() {
        paragraphs().forEach { text ->
            assertTrue("em dash in \"$text\"", !text.contains('\u2014'))
            assertTrue("semicolon in \"$text\"", !text.contains(';'))
            assertTrue("clause colon in \"$text\"", !text.contains(": "))
        }
    }

    @Test
    fun `post percent tracks the chapter, not the book`() {
        fun post(indexInChapter: Int) = FeedPost(
            id = "p$indexInChapter",
            chapterIndex = 1,
            indexInChapter = indexInChapter,
            globalIndex = 400 + indexInChapter,
            isChapterTitlePost = indexInChapter == 0,
            blocks = emptyList(),
            liked = false,
            bookmarked = false,
            plainText = "text",
        )

        assertEquals("10%", percentLabel(post(0), 10))
        assertEquals("50%", percentLabel(post(4), 10))
        assertEquals("100%", percentLabel(post(9), 10))
        assertEquals("0%", percentLabel(post(3), 0))
        // The same post reads differently in a shorter chapter, which is the whole point.
        assertEquals("80%", percentLabel(post(3), 5))
    }
}
