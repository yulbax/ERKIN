package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.AppDao
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.InstalledAppsSource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine

data class RuleAppChoice(
    val packageName: String,
    val name: String,
    val path: String?,
    val regular: Boolean,
    val bypassed: Boolean = false
)

internal fun ViewModel.ruleAppChoicesOf(installed: InstalledAppsSource, appDao: AppDao): StateFlow<List<RuleAppChoice>> =
    ruleAppChoices(installed, appDao).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

private fun ruleAppChoices(installed: InstalledAppsSource, appDao: AppDao): Flow<List<RuleAppChoice>> =
    combine(installed.installedApps, appDao.getAllApps()) { apps, saved ->
        val bypassed = saved.filter { it.connectionType == ConnectionType.BYPASS }.mapTo(HashSet()) { it.packageName }
        apps.map { RuleAppChoice(it.packageName, it.name, it.path, it.isRegular, it.packageName in bypassed) }
            .sortedBy { it.name.lowercase() }
    }

private const val STOP_TIMEOUT_MS = 5_000L
