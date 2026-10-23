package com.epubfeed.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubfeed.data.local.BookEntity
import com.epubfeed.data.repo.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val loading: Boolean = true,
    val book: BookEntity? = null,
    val chapterCount: Int = 0,
    val postCount: Int = 0,
    val wordCount: Int = 0,
    val secondsRead: Long = 0L,
    val likeCount: Int = 0,
    val bookmarkCount: Int = 0,
    val noteCount: Int = 0,
    /** 0..1, how far into the book the reader is. */
    val progress: Float = 0f,
    val currentChapter: String = "",
) {
    /** Very rough print page count: 275 words per page. */
    val estimatedPages: Int get() = if (wordCount <= 0) 0 else (wordCount / 275).coerceAtLeast(1)

    /** How long the book takes at a relaxed 240 words per minute. */
    val estimatedMinutes: Int get() = if (wordCount <= 0) 0 else (wordCount / 240).coerceAtLeast(1)

    val minutesLeft: Int
        get() {
            val total = estimatedMinutes
            val remaining = (total * (1f - progress)).toInt()
            return remaining.coerceAtLeast(if (total > 0) 1 else 0)
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: BookRepository,
) : ViewModel() {

    private val bookId: String? = savedStateHandle.get<String>("bookId")

    init {
        // Older imports predate the stored word count; fill it once so the profile has it.
        bookId?.let { id -> viewModelScope.launch { runCatching { repository.ensureWordCount(id) } } }
    }

    private val bookFlow = bookId?.let { repository.observeBook(it) } ?: flowOf(null)
    private val chaptersFlow = bookId?.let { repository.observeChapters(it) } ?: flowOf(emptyList())
    private val statsFlow = bookId?.let { repository.observeReadingSeconds(it) } ?: flowOf(0L)
    private val likedFlow = bookId?.let { repository.observeLiked(it) } ?: flowOf(emptyList())
    private val savedFlow = bookId?.let { repository.observeSaved(it) } ?: flowOf(emptyList())
    private val notesFlow = bookId?.let { repository.observeNotes(it) } ?: flowOf(emptyList())

    val uiState: StateFlow<ProfileUiState> = combine(
        combine(bookFlow, chaptersFlow, statsFlow) { book, chapters, seconds -> Triple(book, chapters, seconds) },
        combine(likedFlow, savedFlow, notesFlow) { liked, saved, notes -> Triple(liked, saved, notes) },
    ) { head, tail ->
        val book = head.first
        val chapters = head.second
        val bookmarkCount = tail.second.size
        ProfileUiState(
            loading = false,
            book = book,
            chapterCount = chapters.size,
            postCount = book?.postCount ?: 0,
            wordCount = book?.wordCount ?: 0,
            secondsRead = head.third,
            likeCount = tail.first.size,
            bookmarkCount = bookmarkCount,
            noteCount = tail.third.size,
            progress = if (book == null || book.postCount <= 0) 0f else {
                ((book.lastPostIndex + 1).toFloat() / book.postCount).coerceIn(0f, 1f)
            },
            currentChapter = chapters
                .lastOrNull { it.firstIndex <= (book?.lastPostIndex ?: 0) }
                ?.title
                .orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())
}
