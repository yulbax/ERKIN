package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.AppDao
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.InstalledAppsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class RuleAppChoice(
    val packageName: String,
    val name: String,
    val path: String?,
    val regular: Boolean,
    val channel: ConnectionType?
)

internal fun ruleAppChoices(installed: InstalledAppsSource, appDao: AppDao): Flow<List<RuleAppChoice>> =
    combine(installed.installedApps, appDao.getAllApps()) { apps, saved ->
        val channels = saved.associate { it.packageName to it.connectionType }
        apps.map { RuleAppChoice(it.packageName, it.name, it.path, it.isRegular, channels[it.packageName]) }
            .sortedBy { it.name.lowercase() }
    }
