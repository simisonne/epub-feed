package com.epubfeed.ui.feed

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.epubfeed.domain.model.FeedPost
import com.epubfeed.domain.model.UserSettings
import com.epubfeed.ui.components.BookAvatar
import com.epubfeed.ui.components.PostCard
import com.epubfeed.ui.components.PinnedWelcomePost
import com.epubfeed.ui.drawer.LibraryDrawer
import com.epubfeed.ui.settings.SettingsActions
import com.epubfeed.ui.settings.SettingsSheet
import com.epubfeed.ui.theme.LocalXColors
import com.epubfeed.ui.theme.readingFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val MIME_TYPES = arrayOf("application/epub+zip", "application/pdf")

@Composable
fun FeedScreen(
    bookId: String?,
    settings: UserSettings,
    settingsActions: SettingsActions,
    onImportUri: (Uri) -> Unit,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val x = LocalXColors.current
    val context = LocalContext.current
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportUri(uri)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.savePositionNow() }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val book = ui.book
    val readingSize = settings.textSize.sp.sp
    val readingFamily = readingFontFamily(settings.readingFont)
    val pinnedVisible = book?.isSample == true && ui.windowStart == 0
    val leading = if (pinnedVisible) 1 else 0

    fun copyToClipboard(text: String, message: String) {
        clipboard.setText(AnnotatedString(text))
        scope.launch { snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short) }
    }

    fun sharePost(post: FeedPost) {
        val text = shareText(post, book?.author.orEmpty(), book?.title.orEmpty())
        clipboard.setText(AnnotatedString(text))
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Copied to clipboard",
                actionLabel = "Share",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) openShareSheet(context, text)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        drawerContent = {
            LibraryDrawer(
                books = ui.books,
                currentBookId = book?.id,
                onSelect = { selected ->
                    scope.launch { drawerState.close() }
                    if (selected.id != book?.id) viewModel.switchToBook(selected)
                },
                onAddBook = {
                    scope.launch { drawerState.close() }
                    picker.launch(MIME_TYPES)
                },
                onDelete = { target -> viewModel.deleteBook(target) },
            )
        },
    ) {
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
            topBar = {
                FeedTopBar(
                    title = book?.title ?: "Epub Feed",
                    author = book?.author.orEmpty(),
                    coverPath = book?.coverPath,
                    hue = book?.hue ?: 210,
                    progress = if (book != null && book.postCount > 0) {
                        (book.lastPostIndex + 1).toFloat() / book.postCount
                    } else {
                        0f
                    },
                    selectedTab = tab,
                    onAvatarClick = { scope.launch { drawerState.open() } },
                    onSettingsClick = { showSettings = true },
                    onTabSelected = { tab = it },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { picker.launch(MIME_TYPES) },
                    containerColor = x.accent,
                    contentColor = Color.White,
                    shape = androidx.compose.foundation.shape.CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Add book",
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when {
                    ui.loading -> LoadingState()
                    book == null -> EmptyLibraryState(onAddBook = { picker.launch(MIME_TYPES) })
                    tab == 0 -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 96.dp),
                        ) {
                            if (pinnedVisible) {
                                item(key = "pinned-welcome") {
                                    PinnedWelcomePost(
                                        displayName = book.title,
                                        handle = handleFor(book.author),
                                        coverPath = book.coverPath,
                                        hue = book.hue,
                                        onAddBook = { picker.launch(MIME_TYPES) },
                                    )
                                    HorizontalDivider(color = x.divider, thickness = 1.dp)
                                }
                            }
                            itemsIndexed(ui.posts, key = { _, post -> post.id }) { index, post ->
                                val next = ui.posts.getOrNull(index + 1)
                                val previous = ui.posts.getOrNull(index - 1)
                                PostCard(
                                    post = post,
                                    displayName = book.title,
                                    handle = handleFor(book.author),
                                    coverPath = book.coverPath,
                                    hue = book.hue,
                                    showThreadLine = next != null && next.chapterIndex == post.chapterIndex,
                                    showChapterDivider = previous != null && previous.chapterIndex != post.chapterIndex,
                                    readingFontSize = readingSize,
                                    readingFontFamily = readingFamily,
                                    viewsLabel = percentLabel(post.globalIndex, ui.totalPosts),
                                    onLike = { viewModel.toggleLike(post) },
                                    onBookmark = { viewModel.toggleBookmark(post) },
                                    onShare = { sharePost(post) },
                                    onCopy = {
                                        copyToClipboard(
                                            postText(post, book.author, book.title),
                                            "Post copied",
                                        )
                                    },
                                )
                                HorizontalDivider(color = x.divider, thickness = 1.dp)
                            }
                            item(key = "feed-bottom") { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                    tab == 1 -> ChaptersTab(
                        chapters = ui.chapters,
                        lastPostIndex = book.lastPostIndex,
                        onChapterClick = { index ->
                            viewModel.jumpTo(index)
                            tab = 0
                        },
                    )
                    else -> SavedTab(
                        posts = ui.savedPosts,
                        displayName = book.title,
                        handle = handleFor(book.author),
                        coverPath = book.coverPath,
                        hue = book.hue,
                        readingFontSize = readingSize,
                        readingFontFamily = readingFamily,
                        totalPosts = ui.totalPosts,
                        onLike = { viewModel.toggleLike(it) },
                        onBookmark = { viewModel.toggleBookmark(it) },
                        onShare = { sharePost(it) },
                        onCopy = { copyToClipboard(postText(it, book.author, book.title), "Post copied") },
                        onOpen = { saved ->
                            viewModel.jumpTo(saved.globalIndex)
                            tab = 0
                        },
                    )
                }

                if (ui.busyMessage != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(x.elevated)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "${ui.busyMessage}\u2026",
                            color = x.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            actions = settingsActions,
            onDismiss = { showSettings = false },
        )
    }

    // Report the visible window so the ViewModel can grow it and save the position.
    LaunchedEffect(listState, leading, ui.posts.size, ui.windowStart) {
        snapshotFlow {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: first
            first to last
        }
            .distinctUntilChanged()
            .collect { (first, last) ->
                viewModel.onVisibleRange(first - leading, last - leading)
            }
    }

    // The window can be shorter than the viewport for short chapters: keep filling.
    LaunchedEffect(ui.posts.size, ui.totalPosts, ui.windowStart) {
        val loaded = ui.windowStart + ui.posts.size
        if (ui.posts.isNotEmpty() && ui.posts.size < 24 && loaded < ui.totalPosts) {
            delay(120)
            viewModel.onVisibleRange(0, 1000)
        }
    }

    // Restore / jump to the requested post once its window has arrived.
    LaunchedEffect(ui.scrollTarget, ui.posts.size, leading, ui.windowStart) {
        val target = ui.scrollTarget ?: return@LaunchedEffect
        if (ui.posts.isEmpty()) return@LaunchedEffect
        val itemCount = listState.layoutInfo.totalItemsCount
        if (itemCount == 0) return@LaunchedEffect
        // Target 0 (or the very first post of the window) means the top of the timeline,
        // which is where the pinned welcome post lives for the sample book.
        val index = if (target <= ui.windowStart) 0 else {
            (target - ui.windowStart + leading).coerceIn(0, itemCount - 1)
        }
        listState.scrollToItem(index)
        viewModel.consumeScrollTarget()
    }
}

@Composable
private fun LoadingState() {
    val x = LocalXColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = x.accent, strokeWidth = 2.dp)
    }
}

@Composable
private fun EmptyLibraryState(onAddBook: () -> Unit) {
    val x = LocalXColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BookAvatar(coverPath = null, title = "E", hue = 210, size = 64.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Your library is empty",
            color = x.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Import an EPUB or a PDF and it becomes a timeline: chapters turn into threads, paragraphs into posts.",
            color = x.muted,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .background(x.accent, androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                .clickable(onClick = onAddBook)
                .padding(horizontal = 22.dp, vertical = 11.dp),
        ) {
            Text("Add a book", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

fun handleFor(author: String): String {
    val slug = author.lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
    return slug.ifBlank { "unknown" }.take(20)
}

fun shareText(post: FeedPost, author: String, title: String): String {
    val attribution = listOf(author.trim(), title.trim()).filter { it.isNotEmpty() }.joinToString(", ")
    return if (attribution.isEmpty()) post.plainText else "${post.plainText}\n\n\u2014 $attribution"
}

fun postText(post: FeedPost, author: String, title: String): String = shareText(post, author, title)

private fun openShareSheet(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share post")) }
}
