package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.byedpi.ByeDpiSiteLists
import io.github.yulbax.erkin.data.byedpi.SiteList
import io.github.yulbax.erkin.data.byedpi.SiteListDraft
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SiteListsViewModel(private val siteLists: ByeDpiSiteLists) : ViewModel() {

    val lists: StateFlow<List<SiteList>> = siteLists.lists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setActive(id: Long, active: Boolean) {
        viewModelScope.launch { siteLists.setActive(id, active) }
    }

    fun add(draft: SiteListDraft) {
        viewModelScope.launch { siteLists.add(draft) }
    }

    fun edit(id: Long, draft: SiteListDraft) {
        viewModelScope.launch { siteLists.edit(id, draft) }
    }

    fun reset(id: Long) {
        viewModelScope.launch { siteLists.reset(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { siteLists.delete(id) }
    }
}
