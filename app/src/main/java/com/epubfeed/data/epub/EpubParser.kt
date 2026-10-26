package com.epubfeed.data.epub

import com.epubfeed.data.image.PictureFilter
import com.epubfeed.domain.model.Block
import com.epubfeed.domain.model.BlockType
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Minimal, dependency light EPUB 2 / EPUB 3 parser.
 *
 * ZIP -> META-INF/container.xml -> OPF (manifest + spine) -> XHTML documents.
 * Chapter titles come from the EPUB 3 nav document or the EPUB 2 NCX, falling
 * back to the first heading in the document and finally to "Chapter N".
 */
object EpubParser {

    private const val MAX_TOTAL_UNCOMPRESSED = 300L * 1024 * 1024
    private const val MAX_DOCUMENT_BYTES = 24L * 1024L * 1024L
    private const val MAX_COVER_BYTES = 8L * 1024L * 1024L
    private const val MAX_ILLUSTRATION_BYTES = 16L * 1024L * 1024L
    private const val MAX_TOTAL_ILLUSTRATION_BYTES = 96L * 1024L * 1024L

    /** Enough of an image file to read its size out of the header. */
    private const val MAX_HEADER_BYTES = 64 * 1024

    /** Only formats Android can draw. SVG needs a renderer the app does not ship. */
    private val RASTER_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

    fun parse(file: File): ParsedBook {
        if (!file.exists() || file.length() == 0L) {
            throw BookParseException("This file is empty.")
        }
        val zip = try {
            ZipFile(file)
        } catch (e: ZipException) {
            throw BookParseException("This EPUB is damaged: the ZIP archive cannot be read.", e)
        } catch (e: IOException) {
            throw BookParseException("This EPUB could not be opened.", e)
        }

        zip.use { zf ->
            val entries = zf.entries().toList()
            if (entries.isEmpty()) throw BookParseException("This EPUB is empty.")
            var total = 0L
            entries.forEach { entry ->
                total += entry.size.coerceAtLeast(0)
                if (total > MAX_TOTAL_UNCOMPRESSED) {
                    throw BookParseException("This EPUB is too large to import on this device.")
                }
            }
            checkDrm(zf, entries.map { it.name })

            val containerEntry = zf.getEntry("META-INF/container.xml")
            val opfPath = if (containerEntry != null) {
                val xml = readText(zf, containerEntry, 1024L * 1024L)
                val doc = Jsoup.parse(xml, "", Parser.xmlParser())
                doc.getElementsByTag("rootfile").firstOrNull()?.attr("full-path")?.takeIf { it.isNotBlank() }
            } else {
                entries.firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }?.name
            } ?: throw BookParseException("This EPUB is missing its package document (content.opf).")

            val opfText = readText(zf, zf.getEntry(opfPath) ?: throw BookParseException("This EPUB is missing its package document."), MAX_DOCUMENT_BYTES)
            val opf = Jsoup.parse(opfText, "", Parser.xmlParser())
            val opfDir = opfPath.substringBeforeLast('/', "")

            val title = metadata(opf, "title").ifBlank { file.nameWithoutExtension }
            val author = metadata(opf, "creator").ifBlank { "Unknown author" }
            val publisher = metadata(opf, "publisher").takeIf { it.isNotBlank() }
            val publishedYear = metadata(opf, "date")
                .let { Regex("\\d{4}").find(it)?.value }

            val manifest = LinkedHashMap<String, ManifestItem>()
            opf.getElementsByTag("item").forEach { item ->
                val id = item.attr("id")
                if (id.isBlank()) return@forEach
                manifest[id] = ManifestItem(
                    id = id,
                    href = resolve(opfDir, item.attr("href")),
                    mediaType = item.attr("media-type").lowercase(),
                    properties = item.attr("properties").lowercase(),
                )
            }
            if (manifest.isEmpty()) throw BookParseException("This EPUB is missing its manifest.")

            val tocTitles = runCatching { navTitles(zf, manifest) }
                .getOrElse { emptyMap() }
                .ifEmpty { runCatching { ncxTitles(zf, opf, opfDir, manifest) }.getOrElse { emptyMap() } }

            val spine = spineItems(opf, manifest)
            if (spine.isEmpty()) throw BookParseException("This EPUB has no readable chapters.")

            val chapters = mutableListOf<ParsedChapter>()
            // Pictures that arrive before the first text chapter (a frontispiece, or a book that
            // opens with a plate) are held back and attached to the opener instead of becoming a
            // chapter of their own.
            var leadingImages = mutableListOf<Block>()
            var visited = 0
            for (item in spine) {
                if (item.mediaType !in HTML_TYPES) continue
                visited++
                val entry = zf.getEntry(item.href) ?: continue
                val html = readText(zf, entry, MAX_DOCUMENT_BYTES.toLong())
                val doc = Jsoup.parse(html, "")
                val docDir = item.href.substringBeforeLast('/', "")
                val (blocks, heading) = HtmlBlocks.extract(doc) { src ->
                    val trimmed = src.trim()
                    when {
                        trimmed.isEmpty() -> null
                        trimmed.startsWith("data:", ignoreCase = true) -> null
                        trimmed.contains("://") -> null
                        else -> {
                            val ref = resolve(docDir, trimmed)
                            val ext = ref.substringAfterLast('.', "").lowercase()
                            if (ext !in RASTER_EXTENSIONS) {
                                null
                            } else {
                                val entry = zf.getEntry(ref)?.takeIf { it.size in 1..MAX_ILLUSTRATION_BYTES }
                                // A mark too small to look at never becomes a block, so the block
                                // list and the stored pictures can never disagree.
                                if (entry == null || !pictureQualifies(zf, entry)) null else entry.name
                            }
                        }
                    }
                }
                if (blocks.isEmpty()) continue
                if (blocks.none { it.type != BlockType.IMAGE }) {
                    // A document that holds nothing but pictures: no title, no thread of its own.
                    if (chapters.isEmpty()) {
                        leadingImages += blocks
                    } else {
                        val last = chapters.removeAt(chapters.lastIndex)
                        chapters += last.copy(blocks = last.blocks + blocks)
                    }
                    continue
                }
                val tocTitle = tocTitles[item.href] ?: tocTitles[stripFragment(item.href)]
                val chapterTitle = tocTitle?.takeIf { it.isNotBlank() }
                    ?: heading?.takeIf { it.isNotBlank() }
                    ?: "Chapter ${chapters.size + 1}"
                val finalTitle = tidyTitle(chapterTitle)
                val body = ChapterBlocks.stripTitleHeading(blocks, finalTitle)
                val withLeading = if (leadingImages.isEmpty()) body else leadingImages + body
                leadingImages = mutableListOf()
                chapters += ParsedChapter(title = finalTitle, blocks = withLeading)
            }

            if (chapters.isEmpty()) {
                if (leadingImages.isEmpty()) {
                    throw BookParseException(
                        if (visited == 0) "This EPUB has no readable chapters." else "This EPUB contains no readable text.",
                    )
                }
                // A picture book: nothing but plates, which still make a readable feed.
                chapters += ParsedChapter(title = "Illustrations", blocks = leadingImages)
            }

            val cover = runCatching { coverBytes(zf, opf, manifest) }.getOrNull()
            return ParsedBook(
                title = tidyTitle(title),
                author = tidyTitle(author),
                chapters = chapters,
                coverBytes = cover,
                publisher = publisher,
                publishedYear = publishedYear,
                illustrations = collectIllustrations(zf, chapters),
            )
        }
    }

    /**
     * Reads the pictures the chapters refer to, once each. Any picture left out here (too large,
     * unreadable, too small to be an illustration, or over the book's budget) is dropped along
     * with its block at import time, so a damaged plate never turns into an empty post.
     */
    private fun collectIllustrations(zf: ZipFile, chapters: List<ParsedChapter>): Map<String, ByteArray> {
        val refs = chapters.asSequence()
            .flatMap { it.blocks.asSequence() }
            .mapNotNull { it.imageRef }
            .distinct()
            .toList()
        if (refs.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, ByteArray>()
        var total = 0L
        for (ref in refs) {
            val entry = zf.getEntry(ref) ?: continue
            if (entry.size <= 0 || entry.size > MAX_ILLUSTRATION_BYTES) continue
            if (total + entry.size > MAX_TOTAL_ILLUSTRATION_BYTES) break
            val bytes = runCatching { readBytes(zf, entry, MAX_ILLUSTRATION_BYTES) }.getOrNull() ?: continue
            if (bytes.isEmpty()) continue
            if (!PictureFilter.qualifies(bytes)) continue
            out[ref] = bytes
            total += bytes.size
        }
        return out
    }

    private data class ManifestItem(val id: String, val href: String, val mediaType: String, val properties: String)

    /**
     * Decides whether a picture is big enough to be worth a post by reading only the head of its
     * entry: a chapter can repeat the same mark a hundred times and the whole picture is read once,
     * further down, only for the pictures this lets through.
     */
    private fun pictureQualifies(zf: ZipFile, entry: java.util.zip.ZipEntry): Boolean {
        val head = runCatching {
            zf.getInputStream(entry).use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (out.size() < MAX_HEADER_BYTES) {
                    val read = input.read(buffer, 0, minOf(buffer.size, MAX_HEADER_BYTES - out.size()))
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        }.getOrNull() ?: return true
        return PictureFilter.qualifies(head)
    }

    private val HTML_TYPES = setOf("application/xhtml+xml", "text/html", "application/x-dtbook+xml")

    private fun spineItems(opf: Document, manifest: Map<String, ManifestItem>): List<ManifestItem> {
        val out = mutableListOf<ManifestItem>()
        opf.getElementsByTag("itemref").forEach { ref ->
            val idref = ref.attr("idref")
            val item = manifest[idref] ?: return@forEach
            out += item
        }
        if (out.isEmpty()) {
            // Broken spine: fall back to manifest order, like many reader apps do.
            out += manifest.values.filter { it.mediaType in HTML_TYPES }
        }
        return out.distinctBy { it.href }
    }

    private fun navTitles(zf: ZipFile, manifest: Map<String, ManifestItem>): Map<String, String> {
        val nav = manifest.values.firstOrNull { it.properties.split(' ').contains("nav") } ?: return emptyMap()
        val entry = zf.getEntry(nav.href) ?: return emptyMap()
        val doc = Jsoup.parse(readText(zf, entry, MAX_DOCUMENT_BYTES.toLong()), "")
        val navEl = doc.selectFirst("nav[epub\\:type=toc]") ?: doc.selectFirst("nav") ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        navEl.select("a[href]").forEach { a ->
            val href = resolve(nav.href.substringBeforeLast('/', ""), a.attr("href"))
            val label = a.text().trim()
            if (href.isNotBlank() && label.isNotBlank()) out.putIfAbsent(href, label)
        }
        return out
    }

    private fun ncxTitles(
        zf: ZipFile,
        opf: Document,
        opfDir: String,
        manifest: Map<String, ManifestItem>,
    ): Map<String, String> {
        val tocId = opf.getElementsByTag("spine").firstOrNull()?.attr("toc").orEmpty()
        val ncxItem = manifest[tocId]
            ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
            ?: manifest.values.firstOrNull { it.href.endsWith(".ncx", ignoreCase = true) }
            ?: return emptyMap()
        val entry = zf.getEntry(ncxItem.href) ?: return emptyMap()
        val doc = Jsoup.parse(readText(zf, entry, MAX_DOCUMENT_BYTES.toLong()), "", Parser.xmlParser())
        val baseDir = ncxItem.href.substringBeforeLast('/', "")
        val out = LinkedHashMap<String, String>()
        doc.getElementsByTag("navPoint").forEach { point ->
            val label = point.getElementsByTag("navLabel").firstOrNull()?.text()?.trim().orEmpty()
            val src = point.getElementsByTag("content").firstOrNull()?.attr("src").orEmpty()
            if (label.isBlank() || src.isBlank()) return@forEach
            val href = resolve(baseDir, src)
            out.putIfAbsent(href, label)
        }
        return out
    }

    private fun coverBytes(zf: ZipFile, opf: Document, manifest: Map<String, ManifestItem>): ByteArray? {
        val byProperty = manifest.values.firstOrNull { it.properties.split(' ').contains("cover-image") }
        val metaCoverId = opf.getElementsByTag("meta")
            .firstOrNull { it.attr("name").equals("cover", ignoreCase = true) }
            ?.attr("content")
        val byMeta = metaCoverId?.let { manifest[it] }
        val byName = manifest.values.firstOrNull {
            it.mediaType.startsWith("image/") &&
                (it.id.contains("cover", true) || it.href.contains("cover", true))
        }
        val item = byProperty ?: byMeta ?: byName ?: return null
        val entry = zf.getEntry(item.href) ?: return null
        if (entry.size > MAX_COVER_BYTES) return null
        val bytes = readBytes(zf, entry, MAX_COVER_BYTES.toLong())
        return bytes.takeIf { it.isNotEmpty() }
    }

    private fun checkDrm(zf: ZipFile, names: List<String>) {
        if (names.any { it.equals("META-INF/rights.xml", ignoreCase = true) }) {
            throw BookParseException("This EPUB is DRM protected and cannot be opened.")
        }
        val encryption = names.firstOrNull { it.equals("META-INF/encryption.xml", ignoreCase = true) } ?: return
        val entry = zf.getEntry(encryption) ?: return
        val text = runCatching { readText(zf, entry, 4L * 1024 * 1024) }.getOrNull() ?: return
        val doc = Jsoup.parse(text, "", Parser.xmlParser())
        val encryptedHrefs = doc.getElementsByTag("CipherReference").map { it.attr("URI").lowercase() }
        val protectsContent = encryptedHrefs.any { href ->
            href.endsWith(".xhtml") || href.endsWith(".html") || href.endsWith(".htm") ||
                href.endsWith(".opf") || href.endsWith(".pdf") || href.endsWith(".epub")
        }
        val hasAdept = text.contains("EncryptedData") && text.contains("http://www.w3.org/2001/04/xmlenc#")
        if (protectsContent || (hasAdept && encryptedHrefs.isEmpty())) {
            throw BookParseException("This EPUB is DRM protected and cannot be opened.")
        }
    }

    /** Collects dc:title, dc:creator and friends regardless of namespace prefix style. */
    private fun metadata(opf: Document, localName: String): String {
        val metadataEl = opf.getElementsByTag("metadata").firstOrNull() ?: return ""
        val candidates = metadataEl.allElements.filter { el ->
            val tag = el.tagName().substringAfter(':').lowercase()
            tag == localName
        }
        return candidates.firstOrNull { it.text().isNotBlank() }?.text()?.trim().orEmpty()
    }

    private fun tidyTitle(raw: String): String =
        raw.replace(Regex("""\s+"""), " ").trim().trim('"', '\u201C', '\u201D')

    fun resolve(baseDir: String, href: String): String {
        val decoded = decodeHref(href.trim())
        val withoutFragment = decoded.substringBefore('#')
        val combined = if (baseDir.isBlank()) withoutFragment else "$baseDir/$withoutFragment"
        val parts = ArrayDeque<String>()
        combined.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/")
    }

    private fun decodeHref(href: String): String {
        if (!href.contains('%')) return href
        return try {
            java.net.URLDecoder.decode(href.replace("+", "%2B"), "UTF-8")
        } catch (_: Exception) {
            href
        }
    }

    private fun stripFragment(href: String) = href.substringBefore('#')

    private fun readText(zf: ZipFile, entry: java.util.zip.ZipEntry, limit: Long): String =
        String(readBytes(zf, entry, limit), Charsets.UTF_8)

    private fun readBytes(zf: ZipFile, entry: java.util.zip.ZipEntry, limit: Long): ByteArray {
        if (entry.size > limit) throw BookParseException("This EPUB contains an oversized file.")
        return zf.getInputStream(entry).use { input: InputStream -> readCapped(input, limit) }
    }

    private fun readCapped(input: InputStream, limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > limit) throw BookParseException("This EPUB contains an oversized file.")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
