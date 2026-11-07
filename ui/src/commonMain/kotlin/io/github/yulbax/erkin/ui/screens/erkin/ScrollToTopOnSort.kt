package io.github.yulbax.erkin.ui.screens.erkin

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

@Composable
internal fun rememberScrollToTopAfterSort(state: LazyListState, shown: List<Long>): (Deferred<List<Long>?>) -> Unit {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<Long>?>(null) }
    LaunchedEffect(pending, shown) {
        val sorted = pending ?: return@LaunchedEffect
        val visible = shown.toSet()
        if (sorted.filter { it in visible } == shown) {
            pending = null
            state.scrollToItem(0)
        }
    }
    return remember(scope) { { sort -> scope.launch { pending = sort.await() } } }
}
