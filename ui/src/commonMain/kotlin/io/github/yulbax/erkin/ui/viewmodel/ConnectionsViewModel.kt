package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.AppDao
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import io.github.yulbax.erkin.data.rules.RoutingRuleRepository
import io.github.yulbax.erkin.vpn.VpnStateRepository
import io.github.yulbax.erkin.vpn.connections.AppActivity
import io.github.yulbax.erkin.vpn.connections.ConnectionMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    profileRepository: ProfileRepository,
    vpnStateRepository: VpnStateRepository,
    private val rules: RoutingRuleRepository,
    probeResults: ProbeResults
) : ViewModel() {

    val running: StateFlow<Boolean> = vpnStateRepository.state.map { it.isActive }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val apps: StateFlow<List<ConnectionApp>> = combine(monitor.activity, appDao.getAllApps()) { activity, apps ->
        val byId = apps.associateBy { it.packageName }
        val byPath = apps.filter { it.path != null }.associateBy { it.path }
        activity.map { app -> app.resolve(byId[app.app] ?: byPath[app.path.ifEmpty { null }]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val serverChoices: StateFlow<ServerChoices> = serverChoicesOf(profileRepository, vpnStateRepository, probeResults)

    fun rulesOf(app: String): Flow<List<RoutingRuleEntity>> = rules.rules.map { list -> list.filter { it.app == app } }

    fun deleteRule(rule: RoutingRuleEntity) {
        viewModelScope.launch(Dispatchers.IO) { rules.delete(listOf(rule)) }
    }

    private fun AppActivity.resolve(known: App?) = ConnectionApp(
        id = app,
        ruleApp = known?.packageName ?: app,
        name = known?.name,
        path = known?.path ?: path.ifEmpty { null },
        channel = known?.connectionType,
        serverId = known?.serverId,
        activity = this
    )
}
