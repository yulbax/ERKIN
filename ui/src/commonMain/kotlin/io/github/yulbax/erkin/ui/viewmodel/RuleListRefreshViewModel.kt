package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.rulelist.RuleListDatabase
import io.github.yulbax.erkin.vpn.VpnCommandBus
import kotlinx.coroutines.launch

class RuleListRefreshViewModel(
    ruleListDatabase: RuleListDatabase,
    commandBus: VpnCommandBus
) : ViewModel() {
    init {
        viewModelScope.launch {
            if (ruleListDatabase.refreshStale()) commandBus.reload()
        }
    }
}
