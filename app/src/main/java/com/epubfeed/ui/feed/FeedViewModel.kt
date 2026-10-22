package com.epubfeed.ui.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubfeed.data.local.BookEntity
import com.epubfeed.data.prefs.SettingsRepository
import com.epubfeed.data.repo.BookRepository
import com.epubfeed.data.repo.SampleBooks
import com.epubfeed.domain.model.ChapterSummary
import com.epubfeed.domain.model.FeedPost
import com.epubfeed.domain.model.PostLength
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val windowStart: Int = 0,
    val chapters: List<ChapterSummary> = emptyList(),
    val savedPosts: List<FeedPost> = emptyList(),
    val totalPosts: Int = 0,
    val scrollTarget: Int? = null,
    val busyMessage: String? = null,
)

private data class Core(
    val book: BookEntity?,
    val posts: List<FeedPost>,
    val chapters: List<ChapterSummary>,
    val saved: List<FeedPost>,
)

private data class Part(
    val window: IntRange,
    val scrollTarget: Int?,
    val loading: Boolean,
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
    private val window = MutableStateFlow(0 until 0)
    private val scrollTarget = MutableStateFlow<Int?>(null)
    private val loading = MutableStateFlow(true)
    private val busy = MutableStateFlow<String?>(null)

    private var restoredFor: String? = null
    private var totalPostsValue: Int = 0
    private var lastVisibleIndex: Int = 0

    private val bookFlow = activeBookId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.observeBook(id)
    }

    private val postsFlow = combine(activeBookId, window) { id, range -> id to range }
        .flatMapLatest { (id, range) ->
            if (id == null || range.first >= range.last) {
                flowOf(emptyList())
            } else {
                repository.observePosts(id, range.first, range.last)
            }
        }

    private val chaptersFlow = activeBookId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeChapters(id)
    }

    private val savedFlow = activeBookId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeSaved(id)
    }

    private val coreFlow = combine(bookFlow, postsFlow, chaptersFlow, savedFlow) { book, posts, chapters, saved ->
        Core(book, posts, chapters, saved)
    }

    private val partFlow = combine(window, scrollTarget, loading, busy) { w, target, isLoading, busyMessage ->
        Part(w, target, isLoading, busyMessage)
    }

    val uiState: StateFlow<FeedUiState> =
        combine(coreFlow, partFlow, repository.observeBooks()) { core, part, books ->
            totalPostsValue = core.book?.postCount ?: 0
            FeedUiState(
                loading = part.loading,
                books = books,
                book = core.book,
                posts = core.posts,
                windowStart = part.window.first,
                chapters = core.chapters,
                savedPosts = core.saved,
                totalPosts = totalPostsValue,
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

        // Restore the saved reading position once per opened book.
        viewModelScope.launch {
            bookFlow.filterNotNull().collect { book ->
                if (restoredFor != book.id) {
                    restoredFor = book.id
                    applyWindowAround(book.lastPostIndex, book.postCount)
                }
            }
        }

        // Changing the post length regenerates the current book and keeps the place.
        viewModelScope.launch {
            var previous: PostLength? = null
            settingsRepository.settings.map { it.postLength }.distinctUntilChanged().collect { length ->
                val before = previous
                previous = length
                val id = activeBookId.value ?: return@collect
                if (before == null || before == length) return@collect
                busy.value = "Rebuilding posts"
                runCatching { repository.setPostLength(id, length) }
                    .onSuccess { position ->
                        val total = repository.getBook(id)?.postCount ?: 0
                        applyWindowAround(position, total)
                    }
                busy.value = null
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

    private fun applyWindowAround(position: Int, total: Int) {
        val maxIndex = (total - 1).coerceAtLeast(0)
        val clamped = position.coerceIn(0, maxIndex)
        window.value = (clamped - 40).coerceAtLeast(0) until (clamped + 260)
        scrollTarget.value = clamped
    }

    /** Maps the visible list window onto absolute post indices and grows the window at the edges. */
    fun onVisibleRange(firstListIndex: Int, lastListIndex: Int) {
        val range = window.value
        val total = totalPostsValue
        if (total <= 0 || range.first >= range.last) return
        val size = range.last - range.first
        val first = (range.first + firstListIndex).coerceIn(0, total - 1)
        lastVisibleIndex = first
        activeBookId.value?.let { id -> repository.saveReadingPositionAsync(id, first) }

        // Prefetch well before the edge so the next query lands while the fling is
        // still mid screen rather than exactly when the list runs out of items.
        when {
            firstListIndex < PREFETCH_DISTANCE && range.first > 0 ->
                window.value = (range.first - WINDOW_CHUNK).coerceAtLeast(0) until range.last
            lastListIndex > size - PREFETCH_DISTANCE && range.last < total ->
                window.value = range.first until (range.last + WINDOW_CHUNK).coerceAtMost(total)
        }
    }

    fun consumeScrollTarget() {
        scrollTarget.value = null
    }

    fun jumpTo(globalIndex: Int) {
        applyWindowAround(globalIndex, totalPostsValue)
    }

    fun toggleLike(post: FeedPost) {
        viewModelScope.launch { repository.toggleLike(post.id) }
    }

    fun toggleBookmark(post: FeedPost) {
        viewModelScope.launch { repository.toggleBookmark(post.id) }
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
            }
        }
    }

    fun switchToBook(book: BookEntity) {
        if (activeBookId.value == book.id) return
        restoredFor = null
        activeBookId.value = book.id
        viewModelScope.launch { settingsRepository.setLastBookId(book.id) }
    }

    fun savePositionNow() {
        val id = activeBookId.value ?: return
        repository.saveReadingPositionNow(id, lastVisibleIndex)
    }

    private companion object {
        const val WINDOW_CHUNK = 300
        const val PREFETCH_DISTANCE = 80
    }
}
