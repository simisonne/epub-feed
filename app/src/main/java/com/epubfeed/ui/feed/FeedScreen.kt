package com.epubfeed.ui.feed

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val MIME_TYPES = arrayOf("application/epub+zip", "application/pdf")

@Composable
fun FeedScreen(
    bookId: String?,
    settings: UserSettings,
    settingsActions: SettingsActions,
    onImportUri: (Uri) -> Unit,
    onOpenProfile: (String) -> Unit,
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
    val pagerState = rememberPagerState(initialPage = 0) { FeedTabs.size }

    var showSettings by remember { mutableStateOf(false) }
    var noteTarget by remember { mutableStateOf<FeedPost?>(null) }
    var menuTarget by remember { mutableStateOf<FeedPost?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportUri(uri)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.savePositionNow() }
    }

    // The reading clock only runs while the app is actually in front.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onScreenResumed()
                Lifecycle.Event.ON_PAUSE -> viewModel.onScreenPaused()
                Lifecycle.Event.ON_STOP -> viewModel.savePositionNow()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val book = ui.book
    val readingSize = settings.textSize.sp.sp
    val readingFamily = readingFontFamily(settings.readingFont)
    val pinnedVisible = book?.isSample == true && ui.chapterIndex == 0
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

    // Leaving the feed (a swipe to another tab) persists the place immediately.
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != 0) viewModel.savePositionNow()
        viewModel.onReadingInteraction()
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
                    title = book?.title ?: "ePUB Feed",
                    author = book?.author.orEmpty(),
                    coverPath = book?.coverPath,
                    hue = book?.hue ?: 210,
                    progress = if (book != null && book.postCount > 0) {
                        (ui.currentIndex + 1).toFloat() / book.postCount
                    } else {
                        0f
                    },
                    selectedTab = pagerState.currentPage,
                    onAvatarClick = { scope.launch { drawerState.open() } },
                    onSettingsClick = { showSettings = true },
                    onTabSelected = { selected ->
                        viewModel.onReadingInteraction()
                        scope.launch { pagerState.animateScrollToPage(selected) }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { picker.launch(MIME_TYPES) },
                    containerColor = x.accent,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(999.dp),
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
                    else -> HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { it },
                    ) { page ->
                        when (page) {
                            0 -> ChapterFeed(
                                posts = ui.posts,
                                notes = ui.notes,
                                book = book,
                                displayName = book.title,
                                handle = handleFor(book.author),
                                readingSize = readingSize,
                                readingFamily = readingFamily,
                                totalPosts = ui.totalPosts,
                                pinnedVisible = pinnedVisible,
                                leading = leading,
                                listState = listState,
                                chapterTitle = ui.chapterTitle,
                                chapterIndex = ui.chapterIndex,
                                hasNextChapter = ui.hasNextChapter,
                                nextChapterTitle = ui.chapters.getOrNull(ui.chapterIndex + 1)?.title,
                                scrollTarget = ui.scrollTarget,
                                onVisibleListIndex = viewModel::onVisibleListIndex,
                                onConsumeScrollTarget = viewModel::consumeScrollTarget,
                                onInteraction = viewModel::onReadingInteraction,
                                onLike = { viewModel.toggleLike(it) },
                                onBookmark = { viewModel.toggleBookmark(it) },
                                onNote = { noteTarget = it },
                                onShare = { sharePost(it) },
                                onCopy = { copyToClipboard(postText(it, book.author, book.title), "Post copied") },
                                onDeleteNote = { viewModel.deleteNote(it.id) },
                                onLongPress = { menuTarget = it },
                                onAvatarClick = { onOpenProfile(book.id) },
                                onNextChapter = { viewModel.nextChapter() },
                                onAddBook = { picker.launch(MIME_TYPES) },
                            )
                            1 -> ChaptersTab(
                                chapters = ui.chapters,
                                lastPostIndex = ui.currentIndex,
                                onChapterClick = { firstIndex ->
                                    // The chapters list reports the chapter's first post, not
                                    // its position in the list.
                                    viewModel.openPost(firstIndex)
                                    scope.launch { pagerState.animateScrollToPage(0) }
                                },
                            )
                            else -> CollectionPage(
                                liked = page == 2,
                                likedPosts = ui.likedPosts,
                                savedPosts = ui.savedPosts,
                                notes = ui.notes,
                                book = book,
                                displayName = book.title,
                                handle = handleFor(book.author),
                                readingSize = readingSize,
                                readingFamily = readingFamily,
                                totalPosts = ui.totalPosts,
                                onLike = { viewModel.toggleLike(it) },
                                onBookmark = { viewModel.toggleBookmark(it) },
                                onComment = { noteTarget = it },
                                onShare = { sharePost(it) },
                                onCopy = { copyToClipboard(postText(it, book.author, book.title), "Post copied") },
                                onDeleteNote = { viewModel.deleteNote(it.id) },
                                onAvatarClick = { onOpenProfile(book.id) },
                                onLongPress = { menuTarget = it },
                                onOpen = { opened ->
                                    viewModel.openPost(opened.globalIndex)
                                    scope.launch { pagerState.animateScrollToPage(0) }
                                },
                            )
                        }
                    }
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

    noteTarget?.let { post ->
        NoteComposer(
            post = post,
            displayName = book?.title.orEmpty(),
            coverPath = book?.coverPath,
            hue = book?.hue ?: 210,
            existing = ui.notes[post.id].orEmpty(),
            onDismiss = { noteTarget = null },
            onPost = { text ->
                viewModel.addNote(post.id, text)
                noteTarget = null
            },
        )
    }

    menuTarget?.let { post ->
        val chapterOfPost = ui.chapters.firstOrNull { it.index == post.chapterIndex }
        val alreadyStart = post.isChapterTitlePost || post.indexInChapter <= 1
        PostActionsSheet(
            post = post,
            notes = ui.notes[post.id].orEmpty(),
            chapterStart = alreadyStart,
            canUnmark = alreadyStart && post.chapterIndex > 0 && chapterOfPost != null,
            onMark = {
                menuTarget = null
                viewModel.markAsChapter(post)
            },
            onUnmark = {
                menuTarget = null
                chapterOfPost?.let { viewModel.unmarkChapter(it) }
            },
            onCopy = {
                menuTarget = null
                copyToClipboard(postText(post, book?.author.orEmpty(), book?.title.orEmpty()), "Post copied")
            },
            onNote = {
                menuTarget = null
                noteTarget = post
            },
            onDismiss = { menuTarget = null },
        )
    }
}

/**
 * One chapter, one feed: the posts of the current chapter and a "go to next chapter"
 * button where a timeline would offer a refresh.
 */
@Composable
private fun ChapterFeed(
    posts: List<FeedPost>,
    notes: Map<String, List<com.epubfeed.domain.model.Note>>,
    book: com.epubfeed.data.local.BookEntity,
    displayName: String,
    handle: String,
    readingSize: androidx.compose.ui.unit.TextUnit,
    readingFamily: androidx.compose.ui.text.font.FontFamily,
    totalPosts: Int,
    pinnedVisible: Boolean,
    leading: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    chapterTitle: String,
    chapterIndex: Int,
    hasNextChapter: Boolean,
    nextChapterTitle: String?,
    scrollTarget: Int?,
    onVisibleListIndex: (Int) -> Unit,
    onConsumeScrollTarget: () -> Unit,
    onInteraction: () -> Unit,
    onLike: (FeedPost) -> Unit,
    onBookmark: (FeedPost) -> Unit,
    onNote: (FeedPost) -> Unit,
    onShare: (FeedPost) -> Unit,
    onCopy: (FeedPost) -> Unit,
    onDeleteNote: (com.epubfeed.domain.model.Note) -> Unit,
    onLongPress: (FeedPost) -> Unit,
    onAvatarClick: () -> Unit,
    onNextChapter: () -> Unit,
    onAddBook: () -> Unit,
) {
    val x = LocalXColors.current

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        if (pinnedVisible) {
            item(key = "pinned-welcome") {
                PinnedWelcomePost(
                    displayName = displayName,
                    handle = handle,
                    coverPath = book.coverPath,
                    hue = book.hue,
                    onAddBook = onAddBook,
                )
                HorizontalDivider(color = x.divider, thickness = 1.dp)
            }
        }
        itemsIndexed(posts, key = { _, post -> post.id }) { index, post ->
            val next = posts.getOrNull(index + 1)
            val previous = posts.getOrNull(index - 1)
            PostCard(
                post = post,
                displayName = displayName,
                handle = handle,
                coverPath = book.coverPath,
                hue = book.hue,
                showThreadLine = next != null,
                showChapterDivider = previous != null,
                readingFontSize = readingSize,
                readingFontFamily = readingFamily,
                viewsLabel = percentLabel(post.globalIndex, totalPosts),
                notes = notes[post.id].orEmpty(),
                onLike = { onLike(post) },
                onBookmark = { onBookmark(post) },
                onComment = { onNote(post) },
                onShare = { onShare(post) },
                onCopy = { onCopy(post) },
                onDeleteNote = onDeleteNote,
                onAvatarClick = onAvatarClick,
                onLongPress = { onLongPress(post) },
            )
            HorizontalDivider(color = x.divider, thickness = 1.dp)
        }
        item(key = "chapter-footer-$chapterIndex") {
            ChapterFooter(
                chapterTitle = chapterTitle,
                hasNextChapter = hasNextChapter,
                nextChapterTitle = nextChapterTitle,
                onNextChapter = onNextChapter,
            )
        }
    }

    // Report the top visible post, and treat scrolling as reading activity.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (index, scrolling) ->
                if (scrolling) onInteraction()
                onVisibleListIndex(index - leading)
            }
    }

    // Put the reader back where they were: the target is a global post index.
    LaunchedEffect(scrollTarget, posts.firstOrNull()?.id, posts.size, leading) {
        val target = scrollTarget ?: return@LaunchedEffect
        if (posts.isEmpty()) return@LaunchedEffect
        val index = posts.indexOfFirst { it.globalIndex >= target }.let { if (it < 0) 0 else it }
        // The first post of the sample book sits under the pinned welcome post: park on the
        // welcome post there, and offset by it everywhere else.
        val listIndex = if (index == 0) 0 else index + leading
        listState.scrollToItem(listIndex)
        withTimeoutOrNull(1_500) {
            snapshotFlow { listState.firstVisibleItemIndex }.first { it >= listIndex }
        }
        onConsumeScrollTarget()
    }
}

@Composable
private fun CollectionPage(
    liked: Boolean,
    likedPosts: List<FeedPost>,
    savedPosts: List<FeedPost>,
    notes: Map<String, List<com.epubfeed.domain.model.Note>>,
    book: com.epubfeed.data.local.BookEntity,
    displayName: String,
    handle: String,
    readingSize: androidx.compose.ui.unit.TextUnit,
    readingFamily: androidx.compose.ui.text.font.FontFamily,
    totalPosts: Int,
    onLike: (FeedPost) -> Unit,
    onBookmark: (FeedPost) -> Unit,
    onComment: (FeedPost) -> Unit,
    onShare: (FeedPost) -> Unit,
    onCopy: (FeedPost) -> Unit,
    onDeleteNote: (com.epubfeed.domain.model.Note) -> Unit,
    onAvatarClick: () -> Unit,
    onLongPress: (FeedPost) -> Unit,
    onOpen: (FeedPost) -> Unit,
) {
    if (liked) {
        LikedTab(
            posts = likedPosts,
            notes = notes,
            displayName = displayName,
            handle = handle,
            coverPath = book.coverPath,
            hue = book.hue,
            readingFontSize = readingSize,
            readingFontFamily = readingFamily,
            totalPosts = totalPosts,
            onLike = onLike,
            onBookmark = onBookmark,
            onComment = onComment,
            onShare = onShare,
            onCopy = onCopy,
            onDeleteNote = onDeleteNote,
            onAvatarClick = onAvatarClick,
            onLongPress = onLongPress,
            onOpen = onOpen,
        )
    } else {
        SavedTab(
            posts = savedPosts,
            notes = notes,
            displayName = displayName,
            handle = handle,
            coverPath = book.coverPath,
            hue = book.hue,
            readingFontSize = readingSize,
            readingFontFamily = readingFamily,
            totalPosts = totalPosts,
            onLike = onLike,
            onBookmark = onBookmark,
            onComment = onComment,
            onShare = onShare,
            onCopy = onCopy,
            onDeleteNote = onDeleteNote,
            onAvatarClick = onAvatarClick,
            onLongPress = onLongPress,
            onOpen = onOpen,
        )
    }
}

@Composable
private fun ChapterFooter(
    chapterTitle: String,
    hasNextChapter: Boolean,
    nextChapterTitle: String?,
    onNextChapter: () -> Unit,
) {
    val x = LocalXColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (hasNextChapter) {
            Text(
                text = "You finished this chapter",
                color = x.muted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clickable(onClick = onNextChapter)
                    .background(x.accent, RoundedCornerShape(999.dp))
                    .padding(horizontal = 26.dp, vertical = 13.dp),
            ) {
                Text(
                    text = "Go to next chapter",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (!nextChapterTitle.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Up next: $nextChapterTitle",
                    color = x.muted,
                    fontSize = 13.sp,
                )
            }
        } else {
            Text(
                text = "That is the last chapter of the book.",
                color = x.muted,
                fontSize = 14.sp,
            )
            if (chapterTitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = chapterTitle,
                    color = x.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
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
                .background(x.accent, RoundedCornerShape(999.dp))
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
