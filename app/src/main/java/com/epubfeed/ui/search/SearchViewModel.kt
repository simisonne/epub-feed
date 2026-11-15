package com.epubfeed.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubfeed.data.repo.SearchRepository
import com.epubfeed.domain.search.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One search inside the open book: the query, the hits, and whether a pass is running. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val result: SearchResult? = null,
        val searching: Boolean = false,
    )

    private val query = MutableStateFlow("")
    private val result = MutableStateFlow<SearchResult?>(null)
    private val searching = MutableStateFlow(false)
    private var bookId: String? = null

    val state: StateFlow<UiState> = combine(query, result, searching) { q, res, running ->
        UiState(query = q, result = res, searching = running)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        // Typing is debounced: a search rewrites every post's text, and doing that per keystroke
        // on a long book is wasted work.
        viewModelScope.launch {
            query
                .map { it.trim() }
                .distinctUntilChanged()
                .debounce(DEBOUNCE_MS)
                .collect { text ->
                    val book = bookId
                    if (book == null || text.isEmpty()) {
                        result.value = null
                        searching.value = false
                        return@collect
                    }
                    if (text.length < 1) return@collect
                    searching.value = true
                    result.value = repository.search(book, text)
                    searching.value = false
                }
        }
    }

    /**
     * A fresh search for the book being read. The field starts empty on purpose: the view model
     * outlives the dialog, and coming back to a typed query with no results under it would look
     * like a search that never ran.
     */
    fun open(bookId: String) {
        this.bookId = bookId
        query.value = ""
        result.value = null
        searching.value = false
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun clear() {
        query.value = ""
        result.value = null
        searching.value = false
    }

    private companion object {
        const val DEBOUNCE_MS = 220L
    }
}
