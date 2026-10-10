package io.github.yulbax.erkin.data

import kotlinx.coroutines.flow.StateFlow

data class InstalledApp(
    val packageName: String,
    val name: String,
    val isSystemApp: Boolean,
    val isLaunchable: Boolean,
    val path: String? = null
)

interface InstalledAppsSource {
    val installedApps: StateFlow<List<InstalledApp>>
    val isLoading: StateFlow<Boolean>
    val error: StateFlow<String?>

    fun retry()
}
