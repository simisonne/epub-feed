package com.epubfeed.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubfeed.ui.theme.LocalXColors

/**
 * The pinned post that only the bundled sample book shows. It explains how to import
 * real books, sitting at the top of the timeline the way a pinned tweet does.
 */
@Composable
fun PinnedWelcomePost(
    displayName: String,
    handle: String,
    coverPath: String?,
    hue: Int,
    onAddBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val x = LocalXColors.current
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = null,
                tint = x.muted,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Pinned",
                color = x.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row {
            BookAvatar(coverPath = coverPath, title = displayName, hue = hue, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        color = x.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "Verified book",
                        tint = x.accent,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = " @$handle",
                        color = x.muted,
                        fontSize = 15.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Welcome to Epub Feed. This is Pride and Prejudice, split into a timeline: " +
                        "every chapter is a thread, every paragraph a post.",
                    color = x.text,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Tap + to import your own EPUB or PDF, tap the avatar to switch books, " +
                        "and tap Aa to change the type, the post length and the theme.",
                    color = x.text,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Add a book",
                    color = x.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(x.accent.copy(alpha = 0.12f))
                        .clickable(onClick = onAddBook)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}
