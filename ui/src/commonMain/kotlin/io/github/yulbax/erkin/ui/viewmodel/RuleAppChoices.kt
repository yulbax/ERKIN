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
    val bypassed: Boolean = false
)

internal fun ruleAppChoices(installed: InstalledAppsSource, appDao: AppDao): Flow<List<RuleAppChoice>> =
    combine(installed.installedApps, appDao.getAllApps()) { apps, saved ->
        val bypassed = saved.filter { it.connectionType == ConnectionType.BYPASS }.mapTo(HashSet()) { it.packageName }
        apps.map { RuleAppChoice(it.packageName, it.name, it.path, it.isRegular, it.packageName in bypassed) }
            .sortedBy { it.name.lowercase() }
    }
