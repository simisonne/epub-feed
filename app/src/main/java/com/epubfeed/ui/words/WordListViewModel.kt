package com.epubfeed.ui.words

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubfeed.data.local.BookEntity
import com.epubfeed.data.prefs.SettingsRepository
import com.epubfeed.data.repo.WordRepository
import com.epubfeed.data.thesaurus.ThesaurusRepository
import com.epubfeed.domain.model.Word
import com.epubfeed.domain.model.WordSort
import com.epubfeed.domain.thesaurus.LookupOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The word list: sorting, the book filter, adding by hand, removing with undo, export and import. */
@HiltViewModel
class WordListViewModel @Inject constructor(
    private val words: WordRepository,
    private val settings: SettingsRepository,
    private val thesaurus: ThesaurusRepository,
) : ViewModel() {

    data class UiState(
        val words: List<Word> = emptyList(),
        /** Only books that actually have words, so the filter never offers a dead choice. */
        val books: List<BookEntity> = emptyList(),
        val sort: WordSort = WordSort.ALPHABETICAL,
        val bookFilter: String? = null,
        /** False until the first list arrives, so the empty state cannot flash on a cold start. */
        val loaded: Boolean = false,
        val message: String? = null,
        val busy: Boolean = false,
        val undoAvailable: Boolean = false,
    )

    private data class Chrome(val message: String? = null, val busy: Boolean = false, val undo: Boolean = false)

    private val bookFilter = MutableStateFlow<String?>(null)
    private val chrome = MutableStateFlow(Chrome())
    private var lastRemoved: Word? = null

    val state: StateFlow<UiState> =
        combine(
            words.observeWords(),
            words.observeBooks(),
            settings.settings,
            bookFilter,
            chrome,
        ) { all, books, userSettings, filter, extra ->
            val bookIds = all.mapNotNull { it.bookId }.toSet()
            val active = filter?.takeIf { it in bookIds }
            UiState(
                words = if (active == null) all else all.filter { it.bookId == active },
                books = books.filter { it.id in bookIds },
                sort = userSettings.wordSort,
                bookFilter = active,
                loaded = true,
                message = extra.message,
                busy = extra.busy,
                undoAvailable = extra.undo,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setSort(order: WordSort) {
        viewModelScope.launch { settings.setWordSort(order) }
    }

    fun setBookFilter(bookId: String?) {
        bookFilter.value = bookId
    }

    fun consumeMessage() {
        chrome.value = chrome.value.copy(message = null)
    }

    fun addCustom(term: String, definition: String) {
        viewModelScope.launch {
            val added = words.addCustom(term, definition.takeIf { it.isNotBlank() })
            chrome.value = chrome.value.copy(
                message = if (added == null) "Type a word first" else "\u201C${added.term}\u201D saved",
                undo = false,
            )
        }
    }

    fun remove(word: Word) {
        viewModelScope.launch {
            words.remove(word.id)
            lastRemoved = word
            chrome.value = chrome.value.copy(message = "\u201C${word.term}\u201D removed", undo = true)
        }
    }

    fun undoRemove() {
        val word = lastRemoved ?: return
        lastRemoved = null
        viewModelScope.launch {
            words.restore(word)
            chrome.value = chrome.value.copy(message = null, undo = false)
        }
    }

    /** Asks the dictionary again, for a word whose definition came from an older pack. */
    fun refresh(word: Word) {
        viewModelScope.launch {
            chrome.value = chrome.value.copy(busy = true)
            val outcome = thesaurus.lookup(word.term)
            when (outcome) {
                is LookupOutcome.Found -> {
                    words.refresh(word, outcome.entry)
                    chrome.value = chrome.value.copy(message = "Definition updated", busy = false)
                }

                is LookupOutcome.Missing -> chrome.value = chrome.value.copy(
                    message = "No entry for \u201C${word.term}\u201D",
                    busy = false,
                )
            }
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            chrome.value = chrome.value.copy(busy = true)
            val result = runCatching { words.export(uri) }
            chrome.value = Chrome(
                message = result.fold(
                    onSuccess = { count -> "$count words exported" },
                    onFailure = { "Export failed" },
                ),
            )
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            chrome.value = chrome.value.copy(busy = true)
            val result = runCatching { words.import(uri) }
            chrome.value = Chrome(
                message = result.fold(
                    onSuccess = { result ->
                    when {
                        result.added == 0 && result.skipped == 0 -> "That file held no words"
                        result.added == 0 -> "All ${result.skipped} words were already on the list"
                        result.skipped == 0 -> "${result.added} words imported"
                        else -> "${result.added} words imported, ${result.skipped} already on the list"
                    }
                },
                    onFailure = { "That file could not be read" },
                ),
            )
        }
    }
}
