package com.epubfeed.ui.feed

import com.epubfeed.domain.model.FeedPost

/**
 * The list index the feed should park on to restore the reader's place.
 *
 * [posts] is the chapter that is loaded, [target] the global post index the reader left off at and
 * [leading] the number of items in front of the chapter (the pinned welcome post of the sample
 * book). Null means there is nothing to scroll to.
 */
fun restoreListIndex(posts: List<FeedPost>, target: Int, leading: Int): Int? {
    if (posts.isEmpty()) return null
    val found = posts.indexOfFirst { it.globalIndex >= target }
    return when {
        // Park on the post itself, offset by whatever sits in front of the chapter.
        found > 0 -> found + leading
        // The chapter's first post: the pinned welcome post, when there is one, comes first.
        found == 0 -> 0
        // The position lies past this chapter (a place written before the book was re-sliced).
        // Showing the last post beats showing the first, and the saver has to be released.
        else -> posts.lastIndex + leading
    }
}
