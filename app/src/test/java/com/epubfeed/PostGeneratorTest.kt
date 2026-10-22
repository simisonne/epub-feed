package com.epubfeed

import com.epubfeed.domain.PostGenerator
import com.epubfeed.domain.SentenceSplitter
import com.epubfeed.domain.model.Block
import com.epubfeed.domain.model.BlockType
import com.epubfeed.domain.model.PostLength
import com.epubfeed.domain.model.Run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostGeneratorTest {

    private val generator = PostGenerator()

    private fun paragraphs(vararg texts: String): List<Block> =
        texts.map { Block(BlockType.PARAGRAPH, listOf(Run(it))) }

    @Test
    fun `chapter opener is the chapter title`() {
        val posts = generator.generate("Chapter I", paragraphs("Body text."), PostLength.MEDIUM)
        assertEquals(2, posts.size)
        assertTrue(posts.first().isChapterTitlePost)
        assertEquals("Chapter I", posts.first().plainText)
        assertEquals(0, posts.first().indexInChapter)
        assertTrue(posts.none { it.isChapterTitlePost && it.indexInChapter != 0 })
    }

    @Test
    fun `empty chapter still produces the opener`() {
        val posts = generator.generate("Chapter One", emptyList(), PostLength.SHORT)
        assertEquals(1, posts.size)
        assertTrue(posts.single().isChapterTitlePost)
    }

    @Test
    fun `block grouping respects the budget and never splits a paragraph`() {
        val text = List(6) { "This is sentence number ${it + 1} in a paragraph about something." }
        val posts = generator.generate(
            "Chapter",
            paragraphs(text.joinToString(" ")),
            PostLength.SHORT,
        )

        val body = posts.drop(1)
        assertTrue(body.isNotEmpty())
        // Nothing was cut: every post is a contiguous, whole sentence sequence.
        body.forEach { post ->
            assertTrue(post.plainText.endsWith("."))
            assertFalse(post.plainText.first().isLowerCase())
        }
        assertEquals(
            text.joinToString(" "),
            body.joinToString(" ") { it.plainText }.replace("  ", " "),
        )
    }

    @Test
    fun `concatenated posts reproduce the chapter text exactly`() {
        val source = paragraphs(
            "Mr. Bennet was among the earliest of those who waited on Mr. Bingley.",
            "He had always intended to visit him, though to the last always assuring his wife that he should not go.",
            "The astonishment of the ladies was just what he wished.",
        )
        val posts = generator.generate("Chapter II", source, PostLength.SHORT)
        val rebuilt = posts.drop(1).joinToString(" ") { it.plainText }
            .replace(Regex("\\s+"), " ")
        val expected = source.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ")
        assertEquals(expected, rebuilt)
    }

    @Test
    fun `no split happens at an abbreviation`() {
        val text = "Mr. Bennet replied that he had not. Mrs. Bennet was not convinced at all. " +
            "Dr. Smith agreed with her, and so did the whole neighbourhood that day."
        val sentences = SentenceSplitter.split(text)
        assertTrue(sentences.any { it.startsWith("Mrs. Bennet") })
        assertTrue(sentences.any { it.startsWith("Dr. Smith") })
        assertEquals(text, sentences.joinToString(" "))
    }

    @Test
    fun `splitting is deterministic`() {
        val blocks = paragraphs(
            "First paragraph with a reasonable amount of text in it.",
            "Second paragraph, also of moderate length, with more words.",
        ) + Block(BlockType.QUOTE, listOf(Run("A quoted line.")))
        val first = generator.generate("Ch", blocks, PostLength.MEDIUM)
        val second = generator.generate("Ch", blocks, PostLength.MEDIUM)
        assertEquals(first.map { it.plainText }, second.map { it.plainText })
        assertEquals(first.map { it.firstBlock }, second.map { it.firstBlock })
    }

    @Test
    fun `short posts are shorter than long posts`() {
        val text = (1..40).joinToString(" ") { "Sentence number $it sits in this paragraph." }
        val blocks = paragraphs(text)
        val short = generator.generate("Ch", blocks, PostLength.SHORT).size
        val medium = generator.generate("Ch", blocks, PostLength.MEDIUM).size
        val long = generator.generate("Ch", blocks, PostLength.LONG).size
        assertTrue(short > medium)
        assertTrue(medium > long)
    }

    @Test
    fun `block anchors cover every source paragraph`() {
        val blocks = paragraphs("One text.", "Two text.", "Three text.")
        val posts = generator.generate("Chapter", blocks, PostLength.MEDIUM).drop(1)
        val covered = posts.flatMap { (it.firstBlock..it.lastBlock).toList() }.sorted()
        assertEquals(blocks.indices.toList(), covered)
    }
}
