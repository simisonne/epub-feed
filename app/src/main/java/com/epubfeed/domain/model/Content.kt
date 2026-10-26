package com.epubfeed.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** A single run of text with one style. Runs are the smallest unit of formatting. */
@Immutable
@Serializable
data class Run(
    val text: String,
    val italic: Boolean = false,
    val bold: Boolean = false,
)

@Serializable
enum class BlockType { PARAGRAPH, QUOTE, HEADING, IMAGE }

/**
 * A block level unit of chapter content: a paragraph, a quote (rendered as an X quote card),
 * a heading, or a picture from the book (rendered as an X picture post).
 */
@Immutable
@Serializable
data class Block(
    val type: BlockType = BlockType.PARAGRAPH,
    val runs: List<Run> = emptyList(),
    /**
     * For [BlockType.IMAGE] only: where the picture lives. A ZIP entry name while the book is
     * being parsed, an absolute file path once the import has written it to app storage.
     */
    val imageRef: String? = null,
    /** For [BlockType.IMAGE] only: the book's own alternative text, when it shipped one. */
    val imageAlt: String? = null,
) {
    val text: String get() = runs.joinToString("") { it.text }
    val isBlank: Boolean get() = text.isBlank()
}

/** Post length preference. Values are the target character budget of a generated post. */
enum class PostLength(val budget: Int) {
    SHORT(180),
    MEDIUM(300),
    LONG(560);

    companion object {
        fun fromName(name: String?): PostLength =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MEDIUM
    }
}

sealed interface ImportOutcome {
    data class Success(val bookId: String) : ImportOutcome
    data class Failure(val userMessage: String, val detail: String? = null) : ImportOutcome
}
