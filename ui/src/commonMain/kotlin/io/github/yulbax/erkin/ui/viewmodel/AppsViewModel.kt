package io.github.yulbax.erkin.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.AppDao
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.InstalledApp
import io.github.yulbax.erkin.data.InstalledAppsSource
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.data.profile.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppInfo(
    val packageName: String,
    val name: String,
    val isSystemApp: Boolean,
    val isLaunchable: Boolean = true,
    val connectionType: ConnectionType = ConnectionType.VPN,
    val path: String? = null,
    val serverId: Long? = null
)

class AppsViewModel(
    private val appDao: AppDao,
    private val installedAppsRepository: InstalledAppsSource,
    private val settingsRepository: SettingsRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    val serverChoices: StateFlow<ServerChoices> = profileRepository.serverChoices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerChoices())

    val appsHintSeen: StateFlow<Boolean> = settingsRepository.settings
        .map { it.appsHintSeen }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun dismissAppsHint() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(appsHintSeen = true) }
        }
    }

    val isLoading: StateFlow<Boolean> = installedAppsRepository.isLoading
    val error: StateFlow<String?> = installedAppsRepository.error

    private val installedAppInfos = installedAppsRepository.installedApps.map { installed ->
        installed.map { it.toAppInfo() }
    }

    val apps: StateFlow<List<AppInfo>> = combine(
        installedAppInfos,
        appDao.getAllApps(),
        settingsRepository.settings.map { it.showSystemApps }
    ) { installed, saved, showSystem ->
        mergeSaved(installed.filter { showSystem || !it.isSystemApp || it.isLaunchable }, saved)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var query by mutableStateOf("")
        private set

    private val _filter = MutableStateFlow<ConnectionType?>(null)
    val filter: StateFlow<ConnectionType?> = _filter

    private val _mode = MutableStateFlow<AppsMode>(AppsMode.Browsing)
    val mode: StateFlow<AppsMode> = _mode

    val visibleApps: StateFlow<List<AppInfo>> = combine(
        apps,
        snapshotFlow { query },
        filter,
        ::filterApps
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateQuery(value: String) {
        query = value
    }

    fun selectFilter(channel: ConnectionType?) {
        _filter.value = channel
    }

    fun startSelecting(packageName: String? = null) {
        _mode.value = AppsMode.Selecting(setOfNotNull(packageName))
    }

    fun toggleSelected(packageName: String) {
        _mode.update { mode -> (mode as? AppsMode.Selecting)?.toggled(packageName) ?: mode }
    }

    fun selectAllVisible() {
        _mode.value = AppsMode.Selecting(visibleApps.value.mapTo(HashSet()) { it.packageName })
    }

    fun stopSelecting() {
        _mode.value = AppsMode.Browsing
    }

    fun setConnectionType(app: AppInfo, type: ConnectionType) {
        viewModelScope.launch {
            appDao.setConnectionTypes(listOf(app.stored(type)))
        }
    }

    fun setServer(app: AppInfo, serverId: Long?) {
        viewModelScope.launch {
            appDao.setConnectionTypes(listOf(app.stored(ConnectionType.VPN, serverId)))
        }
    }

    fun setConnectionTypeForSelection(type: ConnectionType): List<AppInfo> =
        changeSelection { it.stored(type) }

    fun setServerForSelection(serverId: Long?): List<AppInfo> =
        changeSelection { it.stored(ConnectionType.VPN, serverId) }

    fun restoreConnectionTypes(snapshot: List<AppInfo>) {
        if (snapshot.isEmpty()) return
        viewModelScope.launch {
            appDao.setConnectionTypes(snapshot.map { it.stored(it.connectionType) })
        }
    }

    private fun changeSelection(change: (AppInfo) -> App): List<AppInfo> {
        val packages = (_mode.value as? AppsMode.Selecting)?.packages.orEmpty()
        val snapshot = apps.value.filter { it.packageName in packages }
        stopSelecting()
        viewModelScope.launch {
            appDao.setConnectionTypes(snapshot.map(change))
        }
        return snapshot
    }

    private fun AppInfo.stored(
        type: ConnectionType,
        server: Long? = serverId.takeIf { type == ConnectionType.VPN }
    ) = App(packageName, name, isSystemApp, type, serverId = server)

    fun retry() {
        installedAppsRepository.retry()
    }

    private fun mergeSaved(installed: List<AppInfo>, saved: List<App>): List<AppInfo> {
        val savedApps = saved.associateBy { it.packageName }
        return installed.map { app ->
            savedApps[app.packageName]?.let { app.copy(connectionType = it.connectionType, serverId = it.serverId) } ?: app
        }
    }

    private fun InstalledApp.toAppInfo() = AppInfo(
        packageName = packageName,
        name = name,
        isSystemApp = isSystemApp,
        isLaunchable = isLaunchable,
        path = path
    )
}
