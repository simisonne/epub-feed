package com.epubfeed.ui.words

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.epubfeed.data.local.BookEntity
import com.epubfeed.domain.model.Word
import com.epubfeed.domain.model.WordOrder
import com.epubfeed.domain.model.WordSort
import com.epubfeed.ui.components.BookAvatar
import com.epubfeed.ui.components.EpubFeedAvatar
import com.epubfeed.ui.components.SegmentedOptions
import com.epubfeed.ui.theme.LocalXColors
import java.util.Calendar

/**
 * Every saved word, newest first unless the reader says otherwise. The list is a place, not a
 * panel: it sorts, filters, takes words typed by hand, and exports.
 */
@Composable
fun WordListScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    viewModel: WordListViewModel = hiltViewModel(),
) {
    val x = LocalXColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Word?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? -> uri?.let(viewModel::export) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let(viewModel::import) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (state.undoAvailable) "Undo" else null,
            withDismissAction = false,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove() else viewModel.consumeMessage()
    }

    val booksById = remember(state.books) { state.books.associateBy { it.id } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = x.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = x.accent,
                    contentColor = Color.White,
                    actionColor = Color.White,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(x.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = x.text,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Word list",
                    color = x.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier.size(44.dp).clickable { addOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add a word",
                        tint = x.text,
                        modifier = Modifier.size(23.dp),
                    )
                }
                Box {
                    Box(
                        modifier = Modifier.size(44.dp).clickable { menuOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More",
                            tint = x.text,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = x.elevated,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export word list", color = x.text) },
                            onClick = {
                                menuOpen = false
                                exportLauncher.launch("epub-feed-words.json")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Import word list", color = x.text) },
                            onClick = {
                                menuOpen = false
                                importLauncher.launch(arrayOf("application/json", "text/plain"))
                            },
                        )
                    }
                }
            }

            Text(
                text = summary(state.words),
                color = x.muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 12.dp),
            )

            SegmentedOptions(
                options = WordSort.entries.toList(),
                selected = state.sort,
                label = { it.label },
                onSelect = viewModel::setSort,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            if (state.books.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterPill(
                        label = "All books",
                        selected = state.bookFilter == null,
                        onClick = { viewModel.setBookFilter(null) },
                    )
                    state.books.forEach { book ->
                        FilterPill(
                            label = book.title,
                            selected = state.bookFilter == book.id,
                            onClick = { viewModel.setBookFilter(book.id) },
                        )
                    }
                }
            }

            HorizontalDivider(color = x.divider, thickness = 1.dp)

            when {
                !state.loaded -> Spacer(Modifier.fillMaxWidth().height(1.dp))
                state.words.isEmpty() -> EmptyWords(
                    filtered = state.bookFilter != null,
                    onAdd = { addOpen = true },
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    var lastBook: String? = null
                    state.words.forEach { word ->
                        val group = word.bookTitle
                        if (state.sort == WordSort.BY_BOOK && group != lastBook) {
                            lastBook = group
                            item(key = "header-${group ?: "custom"}") {
                                BookGroupHeader(
                                    title = group ?: WordOrder.CUSTOM_GROUP,
                                    book = word.bookId?.let { booksById[it] },
                                    hue = word.bookHue ?: 210,
                                )
                            }
                        }
                        item(key = word.id) {
                            WordRow(
                                word = word,
                                book = word.bookId?.let { booksById[it] },
                                onClick = { detail = word },
                            )
                        }
                    }
                }
            }
        }
    }

    if (addOpen) {
        AddWordSheet(
            onDismiss = { addOpen = false },
            onSave = { term, definition ->
                viewModel.addCustom(term, definition)
                addOpen = false
            },
        )
    }

    detail?.let { word ->
        WordDetailSheet(
            word = word,
            book = word.bookId?.let { booksById[it] },
            onOpenBook = {
                detail = null
                word.bookId?.let(onOpenBook)
            },
            onRefresh = {
                detail = null
                viewModel.refresh(word)
            },
            onRemove = {
                detail = null
                viewModel.remove(word)
            },
            onDismiss = { detail = null },
        )
    }
}

@Composable
private fun WordRow(word: Word, book: BookEntity?, onClick: () -> Unit) {
    val x = LocalXColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = word.term,
                    color = x.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                word.rarity?.let {
                    Text(
                        text = "  ${it.label}",
                        color = x.muted,
                        fontSize = 12.sp,
                    )
                }
            }
            if (word.definition.isNotBlank()) {
                Text(
                    text = word.definition,
                    color = x.muted,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // A word with no book is the app's own, so it is signed with the app's logo rather than a
        // letter circle that looks like a book called "C".
        if (word.bookId == null) {
            EpubFeedAvatar(size = 22.dp)
        } else {
            BookAvatar(
                avatarPath = book?.avatarSource,
                title = word.bookTitle ?: WordOrder.CUSTOM_GROUP,
                hue = word.bookHue ?: 210,
                size = 22.dp,
            )
        }
    }
}

@Composable
private fun BookGroupHeader(title: String, book: BookEntity?, hue: Int) {
    val x = LocalXColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (book == null) {
            EpubFeedAvatar(size = 24.dp)
        } else {
            BookAvatar(
                avatarPath = book.avatarSource,
                title = title,
                hue = hue,
                size = 24.dp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            color = x.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val x = LocalXColors.current
    Box(
        modifier = Modifier
            .height(34.dp)
            .background(
                if (selected) x.accent else x.elevated,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else x.muted,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyWords(filtered: Boolean, onAdd: () -> Unit) {
    val x = LocalXColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (filtered) "No words from this book yet" else "No words yet",
            color = x.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Press and hold a post, choose Look up a word, then save what you find.",
            color = x.muted,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Add a word by hand",
            color = x.accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(onClick = onAdd)
                .padding(vertical = 12.dp, horizontal = 8.dp),
        )
    }
}

/** "42 words, 7 this week", counted from Monday, because "this week" should mean this week. */
private fun summary(words: List<Word>): String {
    if (words.isEmpty()) return "Nothing saved yet"
    val startOfWeek = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_MONTH, -1)
    }.timeInMillis
    val week = words.count { it.createdAt >= startOfWeek }
    val plural = if (words.size == 1) "word" else "words"
    return if (week == 0) {
        "${words.size} $plural"
    } else {
        "${words.size} $plural, $week this week"
    }
}
