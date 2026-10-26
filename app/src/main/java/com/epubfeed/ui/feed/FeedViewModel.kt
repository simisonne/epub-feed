package com.epubfeed.ui.feed

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubfeed.data.local.BookEntity
import com.epubfeed.data.local.NoteEntity
import com.epubfeed.data.prefs.SettingsRepository
import com.epubfeed.data.repo.BookRepository
import com.epubfeed.data.repo.SampleBooks
import com.epubfeed.domain.model.ChapterSummary
import com.epubfeed.domain.model.FeedPost
import com.epubfeed.domain.model.Note
import com.epubfeed.domain.model.PostLength
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedUiState(
    val loading: Boolean = true,
    val books: List<BookEntity> = emptyList(),
    val book: BookEntity? = null,
    val posts: List<FeedPost> = emptyList(),
    val notes: Map<String, List<Note>> = emptyMap(),
    val chapters: List<ChapterSummary> = emptyList(),
    val chapterIndex: Int = 0,
    val chapterTitle: String = "",
    val hasNextChapter: Boolean = false,
    /**
     * True when [posts] are the posts of the chapter the reader is on. A book or chapter switch
     * changes the index and the posts on separate flows, so for a moment the feed still carries
     * the previous chapter's list. Nothing may scroll or save against that list.
     */
    val postsMatchChapter: Boolean = true,
    val likedPosts: List<FeedPost> = emptyList(),
    val savedPosts: List<FeedPost> = emptyList(),
    /** Global index of the post the reader is on right now. */
    val currentIndex: Int = 0,
    val scrollTarget: Int? = null,
    val busyMessage: String? = null,
)

/** The chapter posts that are loaded right now, tagged with the book and chapter they came from. */
private data class Slice(val bookId: String?, val chapterIndex: Int, val posts: List<FeedPost>)

private data class Core(
    val book: BookEntity?,
    val slice: Slice,
    val chapters: List<ChapterSummary>,
    val liked: List<FeedPost>,
    val saved: List<FeedPost>,
    val notes: Map<String, List<Note>>,
)

private data class Part(
    val chapterIndex: Int,
    val scrollTarget: Int?,
    val loading: Boolean,
    val currentIndex: Int,
    val busy: String?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookRepository,
    private val settingsRepository: SettingsRepository,
    private val sampleBooks: SampleBooks,
) : ViewModel() {

    private val routeBookId: String? = savedStateHandle.get<String>("bookId")

    private val activeBookId = MutableStateFlow<String?>(routeBookId)
    private val chapterIndex = MutableStateFlow(0)
    private val scrollTarget = MutableStateFlow<Int?>(null)
    private val loading = MutableStateFlow(true)
    private val busy = MutableStateFlow<String?>(null)
    private val currentIndex = MutableStateFlow(0)

    private var restoredFor: String? = null
    private var lastVisibleIndex = 0

    private val bookFlow = activeBookId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.observeBook(id)
    }

    /**
     * The posts of the chapter the reader is on. The slice keeps its own book and chapter, because
     * the book, the chapter index and the posts arrive on separate flows: right after a switch the
     * index is already new while the posts are still the old list.
     */
    private val sliceFlow = combine(activeBookId, chapterIndex) { id, index -> id to index }
        .flatMapLatest { (id, index) ->
            if (id == null) {
                flowOf(Slice(null, index, emptyList()))
            } else {
                repository.observeChapterPosts(id, index).map { posts -> Slice(id, index, posts) }
            }
        }

    private val chaptersFlow = activeBookId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeChapters(id)
    }

    private val likedFlow = activeBookId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeLiked(id)
    }

    private val savedFlow = activeBookId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeSaved(id)
    }

    private val notesFlow = activeBookId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeNotes(id)
    }

    private val coreFlow = combine(
        combine(bookFlow, sliceFlow, chaptersFlow) { book, slice, chapters -> Triple(book, slice, chapters) },
        combine(likedFlow, savedFlow, notesFlow) { liked, saved, notes ->
            Triple(
                liked,
                saved,
                notes.groupBy { it.postId }
                    .mapValues { (_, list) -> list.map { Note(it.id, it.postId, it.text, it.createdAt) } },
            )
        },
    ) { head, tail ->
        Core(
            book = head.first,
            slice = head.second,
            chapters = head.third,
            liked = tail.first,
            saved = tail.second,
            notes = tail.third,
        )
    }

    private val partFlow = combine(chapterIndex, scrollTarget, loading, currentIndex, busy) { index, target, isLoading, current, busyMessage ->
        Part(index, target, isLoading, current, busyMessage)
    }

    val uiState: StateFlow<FeedUiState> =
        combine(coreFlow, partFlow, repository.observeBooks()) { core, part, books ->
            val chapter = core.chapters.getOrNull(part.chapterIndex)
            val slice = core.slice
            FeedUiState(
                loading = part.loading,
                books = books,
                book = core.book,
                posts = slice.posts,
                notes = core.notes,
                chapters = core.chapters,
                chapterIndex = part.chapterIndex,
                chapterTitle = chapter?.title.orEmpty(),
                hasNextChapter = part.chapterIndex < core.chapters.size - 1,
                postsMatchChapter = slice.bookId == core.book?.id && slice.chapterIndex == part.chapterIndex,
                likedPosts = core.liked,
                savedPosts = core.saved,
                currentIndex = part.currentIndex,
                scrollTarget = part.scrollTarget,
                busyMessage = part.busy,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

    init {
        viewModelScope.launch {
            sampleBooks.ensureSample()
            val resolved = resolveInitialBook()
            activeBookId.value = resolved
            resolved?.let { settingsRepository.setLastBookId(it) }
            loading.value = false
        }

        // Open the chapter the reader left off in, once per book.
        viewModelScope.launch {
            bookFlow.filterNotNull().collect { book ->
                if (restoredFor != book.id) {
                    restoredFor = book.id
                    val chapters = repository.observeChapters(book.id).first()
                    val index = chapters.indexOfLast { it.firstIndex <= book.lastPostIndex }
                        .coerceAtLeast(0)
                    currentIndex.value = book.lastPostIndex
                    lastVisibleIndex = book.lastPostIndex
                    chapterIndex.value = index
                    scrollTarget.value = book.lastPostIndex
                }
            }
        }

        // Changing the post length regenerates the book and keeps the place.
        viewModelScope.launch {
            var previous: PostLength? = null
            settingsRepository.settings.map { it.postLength }.distinctUntilChanged().collect { length ->
                val before = previous
                previous = length
                val id = activeBookId.value ?: return@collect
                if (before == null || before == length) return@collect
                busy.value = "Rebuilding posts"
                runCatching { repository.setPostLength(id, length) }
                    .onSuccess { position -> jumpToPosition(id, position) }
                busy.value = null
            }
        }

        // Reading clock: 5s ticks, credited only while the reader is active.
        viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                accrueReadingTime()
                flushReadingTime()
            }
        }
    }

    private suspend fun resolveInitialBook(): String? {
        val route = routeBookId
        if (route != null && repository.getBook(route) != null) return route
        settingsRepository.currentLastBookId()?.let { stored ->
            if (repository.getBook(stored) != null) return stored
        }
        return repository.observeBooks().first().firstOrNull()?.id
    }

    /** Points the feed at the chapter that contains [position] and asks for a scroll there. */
    private suspend fun jumpToPosition(bookId: String, position: Int) {
        val chapters = repository.observeChapters(bookId).first()
        if (chapters.isEmpty()) {
            scrollTarget.value = position
            return
        }
        val index = chapters.indexOfLast { it.firstIndex <= position }.coerceAtLeast(0)
        currentIndex.value = position
        lastVisibleIndex = position
        chapterIndex.value = index
        scrollTarget.value = position
    }

    /** The list index of the top visible post inside the current chapter. */
    fun onVisibleListIndex(listIndex: Int) {
        if (scrollTarget.value != null) return
        val ui = uiState.value
        // A list that is still the previous chapter describes posts of another book: index 0 of
        // it wrote the reading position away.
        if (!ui.postsMatchChapter) return
        val post = ui.posts.getOrNull(listIndex) ?: return
        lastVisibleIndex = post.globalIndex
        currentIndex.value = post.globalIndex
        activeBookId.value?.let { id -> repository.saveReadingPositionAsync(id, post.globalIndex) }
    }

    /** Jumps to any post in the book, opening the chapter that contains it. */
    fun openPost(globalIndex: Int) {
        viewModelScope.launch { jumpToPosition(activeBookId.value ?: return@launch, globalIndex) }
    }

    /**
     * Puts the reader back where they were when the feed is built from scratch again.
     *
     * The position is restored from the book when a book is opened, but leaving the feed for the
     * profile and coming back rebuilds the list without changing the book, so the old restore never
     * runs and the feed would sit at the top of the chapter.
     */
    fun restorePositionIfNeeded() {
        if (scrollTarget.value != null) return
        val index = currentIndex.value
        if (index > 0) scrollTarget.value = index
    }

    fun consumeScrollTarget() {
        scrollTarget.value = null
    }

    /** Jumps to a chapter, optionally at a specific global post index. */
    fun openChapter(index: Int, position: Int? = null) {
        val chapters = uiState.value.chapters
        val target = position ?: chapters.getOrNull(index)?.firstIndex ?: return
        viewModelScope.launch {
            chapterIndex.value = index.coerceAtLeast(0)
            currentIndex.value = target
            lastVisibleIndex = target
            scrollTarget.value = target
        }
    }

    fun nextChapter() {
        val ui = uiState.value
        if (!ui.hasNextChapter) return
        openChapter(ui.chapterIndex + 1)
    }

    fun addNote(postId: String, text: String) {
        viewModelScope.launch { repository.addNote(postId, text) }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch { repository.deleteNote(noteId) }
    }

    fun toggleLike(post: FeedPost) {
        viewModelScope.launch { repository.toggleLike(post.id) }
    }

    fun toggleBookmark(post: FeedPost) {
        viewModelScope.launch { repository.toggleBookmark(post.id) }
    }

    /**
     * Marks a post as the start of a new chapter. The whole book is re-sliced, so the feed
     * reloads with that post at the top of the new chapter.
     */
    fun markAsChapter(post: FeedPost) {
        val id = activeBookId.value ?: return
        viewModelScope.launch {
            busy.value = "Splitting chapter"
            val position = runCatching { repository.markAsChapter(id, post.globalIndex) }.getOrDefault(-1)
            if (position >= 0) jumpToPosition(id, position)
            busy.value = null
        }
    }

    /** Folds the chapter back into the previous one, parking the feed where it used to start. */
    fun unmarkChapter(chapter: ChapterSummary) {
        val id = activeBookId.value ?: return
        viewModelScope.launch {
            busy.value = "Merging chapter"
            val position = runCatching { repository.unmarkChapter(id, chapter.index) }.getOrDefault(-1)
            if (position >= 0) jumpToPosition(id, position)
            busy.value = null
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            val wasActive = activeBookId.value == book.id
            repository.deleteBook(book.id)
            if (wasActive) {
                val next = repository.observeBooks().first().firstOrNull()
                activeBookId.value = next?.id
                settingsRepository.setLastBookId(next?.id)
                restoredFor = null
                currentIndex.value = 0
                chapterIndex.value = 0
            }
        }
    }

    fun switchToBook(book: BookEntity) {
        if (activeBookId.value == book.id) return
        // The new book's own lastPostIndex becomes the target when its row arrives; asking for
        // post 0 here only scrolled the list that is about to be replaced.
        restoredFor = null
        currentIndex.value = 0
        chapterIndex.value = 0
        scrollTarget.value = null
        activeBookId.value = book.id
        viewModelScope.launch { settingsRepository.setLastBookId(book.id) }
    }

    /** The library order the reader dragged into place, written as one position per book. */
    fun moveBooks(bookIds: List<String>) {
        viewModelScope.launch { repository.reorderBooks(bookIds) }
    }

    fun savePositionNow() {
        val id = activeBookId.value ?: return
        repository.saveReadingPositionNow(id, lastVisibleIndex)
        flushReadingTime(force = true)
    }

    // ---------------------------------------------------------------- reading clock

    private var screenActive = false
    private var lastInteraction = 0L
    private var creditedUpTo = 0L
    private var pendingReadMillis = 0L
    private var lastFlushAt = 0L

    fun onScreenResumed() {
        val now = SystemClock.elapsedRealtime()
        screenActive = true
        lastInteraction = now
        creditedUpTo = now
        lastFlushAt = now
    }

    fun onScreenPaused() {
        accrueReadingTime()
        screenActive = false
        flushReadingTime(force = true)
    }

    /** Any scroll or tap counts as the reader being present. */
    fun onReadingInteraction() {
        if (!screenActive) return
        accrueReadingTime()
        lastInteraction = SystemClock.elapsedRealtime()
        if (creditedUpTo < lastInteraction) creditedUpTo = lastInteraction
    }

    private fun accrueReadingTime() {
        if (!screenActive) return
        val now = SystemClock.elapsedRealtime()
        // Time is credited until the idle deadline: an AFK reader stops the clock after
        // [IDLE_TIMEOUT_MS] of no interaction, and the gap is skipped when they come back.
        val end = minOf(now, lastInteraction + IDLE_TIMEOUT_MS)
        if (end > creditedUpTo) {
            pendingReadMillis += end - creditedUpTo
            creditedUpTo = end
        }
    }

    private fun flushReadingTime(force: Boolean = false) {
        val id = activeBookId.value ?: return
        val seconds = pendingReadMillis / 1000
        if (seconds <= 0) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastFlushAt < FLUSH_MS) return
        pendingReadMillis -= seconds * 1000
        lastFlushAt = now
        viewModelScope.launch { repository.addReadingSeconds(id, seconds) }
    }

    private companion object {
        const val NOTE_LIMIT = 280
        const val IDLE_TIMEOUT_MS = 120_000L
        const val TICK_MS = 5_000L
        const val FLUSH_MS = 15_000L
    }
}
