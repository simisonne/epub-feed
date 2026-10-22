package com.epubfeed.data.epub

import com.epubfeed.domain.model.Block

data class ParsedChapter(
    val title: String,
    val blocks: List<Block>,
)

data class ParsedBook(
    val title: String,
    val author: String,
    val chapters: List<ParsedChapter>,
    val coverBytes: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParsedBook) return false
        return title == other.title && author == other.author && chapters == other.chapters &&
            coverBytes.contentEquals(other.coverBytes)
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + author.hashCode()
        result = 31 * result + chapters.hashCode()
        result = 31 * result + (coverBytes?.contentHashCode() ?: 0)
        return result
    }
}

/** Thrown for anything the user should see as a friendly message instead of a crash. */
class BookParseException(val userMessage: String, cause: Throwable? = null, val detail: String? = null) :
    Exception(userMessage, cause)
