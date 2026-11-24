package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.AppDao
import io.github.yulbax.erkin.data.InstalledAppsSource
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import io.github.yulbax.erkin.data.rules.RoutingRuleRepository
import io.github.yulbax.erkin.vpn.ProbeResults
import io.github.yulbax.erkin.vpn.VpnStateRepository
import io.github.yulbax.erkin.vpn.connections.AppActivity
import io.github.yulbax.erkin.vpn.connections.ConnectionMonitor
import io.github.yulbax.erkin.vpn.connections.RuleIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

data class ConnectionApp(
    val id: String,
    val ruleApp: String,
    val name: String?,
    val path: String?,
    val channel: ConnectionType?,
    val serverId: Long?,
    val activity: AppActivity
) {
    val isService: Boolean get() = activity.isService
}

class ConnectionsViewModel(
    monitor: ConnectionMonitor,
    appDao: AppDao,
    installedApps: InstalledAppsSource,
    settings: SettingsRepository,
    profileRepository: ProfileRepository,
    vpnStateRepository: VpnStateRepository,
    private val rules: RoutingRuleRepository,
    probeResults: ProbeResults
) : ViewModel() {

    val running: StateFlow<Boolean> = vpnStateRepository.state.map { it.isActive }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000.milliseconds), false)

    val apps: StateFlow<List<ConnectionApp>> = combine(
        monitor.activity,
        appInfosOf(installedApps, appDao, settings, hideSystemApps = false)
    ) { activity, apps ->
        val lookup = AppLookup(apps)
        activity.map { app -> app.resolve(lookup.find(app.app, app.path)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000.milliseconds), emptyList())

    val serverChoices: StateFlow<ServerChoices> = serverChoicesOf(profileRepository, vpnStateRepository, probeResults)

    val ruleIndex: StateFlow<RuleIndex> = combine(rules.rules, profileRepository.groups) { own, groups ->
        RuleIndex(own.mapNotNull { it.toRule() }, groups.mapNotNull { it.appliedRules })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000.milliseconds), RuleIndex.EMPTY)

    fun rulesOf(app: String): Flow<List<RoutingRuleEntity>> = rules.rules.map { list -> list.filter { it.app == app } }

    fun deleteRule(rule: RoutingRuleEntity) {
        viewModelScope.launch(Dispatchers.IO) { rules.delete(listOf(rule)) }
    }

    private fun AppActivity.resolve(known: AppInfo?) = ConnectionApp(
        id = app,
        ruleApp = known?.packageName ?: app,
        name = known?.name,
        path = if (known != null) known.path else path.ifEmpty { null },
        channel = known?.connectionType,
        serverId = known?.serverId,
        activity = this
    )
}
