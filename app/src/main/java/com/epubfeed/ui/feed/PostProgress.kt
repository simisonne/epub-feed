package com.epubfeed.ui.feed

import com.epubfeed.domain.model.FeedPost

/**
 * How far through its own chapter a post sits, not through the whole book: the counter on
 * a post reads the length of the chapter being read.
 *
 * A chapter's posts are numbered from 0, and the chapter opener is post 0, so the first
 * post of a ten post chapter reads 10% and the last one 100%.
 */
fun percentLabel(post: FeedPost, chapterPosts: Int): String {
    if (chapterPosts <= 0) return "0%"
    val percent = (post.indexInChapter + 1) * 100 / chapterPosts
    return "${percent.coerceIn(1, 100)}%"
}
