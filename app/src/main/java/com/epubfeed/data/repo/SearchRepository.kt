package com.epubfeed.data.repo

import com.epubfeed.data.local.PostDao
import com.epubfeed.domain.search.BookSearch
import com.epubfeed.domain.search.SearchResult
import com.epubfeed.domain.search.SearchText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Search across one book.
 *
 * The text lives in SQLite, so the rows are read once per query and matched in memory: a few
 * hundred milliseconds of work on a big book, and far more accurate than LIKE, which cannot tell
 * a whole word from the inside of another one.
 */
@Singleton
class SearchRepository @Inject constructor(
    private val postDao: PostDao,
) {

    suspend fun search(bookId: String, query: String): SearchResult {
        if (BookSearch.tokenize(query).isEmpty()) return SearchResult()
        val rows = postDao.searchRows(bookId)
        return withContext(Dispatchers.Default) {
            BookSearch.run(
                posts = rows.map {
                    SearchText(
                        postId = it.id,
                        chapterIndex = it.chapterIndex,
                        indexInChapter = it.indexInChapter,
                        globalIndex = it.globalIndex,
                        text = it.plainText,
                    )
                },
                query = query,
            )
        }
    }
}
