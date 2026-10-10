package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.AppDao
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.InstalledApp
import io.github.yulbax.erkin.data.InstalledAppsSource
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.data.profile.ProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppInfo(
    val packageName: String,
    val name: String,
    val isSystemApp: Boolean,
    val isLaunchable: Boolean = true,
    val connectionType: ConnectionType = ConnectionType.VPN,
    val path: String? = null
)

class AppsViewModel(
    private val appDao: AppDao,
    private val installedAppsRepository: InstalledAppsSource,
    private val settingsRepository: SettingsRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    val hasServers: StateFlow<Boolean> = profileRepository.profiles
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

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

    val allApps: StateFlow<List<AppInfo>> = combine(
        installedAppInfos,
        appDao.getAllApps()
    ) { installed, saved ->
        mergeSaved(installed, saved)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setConnectionType(packageName: String, name: String, isSystemApp: Boolean, type: ConnectionType) {
        viewModelScope.launch {
            appDao.setConnectionTypes(listOf(App(packageName, name, isSystemApp, type)))
        }
    }

    fun setAllConnectionTypes(targets: List<AppInfo>, type: ConnectionType) {
        if (targets.isEmpty()) return
        viewModelScope.launch {
            appDao.setConnectionTypes(targets.map { App(it.packageName, it.name, it.isSystemApp, type) })
        }
    }

    fun restoreConnectionTypes(snapshot: List<AppInfo>) {
        if (snapshot.isEmpty()) return
        viewModelScope.launch {
            appDao.setConnectionTypes(
                snapshot.map { App(it.packageName, it.name, it.isSystemApp, it.connectionType) }
            )
        }
    }

    fun retry() {
        installedAppsRepository.retry()
    }

    private fun mergeSaved(installed: List<AppInfo>, saved: List<App>): List<AppInfo> {
        val savedTypes = saved.associate { it.packageName to it.connectionType }
        return installed.map { app ->
            savedTypes[app.packageName]?.let { app.copy(connectionType = it) } ?: app
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
