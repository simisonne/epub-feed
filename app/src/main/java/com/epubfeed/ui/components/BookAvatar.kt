package com.epubfeed.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

private object CoverCache {
    val cache = LruCache<String, ImageBitmap>(24)
}

/**
 * A book's avatar: the cover art when the EPUB shipped one, otherwise a coloured
 * circle with the title's initial, like an X account with no profile photo.
 */
@Composable
fun BookAvatar(
    coverPath: String?,
    title: String,
    hue: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val density = LocalDensity.current
    val px = with(density) { size.roundToPx() }.coerceAtLeast(1)
    val cacheKey = "${coverPath ?: "none"}-$px"
    val bitmap by produceState(initialValue = CoverCache.cache.get(cacheKey), coverPath, px) {
        if (value == null && coverPath != null) {
            val loaded = withContext(Dispatchers.IO) { decodeSampled(coverPath, px) }
            if (loaded != null) CoverCache.cache.put(cacheKey, loaded)
            value = loaded
        }
    }

    val semantics = if (contentDescription == null) Modifier else Modifier.semantics {
        this.contentDescription = contentDescription
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(semantics),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .background(Color.hsv(hue.toFloat().coerceIn(0f, 360f), 0.45f, 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                val initial = title.trim().firstOrNull()?.uppercase() ?: "?"
                Text(
                    text = initial,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = with(density) { (size.value * 0.42f).sp },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private fun decodeSampled(path: String, targetPx: Int): ImageBitmap? {
    return runCatching {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val shortest = minOf(bounds.outWidth, bounds.outHeight)
        while (shortest / (sample * 2) >= targetPx) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap: Bitmap = BitmapFactory.decodeFile(path, options) ?: return null
        // Cover art is usually portrait: centre crop to a square so the circle fills nicely.
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(
            bitmap,
            max(0, (bitmap.width - side) / 2),
            max(0, (bitmap.height - side) / 2),
            side,
            side,
        )
        if (square !== bitmap) bitmap.recycle()
        square.asImageBitmap()
    }.getOrNull()
}
