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
import com.epubfeed.data.local.NoteDao
import com.epubfeed.data.local.NoteEntity
import com.epubfeed.data.local.PostDao
import com.epubfeed.data.local.PostEntity
import com.epubfeed.data.local.PostRow
import com.epubfeed.data.local.ReactionDao
import com.epubfeed.data.local.ReactionEntity
import com.epubfeed.data.local.StatsDao
import com.epubfeed.data.prefs.SettingsRepository
import com.epubfeed.domain.PostGenerator
import com.epubfeed.domain.model.Block
import com.epubfeed.domain.model.BlockType
import com.epubfeed.domain.model.ChapterSummary
import com.epubfeed.domain.model.CollectionOrder
import com.epubfeed.domain.model.FeedPost
import com.epubfeed.domain.model.PostLength
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A chapter as it will be written back: a title and its original source blocks. */
private data class ChapterSlice(val title: String, val blocks: List<Block>)

/** A reaction waiting to be re-attached after the posts were regenerated. */
private data class ReactionCarry(
    val offset: Int,
    val liked: Boolean,
    val bookmarked: Boolean,
    val updatedAt: Long,
)

/** A note waiting to be re-attached after the posts were regenerated. */
private data class NoteCarry(val offset: Int, val note: NoteEntity)

@Singleton
class BookRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val postDao: PostDao,
    private val reactionDao: ReactionDao,
    private val noteDao: NoteDao,
    private val statsDao: StatsDao,
    private val settings: SettingsRepository,
    private val generator: PostGenerator,
    private val json: Json,
) {

    fun observeBooks(): Flow<List<BookEntity>> = bookDao.observeAll()

    fun observeBook(bookId: String): Flow<BookEntity?> = bookDao.observe(bookId)

    suspend fun getBook(bookId: String): BookEntity? = bookDao.get(bookId)

    suspend fun anyBooks(): Boolean = bookDao.count() > 0

    /** One chapter is one feed: the whole chapter arrives in order. */
    fun observeChapterPosts(bookId: String, chapterIndex: Int): Flow<List<FeedPost>> =
        postDao.observeChapter(bookId, chapterIndex).map { rows -> rows.map { it.toFeedPost() } }

    fun observeLiked(bookId: String): Flow<List<FeedPost>> = inReadingOrder(postDao.observeLiked(bookId))

    fun observeSaved(bookId: String): Flow<List<FeedPost>> = inReadingOrder(postDao.observeSaved(bookId))

    /**
     * The Likes and Saved lists follow the book, so the first entry is the post nearest the start
     * of the book. The setting flips that for readers who want the furthest read first.
     */
    private fun inReadingOrder(rows: Flow<List<PostRow>>): Flow<List<FeedPost>> =
        combine(rows, settings.settings.map { it.collectionOrder }.distinctUntilChanged()) { list, order ->
            val posts = list.map { it.toFeedPost() }
            if (order == CollectionOrder.LAST_TO_FIRST) posts.asReversed() else posts
        }

    fun observeNotes(bookId: String): Flow<List<NoteEntity>> = noteDao.observeForBook(bookId)

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

    fun observeReadingSeconds(bookId: String): Flow<Long> =
        statsDao.observe(bookId).map { it?.secondsRead ?: 0L }

    suspend fun firstPostIndex(bookId: String): Int = postDao.firstIndexForBook(bookId) ?: 0

    suspend fun lastPostIndex(bookId: String): Int = postDao.lastIndexForBook(bookId) ?: 0

    suspend fun saveReadingPosition(bookId: String, index: Int) {
        bookDao.updateLastPostIndex(bookId, index)
    }

    /**
     * Position writes are debounced in an app scoped coroutine so a fling does not
     * hammer SQLite, and so the last write still lands after the screen is gone.
     *
     * The pending position is always replaced, but the timer only restarts when the
     * index actually changed. Cancelling the job and then returning early (the old
     * behaviour) meant a run of single-post moves cancelled the only pending write
     * and never scheduled another one, so nothing was ever persisted.
     */
    fun saveReadingPositionAsync(bookId: String, index: Int) {
        val scheduled = pendingPosition
        pendingPosition = bookId to index
        if (scheduled != null && scheduled.first == bookId && scheduled.second == index) return
        positionJob?.cancel()
        positionJob = ioScope.launch {
            delay(POSITION_DEBOUNCE_MS)
            val (id, position) = pendingPosition ?: return@launch
            bookDao.updateLastPostIndex(id, position)
        }
    }

    fun saveReadingPositionNow(bookId: String, index: Int) {
        pendingPosition = bookId to index
        positionJob?.cancel()
        positionJob = null
        ioScope.launch { bookDao.updateLastPostIndex(bookId, index) }
    }

    suspend fun addReadingSeconds(bookId: String, seconds: Long) {
        if (seconds <= 0L) return
        db.withTransaction {
            if (statsDao.secondsFor(bookId) == null) {
                statsDao.upsert(com.epubfeed.data.local.BookStatsEntity(bookId, 0))
            }
            statsDao.addSeconds(bookId, seconds)
        }
    }

    suspend fun addNote(postId: String, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val post = postDao.get(postId) ?: return false
        noteDao.upsert(
            NoteEntity(
                id = UUID.randomUUID().toString(),
                postId = postId,
                bookId = post.bookId,
                text = trimmed.take(NOTE_LIMIT),
                createdAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    suspend fun deleteNote(noteId: String) = noteDao.delete(noteId)

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

    /** Books imported before the word count column existed get it filled on first use. */
    suspend fun ensureWordCount(bookId: String): Int {
        val book = bookDao.get(bookId) ?: return 0
        if (book.wordCount > 0) return book.wordCount
        val posts = postDao.allForBook(bookId)
        if (posts.isEmpty()) return 0
        val words = posts.sumOf { post ->
            post.plainText.split(' ', '\n').count { it.isNotBlank() }
        }
        bookDao.updateWordCount(bookId, words)
        return words
    }

    suspend fun deleteBook(bookId: String) {
        val book = bookDao.get(bookId)
        bookDao.delete(bookId)
        statsDao.deleteForBook(bookId)
        book?.coverPath?.let { path -> runCatching { File(path).delete() } }
        runCatching { File(illustrationDir(bookId)).deleteRecursively() }
        runCatching { File(avatarDir(bookId)).deleteRecursively() }
        if (settings.currentLastBookId() == bookId) {
            settings.setLastBookId(bookDao.all().firstOrNull()?.id)
        }
    }

    suspend fun setActiveBook(bookId: String) = settings.setLastBookId(bookId)

    /** Renames the book: the display name, the author line and the @ the reader wants. */
    suspend fun updateIdentity(bookId: String, title: String, author: String, handle: String?) {
        val cleanTitle = title.trim().ifBlank { "Untitled" }
        val cleanAuthor = author.trim().ifBlank { "Unknown author" }
        val cleanTag = handle?.trim()?.removePrefix("@")?.trim()?.takeIf { it.isNotBlank() }
        bookDao.updateIdentity(bookId, cleanTitle, cleanAuthor, cleanTag)
    }

    /** Sets the profile photo, or clears it back to the cover / letter fallback. */
    suspend fun setAvatar(bookId: String, path: String?) {
        bookDao.updateAvatar(bookId, path?.takeIf { File(it).exists() })
    }

    /** Picks the letter circle over the book's cover, or back again with a real picture. */
    suspend fun useLetterAvatar(bookId: String) {
        bookDao.updateAvatar(bookId, BookEntity.LetterAvatar)
    }

    /** Writes the library order the reader dragged into place, one position per book. */
    suspend fun reorderBooks(bookIds: List<String>) {
        if (bookIds.isEmpty()) return
        db.withTransaction {
            bookIds.forEachIndexed { index, id -> bookDao.updateLibraryOrder(id, index) }
        }
    }

    /**
     * Stores a freshly parsed book. Posts are generated from the chapter paragraphs, which are
     * also persisted so a post length change never needs the original file.
     *
     * Pictures are written to app storage first: the parsed blocks carry ZIP entry names, the
     * stored blocks carry file paths. A picture that could not be written loses its block, so a
     * broken plate never leaves an empty post behind.
     */
    suspend fun persistParsedBook(
        parsed: ParsedBook,
        format: String,
        isSample: Boolean,
        length: PostLength,
        pageImages: List<ByteArray> = emptyList(),
    ): String {
        val bookId = UUID.randomUUID().toString()
        val illustrationPaths = writeIllustrations(bookId, parsed.illustrations)
        val slices = parsed.chapters.map { chapter ->
            ChapterSlice(chapter.title, resolveImages(chapter.blocks, illustrationPaths))
        }
        if (slices.isEmpty()) {
            throw com.epubfeed.data.epub.BookParseException("This book contains no readable text.")
        }

        val chapterEntities = slices.mapIndexed { index, slice ->
            ChapterEntity(
                id = chapterId(bookId, index),
                bookId = bookId,
                index = index,
                title = slice.title,
                blocksJson = json.encodeToString(ListSerializer, slice.blocks),
                postCount = 0,
            )
        }

        // A PDF rarely carries a cover image object, so its first rendered page becomes the cover.
        // The rendered pages are also what the profile photo can be taken from.
        val renderedPages = writePageImages(bookId, pageImages)
        val coverPath = writeCover(bookId, parsed.coverBytes) ?: renderedPages.firstOrNull()
        var global = 0
        var words = 0
        val posts = ArrayList<PostEntity>()

        slices.forEachIndexed { index, slice ->
            val chapter = chapterEntities[index]
            val generated = generator.generate(chapter.title, slice.blocks, length)
            chapter.postCount = generated.size
            generated.forEach { post ->
                words += post.plainText.split(' ', '\n').count { it.isNotBlank() }
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
            publisher = parsed.publisher,
            publishedYear = parsed.publishedYear,
            wordCount = words,
            libraryOrder = bookDao.maxLibraryOrder() + 1,
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
     * bookmarks / notes are carried over by anchoring them to the book-wide block offset.
     */
    suspend fun setPostLength(bookId: String, length: PostLength): Int {
        val book = bookDao.get(bookId) ?: return 0
        val chapters = chapterDao.listForBook(bookId).sortedBy { it.index }
        if (chapters.isEmpty()) return book.lastPostIndex
        val slices = chapters.map { ChapterSlice(it.title, decode(it.blocksJson)) }
        val prefix = prefixSums(slices.map { it.blocks.size })
        val current = postDao.getAt(bookId, book.lastPostIndex)
        val target = current?.let { offsetOf(it, prefix) } ?: 0
        return rebuild(bookId, slices, target, length)
    }

    /**
     * Manual chapter correction. The reader marks a post as the start of a new chapter
     * because the book's own chapter detection was wrong: the post's first source block
     * becomes the first block of a new chapter, everything is regenerated, and the feed
     * comes back parked on that chapter.
     *
     * Returns the global post index to view, or -1 when the post already starts a chapter.
     */
    suspend fun markAsChapter(bookId: String, globalIndex: Int): Int {
        val book = bookDao.get(bookId) ?: return -1
        val chapters = chapterDao.listForBook(bookId).sortedBy { it.index }
        if (chapters.isEmpty()) return -1
        val post = postDao.getAt(bookId, globalIndex) ?: return -1
        val ci = chapters.indexOfFirst { it.index == post.chapterIndex }
        if (ci < 0) return -1
        // A chapter always opens with its title post, so the title post and the first body
        // post both already start a chapter.
        if (post.isChapterTitlePost || post.firstBlock <= 0) return -1

        val slices = chapters.map { ChapterSlice(it.title, decode(it.blocksJson)) }.toMutableList()
        val blocks = slices[ci].blocks
        if (post.firstBlock >= blocks.size) return -1
        val head = blocks.subList(0, post.firstBlock)
        val tail = blocks.subList(post.firstBlock, blocks.size)
        if (head.isEmpty() || tail.isEmpty()) return -1

        val newTitle = chapterTitleFor(tail, "Chapter ${ci + 2}")
        slices[ci] = ChapterSlice(slices[ci].title, head)
        slices.add(ci + 1, ChapterSlice(newTitle, tail))

        val prefix = prefixSums(slices.map { it.blocks.size })
        val target = -(prefix[ci + 1] + 1) // open the new chapter, at its title post
        return rebuild(bookId, slices, target, currentLength())
    }

    /**
     * Undoes a [markAsChapter]: the chapter is folded back into the one before it and the
     * feed comes back parked on the post that used to open it.
     *
     * Returns the global post index to view, or -1 when the chapter cannot be merged.
     */
    suspend fun unmarkChapter(bookId: String, chapterIndex: Int): Int {
        val book = bookDao.get(bookId) ?: return -1
        val chapters = chapterDao.listForBook(bookId).sortedBy { it.index }
        if (chapters.size < 2) return -1
        val ci = chapters.indexOfFirst { it.index == chapterIndex }
        if (ci <= 0) return -1

        val slices = chapters.map { ChapterSlice(it.title, decode(it.blocksJson)) }.toMutableList()
        val before = slices[ci - 1]
        val current = slices[ci]
        val merged = ChapterSlice(before.title, before.blocks + current.blocks)
        val prefixBefore = prefixSums(slices.map { it.blocks.size })
        val target = prefixBefore[ci] // where the folded chapter's content now begins
        slices[ci - 1] = merged
        slices.removeAt(ci)
        return rebuild(bookId, slices, target, currentLength())
    }

    /**
     * Writes a whole new chapter structure for a book: chapters, posts, reactions, notes and
     * reading position, in one transaction.
     *
     * Everything the reader attached to a post is anchored to a book-wide block offset, so
     * it survives chapter boundaries moving (a split, a merge or a different post length).
     */
    private suspend fun rebuild(
        bookId: String,
        slices: List<ChapterSlice>,
        targetOffset: Int,
        length: PostLength,
    ): Int {
        if (slices.isEmpty()) return -1
        val oldChapters = chapterDao.listForBook(bookId).sortedBy { it.index }
        val oldPrefix = prefixSums(oldChapters.map { decode(it.blocksJson).size })
        val oldPosts = postDao.allForBook(bookId)
        val byId = oldPosts.associateBy { it.id }
        val indexById = oldChapters.associate { it.id to it.index }

        fun offsetOfPost(post: PostEntity): Int? {
            val ci = indexById[post.chapterId] ?: return null
            return offsetOf(post, oldPrefix, ci)
        }

        val reactions = reactionDao.forBook(bookId).mapNotNull { reaction ->
            val post = byId[reaction.postId] ?: return@mapNotNull null
            val offset = offsetOfPost(post) ?: return@mapNotNull null
            ReactionCarry(offset, reaction.liked, reaction.bookmarked, reaction.updatedAt)
        }
        val notes = noteDao.forBook(bookId).mapNotNull { note ->
            val post = byId[note.postId] ?: return@mapNotNull null
            val offset = offsetOfPost(post) ?: return@mapNotNull null
            NoteCarry(offset, note)
        }

        // Generate the new structure in memory first: anchors are resolved against it.
        val newPrefix = prefixSums(slices.map { it.blocks.size })
        val newChapters = ArrayList<ChapterEntity>(slices.size)
        val newPosts = ArrayList<PostEntity>()
        var global = 0
        slices.forEachIndexed { ci, slice ->
            val chapterId = chapterId(bookId, ci)
            val generated = generator.generate(slice.title, slice.blocks, length)
            newChapters += ChapterEntity(
                id = chapterId,
                bookId = bookId,
                index = ci,
                title = slice.title,
                blocksJson = json.encodeToString(ListSerializer, slice.blocks),
                postCount = generated.size,
            )
            generated.forEach { post ->
                newPosts += PostEntity(
                    id = postId(bookId, ci, post.indexInChapter),
                    bookId = bookId,
                    chapterId = chapterId,
                    chapterIndex = ci,
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

        val postsByChapter = newPosts.groupBy { it.chapterIndex }

        /**
         * The post that covers [anchor]. A body post anchors to its book-wide source block
         * offset (always >= 0); a chapter's title post anchors to `-(start + 1)`, which keeps
         * "open of chapter N" distinct from "last block of chapter N - 1".
         */
        fun locate(anchor: Int): PostEntity? {
            if (anchor < 0) {
                val start = -anchor - 1
                val ci = newPrefix.indexOfFirst { it == start }
                val chapterPosts = postsByChapter[if (ci < 0) 0 else ci] ?: return null
                return chapterPosts.firstOrNull { it.isChapterTitlePost } ?: chapterPosts.firstOrNull()
            }
            var ci = 0
            for (i in newPrefix.indices) {
                if (newPrefix[i] <= anchor) ci = i else break
            }
            val chapterPosts = postsByChapter[ci] ?: return null
            val local = anchor - newPrefix[ci]
            return chapterPosts.firstOrNull {
                !it.isChapterTitlePost && local >= it.firstBlock && local <= it.lastBlock
            } ?: chapterPosts.firstOrNull { !it.isChapterTitlePost && it.firstBlock >= local }
                ?: chapterPosts.firstOrNull { !it.isChapterTitlePost }
                ?: chapterPosts.firstOrNull()
        }

        val newReactions = reactions.mapNotNull { carry ->
            val post = locate(carry.offset) ?: return@mapNotNull null
            ReactionEntity(
                postId = post.id,
                bookId = bookId,
                liked = carry.liked,
                bookmarked = carry.bookmarked,
                updatedAt = carry.updatedAt,
            )
        }
        val newNotes = notes.mapNotNull { carry ->
            val post = locate(carry.offset) ?: return@mapNotNull null
            carry.note.copy(postId = post.id)
        }

        val position = locate(targetOffset)?.globalIndex ?: 0
        val words = newPosts.sumOf { it.plainText.split(' ', '\n').count { w -> w.isNotBlank() } }

        db.withTransaction {
            postDao.deleteForBook(bookId) // cascades reactions + notes
            reactionDao.deleteForBook(bookId)
            noteDao.deleteForBook(bookId)
            chapterDao.deleteForBook(bookId)
            chapterDao.insertAll(newChapters)
            newPosts.chunked(INSERT_CHUNK).forEach { postDao.insertAll(it) }
            newReactions.chunked(INSERT_CHUNK).forEach { reactionDao.insertAll(it) }
            newNotes.chunked(INSERT_CHUNK).forEach { noteDao.insertAll(it) }
            bookDao.updateStructure(bookId, global, newChapters.size, words, position)
        }
        return position
    }

    /** See [rebuild]'s locate: titles are negative so they can never collide with a block. */
    private fun offsetOf(post: PostEntity, prefix: IntArray, chapterIndex: Int? = null): Int {
        val ci = chapterIndex ?: post.chapterIndex
        val base = prefix.getOrElse(ci) { 0 }
        return if (post.isChapterTitlePost) -(base + 1) else base + post.firstBlock
    }

    private fun prefixSums(sizes: List<Int>): IntArray {
        val out = IntArray(sizes.size)
        var acc = 0
        sizes.forEachIndexed { index, size ->
            out[index] = acc
            acc += size
        }
        return out
    }

    private suspend fun currentLength(): PostLength = settings.currentSettings().postLength

    /**
     * A title for a chapter the reader created by hand. The first heading is used when the
     * chapter opens with one, otherwise a short label: a long sentence makes an ugly
     * heading, so it is cut at a word boundary and stripped of dangling punctuation.
     */
    private fun chapterTitleFor(blocks: List<Block>, fallback: String): String {
        val source = blocks.firstOrNull { it.type == BlockType.HEADING && it.text.isNotBlank() }?.text
            ?: blocks.firstOrNull { it.text.isNotBlank() }?.text
            ?: return fallback
        val clean = source.trim()
        val limit = 64
        val shortened = if (clean.length <= limit) {
            clean
        } else {
            val cut = clean.take(limit)
            val space = cut.lastIndexOf(' ')
            (if (space > 24) cut.substring(0, space) else cut).trimEnd()
        }
        return shortened.trimEnd(' ', ',', ';', ':', '-', '\u2014', '\u2013').ifBlank { fallback }
    }

    private suspend fun transactionally(block: suspend () -> Unit) {
        db.withTransaction { block() }
    }

    /** Writes the book's pictures under its own folder and reports entry name -> file path. */
    private fun writeIllustrations(bookId: String, illustrations: Map<String, ByteArray>): Map<String, String> {
        if (illustrations.isEmpty()) return emptyMap()
        val dir = File(illustrationDir(bookId)).apply { mkdirs() }
        val out = HashMap<String, String>(illustrations.size)
        illustrations.entries.forEachIndexed { index, (ref, bytes) ->
            if (bytes.isEmpty()) return@forEachIndexed
            val ext = ref.substringAfterLast('.', "img").lowercase().take(5)
            val file = File(dir, "$index.$ext")
            runCatching { file.writeBytes(bytes) }
                .onSuccess { out[ref] = file.absolutePath }
        }
        return out
    }

    private fun illustrationDir(bookId: String): String = File(File(context.filesDir, "illustrations"), bookId).absolutePath

    /** Swaps the parser's ZIP entry names for file paths, dropping pictures that were not written. */
    private fun resolveImages(blocks: List<Block>, paths: Map<String, String>): List<Block> = blocks.mapNotNull { block ->
        if (block.type != BlockType.IMAGE) return@mapNotNull block
        val ref = block.imageRef ?: return@mapNotNull null
        val path = paths[ref] ?: return@mapNotNull null
        block.copy(imageRef = path)
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

    private fun avatarDir(bookId: String): String = File(File(context.filesDir, "avatars"), bookId).absolutePath

    /** Writes the first pages of a PDF, numbered, so the avatar picker can offer them. */
    private fun writePageImages(bookId: String, pages: List<ByteArray>): List<String> {
        if (pages.isEmpty()) return emptyList()
        val dir = File(avatarDir(bookId)).apply { mkdirs() }
        return pages.mapIndexedNotNull { index, bytes ->
            if (bytes.isEmpty()) return@mapIndexedNotNull null
            runCatching {
                val file = File(dir, "page-${index + 1}.jpg")
                file.writeBytes(bytes)
                file.absolutePath
            }.getOrNull()
        }
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

    private fun decode(blocksJson: String): List<Block> = decodeBlocks(blocksJson)

    fun decodeBlocks(blocksJson: String): List<Block> =
        runCatching { json.decodeFromString(ListSerializer, blocksJson) }.getOrDefault(emptyList())

    companion object {
        private const val INSERT_CHUNK = 500
        private const val POSITION_DEBOUNCE_MS = 700L
        const val NOTE_LIMIT = 280
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
