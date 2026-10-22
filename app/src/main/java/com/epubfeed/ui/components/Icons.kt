package com.epubfeed.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The feed's icon set. Generic outline vectors in the spirit of X's action row;
 * no X artwork is bundled.
 */
object FeedIcons {
    val Reply: ImageVector = Icons.Outlined.ChatBubbleOutline
    val Repost: ImageVector = Icons.Outlined.Repeat
    val LikeOutline: ImageVector = Icons.Outlined.FavoriteBorder
    val LikeFilled: ImageVector = Icons.Filled.Favorite
    val Views: ImageVector = Icons.Outlined.BarChart
    val BookmarkOutline: ImageVector = Icons.Outlined.BookmarkBorder
    val BookmarkFilled: ImageVector = Icons.Filled.Bookmark
    val Share: ImageVector = Icons.Outlined.Share
    val Verified: ImageVector = Icons.Filled.Verified
}

fun Modifier.describeAs(text: String): Modifier = this.semantics {
    contentDescription = text
}
