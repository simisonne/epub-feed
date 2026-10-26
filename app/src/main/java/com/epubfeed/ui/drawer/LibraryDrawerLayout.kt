package com.epubfeed.ui.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Width of the strip beside the system back gesture that opens the library. */
private val OpenBandWidth = 60.dp

/** Past this speed a drag is a fling, not a placement. */
private const val FLING_VELOCITY = 900f

/**
 * What the library drawer is heading to.
 *
 * The parent sets [isOpen] (a tap on the avatar, the back gesture); the layout animates to it. A
 * drag writes it once the finger is lifted, so the drawer follows the finger in between.
 */
@Stable
class LibraryDrawerState(open: Boolean = false) {
    var isOpen by mutableStateOf(open)
}

/**
 * The library drawer, dragged out by hand.
 *
 * `ModalNavigationDrawer` cannot be used here: its own gesture is a parent of the tab pager, the
 * pager claims every horizontal drag first, and the drawer consequently pops open only when this
 * app asks it to. So the sheet, the scrim and both drags are owned here, and the sheet is
 * positioned from the drag offset on every frame, which is exactly what it did before the tabs
 * were a pager: it comes out under the finger and settles where it is let go.
 *
 * The opening band starts after the system back gesture inset, because a drag that begins in that
 * strip closes the activity before the app ever sees it.
 */
@Composable
fun LibraryDrawerLayout(
    state: LibraryDrawerState,
    modifier: Modifier = Modifier,
    drawerWidth: Dp = 310.dp,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val widthPx = with(density) { drawerWidth.toPx() }
    val scope = rememberCoroutineScope()

    // 0 is closed, 1 is fully out. While a finger is down the value is [dragValue], so the sheet
    // tracks the touch with no animation in between.
    val progress = remember { Animatable(if (state.isOpen) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(if (state.isOpen) 1f else 0f) }

    val shown = if (dragging) dragValue else progress.value

    LaunchedEffect(state.isOpen) {
        val target = if (state.isOpen) 1f else 0f
        if (!dragging && progress.value != target) {
            progress.animateTo(target, tween(240))
        }
    }

    fun beginDrag() {
        if (dragging) return
        dragging = true
        dragValue = progress.value
    }

    /** Snaps the animator to where the finger left the sheet, then settles on one side. */
    fun endDrag(velocity: Float) {
        if (!dragging) return
        val from = dragValue
        val target = when {
            velocity > FLING_VELOCITY -> 1f
            velocity < -FLING_VELOCITY -> 0f
            from > 0.5f -> 1f
            else -> 0f
        }
        dragging = false
        scope.launch {
            progress.snapTo(from)
            val next = target >= 1f
            // Only writes the state when it actually changes, when it does the effect above runs
            // the settle animation; otherwise the animation is started here.
            if (state.isOpen == next) progress.animateTo(target, tween(240)) else state.isOpen = next
        }
    }

    // Where the system back gesture ends: a swipe starting inside that strip never reaches the app.
    val view = LocalView.current
    var gestureInset by remember { mutableStateOf(24.dp) }
    LaunchedEffect(view) {
        val insets = ViewCompat.getRootWindowInsets(view)
        val px = insets?.getInsets(WindowInsetsCompat.Type.systemGestures())?.left ?: 0
        gestureInset = with(density) { px.toDp() }
    }
    val bandPx = with(density) { gestureInset.toPx() }
    val bandWidthPx = with(density) { OpenBandWidth.toPx() }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    // Keyed on the drawer being closed, never on the drag itself: rekeying while a
                    // gesture is in flight cancels the very gesture that started the drag.
                    if (state.isOpen) Modifier else Modifier.drawerOpenDrag(
                        enabled = true,
                        startPx = bandPx,
                        widthPx = bandWidthPx,
                        onBegin = { beginDrag() },
                        onDelta = { delta -> dragValue = (dragValue + delta / widthPx).coerceIn(0f, 1f) },
                        onEnd = { velocity -> endDrag(velocity) },
                    ),
                ),
        ) {
            content()
        }

        if (shown > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f * shown.coerceIn(0f, 1f)))
                    .pointerInput(Unit) {
                        detectTapGestures {
                            if (!dragging) state.isOpen = false
                        }
                    },
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
                    .offset { IntOffset((-widthPx * (1f - shown)).roundToInt(), 0) }
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            // Only a leftward drag anywhere on the sheet closes it.
                            if (delta < 0f) {
                                beginDrag()
                                dragValue = (dragValue + delta / widthPx).coerceIn(0f, 1f)
                            }
                        },
                        onDragStopped = { velocity -> endDrag(velocity) },
                    ),
            ) {
                drawerContent()
            }
        }
    }
}

/**
 * Feeds the drawer from a drag that starts in the band beside the system back strip.
 *
 * Read in the initial pointer pass so the pager below never takes the gesture, but only once the
 * movement is clearly horizontal and rightward: a vertical drag stays a scroll, and a leftward one
 * stays a tab swipe. Taps are never consumed, so posts under the band keep working.
 */
private fun Modifier.drawerOpenDrag(
    enabled: Boolean,
    startPx: Float,
    widthPx: Float,
    onBegin: () -> Unit,
    onDelta: (Float) -> Unit,
    onEnd: (Float) -> Unit,
): Modifier = if (!enabled) this else this.pointerInput(enabled, startPx, widthPx) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.position.x < startPx || down.position.x > startPx + widthPx) return@awaitEachGesture
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var dx = 0f
        var dy = 0f
        var owned = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val delta = change.positionChange()
            dx += delta.x
            dy += delta.y
            if (!owned) {
                val horizontal = abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy)
                if (horizontal && dx > 0f) {
                    owned = true
                    onBegin()
                } else if (horizontal || abs(dy) > viewConfiguration.touchSlop) {
                    break
                }
            }
            if (owned) {
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                onDelta(delta.x)
            }
            if (!change.pressed) {
                if (owned) onEnd(tracker.calculateVelocity().x)
                break
            }
        }
    }
}
