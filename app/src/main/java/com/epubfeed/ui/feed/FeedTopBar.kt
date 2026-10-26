package com.epubfeed.ui.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubfeed.ui.components.BookAvatar
import com.epubfeed.ui.theme.LocalXColors

val FeedTabs = listOf("Feed", "Chapters", "Likes", "Saved")

/**
 * Sticky header: book identity on the left (the "account"), Aa on the right, the tab
 * row and a 2dp reading progress hairline underneath.
 */
@Composable
fun FeedTopBar(
    title: String,
    author: String,
    avatarPath: String?,
    hue: Int,
    progress: Float,
    selectedTab: Int,
    onAvatarClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTabSelected: (Int) -> Unit,
) {
    val x = LocalXColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(x.background.copy(alpha = 0.96f))
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAvatarClick)
                    .semantics { contentDescription = "Open library" },
                contentAlignment = Alignment.Center,
            ) {
                BookAvatar(
                    avatarPath = avatarPath,
                    title = title,
                    hue = hue,
                    size = 32.dp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = x.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (author.isNotBlank()) {
                    Text(
                        text = author,
                        color = x.muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Reading settings",
                    tint = x.text,
                    modifier = Modifier.size(21.dp),
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            FeedTabs.forEachIndexed { index, label ->
                TabItem(
                    label = label,
                    selected = index == selectedTab,
                    modifier = Modifier.weight(1f),
                    onClick = { onTabSelected(index) },
                )
            }
        }

        val animatedProgress by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            label = "readingProgress",
        )
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxSize()
                    .background(x.accent.copy(alpha = 0.85f)),
            )
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val x = LocalXColors.current
    val underline by animateFloatAsState(if (selected) 1f else 0f, label = "tabUnderline")
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = if (selected) x.text else x.muted,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(4.dp)
                    .alpha(underline)
                    .clip(RoundedCornerShape(999.dp))
                    .background(x.accent),
            )
        }
    }
}
