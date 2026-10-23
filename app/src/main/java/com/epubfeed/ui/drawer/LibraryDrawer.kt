package com.epubfeed.ui.drawer

import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubfeed.data.local.BookEntity
import com.epubfeed.ui.components.BookAvatar
import com.epubfeed.ui.theme.LocalXColors

/**
 * Slide out library drawer. Same gesture and placement as the X account drawer, but
 * it lists imported books instead of menu entries.
 */
/** The installed version, read from the package so it can never drift from the build. */
@Composable
private fun appVersion(): String = run {
    val context = LocalContext.current
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "1.0"
}

@Composable
fun LibraryDrawer(
    books: List<BookEntity>,
    currentBookId: String?,
    onSelect: (BookEntity) -> Unit,
    onAddBook: () -> Unit,
    onDelete: (BookEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val x = LocalXColors.current
    var pendingDelete by remember { mutableStateOf<BookEntity?>(null) }

    ModalDrawerSheet(
        modifier = modifier.width(310.dp),
        drawerContainerColor = x.background,
        drawerContentColor = x.text,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(
                text = "Library",
                color = x.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${books.size} ${if (books.size == 1) "book" else "books"} on this device",
                color = x.muted,
                fontSize = 13.sp,
            )
        }
        HorizontalDivider(color = x.divider, thickness = 1.dp)

        LazyColumn(Modifier.weight(1f)) {
            items(books, key = { it.id }) { book ->
                val isCurrent = book.id == currentBookId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(book) }
                        .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BookAvatar(
                        coverPath = book.coverPath,
                        title = book.title,
                        hue = book.hue,
                        size = 40.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = book.title,
                            color = x.text,
                            fontSize = 15.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${book.author} \u00B7 ${book.format} \u00B7 ${book.postCount} posts",
                            color = x.muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = { pendingDelete = book },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete ${book.title}",
                            tint = x.muted,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
            item(key = "add-book") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddBook() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = x.accent,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = "Add book",
                        color = x.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            HorizontalDivider(color = x.divider, thickness = 1.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "ePUB Feed ${appVersion()}",
                color = x.muted,
                fontSize = 12.sp,
            )
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = x.elevated,
            titleContentColor = x.text,
            textContentColor = x.muted,
            title = { Text("Delete this book?", fontWeight = FontWeight.Bold) },
            text = { Text("\"${book.title}\" and its likes, bookmarks and reading position will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(book)
                    pendingDelete = null
                }) {
                    Text("Delete", color = androidx.compose.ui.graphics.Color(0xFFF4212E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = x.text)
                }
            },
        )
    }
}
