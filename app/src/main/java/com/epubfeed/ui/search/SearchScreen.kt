package com.epubfeed.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.epubfeed.domain.model.ChapterSummary
import com.epubfeed.domain.search.SearchHit
import com.epubfeed.domain.thesaurus.Span
import com.epubfeed.ui.theme.LocalXColors
import kotlin.math.roundToInt

/**
 * Search the whole book. Every hit is a post, says which chapter it sits in, and shows the passage
 * that matched with the words marked, so the reader can tell one hit from another before opening it.
 */
@Composable
fun BookSearchScreen(
    bookId: String,
    chapters: List<ChapterSummary>,
    onOpenHit: (SearchHit) -> Unit,
    onDismiss: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val x = LocalXColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }

    LaunchedEffect(bookId) {
        viewModel.open(bookId)
        runCatching { focus.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(x.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close the search",
                        tint = x.text,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .background(x.elevated, RoundedCornerShape(999.dp))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (state.query.isEmpty()) {
                            Text("Search this book", color = x.muted, fontSize = 16.sp)
                        }
                        BasicTextField(
                            value = state.query,
                            onValueChange = viewModel::setQuery,
                            singleLine = true,
                            textStyle = TextStyle(color = x.text, fontSize = 16.sp),
                            cursorBrush = SolidColor(x.accent),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    if (state.query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { viewModel.clear() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear the search",
                                tint = x.muted,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
            }

            HorizontalDivider(color = x.divider, thickness = 1.dp)

            SearchStatus(state = state)

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.result?.hits.orEmpty(), key = { it.postId }) { hit ->
                    SearchHitRow(
                        hit = hit,
                        chapters = chapters,
                        onClick = { onOpenHit(hit) },
                    )
                    HorizontalDivider(color = x.divider, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun SearchStatus(state: SearchViewModel.UiState) {
    val x = LocalXColors.current
    val result = state.result
    val message = when {
        state.searching -> "Searching\u2026"
        result == null -> null
        result.hits.isEmpty() -> "No post holds those words."
        !result.everyTerm -> "No post holds every word. These hold at least one of them."
        result.capped -> "First ${result.hits.size} posts. Narrow the search to see the rest."
        result.hits.size == 1 -> "1 post"
        else -> "${result.hits.size} posts"
    }
    if (message == null) {
        Spacer(Modifier.height(8.dp))
        return
    }
    Text(
        text = message,
        color = x.muted,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
    )
}

@Composable
private fun SearchHitRow(hit: SearchHit, chapters: List<ChapterSummary>, onClick: () -> Unit) {
    val x = LocalXColors.current
    val excerpt = remember(hit) {
        marked(hit.excerpt, hit.marks, x.accent.copy(alpha = 0.35f))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = where(hit, chapters),
            color = x.muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = excerpt,
            color = x.text,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
    }
}

/** "Chapter 4, 62%", or the chapter's own title when it has one. */
private fun where(hit: SearchHit, chapters: List<ChapterSummary>): String {
    val chapter = chapters.firstOrNull { it.index == hit.chapterIndex }
    val name = chapter?.title?.takeIf { it.isNotBlank() } ?: "Chapter ${hit.chapterIndex + 1}"
    val percent = chapter?.postCount
        ?.takeIf { it > 0 }
        ?.let { ((hit.indexInChapter + 1) * 100.0 / it).roundToInt() }
    val place = if (percent != null) "$name, $percent%" else name
    return if (hit.occurrences > 1) "$place \u00b7 ${hit.occurrences} matches" else place
}

/** The excerpt with every matched word marked, so the eye lands on the query inside the passage. */
private fun marked(text: String, marks: List<Span>, highlight: Color): AnnotatedString =
    buildAnnotatedString {
        var cursor = 0
        for (mark in marks.sortedBy { it.start }) {
            val start = mark.start.coerceIn(cursor, text.length)
            val end = mark.end.coerceIn(start, text.length)
            if (start > cursor) append(text.substring(cursor, start))
            if (end > start) {
                withStyle(SpanStyle(background = highlight, fontWeight = FontWeight.SemiBold)) {
                    append(text.substring(start, end))
                }
            }
            cursor = end
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
