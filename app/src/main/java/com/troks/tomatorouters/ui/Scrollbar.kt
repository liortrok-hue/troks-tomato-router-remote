package com.troks.tomatorouters.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Material-style scrollbar on a list's end edge. It appears while the list scrolls and fades
 * out about a second after it stops. While shown, the thumb can be dragged to jump through
 * the list. Nothing is drawn when everything fits on one screen.
 */
@Composable
fun ListScrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    val active = state.isScrollInProgress || dragging
    LaunchedEffect(active) {
        if (active) shown = true else { delay(1200); shown = false }
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(if (shown) 150 else 400), label = "scrollbarAlpha")
    val width by animateDpAsState(if (dragging) 8.dp else 4.dp, label = "scrollbarWidth")
    val cs = MaterialTheme.colorScheme
    val color = if (dragging) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.6f)

    val info = state.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo
    if (total == 0 || visible.isEmpty() || (!state.canScrollForward && !state.canScrollBackward)) return

    val first = visible.first()
    val passed = first.index + (-first.offset.toFloat() / first.size.coerceAtLeast(1)).coerceIn(0f, 1f)
    val thumb = (visible.size.toFloat() / total).coerceIn(0.06f, 1f)
    val pos = (passed / (total - visible.size).coerceAtLeast(1)).coerceIn(0f, 1f)

    Box(
        modifier
            .width(20.dp)
            // only grabbable while visible, so an invisible bar never steals a swipe
            .then(
                if (shown) Modifier.pointerInput(total) {
                    fun jump(y: Float) = scope.launch {
                        state.scrollToItem(((y / size.height).coerceIn(0f, 1f) * (total - 1)).roundToInt())
                    }
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true; jump(it.y) },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ -> change.consume(); jump(change.position.y) }
                } else Modifier
            )
            .drawBehind {
                if (alpha == 0f) return@drawBehind
                val w = width.toPx()
                val h = size.height * thumb
                val edge = 4.dp.toPx()
                val x = if (layoutDirection == LayoutDirection.Ltr) size.width - w - edge else edge
                drawRoundRect(
                    color.copy(alpha = color.alpha * alpha),
                    topLeft = Offset(x, (size.height - h) * pos),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w / 2),
                )
            },
    )
}
