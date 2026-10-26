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
        extraBinaryEntries: Map<String, ByteArray> = emptyMap(),
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
            extraBinaryEntries.keys.forEach { path ->
                val href = path.removePrefix("OEBPS/")
                val id = "res-" + href.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9]"), "-")
                val media = when (href.substringAfterLast('.').lowercase()) {
                    "png" -> "image/png"
                    "jpg", "jpeg" -> "image/jpeg"
                    "gif" -> "image/gif"
                    "svg" -> "image/svg+xml"
                    else -> "application/octet-stream"
                }
                items += """<item id="$id" href="$href" media-type="$media"/>"""
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

            extraBinaryEntries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
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

    /**
     * A PNG with real dimensions in its header and no pixel data.
     *
     * That is all the parser and the picture filter ever look at, so a test can say "a 400x600
     * plate" without shipping a photograph.
     */
    fun png(width: Int, height: Int): ByteArray {
        val header = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52,
            (width ushr 24).toByte(), (width ushr 16).toByte(), (width ushr 8).toByte(), width.toByte(),
            (height ushr 24).toByte(), (height ushr 16).toByte(), (height ushr 8).toByte(), height.toByte(),
            0x08, 0x02, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
        )
        return header
    }
}
