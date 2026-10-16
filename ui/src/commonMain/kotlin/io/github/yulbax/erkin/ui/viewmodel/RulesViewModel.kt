package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import io.github.yulbax.erkin.data.rules.RoutingRuleRepository
import io.github.yulbax.erkin.geoip.GeoIpDatabase
import io.github.yulbax.erkin.geoip.GeoIpState
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.vpn.core.RuleTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesViewModel(
    private val repository: RoutingRuleRepository,
    private val settingsRepository: SettingsRepository,
    private val geoIpDatabase: GeoIpDatabase
) : ViewModel() {

    val countryAvailable: StateFlow<Boolean> = geoIpDatabase.state
        .map { it is GeoIpState.Ready }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), geoIpDatabase.state.value is GeoIpState.Ready)

    fun refreshGeoIp() {
        viewModelScope.launch(Dispatchers.IO) { geoIpDatabase.refresh() }
    }

    val rules: StateFlow<List<RoutingRuleEntity>> =
        repository.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hintSeen: StateFlow<Boolean> = settingsRepository.settings
        .map { it.rulesHintSeen }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun dismissHint() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.update { it.copy(rulesHintSeen = true) }
        }
    }

    fun save(existing: RoutingRuleEntity?, match: RuleMatch, raw: String, target: RuleTarget) {
        viewModelScope.launch(Dispatchers.IO) {
            if (existing == null) repository.add(match, raw, target) else repository.update(existing, match, raw, target)
        }
    }

    fun setTarget(rule: RoutingRuleEntity, target: RuleTarget) {
        viewModelScope.launch(Dispatchers.IO) { repository.setTarget(rule, target) }
    }

    fun delete(rules: List<RoutingRuleEntity>) {
        viewModelScope.launch(Dispatchers.IO) { repository.delete(rules) }
    }

    fun reorder(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) { repository.reorder(ids) }
    }
}
