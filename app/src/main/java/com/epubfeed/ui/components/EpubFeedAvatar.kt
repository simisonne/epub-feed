package com.epubfeed.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.epubfeed.R

/**
 * The app's own avatar, used as the reader's profile picture: the launcher icon on its dark
 * background, so a note the reader wrote is signed with ePUB Feed rather than a placeholder letter.
 *
 * The launcher foreground is drawn on a 108dp canvas whose art only fills the middle, so it is
 * scaled up to fill the small circle instead of sitting in the middle of it.
 */
@Composable
fun EpubFeedAvatar(size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF0A0A0A))
            .semantics { contentDescription = "ePUB Feed" },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.7f),
        )
    }
}
