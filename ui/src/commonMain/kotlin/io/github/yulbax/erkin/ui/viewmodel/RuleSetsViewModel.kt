package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.rules.RoutingRuleRepository
import io.github.yulbax.erkin.rulelist.RuleListDatabase
import io.github.yulbax.erkin.rulelist.RuleListState
import io.github.yulbax.erkin.vpn.VpnCommandBus
import io.github.yulbax.erkin.vpn.core.RuleListSource
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.vpn.core.RuleTarget
import io.github.yulbax.erkin.vpn.core.RuleValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RuleSetsViewModel(
    private val ruleListDatabase: RuleListDatabase,
    private val ruleRepository: RoutingRuleRepository,
    private val commandBus: VpnCommandBus
) : ViewModel() {

    val states: StateFlow<Map<String, RuleListState>> =
        ruleListDatabase.states.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val usage: StateFlow<Map<String, Int>> = ruleRepository.rules
        .map { rules -> rules.mapNotNull { it.toRule()?.let(RuleLists::url) }.groupingBy { it }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun normalizeLink(raw: String): String? = RuleValues.Link.normalize(raw)

    fun add(source: RuleListSource, name: String? = null, onResult: (Throwable?) -> Unit) {
        viewModelScope.launch {
            val result = ruleListDatabase.add(source, name?.trim()?.takeIf { it.isNotEmpty() })
            if (result.isSuccess) commandBus.reload()
            onResult(result.exceptionOrNull())
        }
    }

    fun update(url: String, onResult: (Throwable?) -> Unit) {
        viewModelScope.launch {
            val result = ruleListDatabase.download(url)
            if (result.isSuccess) commandBus.reload()
            onResult(result.exceptionOrNull())
        }
    }

    fun rename(url: String, name: String) {
        viewModelScope.launch { ruleListDatabase.rename(url, name) }
    }

    fun delete(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ruleRepository.deleteUsing(url)
            ruleListDatabase.delete(url)
            commandBus.reload()
        }
    }

    fun createRule(match: RuleMatch, raw: String, target: RuleTarget) {
        viewModelScope.launch(Dispatchers.IO) { ruleRepository.add(match, raw, target) }
    }
}
