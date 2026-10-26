package com.epubfeed.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.epubfeed.ui.theme.LocalXColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Wide and tall crops, the range X allows for one picture in a post. */
private const val MaxAspect = 1.91f
private const val MinAspect = 0.8f

private const val TargetWidthPx = 1080

private sealed interface LoadState {
    data object Loading : LoadState

    /** The file is gone or undrawable: the post shows no picture rather than an empty frame. */
    data object Missing : LoadState

    data class Ready(val image: ImageBitmap, val aspect: Float) : LoadState
}

private object IllustrationCache {
    val cache = LruCache<String, LoadState.Ready>(6)
}

/**
 * A picture from the book, drawn the way X draws one: the full width of the post's text column,
 * rounded 16dp, a hairline border, cropped into a sane aspect instead of running off the screen.
 */
@Composable
fun Illustration(path: String?, alt: String?, modifier: Modifier = Modifier) {
    if (path == null) return
    val x = LocalXColors.current
    val shape = RoundedCornerShape(16.dp)

    // The producer restarts on a path change but keeps the previous value, so reset it first.
    val state by produceState<LoadState>(initialValue = IllustrationCache.cache.get(path) ?: LoadState.Loading, path) {
        val cached = IllustrationCache.cache.get(path)
        if (cached != null) {
            value = cached
            return@produceState
        }
        value = LoadState.Loading
        val loaded = withContext(Dispatchers.IO) { decode(path) }
        if (loaded != null) IllustrationCache.cache.put(path, loaded)
        value = loaded ?: LoadState.Missing
    }

    when (val current = state) {
        LoadState.Missing -> Unit
        LoadState.Loading -> Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(x.elevated),
        )
        is LoadState.Ready -> Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(current.aspect.coerceIn(MinAspect, MaxAspect))
                .clip(shape)
                .border(1.dp, x.divider, shape),
        ) {
            Image(
                bitmap = current.image,
                contentDescription = alt,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun decode(path: String): LoadState.Ready? {
    return runCatching {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TargetWidthPx) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap: Bitmap = BitmapFactory.decodeFile(path, options) ?: return null
        LoadState.Ready(bitmap.asImageBitmap(), bitmap.width.toFloat() / bitmap.height.toFloat())
    }.getOrNull()
}
