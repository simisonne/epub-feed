package com.epubfeed.ui.lookup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubfeed.data.local.BookEntity
import com.epubfeed.data.repo.WordRepository
import com.epubfeed.data.thesaurus.ThesaurusRepository
import com.epubfeed.domain.model.FeedPost
import com.epubfeed.domain.thesaurus.LookupOutcome
import com.epubfeed.domain.thesaurus.WordUses
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One lookup, from the selected word to a saved entry. */
@HiltViewModel
class LookupViewModel @Inject constructor(
    private val thesaurus: ThesaurusRepository,
    private val words: WordRepository,
) : ViewModel() {

    data class UiState(
        val selection: String = "",
        val excerpt: String? = null,
        val loading: Boolean = false,
        val outcome: LookupOutcome? = null,
        val uses: WordUses? = null,
        val saved: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Called on every selection change: a collapsed selection clears the panel. */
    fun select(term: String, excerpt: String?) {
        if (term == _state.value.selection) return
        _state.value = UiState(selection = term, excerpt = excerpt)
    }

    fun lookUp(post: FeedPost, book: BookEntity?) {
        val term = _state.value.selection
        if (term.isBlank()) return
        _state.value = _state.value.copy(loading = true, outcome = null)
        viewModelScope.launch {
            val outcome = thesaurus.lookup(term)
            // Count with the form the dictionary answered, so a Korean noun counts every use even
            // when the selection carried a particle.
            val counted = (outcome as? LookupOutcome.Found)?.entry?.term ?: term
            val uses = book?.let { words.usesInBook(it.id, counted) }
            _state.value = _state.value.copy(loading = false, outcome = outcome, uses = uses)
        }
    }

    /** Saves the dictionary answer together with where it came from. */
    fun save(post: FeedPost?, book: BookEntity?) {
        val found = _state.value.outcome as? LookupOutcome.Found ?: return
        viewModelScope.launch {
            words.save(
                term = found.entry.term,
                entry = found.entry,
                book = book,
                postId = post?.id,
                chapterIndex = post?.chapterIndex,
                excerpt = _state.value.excerpt,
            )
            _state.value = _state.value.copy(saved = true)
        }
    }

    /**
     * For the words no dictionary holds: the word still goes in, and it goes in as a word of the
     * book it was found in, with the post and the sentence, not as a custom word with no home.
     */
    fun saveWithoutEntry(post: FeedPost?, book: BookEntity?) {
        val term = _state.value.selection
        if (term.isBlank()) return
        viewModelScope.launch {
            words.saveWithoutEntry(
                term = term,
                book = book,
                postId = post?.id,
                chapterIndex = post?.chapterIndex,
                excerpt = _state.value.excerpt,
            )
            _state.value = _state.value.copy(saved = true)
        }
    }
}
