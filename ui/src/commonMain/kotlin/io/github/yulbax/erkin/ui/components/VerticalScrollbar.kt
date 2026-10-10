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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.platform.scrollbarAlwaysVisible
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun VerticalScrollbar(state: ScrollState, modifier: Modifier = Modifier) {
    if (state.maxValue <= 0 || state.maxValue == Int.MAX_VALUE) return
    var dragging by remember { mutableStateOf(false) }
    var recentlyActive by remember { mutableStateOf(false) }
    val active = dragging || state.isScrollInProgress
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(TOUCH_WIDTH)
            .alpha(alpha)
    ) {
        val track = constraints.maxHeight.toFloat()
        val viewport = state.viewportSize.takeIf { it > 0 }?.toFloat() ?: track
        val content = viewport + state.maxValue
        val minThumb = with(density) { MIN_THUMB.toPx() }
        val thumb = (track * viewport / content).coerceIn(minThumb, track)
        val travel = (track - thumb).coerceAtLeast(1f)
        val offset = travel * state.value / state.maxValue

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(TOUCH_WIDTH)
                .pointerInput(state, track) {
                    detectTapGestures { position ->
                        val target = ((position.y - thumb / 2) / travel).coerceIn(0f, 1f) * state.maxValue
                        scope.launch { state.animateScrollTo(target.roundToInt()) }
                    }
                }
                .pointerInput(state, track) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false }
                    ) { change, dragAmount ->
                        change.consume()
                        state.dispatchRawDelta(dragAmount * state.maxValue / travel)
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(-with(density) { EDGE_PADDING.roundToPx() }, offset.roundToInt()) }
                    .width(thickness)
                    .height(with(density) { thumb.toDp() })
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

private val TOUCH_WIDTH = 24.dp
private val MIN_THUMB = 32.dp
private val EDGE_PADDING = 4.dp
