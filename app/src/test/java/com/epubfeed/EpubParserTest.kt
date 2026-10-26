package com.epubfeed

import com.epubfeed.data.epub.BookParseException
import com.epubfeed.data.epub.EpubParser
import com.epubfeed.domain.model.BlockType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EpubParserTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `reads title author and spine order`() {
        val file = TestEpubs.writeEpub(
            temp.newFolder("book"),
            chapters = listOf(
                Triple("c1", "one.xhtml", "<html><body><p>Alpha text here.</p></body></html>"),
                Triple("c2", "two.xhtml", "<html><body><p>Beta text here.</p></body></html>"),
                Triple("c3", "three.xhtml", "<html><body><p>Gamma text here.</p></body></html>"),
            ),
            title = "Middlemarch",
            author = "George Eliot",
            navTitles = listOf("Alpha", "Beta", "Gamma"),
        )

        val parsed = EpubParser.parse(file)

        assertEquals("Middlemarch", parsed.title)
        assertEquals("George Eliot", parsed.author)
        assertEquals(listOf("Alpha", "Beta", "Gamma"), parsed.chapters.map { it.title })
        assertTrue(parsed.chapters[0].blocks.first().text.startsWith("Alpha"))
        assertEquals(3, parsed.chapters.size)
    }

    @Test
    fun `spine order decides chapter order not manifest order`() {
        val dir = temp.newFolder("spine")
        val file = TestEpubs.writeEpub(
            dir,
            chapters = listOf(
                Triple("a", "a.xhtml", "<html><body><p>Chapter A text.</p></body></html>"),
                Triple("b", "b.xhtml", "<html><body><p>Chapter B text.</p></body></html>"),
            ),
            navTitles = listOf("A", "B"),
        )
        val parsed = EpubParser.parse(file)
        assertEquals(listOf("A", "B"), parsed.chapters.map { it.title })
    }

    @Test
    fun `chapter titles fall back to the first heading`() {
        val file = TestEpubs.writeEpub(
            temp.newFolder("heading"),
            chapters = listOf(
                Triple(
                    "c1",
                    "c1.xhtml",
                    "<html><body><h1>The Long Gallery</h1><p>Some body text that is long enough.</p></body></html>",
                ),
            ),
            navTitles = null,
            includeToc = false,
        )

        val parsed = EpubParser.parse(file)
        assertEquals("The Long Gallery", parsed.chapters.single().title)
    }

    @Test
    fun `chapter titles fall back to chapter numbers`() {
        val file = TestEpubs.writeEpub(
            temp.newFolder("numbered"),
            chapters = listOf(
                Triple("c1", "c1.xhtml", "<html><body><p>Plain text with no heading at all.</p></body></html>"),
                Triple("c2", "c2.xhtml", "<html><body><p>Another plain chapter of text.</p></body></html>"),
            ),
            navTitles = null,
            includeToc = false,
        )

        val parsed = EpubParser.parse(file)
        assertEquals(listOf("Chapter 1", "Chapter 2"), parsed.chapters.map { it.title })
    }

    @Test
    fun `ncx titles are used when there is no nav document`() {
        val file = TestEpubs.writeEpub(
            temp.newFolder("ncx"),
            chapters = listOf(
                Triple("c1", "c1.xhtml", "<html><body><p>One two three.</p></body></html>"),
                Triple("c2", "c2.xhtml", "<html><body><p>Four five six.</p></body></html>"),
            ),
            navTitles = listOf("From NCX One", "From NCX Two"),
            useNcxOnly = true,
        )

        val parsed = EpubParser.parse(file)
        assertEquals(listOf("From NCX One", "From NCX Two"), parsed.chapters.map { it.title })
    }

    @Test
    fun `italic runs and quotes survive and noise is stripped`() {
        val html = """
            <html><body>
              <p>He said <em>nothing</em> at all.<script>alert('x')</script></p>
              <blockquote>Quoted wisdom.</blockquote>
              <p>More text<img src="x.png"/> follows here.</p>
              <sup>12</sup>
            </body></html>
        """.trimIndent()
        val file = TestEpubs.writeEpub(
            temp.newFolder("formatting"),
            chapters = listOf(Triple("c1", "c1.xhtml", html)),
            navTitles = listOf("Formatting"),
        )

        val parsed = EpubParser.parse(file)
        val blocks = parsed.chapters.single().blocks

        assertTrue(blocks[0].runs.any { it.italic && it.text == "nothing" })
        assertEquals(BlockType.QUOTE, blocks[1].type)
        assertEquals("Quoted wisdom.", blocks[1].text)
        assertTrue(blocks.none { it.text.contains("alert") })
        assertTrue(blocks.none { it.text.contains("img") })
    }

    @Test
    fun `a heading that repeats the chapter title is not duplicated in the body`() {
        val file = TestEpubs.writeEpub(
            temp.newFolder("dupe"),
            chapters = listOf(
                Triple(
                    "c1",
                    "c1.xhtml",
                    "<html><body><h2>Chapter I</h2><p>The real opening paragraph of the chapter.</p></body></html>",
                ),
            ),
            navTitles = listOf("Chapter I"),
        )

        val parsed = EpubParser.parse(file)
        val blocks = parsed.chapters.single().blocks
        assertEquals(1, blocks.size)
        assertTrue(blocks.single().text.startsWith("The real opening paragraph"))
    }

    @Test
    fun `drm protected epub is rejected with a friendly message`() {
        val encryption = """
            <?xml version="1.0"?>
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"
                        xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
              <enc:EncryptedData>
                <enc:CipherData><enc:CipherReference URI="OEBPS/c1.xhtml"/></enc:CipherData>
              </enc:EncryptedData>
            </encryption>
        """.trimIndent()
        val file = TestEpubs.writeEpub(
            temp.newFolder("drm"),
            chapters = listOf(Triple("c1", "c1.xhtml", "<html><body><p>Locked text.</p></body></html>")),
            navTitles = listOf("Locked"),
            extraEntries = mapOf("META-INF/encryption.xml" to encryption),
        )

        try {
            EpubParser.parse(file)
            fail("Expected BookParseException")
        } catch (e: BookParseException) {
            assertTrue(e.userMessage.contains("DRM", ignoreCase = true))
        }
    }

    @Test
    fun `broken zip is rejected without crashing`() {
        val file = TestEpubs.writeGarbage(temp.newFolder("broken"))
        try {
            EpubParser.parse(file)
            fail("Expected BookParseException")
        } catch (e: BookParseException) {
            assertTrue(e.userMessage.isNotBlank())
        }
    }

    @Test
    fun `empty file is rejected`() {
        val file = File(temp.newFolder("empty"), "empty.epub")
        file.writeBytes(ByteArray(0))
        try {
            EpubParser.parse(file)
            fail("Expected BookParseException")
        } catch (e: BookParseException) {
            assertTrue(e.userMessage.contains("empty", ignoreCase = true))
        }
    }

    @Test
    fun `book with no readable text is rejected`() {
        val file = TestEpubs.writeEpub(
            temp.newFolder("notext"),
            chapters = listOf(
                Triple("c1", "c1.xhtml", "<html><body><div></div></body></html>"),
            ),
            navTitles = listOf("Nothing"),
        )
        try {
            EpubParser.parse(file)
            fail("Expected BookParseException")
        } catch (e: BookParseException) {
            assertTrue(e.userMessage.isNotBlank())
        }
    }

    @Test
    fun `relative hrefs and percent escapes resolve`() {
        assertEquals("OEBPS/text/ch1.xhtml", EpubParser.resolve("OEBPS", "text/ch1.xhtml"))
        assertEquals("OEBPS/ch 1.xhtml", EpubParser.resolve("OEBPS", "ch%201.xhtml"))
        assertEquals("OEBPS/ch1.xhtml", EpubParser.resolve("OEBPS", "ch1.xhtml#frag"))
        assertEquals("OEBPS/ch1.xhtml", EpubParser.resolve("OEBPS/text", "../ch1.xhtml"))
    }

    @Test
    fun `a figure becomes a picture between the prose it sits in`() {
        val html = """
            <html><body>
              <p>Before the picture.</p>
              <figure><img src="../images/plate.png" alt="A plate of fruit"/><figcaption>The plate.</figcaption></figure>
              <p>After the picture.</p>
            </body></html>
        """.trimIndent()
        val file = TestEpubs.writeEpub(
            temp.newFolder("plate"),
            chapters = listOf(Triple("c1", "text/ch1.xhtml", html)),
            navTitles = listOf("The Plate"),
            extraBinaryEntries = mapOf("OEBPS/images/plate.png" to PlatePng),
        )

        val parsed = EpubParser.parse(file)
        val blocks = parsed.chapters.single().blocks

        assertEquals(
            listOf(BlockType.PARAGRAPH, BlockType.IMAGE, BlockType.PARAGRAPH, BlockType.PARAGRAPH),
            blocks.map { it.type },
        )
        val picture = blocks[1]
        assertEquals("OEBPS/images/plate.png", picture.imageRef)
        assertEquals("A plate of fruit", picture.imageAlt)
        assertEquals("The plate.", blocks[2].text)
        assertArrayEquals(PlatePng, parsed.illustrations["OEBPS/images/plate.png"])
    }

    @Test
    fun `a page that holds only a picture joins the chapter before it`() {
        val file = TestEpubs.writeEpub(
            temp.newFolder("plates"),
            chapters = listOf(
                Triple("c1", "c1.xhtml", "<html><body><p>The chapter that owns the plate.</p></body></html>"),
                Triple("p1", "plate.xhtml", "<html><body><div><img src=\"plate.png\"/></div></body></html>"),
            ),
            navTitles = listOf("Chapter One"),
            extraBinaryEntries = mapOf("OEBPS/plate.png" to PlatePng),
        )

        val parsed = EpubParser.parse(file)

        assertEquals(1, parsed.chapters.size)
        assertEquals(
            listOf(BlockType.PARAGRAPH, BlockType.IMAGE),
            parsed.chapters.single().blocks.map { it.type },
        )
        assertEquals("OEBPS/plate.png", parsed.chapters.single().blocks[1].imageRef)
    }

    @Test
    fun `a picture the app cannot draw is dropped instead of left as an empty post`() {
        val html = """
            <html><body>
              <p>Text around a drawing.</p>
              <div><img src="art.svg"/></div>
              <div><img src="missing.png"/></div>
            </body></html>
        """.trimIndent()
        val file = TestEpubs.writeEpub(
            temp.newFolder("undrawable"),
            chapters = listOf(Triple("c1", "c1.xhtml", html)),
            navTitles = listOf("Drawings"),
            extraBinaryEntries = mapOf("OEBPS/art.svg" to "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".toByteArray()),
        )

        val parsed = EpubParser.parse(file)
        val blocks = parsed.chapters.single().blocks

        assertTrue(blocks.none { it.type == BlockType.IMAGE })
        assertTrue(parsed.illustrations.isEmpty())
    }

    @Test
    fun `a picture book still produces a readable chapter`() {
        val file = TestEpubs.writeEpub(
            temp.newFolder("picturebook"),
            chapters = listOf(
                Triple("p1", "one.xhtml", "<html><body><div><img src=\"a.png\"/></div></body></html>"),
            ),
            navTitles = listOf("One"),
            includeToc = false,
            extraBinaryEntries = mapOf("OEBPS/a.png" to PlatePng),
        )

        val parsed = EpubParser.parse(file)

        assertEquals("Illustrations", parsed.chapters.single().title)
        assertEquals(BlockType.IMAGE, parsed.chapters.single().blocks.single().type)
    }

    @Test
    fun `a mark too small to be an illustration never becomes a picture post`() {
        val html = """
            <html><body>
              <p>A paragraph between two marks.</p>
              <div><img src="speck.png"/></div>
              <p>And a plate that is worth looking at.</p>
              <div><img src="plate.png"/></div>
            </body></html>
        """.trimIndent()
        val file = TestEpubs.writeEpub(
            temp.newFolder("marks"),
            chapters = listOf(Triple("c1", "c1.xhtml", html)),
            navTitles = listOf("Marks"),
            extraBinaryEntries = mapOf(
                "OEBPS/speck.png" to SpeckPng,
                "OEBPS/plate.png" to PlatePng,
            ),
        )

        val parsed = EpubParser.parse(file)
        val pictures = parsed.chapters.single().blocks.filter { it.type == BlockType.IMAGE }

        assertEquals(listOf("OEBPS/plate.png"), pictures.map { it.imageRef })
        assertEquals(setOf("OEBPS/plate.png"), parsed.illustrations.keys)
    }

    private companion object {
        // A plate big enough to count as an illustration (400x600), header only.
        val PlatePng: ByteArray = TestEpubs.png(400, 600)

        // The kind of mark a book puts between sections: too small to be looked at.
        val SpeckPng: ByteArray = TestEpubs.png(16, 16)
    }
}
