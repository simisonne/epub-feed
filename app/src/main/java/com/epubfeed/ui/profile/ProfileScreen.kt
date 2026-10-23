package com.epubfeed.ui.profile

import androidx.compose.foundation.background
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
                        .padding(3.dp),
                ) {
                    BookAvatar(
                        coverPath = book?.coverPath,
                        title = book?.title.orEmpty(),
                        hue = book?.hue ?: 210,
                        size = 78.dp,
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
                    text = "@${handleFor(book?.author.orEmpty())}",
                    color = x.muted,
                    fontSize = 15.sp,
                )
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
                        text = "Currently in: ${ui.currentChapter}",
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

                SectionTitle("Reading time")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    InfoCell("Tracked", formatDuration(ui.secondsRead), Modifier.weight(1f))
                    InfoCell("Full book", "~${formatMinutes(ui.estimatedMinutes)}", Modifier.weight(1f))
                    InfoCell("Left", "~${formatMinutes(ui.minutesLeft)}", Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Time only counts while you are reading: it pauses after two minutes without a touch.",
                    color = x.muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = x.divider, thickness = 1.dp)
                Spacer(Modifier.height(16.dp))

                SectionTitle("About this book")
                Spacer(Modifier.height(10.dp))
                MetaRow("Publisher", book?.publisher ?: "Not listed")
                MetaRow("Published", book?.publishedYear ?: "Not listed")
                MetaRow("Format", book?.format ?: "-")
                MetaRow("Length", "${formatWords(ui.wordCount)} words \u00B7 ~${ui.estimatedPages} pages")
                MetaRow("Imported", book?.importedAt?.let { formatDate(it) } ?: "-")

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = x.divider, thickness = 1.dp)
                Spacer(Modifier.height(16.dp))

                SectionTitle("Your activity")
                Spacer(Modifier.height(10.dp))
                MetaRow("Liked posts", ui.likeCount.toString())
                MetaRow("Saved posts", ui.bookmarkCount.toString())
                MetaRow("Notes written", ui.noteCount.toString())

                Spacer(Modifier.height(40.dp))
            }
        }
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
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier) {
    val x = LocalXColors.current
    Column(modifier) {
        Text(value, color = x.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, color = x.muted, fontSize = 12.sp)
    }
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

private fun formatMinutes(minutes: Int): String = when {
    minutes <= 0 -> "0m"
    minutes < 60 -> "${minutes}m"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

private fun formatWords(words: Int): String = when {
    words >= 1_000_000 -> String.format(Locale.US, "%.1fM", words / 1_000_000f)
    words >= 1_000 -> String.format(Locale.US, "%.1fk", words / 1_000f)
    else -> words.toString()
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))

private fun handleFor(author: String): String = author.lowercase()
    .replace(Regex("[^a-z0-9]+"), "_")
    .trim('_')
    .ifBlank { "unknown" }
    .take(20)
