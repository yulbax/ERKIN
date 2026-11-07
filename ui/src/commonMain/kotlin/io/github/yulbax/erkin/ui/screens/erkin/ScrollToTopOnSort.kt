package io.github.yulbax.erkin.ui.screens.erkin

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

internal class PendingSort(private val order: List<Long>, private val shownBefore: List<Long>) {
    fun isShownIn(shown: List<Long>): Boolean {
        val visible = shown.toSet()
        return order.filter { it in visible } == shown
    }

    fun isOutdatedBy(shown: List<Long>): Boolean = shown != shownBefore && !isShownIn(shown)
}

@Composable
internal fun rememberScrollToTopAfterSort(state: LazyListState, shown: List<Long>): (Deferred<List<Long>?>) -> Unit {
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(shown)
    var pending by remember { mutableStateOf<PendingSort?>(null) }
    LaunchedEffect(pending, shown) {
        val sort = pending ?: return@LaunchedEffect
        when {
            sort.isShownIn(shown) -> {
                pending = null
                state.scrollToItem(0)
            }
            sort.isOutdatedBy(shown) -> pending = null
        }
    }
    return remember(scope) { { sort -> scope.launch { pending = sort.await()?.let { PendingSort(it, current) } } } }
}
