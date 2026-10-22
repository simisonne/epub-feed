package com.epubfeed

import com.epubfeed.data.epub.BookParseException
import com.epubfeed.data.pdf.PdfBookParser
import com.epubfeed.data.pdf.PdfOutlineEntry
import com.epubfeed.data.pdf.PdfTextSanitizer
import com.epubfeed.data.pdf.PdfTextSource
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PdfBookParserTest {

    @get:Rule
    val temp = TemporaryFolder()

    private class FakePdf(
        private val pages: List<String>,
        private val entries: List<PdfOutlineEntry> = emptyList(),
    ) : PdfTextSource {
        override val pageCount: Int get() = pages.size
        override val title: String? get() = "Fake"
        override val author: String? get() = "Tester"
        override fun pageText(pageIndex: Int): String = pages[pageIndex]
        override fun outline(): List<PdfOutlineEntry> = entries
    }

    private fun page(number: Int, paragraph: String = "Body text for page $number.") =
        "$paragraph\n"

    @Test
    fun `outline becomes chapters with the right page ranges`() {
        val pages = (0 until 10).map { page(it, "Content of page $it with enough words.") }
        val source = FakePdf(
            pages,
            listOf(
                PdfOutlineEntry("Introduction", 0),
                PdfOutlineEntry("The Middle", 3),
                PdfOutlineEntry("The End", 7),
            ),
        )

        val parsed = PdfBookParser.parse(source)
        assertEquals(listOf("Introduction", "The Middle", "The End"), parsed.chapters.map { it.title })
        assertTrue(parsed.chapters[0].blocks.joinToString(" ") { it.text }.contains("page 0"))
        assertTrue(parsed.chapters[1].blocks.joinToString(" ") { it.text }.contains("page 3"))
        assertTrue(parsed.chapters[2].blocks.joinToString(" ") { it.text }.contains("page 9"))
    }

    @Test
    fun `without an outline pages are chunked`() {
        val pages = (0 until 25).map { page(it) }
        val parsed = PdfBookParser.parse(FakePdf(pages), pagesPerChapter = 10)
        assertEquals(3, parsed.chapters.size)
        assertEquals("Pages 1\u201310", parsed.chapters[0].title)
        assertEquals("Pages 11\u201320", parsed.chapters[1].title)
        assertEquals("Pages 21\u201325", parsed.chapters[2].title)
    }

    @Test
    fun `a scanned pdf is rejected with a friendly message`() {
        val source = FakePdf(List(5) { "\n\n  \n" })
        try {
            PdfBookParser.parse(source)
            fail("Expected BookParseException")
        } catch (e: BookParseException) {
            assertTrue(e.userMessage.contains("scan", ignoreCase = true))
        }
    }

    @Test
    fun `progress is reported page by page`() {
        val pages = (0 until 6).map { page(it) }
        var lastDone = 0
        var lastTotal = 0
        PdfBookParser.parse(FakePdf(pages, listOf(PdfOutlineEntry("A", 0), PdfOutlineEntry("B", 3)))) { done, total ->
            lastDone = done
            lastTotal = total
        }
        assertEquals(6, lastDone)
        assertEquals(6, lastTotal)
    }

    @Test
    fun `sanitizer rejoins hyphenated words and drops page numbers`() {
        val raw = """
            The quick brown fox jumps over the lazy
            dog, and then it keeps on run-
            ning through the forest.
            42
            A second paragraph starts here and stays
            together across the wrap.
        """.trimIndent()

        val paragraphs = PdfTextSanitizer.paragraphs(raw)
        assertEquals(2, paragraphs.size)
        assertTrue(paragraphs[0].contains("running through the forest"))
        assertTrue(paragraphs.none { it.trim() == "42" })
        assertTrue(paragraphs[1].startsWith("A second paragraph"))
    }

    @Test
    fun `sanitizer keeps dialogue on one line`() {
        val raw = "\"Where are you going?\" asked the\nlady. \"To town,\" he said.\n"
        val paragraphs = PdfTextSanitizer.paragraphs(raw)
        assertEquals(1, paragraphs.size)
        assertTrue(paragraphs.single().contains("To town"))
    }

    @Test
    fun `real pdf text extraction and chapter splitting`() {
        val file = File(temp.newFolder("pdf"), "sample.pdf")
        PDDocument().use { document ->
            val textPages = listOf(
                "Chapter One\n\nIt was a bright cold day in April, and the clocks were striking thirteen.",
                "The hallway smelt of boiled cabbage and old rag mats.",
                "Chapter Two\n\nWinston Smith, his chin nuzzled into his breast, slipped quickly through the glass doors.",
                "Outside, even through the shut window pane, the world looked cold.",
            )
            textPages.forEach { text ->
                val pageObj = PDPage()
                document.addPage(pageObj)
                PDPageContentStream(document, pageObj).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(72f, 720f)
                    stream.setLeading(16f)
                    text.split("\n").forEach { line ->
                        stream.showText(line)
                        stream.newLine()
                    }
                    stream.endText()
                }
            }
            document.save(file)
        }

        val parsed = PdfBookParser.parse(JvmPdfSource(file), pagesPerChapter = 2)
        val all = parsed.chapters.joinToString("\n\n") { chapter -> chapter.blocks.joinToString(" ") { it.text } }

        assertTrue(all.contains("bright cold day in April"))
        assertTrue(all.contains("Winston Smith"))
        assertEquals(2, parsed.chapters.size)
        assertEquals("Pages 1\u20132", parsed.chapters[0].title)
    }

    /** PDFBox (JVM) text source used to exercise the stripping path in a plain unit test. */
    private class JvmPdfSource(file: File) : PdfTextSource {
        private val document = PDDocument.load(file)
        override val pageCount: Int get() = document.numberOfPages
        override val title: String? get() = null
        override val author: String? get() = null

        override fun pageText(pageIndex: Int): String {
            val stripper = PDFTextStripper()
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            return stripper.getText(document)
        }

        override fun outline(): List<PdfOutlineEntry> = emptyList()
    }
}
