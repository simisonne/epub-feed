package com.epubfeed

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds small but structurally valid EPUB files for the parser tests. */
object TestEpubs {

    fun writeEpub(
        dir: File,
        name: String = "book.epub",
        chapters: List<Triple<String, String?, String>> = listOf(
            Triple("ch1", "chapter1.xhtml", "<html><body><h2>One</h2><p>First chapter text.</p></body></html>"),
            Triple("ch2", "chapter2.xhtml", "<html><body><h2>Two</h2><p>Second chapter text.</p></body></html>"),
            Triple("ch3", "chapter3.xhtml", "<html><body><h2>Three</h2><p>Third chapter text.</p></body></html>"),
        ),
        title: String = "Test Book",
        author: String = "Test Author",
        navTitles: List<String>? = listOf("Chapter One", "Chapter Two", "Chapter Three"),
        useNcxOnly: Boolean = false,
        includeToc: Boolean = true,
        extraEntries: Map<String, String> = emptyMap(),
    ): File {
        dir.mkdirs()
        val file = File(dir, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """<?xml version="1.0"?><container version="1.0"
                   xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                   <rootfiles><rootfile full-path="OEBPS/content.opf"
                   media-type="application/oebps-package+xml"/></rootfiles></container>""".trimIndent().toByteArray(),
            )
            zip.closeEntry()

            val items = mutableListOf<String>()
            val spine = StringBuilder()
            chapters.forEach { (id, href, _) ->
                items += """<item id="$id" href="$href" media-type="application/xhtml+xml"/>"""
                spine.append("""<itemref idref="$id"/>""")
            }
            if (navTitles != null && !useNcxOnly) {
                items += """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>"""
            }
            if (includeToc) {
                items += """<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>"""
            }
            val manifest = items.joinToString("")

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """<?xml version="1.0" encoding="utf-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="id">urn:uuid:test</dc:identifier>
                    <dc:title>$title</dc:title>
                    <dc:creator>$author</dc:creator>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest>$manifest</manifest>
                  <spine>$spine</spine>
                </package>""".trimIndent().toByteArray(),
            )
            zip.closeEntry()

            chapters.forEach { (_, href, html) ->
                zip.putNextEntry(ZipEntry("OEBPS/$href"))
                zip.write(html.toByteArray())
                zip.closeEntry()
            }

            if (navTitles != null && includeToc) {
                val items = chapters.mapIndexed { index, chapter ->
                    """<li><a href="${chapter.second}">${navTitles.getOrElse(index) { "Chapter ${index + 1}" }}</a></li>"""
                }.joinToString("")
                zip.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
                zip.write(
                    """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                       <body><nav epub:type="toc"><ol>$items</ol></nav></body></html>""".trimIndent().toByteArray(),
                )
                zip.closeEntry()
            }

            if (includeToc) {
            val navPoints = chapters.mapIndexed { index, chapter ->
                """<navPoint id="n$index" playOrder="${index + 1}"><navLabel>
                   <text>${navTitles?.getOrNull(index) ?: "Ncx Chapter ${index + 1}"}</text></navLabel>
                   <content src="${chapter.second}"/></navPoint>"""
            }.joinToString("")
            zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            zip.write(
                """<?xml version="1.0"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                   <navMap>$navPoints</navMap></ncx>""".trimIndent().toByteArray(),
            )
            zip.closeEntry()
            }

            extraEntries.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    fun writeGarbage(dir: File, name: String = "broken.epub"): File {
        dir.mkdirs()
        val file = File(dir, name)
        file.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x01, 0x02, 0x03, 0x63))
        return file
    }
}
