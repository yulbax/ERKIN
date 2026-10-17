package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import io.github.yulbax.erkin.data.rules.RoutingRuleRepository
import io.github.yulbax.erkin.rulelist.RuleListDatabase
import io.github.yulbax.erkin.rulelist.RuleListState
import io.github.yulbax.erkin.vpn.VpnCommandBus
import io.github.yulbax.erkin.vpn.core.RuleListSource
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.vpn.core.RuleTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesViewModel(
    private val repository: RoutingRuleRepository,
    private val settingsRepository: SettingsRepository,
    private val ruleListDatabase: RuleListDatabase,
    private val commandBus: VpnCommandBus
) : ViewModel() {

    val rules: StateFlow<List<RoutingRuleEntity>> =
        repository.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val listStates: StateFlow<Map<String, RuleListState>> =
        ruleListDatabase.states.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val hintSeen: StateFlow<Boolean> = settingsRepository.settings
        .map { it.rulesHintSeen }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun dismissHint() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.update { it.copy(rulesHintSeen = true) }
        }
    }

    fun save(
        existing: RoutingRuleEntity?,
        match: RuleMatch,
        raw: String,
        target: RuleTarget,
        onListResult: (Throwable?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = if (existing == null) repository.add(match, raw, target) else repository.update(existing, match, raw, target)
            if (!saved) return@launch
            pruneLists()
            val source = match.normalize(raw)?.let { RuleLists.source(match, it) } ?: return@launch
            if (ruleListDatabase.installed(source.url) == null) download(source, onListResult)
        }
    }

    fun download(source: RuleListSource, onResult: (Throwable?) -> Unit) {
        viewModelScope.launch {
            val result = ruleListDatabase.download(source)
            if (result.isSuccess) commandBus.reload()
            onResult(result.exceptionOrNull())
        }
    }

    fun setTarget(rule: RoutingRuleEntity, target: RuleTarget) {
        viewModelScope.launch(Dispatchers.IO) { repository.setTarget(rule, target) }
    }

    fun delete(rules: List<RoutingRuleEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(rules)
            pruneLists()
        }
    }

    fun reorder(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) { repository.reorder(ids) }
    }

    private suspend fun pruneLists() {
        val sources = repository.rules.first().mapNotNull { it.toRule()?.let(RuleLists::source)?.url }
        ruleListDatabase.prune(sources.toSet())
    }
}
