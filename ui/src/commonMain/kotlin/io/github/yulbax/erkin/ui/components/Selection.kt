package io.github.yulbax.erkin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.state.ToggleableState
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

class Selection(private val saved: MutableState<List<Long>>, private val existing: List<Long>) {
    val checked: Set<Long> = existing.toSet().let { present -> saved.value.filterTo(HashSet()) { it in present } }
    val active: Boolean get() = checked.isNotEmpty()

    operator fun contains(id: Long): Boolean = id in checked

    fun stateOf(ids: List<Long>): ToggleableState = when (ids.count { it in checked }) {
        0 -> ToggleableState.Off
        ids.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    fun toggle(id: Long) {
        saved.value = if (id in checked) (checked - id).toList() else (checked + id).toList()
    }

    fun toggleAll(ids: List<Long>) {
        saved.value = if (checked.containsAll(ids)) (checked - ids.toSet()).toList() else (checked + ids).toList()
    }

    fun selectAll() {
        saved.value = existing
    }

    fun select(ids: Collection<Long>) {
        saved.value = (checked + ids).toList()
    }

    fun clear() {
        saved.value = emptyList()
    }

    fun forget(ids: Collection<Long>) {
        saved.value = saved.value - ids.toSet()
    }
}

@Composable
fun rememberSelection(existing: List<Long>): Selection {
    val saved = rememberSaveable { mutableStateOf(emptyList<Long>()) }
    val selection = Selection(saved, existing)
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = selection.active,
        onBackCompleted = { selection.clear() }
    )
    return selection
}
