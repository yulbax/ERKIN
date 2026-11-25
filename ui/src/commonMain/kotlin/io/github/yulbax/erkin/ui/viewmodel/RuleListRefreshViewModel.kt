package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.rulelist.RuleListDatabase
import io.github.yulbax.erkin.vpn.VpnCommandBus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class RuleListRefreshViewModel(
    ruleListDatabase: RuleListDatabase,
    profileRepository: ProfileRepository,
    commandBus: VpnCommandBus
) : ViewModel() {
    init {
        viewModelScope.launch {
            if (ruleListDatabase.refreshStale()) commandBus.reload()
            profileRepository.groups
                .map { groups -> groups.filter { it.rulesEnabled }.flatMap { it.subscriptionRules?.managedLists.orEmpty() }.toSet() }
                .distinctUntilChanged()
                .collectLatest { lists -> if (ruleListDatabase.refreshManaged(lists)) commandBus.reload() }
        }
    }
}
