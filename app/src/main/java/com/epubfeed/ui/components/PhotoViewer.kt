package com.epubfeed.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

private const val MinScale = 1f
private const val MaxScale = 5f
private const val DoubleTapScale = 2.5f

/**
 * A picture post opened full screen, the way X shows one.
 *
 * A single tap shows or hides the chrome instead of closing, a double tap zooms to the point that
 * was tapped and back out again, a pinch zooms with the picture kept inside its frame, and a drag
 * down (or up) at fit size follows the finger and dismisses once it is far enough or fast enough.
 * The close button and the back gesture both leave.
 */
@Composable
fun PhotoViewer(path: String, onDismiss: () -> Unit) {
    val image by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { decodeForViewer(path) }
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissDistance = with(density) { 110.dp.toPx() }

    var scale by remember(path) { mutableFloatStateOf(1f) }
    var translation by remember(path) { mutableStateOf(Offset.Zero) }
    var dismissOffset by remember(path) { mutableFloatStateOf(0f) }
    var chromeVisible by remember(path) { mutableStateOf(true) }
    var viewSize by remember(path) { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    /** Keeps the picture inside its frame: only the part that overflows can be panned to. */
    fun clampTranslation(candidate: Offset, atScale: Float): Offset {
        val maxX = (viewSize.width * (atScale - 1f)) / 2f
        val maxY = (viewSize.height * (atScale - 1f)) / 2f
        return Offset(
            candidate.x.coerceIn(-maxX, maxX),
            candidate.y.coerceIn(-maxY, maxY),
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Fades and shrinks a little while a dismiss drag is in progress, like X's viewer.
        val dragFraction = (abs(dismissOffset) / dismissDistance).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 1f - dragFraction * 0.6f))
                .onSizeChanged { viewSize = it.toSize() }
                .pointerInput(path) {
                    detectTapGestures(
                        onTap = { chromeVisible = !chromeVisible },
                        onDoubleTap = { tap ->
                            val target = if (scale > MinScale + 0.01f) MinScale else DoubleTapScale
                            val fromScale = scale
                            val fromTranslation = translation
                            val centre = Offset(viewSize.width / 2f, viewSize.height / 2f)
                            val toTranslation = if (target == MinScale) {
                                Offset.Zero
                            } else {
                                clampTranslation((tap - centre) * (1f - target), target)
                            }
                            scope.launch {
                                animate(0f, 1f, animationSpec = tween(220)) { fraction, _ ->
                                    scale = fromScale + (target - fromScale) * fraction
                                    translation = lerp(fromTranslation, toTranslation, fraction)
                                }
                            }
                        },
                    )
                }
                .pointerInput(path) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var furthest = 0f
                        var lastY = down.position.y
                        var lastTime = down.uptimeMillis
                        var velocity = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            if (pressed.size >= 2) {
                                // A pinch: zoom about the centroid and follow the fingers.
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val previous = scale
                                val next = (previous * zoom).coerceIn(MinScale, MaxScale)
                                if (next != previous) {
                                    val centre = Offset(viewSize.width / 2f, viewSize.height / 2f)
                                    val grow = next / previous
                                    translation = clampTranslation(
                                        (translation + (centroid - centre) * (1f - grow)) * 1f + pan,
                                        next,
                                    )
                                    scale = next
                                } else {
                                    translation = clampTranslation(translation + pan, scale)
                                }
                                if (scale > MinScale + 0.01f) dismissOffset = 0f
                                event.changes.forEach { it.consume() }
                            } else {
                                val change = pressed.first()
                                val delta = change.positionChange()
                                if (delta != Offset.Zero) {
                                    if (scale > MinScale + 0.01f) {
                                        translation = clampTranslation(translation + delta, scale)
                                    } else {
                                        dismissOffset += delta.y
                                        furthest = maxOf(furthest, abs(dismissOffset))
                                        val now = change.uptimeMillis
                                        val elapsed = (now - lastTime).coerceAtLeast(1L)
                                        velocity = (change.position.y - lastY) / elapsed * 1000f
                                        lastY = change.position.y
                                        lastTime = now
                                    }
                                    change.consume()
                                }
                            }
                        }

                        if (scale <= MinScale + 0.01f) {
                            val flung = abs(velocity) > 1200f
                            if (furthest > dismissDistance || flung) {
                                onDismiss()
                            } else {
                                val from = dismissOffset
                                scope.launch {
                                    animate(from, 0f, animationSpec = tween(200)) { value, _ -> dismissOffset = value }
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            image?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = translation.x
                            translationY = translation.y - dismissOffset
                        },
                )
            }

            if (chromeVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onDismiss() })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close photo",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

/** The picture is drawn at twice the screen width at most: enough detail without a large bitmap. */
private fun decodeForViewer(path: String): ImageBitmap? = runCatching {
    val file = File(path)
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 2048) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}.getOrNull()
