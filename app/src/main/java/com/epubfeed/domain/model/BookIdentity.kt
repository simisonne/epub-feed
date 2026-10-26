package com.epubfeed.domain.model

/**
 * The @ an author name turns into: lowercase, punctuation folded into single underscores.
 *
 * It is the fallback for a book whose reader has not set a handle of their own, and it is kept
 * here (instead of in a screen) so the feed, the drawer and the profile can never disagree.
 */
fun handleFor(name: String): String {
    val slug = name.lowercase()
        .trim()
        .removePrefix("@")
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
    return slug.ifBlank { "unknown" }.take(20)
}

/** Strips the @ the reader may have typed, so a stored handle never keeps it. */
fun cleanHandle(raw: String): String = raw.trim().removePrefix("@").trim().take(30)
