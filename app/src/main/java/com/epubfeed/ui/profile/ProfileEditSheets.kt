package com.epubfeed.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubfeed.data.local.BookEntity
import com.epubfeed.ui.components.BookAvatar
import com.epubfeed.ui.theme.LocalXColors

/**
 * The identity editor. A book's account carries three things the reader owns: the name shown on
 * every post, the author line under it and the @ next to the name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorSheet(
    title: String,
    author: String,
    handle: String,
    onSave: (title: String, author: String, handle: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val x = LocalXColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(title) }
    var authorText by remember { mutableStateOf(author) }
    var handleText by remember { mutableStateOf(handle) }
    val canSave = name.isNotBlank() && authorText.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = x.background,
        contentColor = x.text,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = x.divider) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Edit profile",
                    color = x.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(
                            if (canSave) x.text else x.text.copy(alpha = 0.35f),
                            RoundedCornerShape(999.dp),
                        )
                        .clickable(enabled = canSave) { onSave(name, authorText, handleText) }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Save",
                        color = x.background,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            EditField(
                label = "Name",
                value = name,
                placeholder = "The name on every post",
                onChange = { name = it.take(80) },
            )
            EditField(
                label = "Author",
                value = authorText,
                placeholder = "Who wrote it",
                onChange = { authorText = it.take(80) },
            )
            EditField(
                label = "Username",
                value = handleText,
                placeholder = "Leave empty to use the author name",
                prefix = "@",
                onChange = { handleText = it.filter { ch -> !ch.isWhitespace() }.take(30) },
            )
        }
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    prefix: String = "",
) {
    val x = LocalXColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, color = x.muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(x.elevated)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (prefix.isNotEmpty()) {
                    Text(prefix, color = x.muted, fontSize = 15.sp)
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = x.muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onChange,
                        singleLine = true,
                        textStyle = TextStyle(color = x.text, fontSize = 15.sp),
                        cursorBrush = SolidColor(x.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * The profile photo picker: the pictures a book already carries, so the reader can take the cover,
 * one of the first pages (a PDF's cover usually hides there) or one of the book's own pictures.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarPickerSheet(
    coverPath: String?,
    pageLabel: String,
    title: String,
    hue: Int,
    letterSelected: Boolean,
    onUseFirstPage: () -> Unit,
    onUseLetter: () -> Unit,
    onDismiss: () -> Unit,
) {
    val x = LocalXColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = x.background,
        contentColor = x.text,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = x.divider) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Profile photo",
                color = x.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 2.dp),
            )
            if (coverPath == null) {
                Text(
                    text = "This book has no page saved to use as a photo.",
                    color = x.muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            } else {
                Spacer(Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (coverPath != null) {
                    PhotoChoice(
                        label = pageLabel,
                        title = title,
                        hue = hue,
                        imagePath = coverPath,
                        selected = !letterSelected,
                        onClick = onUseFirstPage,
                    )
                }
                PhotoChoice(
                    label = "Letter",
                    title = title,
                    hue = hue,
                    imagePath = BookEntity.LetterAvatar,
                    selected = letterSelected,
                    onClick = onUseLetter,
                )
            }
        }
    }
}

/** One of the two profile photo choices: circle preview, name under it, ring when it is in use. */
@Composable
private fun PhotoChoice(
    label: String,
    title: String,
    hue: Int,
    imagePath: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val x = LocalXColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(88.dp),
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) x.accent else x.divider,
                    shape = CircleShape,
                )
                .padding(3.dp)
                .clickable(onClick = onClick),
        ) {
            BookAvatar(
                avatarPath = imagePath,
                title = title,
                hue = hue,
                size = 70.dp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = if (selected) x.text else x.muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
