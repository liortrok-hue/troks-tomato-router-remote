package com.troks.tomatorouters.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Material fast-scroll handle on a list's end edge. It appears while the list scrolls and
 * fades out about a second after it stops. While shown, the handle (a tab with up/down
 * arrows, with a finger-sized touch area) can be dragged to move through the list.
 * Nothing is drawn when everything fits on one screen.
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
    val cs = MaterialTheme.colorScheme
    val handleColor by animateColorAsState(if (dragging) cs.primary else cs.secondaryContainer, label = "handleColor")
    val iconColor by animateColorAsState(if (dragging) cs.onPrimary else cs.onSecondaryContainer, label = "handleIcon")

    val info = state.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo
    if (total == 0 || visible.isEmpty() || (!state.canScrollForward && !state.canScrollBackward)) return

    // Items differ in height, so counting visible items would make the handle jump.
    // Estimate in pixels with an average item height taken once per list size.
    val first = visible.first()
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    val avg = remember(total) { visible.sumOf { it.size }.toFloat() / visible.size + info.mainAxisItemSpacing }
    val scrollRange = (avg * total - viewport).coerceAtLeast(1f)
    val pos = when {
        !state.canScrollBackward -> 0f
        !state.canScrollForward -> 1f
        else -> ((first.index * avg - first.offset) / scrollRange).coerceIn(0f, 1f)
    }
    // while dragging, the handle follows the finger exactly
    var dragPos by remember { mutableFloatStateOf(0f) }
    // the drag handler lives across recompositions; read current values through these
    val posNow = rememberUpdatedState(pos)
    val rangeNow = rememberUpdatedState(scrollRange)
    val avgNow = rememberUpdatedState(avg)
    val shownPos = if (dragging) dragPos else pos

    BoxWithConstraints(modifier.width(32.dp).fillMaxHeight()) {
        val density = LocalDensity.current
        val handleH = 56.dp
        val travelPx = with(density) { (maxHeight - handleH).toPx() }.coerceAtLeast(1f)

        Surface(
            color = handleColor,
            shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 4.dp, bottomEnd = 4.dp),
            shadowElevation = if (dragging) 4.dp else 2.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = with(density) { (travelPx * shownPos).toDp() })
                .size(width = 24.dp, height = handleH)
                .graphicsLayer { this.alpha = alpha },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.UnfoldMore, null, Modifier.size(20.dp), tint = iconColor)
            }
        }
        // touch layer on top of the handle (a Surface would block touches to anything under it)
        Box(
            Modifier
                .matchParentSize()
                // only grabbable while visible, so an invisible handle never steals a swipe
                .then(
                    if (shown) Modifier.pointerInput(total, travelPx) {
                        var grabbed = false
                        fun scrollTo(fraction: Float) = scope.launch {
                            val target = fraction * rangeNow.value
                            val index = (target / avgNow.value).toInt().coerceIn(0, total - 1)
                            state.scrollToItem(index, (target - index * avgNow.value).toInt())
                        }
                        detectVerticalDragGestures(
                            onDragStart = { at ->
                                // only a touch on (or right next to) the handle grabs it
                                val top = travelPx * posNow.value
                                val slop = 24.dp.toPx()
                                grabbed = at.y in (top - slop)..(top + handleH.toPx() + slop)
                                if (grabbed) { dragPos = posNow.value; dragging = true }
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, dy ->
                            if (!grabbed) return@detectVerticalDragGestures
                            change.consume()
                            dragPos = (dragPos + dy / travelPx).coerceIn(0f, 1f)
                            scrollTo(dragPos)
                        }
                    } else Modifier
                )
        )
    }
}
