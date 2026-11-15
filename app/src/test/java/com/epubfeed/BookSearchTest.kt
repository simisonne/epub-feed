package com.epubfeed

import com.epubfeed.domain.search.BookSearch
import com.epubfeed.domain.search.SearchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSearchTest {

    private fun post(
        id: String,
        text: String,
        chapter: Int = 0,
        indexInChapter: Int = 0,
        globalIndex: Int = indexInChapter,
    ) = SearchText(
        postId = id,
        chapterIndex = chapter,
        indexInChapter = indexInChapter,
        globalIndex = globalIndex,
        text = text,
    )

    @Test
    fun `a word is matched whole, never from inside another word`() {
        val result = BookSearch.run(
            listOf(post("a", "They walked across the earth and back.")),
            "art",
        )
        assertTrue(result.hits.isEmpty())
    }

    @Test
    fun `case does not matter and every hit is a post`() {
        val result = BookSearch.run(
            listOf(post("a", "The Gloaming came early."), post("b", "Nothing here.")),
            "gloaming",
        )
        assertEquals(1, result.hits.size)
        assertEquals("a", result.hits.first().postId)
        assertTrue(result.everyTerm)
    }

    @Test
    fun `an inflected query still finds the plain word`() {
        val result = BookSearch.run(
            listOf(post("a", "The house stood at the end of the lane.")),
            "houses",
        )
        assertEquals(1, result.hits.size)
    }

    @Test
    fun `an exact hit outranks a guessed inflection`() {
        val result = BookSearch.run(
            listOf(
                post("guess", "He ran the house."),
                post("exact", "Two houses on the street."),
            ),
            "houses",
        )
        assertEquals("exact", result.hits.first().postId)
    }

    @Test
    fun `every word of the query must appear in a post`() {
        val posts = listOf(
            post("both", "The glass bead game was his last novel."),
            post("one", "The game began at dusk."),
        )
        val result = BookSearch.run(posts, "glass game")
        assertEquals(1, result.hits.size)
        assertEquals("both", result.hits.first().postId)
        assertTrue(result.everyTerm)
    }

    @Test
    fun `when no post holds every word the loose pass says so`() {
        val posts = listOf(
            post("one", "The game began at dusk."),
            post("two", "A glass of water stood there."),
        )
        val result = BookSearch.run(posts, "glass game")
        assertFalse(result.everyTerm)
        assertEquals(2, result.hits.size)
    }

    @Test
    fun `nothing at all found is an empty result, not a loose one`() {
        val result = BookSearch.run(listOf(post("a", "Nothing relevant here.")), "gloaming")
        assertTrue(result.hits.isEmpty())
        assertTrue(result.everyTerm)
    }

    @Test
    fun `the adjacent phrase ranks above the same words apart`() {
        val posts = listOf(
            post("apart", "The bead was glass. The game came later.", indexInChapter = 0),
            post("phrase", "He finished the glass bead game in 1943.", indexInChapter = 5),
        )
        val result = BookSearch.run(posts, "glass bead game")
        assertEquals("phrase", result.hits.first().postId)
        assertTrue(result.hits.first().phrase)
    }

    @Test
    fun `the marks slice exactly the matched words out of the excerpt`() {
        val text = "It was an ephemeral pleasure, gone by morning. Nothing else happened that day."
        val result = BookSearch.run(listOf(post("a", text)), "ephemeral")
        val hit = result.hits.single()
        assertEquals(1, hit.marks.size)
        assertEquals("ephemeral", hit.excerpt.substring(hit.marks.first().start, hit.marks.first().end))
        assertTrue(hit.excerpt.contains("pleasure"))
    }

    @Test
    fun `a mark never points outside the excerpt`() {
        val long = "word ".repeat(200) + "gloaming " + "more ".repeat(200)
        val hit = BookSearch.run(listOf(post("a", long)), "gloaming").hits.single()
        hit.marks.forEach { mark ->
            assertTrue(mark.start >= 0)
            assertTrue(mark.end <= hit.excerpt.length)
            assertTrue(mark.start < mark.end)
        }
        assertTrue(hit.excerpt.contains("gloaming"))
    }

    @Test
    fun `results are capped and say that they were`() {
        val posts = (1..5).map { post("p$it", "A gloaming appeared.", indexInChapter = it) }
        val result = BookSearch.run(posts, "gloaming", limit = 2)
        assertEquals(2, result.hits.size)
        assertTrue(result.capped)
    }

    @Test
    fun `an empty query searches nothing`() {
        val result = BookSearch.run(listOf(post("a", "text")), "   ")
        assertTrue(result.hits.isEmpty())
        assertTrue(result.terms.isEmpty())
    }

    @Test
    fun `the place of a post is carried through to the hit`() {
        val hit = BookSearch.run(
            listOf(post("a", "A gloaming appeared.", chapter = 3, indexInChapter = 7, globalIndex = 42)),
            "gloaming",
        ).hits.single()
        assertEquals(3, hit.chapterIndex)
        assertEquals(7, hit.indexInChapter)
        assertEquals(42, hit.globalIndex)
    }
}
