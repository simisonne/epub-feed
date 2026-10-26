package com.epubfeed.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.epubfeed.ui.components.BookAvatar
import com.epubfeed.ui.theme.LocalXColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** The book's "account" page: cover, identity and everything the reader could want to know. */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onContinueReading: (String) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val x = LocalXColors.current
    val book = ui.book
    var editingIdentity by remember { mutableStateOf(false) }
    var editingPhoto by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(x.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = x.text,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = book?.title.orEmpty(),
                    color = x.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${ui.postCount} posts",
                    color = x.muted,
                    fontSize = 12.sp,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Banner(hue = book?.hue ?: 210)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-38).dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(x.background)
                        .padding(3.dp)
                        .clickable { editingPhoto = true },
                ) {
                    BookAvatar(
                        avatarPath = book?.avatarSource,
                        title = book?.title.orEmpty(),
                        hue = book?.hue ?: 210,
                        size = 78.dp,
                        contentDescription = "Change the profile photo",
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(x.text)
                        .clickable { book?.let { onContinueReading(it.id) } }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                ) {
                    Text(
                        text = "Continue reading",
                        color = x.background,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Column(Modifier.padding(horizontal = 16.dp).offset(y = (-24).dp)) {
                Text(
                    text = book?.title.orEmpty(),
                    color = x.text,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "@${book?.displayHandle.orEmpty()}",
                    color = x.muted,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, x.divider, RoundedCornerShape(999.dp))
                            .clickable { editingIdentity = true }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = "Edit profile",
                            color = x.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = buildString {
                        append(book?.author.orEmpty())
                        if (book != null) {
                            append(" \u00B7 ${book.format}")
                            append(" \u00B7 ${ui.chapterCount} chapters")
                            append(" \u00B7 ${ui.postCount} posts")
                        }
                    },
                    color = x.text,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
                if (ui.currentChapter.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Now reading ${ui.currentChapter}",
                        color = x.muted,
                        fontSize = 14.sp,
                    )
                }

                Spacer(Modifier.height(16.dp))
                StatsRow(
                    left = ui.chapterCount.toString() to "Chapters",
                    middle = formatWords(ui.wordCount) to "Words",
                    right = "${(ui.progress * 100).roundToInt()}%" to "Read",
                )

                Spacer(Modifier.height(16.dp))
                ProgressBar(ui.progress)

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = x.divider, thickness = 1.dp)
                Spacer(Modifier.height(16.dp))

                SectionTitle("About this book")
                Spacer(Modifier.height(10.dp))
                MetaRow("Publisher", book?.publisher ?: "Not listed")
                MetaRow("Published", book?.publishedYear ?: "Not listed")
                MetaRow("Format", book?.format ?: "-")
                MetaRow(
                    "Length",
                    "${formatWords(ui.wordCount)} ${plural(ui.wordCount, "word")} \u00B7 " +
                        "~${ui.estimatedPages} ${plural(ui.estimatedPages, "page")}",
                )
                MetaRow("Imported", book?.importedAt?.let { formatDate(it) } ?: "-")

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = x.divider, thickness = 1.dp)
                Spacer(Modifier.height(16.dp))

                SectionTitle("Your activity")
                Spacer(Modifier.height(10.dp))
                MetaRow("Tracked time", formatDuration(ui.secondsRead))
                MetaRow("Liked posts", ui.likeCount.toString())
                MetaRow("Saved posts", ui.bookmarkCount.toString())
                MetaRow("Notes written", ui.noteCount.toString())

                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (editingIdentity && book != null) {
        ProfileEditorSheet(
            title = book.title,
            author = book.author,
            handle = book.handle.orEmpty(),
            onSave = { name, author, handle ->
                viewModel.saveIdentity(name, author, handle)
                editingIdentity = false
            },
            onDismiss = { editingIdentity = false },
        )
    }

    if (editingPhoto && book != null) {
        AvatarPickerSheet(
            coverPath = book.coverPath,
            pageLabel = if (book.format == "PDF") "First page" else "Cover",
            title = book.title,
            hue = book.hue,
            letterSelected = book.showsLetter,
            onUseFirstPage = {
                book.coverPath?.let(viewModel::useFirstPageAvatar)
                editingPhoto = false
            },
            onUseLetter = {
                viewModel.useLetterAvatar()
                editingPhoto = false
            },
            onDismiss = { editingPhoto = false },
        )
    }
}

@Composable
private fun Banner(hue: Int) {
    val x = LocalXColors.current
    val start = Color.hsv(hue.toFloat(), 0.55f, 0.62f)
    val end = Color.hsv(((hue + 40) % 360).toFloat(), 0.65f, 0.32f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(Brush.linearGradient(listOf(start, end))),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, x.background.copy(alpha = 0.35f)),
                    ),
                ),
        )
    }
}

@Composable
private fun StatsRow(left: Pair<String, String>, middle: Pair<String, String>, right: Pair<String, String>) {
    val x = LocalXColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        listOf(left, middle, right).forEach { (value, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, color = x.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(5.dp))
                Text(label, color = x.muted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    val x = LocalXColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(x.divider),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(x.accent),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    val x = LocalXColors.current
    Text(text, color = x.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun MetaRow(label: String, value: String) {
    val x = LocalXColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = x.muted, fontSize = 14.sp, modifier = Modifier.width(112.dp))
        Text(value, color = x.text, fontSize = 14.sp, lineHeight = 19.sp)
    }
}

internal fun formatDuration(seconds: Long): String {
    if (seconds < 60L) return "${seconds}s"
    val minutes = seconds / 60
    if (minutes < 60L) return "${minutes}m"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0L) "${hours}h" else "${hours}h ${rest}m"
}

private fun formatWords(words: Int): String = when {
    words >= 1_000_000 -> String.format(Locale.US, "%.1fM", words / 1_000_000f)
    words >= 1_000 -> String.format(Locale.US, "%.1fk", words / 1_000f)
    else -> words.toString()
}

private fun plural(count: Int, noun: String): String = if (count == 1) noun else "${noun}s"

private fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
