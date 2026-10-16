package io.github.yulbax.erkin.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.platform.scrollbarAlwaysVisible
import io.github.yulbax.erkin.ui.platform.scrollbarEdgeInset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun VerticalScrollbar(state: ScrollState, modifier: Modifier = Modifier) {
    if (state.maxValue <= 0 || state.maxValue == Int.MAX_VALUE) return
    Scrollbar(
        scrolled = state.value.toFloat(),
        maxScroll = state.maxValue.toFloat(),
        viewport = state.viewportSize.toFloat(),
        isScrolling = state.isScrollInProgress,
        scrollTo = { state.animateScrollTo(it.roundToInt()) },
        scrollBy = { state.dispatchRawDelta(it) },
        modifier = modifier
    )
}

data class ScrollbarSection(val label: String, val firstIndex: Int)

@Composable
fun VerticalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    sections: List<ScrollbarSection> = emptyList()
) {
    val metrics = state.scrollMetrics() ?: return
    Scrollbar(
        scrolled = metrics.scrolled,
        maxScroll = metrics.maxScroll,
        viewport = metrics.viewport,
        isScrolling = state.isScrollInProgress,
        scrollTo = { target ->
            val index = (target / metrics.itemSize).toInt()
            state.scrollToItem(index, (target - index * metrics.itemSize).roundToInt())
        },
        scrollBy = { state.dispatchRawDelta(it) },
        modifier = modifier,
        label = sections.sectionAt(metrics.anchorIndex)?.label,
        onRelease = {
            val anchor = state.scrollMetrics()?.anchorIndex
            if (anchor != null) sections.sectionAt(anchor)?.let { state.animateScrollToItem(it.firstIndex) }
        }
    )
}

private class LazyScrollMetrics(
    val itemSize: Float,
    val viewport: Float,
    val maxScroll: Float,
    val scrolled: Float,
    val anchorIndex: Int
)

private fun LazyListState.scrollMetrics(): LazyScrollMetrics? {
    val layout = layoutInfo
    val visible = layout.visibleItemsInfo
    if (visible.isEmpty()) return null
    val itemSize = visible.sumOf { it.size }.toFloat() / visible.size
    val viewport = (layout.viewportEndOffset - layout.viewportStartOffset).toFloat()
    val maxScroll = itemSize * layout.totalItemsCount - viewport
    if (maxScroll <= 0f) return null
    val scrolled = (firstVisibleItemIndex * itemSize + firstVisibleItemScrollOffset).coerceIn(0f, maxScroll)
    val anchorIndex = (scrolled / maxScroll * (layout.totalItemsCount - 1)).roundToInt()
    return LazyScrollMetrics(itemSize, viewport, maxScroll, scrolled, anchorIndex)
}

private fun List<ScrollbarSection>.sectionAt(index: Int): ScrollbarSection? = lastOrNull { it.firstIndex <= index }

@Composable
private fun Scrollbar(
    scrolled: Float,
    maxScroll: Float,
    viewport: Float,
    isScrolling: Boolean,
    scrollTo: suspend (Float) -> Unit,
    scrollBy: (Float) -> Unit,
    modifier: Modifier,
    label: String? = null,
    onRelease: suspend () -> Unit = {}
) {
    var dragging by remember { mutableStateOf(false) }
    var recentlyActive by remember { mutableStateOf(false) }
    val active = dragging || isScrolling
    LaunchedEffect(active) {
        if (active) {
            recentlyActive = true
        } else {
            delay(1_500)
            recentlyActive = false
        }
    }
    val visible = scrollbarAlwaysVisible || active || recentlyActive
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "scrollbarAlpha")
    val thickness by animateDpAsState(if (dragging) 8.dp else 4.dp, label = "scrollbarThickness")
    val color = if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val currentScrollTo by rememberUpdatedState(scrollTo)
    val currentScrollBy by rememberUpdatedState(scrollBy)
    val currentMaxScroll by rememberUpdatedState(maxScroll)
    val currentOnRelease by rememberUpdatedState(onRelease)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(TOUCH_WIDTH)
            .alpha(alpha)
    ) {
        val track = constraints.maxHeight.toFloat()
        val shown = viewport.takeIf { it > 0f } ?: track
        val content = shown + maxScroll
        val minThumb = with(density) { MIN_THUMB.toPx() }
        val thumb = (track * shown / content).coerceIn(minThumb, track)
        val travel = (track - thumb).coerceAtLeast(1f)
        val offset = travel * scrolled / maxScroll
        val currentThumb by rememberUpdatedState(thumb)
        val currentTravel by rememberUpdatedState(travel)

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(TOUCH_WIDTH)
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        val target = ((position.y - currentThumb / 2) / currentTravel).coerceIn(0f, 1f) * currentMaxScroll
                        scope.launch {
                            currentScrollTo(target)
                            currentOnRelease()
                        }
                    }
                }
                .pointerInput(Unit) {
                    try {
                        detectVerticalDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                scope.launch { currentOnRelease() }
                            },
                            onDragCancel = { dragging = false }
                        ) { change, dragAmount ->
                            change.consume()
                            currentScrollBy(dragAmount * currentMaxScroll / currentTravel)
                        }
                    } finally {
                        dragging = false
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(-with(density) { (EDGE_PADDING + scrollbarEdgeInset).roundToPx() }, offset.roundToInt()) }
                    .width(thickness)
                    .height(with(density) { thumb.toDp() })
                    .clip(CircleShape)
                    .background(color)
            )
        }
        if (dragging && label != null) {
            val bubble = with(density) { BUBBLE_SIZE.roundToPx() }
            val gap = with(density) { BUBBLE_GAP.roundToPx() }
            val top = (offset + thumb / 2 - bubble / 2).roundToInt().coerceIn(0, (track.roundToInt() - bubble).coerceAtLeast(0))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints.fixed(bubble, bubble))
                        layout(0, 0) { placeable.place(-bubble - gap, top) }
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

private val TOUCH_WIDTH = 24.dp
private val MIN_THUMB = 32.dp
private val EDGE_PADDING = 4.dp
private val BUBBLE_SIZE = 56.dp
private val BUBBLE_GAP = 8.dp
