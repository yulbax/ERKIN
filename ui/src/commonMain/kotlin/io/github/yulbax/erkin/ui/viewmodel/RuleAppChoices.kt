package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.InstalledAppsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class RuleAppChoice(
    val packageName: String,
    val name: String,
    val path: String?,
    val regular: Boolean
)

internal fun ruleAppChoices(installed: InstalledAppsSource): Flow<List<RuleAppChoice>> =
    installed.installedApps.map { apps ->
        apps.map { RuleAppChoice(it.packageName, it.name, it.path, it.isRegular) }.sortedBy { it.name.lowercase() }
    }
