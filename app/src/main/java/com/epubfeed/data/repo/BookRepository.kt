package com.epubfeed.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.epubfeed.data.epub.ParsedBook
import com.epubfeed.data.epub.ParsedChapter
import com.epubfeed.data.local.AppDatabase
import com.epubfeed.data.local.BookDao
import com.epubfeed.data.local.BookEntity
import com.epubfeed.data.local.ChapterDao
import com.epubfeed.data.local.ChapterEntity
import com.epubfeed.data.local.PostDao
import com.epubfeed.data.local.PostEntity
import com.epubfeed.data.local.PostRow
import com.epubfeed.data.local.ReactionDao
import com.epubfeed.data.local.ReactionEntity
import com.epubfeed.data.prefs.SettingsRepository
import com.epubfeed.domain.PostGenerator
import com.epubfeed.domain.model.Block
import com.epubfeed.domain.model.ChapterSummary
import com.epubfeed.domain.model.FeedPost
import com.epubfeed.domain.model.PostLength
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class BookRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val postDao: PostDao,
    private val reactionDao: ReactionDao,
    private val settings: SettingsRepository,
    private val generator: PostGenerator,
    private val json: Json,
) {

    fun observeBooks(): Flow<List<BookEntity>> = bookDao.observeAll()

    fun observeBook(bookId: String): Flow<BookEntity?> = bookDao.observe(bookId)

    suspend fun getBook(bookId: String): BookEntity? = bookDao.get(bookId)

    suspend fun anyBooks(): Boolean = bookDao.count() > 0

    fun observePosts(bookId: String, start: Int, end: Int): Flow<List<FeedPost>> =
        postDao.observeWindow(bookId, start, end).map { rows -> rows.map { it.toFeedPost() } }

    fun observeSaved(bookId: String): Flow<List<FeedPost>> =
        postDao.observeSaved(bookId).map { rows -> rows.map { it.toFeedPost() } }

    fun observeChapters(bookId: String): Flow<List<ChapterSummary>> =
        chapterDao.observeRows(bookId).map { rows ->
            rows.map {
                ChapterSummary(
                    id = it.id,
                    index = it.chapterIndex,
                    title = it.title,
                    postCount = it.postCount,
                    firstIndex = it.firstIndex,
                    lastIndex = it.lastIndex,
                )
            }
        }

    suspend fun firstPostIndex(bookId: String): Int = postDao.firstIndexForBook(bookId) ?: 0

    suspend fun lastPostIndex(bookId: String): Int = postDao.lastIndexForBook(bookId) ?: 0

    suspend fun saveReadingPosition(bookId: String, index: Int) {
        bookDao.updateLastPostIndex(bookId, index)
    }

    /**
     * Position writes are debounced in an app scoped coroutine so a fling does not
     * hammer SQLite, and so the last write still lands after the screen is gone.
     */
    fun saveReadingPositionAsync(bookId: String, index: Int) {
        val previous = pendingPosition
        pendingPosition = bookId to index
        positionJob?.cancel()
        // Jitter of a single post never needs its own write: keep the last saved spot.
        if (previous != null && previous.first == bookId && kotlin.math.abs(previous.second - index) < 2) return
        positionJob = ioScope.launch {
            delay(POSITION_DEBOUNCE_MS)
            pendingPosition?.let { (id, position) -> bookDao.updateLastPostIndex(id, position) }
        }
    }

    fun saveReadingPositionNow(bookId: String, index: Int) {
        pendingPosition = bookId to index
        positionJob?.cancel()
        ioScope.launch { bookDao.updateLastPostIndex(bookId, index) }
    }

    suspend fun toggleLike(postId: String) {
        val post = postDao.get(postId) ?: return
        transactionally {
            val current = reactionDao.get(postId)
            reactionDao.upsert(
                ReactionEntity(
                    postId = postId,
                    bookId = post.bookId,
                    liked = !(current?.liked ?: false),
                    bookmarked = current?.bookmarked ?: false,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun toggleBookmark(postId: String) {
        val post = postDao.get(postId) ?: return
        transactionally {
            val current = reactionDao.get(postId)
            reactionDao.upsert(
                ReactionEntity(
                    postId = postId,
                    bookId = post.bookId,
                    liked = current?.liked ?: false,
                    bookmarked = !(current?.bookmarked ?: false),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun deleteBook(bookId: String) {
        val book = bookDao.get(bookId)
        bookDao.delete(bookId)
        book?.coverPath?.let { path -> runCatching { File(path).delete() } }
        if (settings.currentLastBookId() == bookId) {
            settings.setLastBookId(bookDao.all().firstOrNull()?.id)
        }
    }

    suspend fun setActiveBook(bookId: String) = settings.setLastBookId(bookId)

    /**
     * Stores a freshly parsed book. Posts are generated from the chapter paragraphs,
     * which are also persisted so a post length change never needs the original file.
     */
    suspend fun persistParsedBook(
        parsed: ParsedBook,
        format: String,
        isSample: Boolean,
        length: PostLength,
    ): String {
        val bookId = UUID.randomUUID().toString()
        val chapterEntities = parsed.chapters.mapIndexed { index, chapter ->
            ChapterEntity(
                id = chapterId(bookId, index),
                bookId = bookId,
                index = index,
                title = chapter.title,
                blocksJson = json.encodeToString(ListSerializer, chapter.blocks),
                postCount = 0,
            )
        }
        if (chapterEntities.isEmpty()) {
            throw com.epubfeed.data.epub.BookParseException("This book contains no readable text.")
        }

        val coverPath = writeCover(bookId, parsed.coverBytes)
        var global = 0
        val posts = ArrayList<PostEntity>()
        val counts = IntArray(chapterEntities.size)

        chapterEntities.forEach { chapter ->
            val blocks = json.decodeFromString(ListSerializer, chapter.blocksJson)
            val generated = generator.generate(chapter.title, blocks, length)
            counts[chapter.index] = generated.size
            chapter.postCount = generated.size
            generated.forEach { post ->
                posts += PostEntity(
                    id = postId(bookId, chapter.index, post.indexInChapter),
                    bookId = bookId,
                    chapterId = chapter.id,
                    chapterIndex = chapter.index,
                    indexInChapter = post.indexInChapter,
                    globalIndex = global,
                    blocksJson = json.encodeToString(ListSerializer, post.blocks),
                    isChapterTitlePost = post.isChapterTitlePost,
                    plainText = post.plainText,
                    firstBlock = post.firstBlock,
                    lastBlock = post.lastBlock,
                )
                global++
            }
        }

        val book = BookEntity(
            id = bookId,
            title = parsed.title.ifBlank { "Untitled" },
            author = parsed.author.ifBlank { "Unknown author" },
            format = format,
            coverPath = coverPath,
            hue = hueFor(parsed.title + parsed.author),
            importedAt = System.currentTimeMillis(),
            lastPostIndex = 0,
            isSample = isSample,
            postCount = global,
            chapterCount = chapterEntities.size,
        )

        db.withTransaction {
            bookDao.upsert(book)
            chapterDao.insertAll(chapterEntities)
            posts.chunked(INSERT_CHUNK).forEach { postDao.insertAll(it) }
        }
        settings.setLastBookId(bookId)
        return bookId
    }

    /**
     * Re-splits every chapter with a new post length. Reading position and all likes /
     * bookmarks are carried over by anchoring reactions to the source paragraph range.
     */
    suspend fun setPostLength(bookId: String, length: PostLength): Int {
        val book = bookDao.get(bookId) ?: return 0
        val chapters = chapterDao.listForBook(bookId)
        if (chapters.isEmpty()) return book.lastPostIndex

        val anchors = reactionDao.anchorsForBook(bookId)
        val currentPost = postDao.getAt(bookId, book.lastPostIndex)
            ?: postDao.allForBook(bookId).firstOrNull { it.globalIndex >= book.lastPostIndex }
        val anchorChapterIndex = currentPost?.chapterIndex ?: 0
        val chapterPosts = currentPost?.let { postDao.forChapter(it.chapterId) } ?: emptyList()
        val fractionInChapter = if (chapterPosts.size <= 1) 0f else {
            val pos = chapterPosts.indexOfFirst { it.globalIndex == currentPost?.globalIndex }
            if (pos < 0) 0f else pos.toFloat() / (chapterPosts.size - 1)
        }

        var newPosition = 0
        var global = 0
        val newPosts = ArrayList<PostEntity>()
        val newReactions = ArrayList<ReactionEntity>()
        val counts = HashMap<Int, Int>()
        val starts = HashMap<Int, Int>()

        chapters.sortedBy { it.index }.forEach { chapter ->
            val blocks = json.decodeFromString(ListSerializer, chapter.blocksJson)
            val generated = generator.generate(chapter.title, blocks, length)
            starts[chapter.index] = global
            counts[chapter.index] = generated.size
            generated.forEach { post ->
                val id = postId(bookId, chapter.index, post.indexInChapter)
                newPosts += PostEntity(
                    id = id,
                    bookId = bookId,
                    chapterId = chapter.id,
                    chapterIndex = chapter.index,
                    indexInChapter = post.indexInChapter,
                    globalIndex = global,
                    blocksJson = json.encodeToString(ListSerializer, post.blocks),
                    isChapterTitlePost = post.isChapterTitlePost,
                    plainText = post.plainText,
                    firstBlock = post.firstBlock,
                    lastBlock = post.lastBlock,
                )
                global++
            }
        }

        // Map every reaction onto the new post that covers the same source paragraphs.
        val byChapter = newPosts.groupBy { it.chapterId }
        anchors.forEach { anchor ->
            val target = if (anchor.firstBlock < 0) {
                byChapter[anchor.chapterId]?.firstOrNull { it.isChapterTitlePost }
            } else {
                byChapter[anchor.chapterId]?.firstOrNull {
                    !it.isChapterTitlePost && anchor.firstBlock >= it.firstBlock && anchor.firstBlock <= it.lastBlock
                }
            } ?: return@forEach
            newReactions += ReactionEntity(
                postId = target.id,
                bookId = bookId,
                liked = anchor.liked,
                bookmarked = anchor.bookmarked,
                updatedAt = anchor.updatedAt,
            )
        }

        val chapterCount = counts[anchorChapterIndex] ?: 1
        val chapterStart = starts[anchorChapterIndex] ?: 0
        newPosition = (chapterStart + (fractionInChapter * (chapterCount - 1)).roundToInt())
            .coerceIn(0, (global - 1).coerceAtLeast(0))

        db.withTransaction {
            postDao.deleteForBook(bookId)
            chapterDao.listForBook(bookId).forEach { chapter ->
                chapterDao.updatePostCount(chapter.id, counts[chapter.index] ?: chapter.postCount)
            }
            newPosts.chunked(INSERT_CHUNK).forEach { postDao.insertAll(it) }
            newReactions.chunked(INSERT_CHUNK).forEach { reactionDao.insertAll(it) }
            bookDao.updateCounts(bookId, global, chapters.size)
            bookDao.updateLastPostIndex(bookId, newPosition)
        }
        return newPosition
    }

    private suspend fun transactionally(block: suspend () -> Unit) {
        db.withTransaction { block() }
    }

    private fun writeCover(bookId: String, bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        return runCatching {
            val dir = File(context.filesDir, "covers").apply { mkdirs() }
            val file = File(dir, "$bookId.img")
            file.writeBytes(bytes)
            file.absolutePath
        }.getOrNull()
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var positionJob: Job? = null
    private var pendingPosition: Pair<String, Int>? = null

    private fun PostRow.toFeedPost(): FeedPost = FeedPost(
        id = id,
        chapterIndex = chapterIndex,
        indexInChapter = indexInChapter,
        globalIndex = globalIndex,
        isChapterTitlePost = isChapterTitlePost,
        blocks = decodeBlocks(blocksJson),
        liked = liked,
        bookmarked = bookmarked,
        plainText = plainText,
    )

    fun decodeBlocks(blocksJson: String): List<Block> =
        runCatching { json.decodeFromString(ListSerializer, blocksJson) }.getOrDefault(emptyList())

    companion object {
        private const val INSERT_CHUNK = 500
        private const val POSITION_DEBOUNCE_MS = 700L
        private val ListSerializer = kotlinx.serialization.builtins.ListSerializer(Block.serializer())

        fun chapterId(bookId: String, index: Int): String = "$bookId:$index"

        fun postId(bookId: String, chapterIndex: Int, indexInChapter: Int): String =
            "$bookId:$chapterIndex:$indexInChapter"

        fun hueFor(seed: String): Int {
            val hash = seed.fold(7) { acc, c -> acc * 31 + c.code }
            return ((hash % 360) + 360) % 360
        }
    }
}
