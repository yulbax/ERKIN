package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.ConnectionType

sealed interface AppsMode {
    data object Browsing : AppsMode
    data class Selecting(val packages: Set<String> = emptySet()) : AppsMode {
        fun toggled(packageName: String): Selecting =
            Selecting(if (packageName in packages) packages - packageName else packages + packageName)

        fun canBlock(totalApps: Int): Boolean = packages.size < totalApps
    }

    fun rowMode(packageName: String): AppRowMode = when (this) {
        Browsing -> AppRowMode.Browsing
        is Selecting -> AppRowMode.Selecting(checked = packageName in packages)
    }
}

sealed interface AppRowMode {
    data object Browsing : AppRowMode
    data class Selecting(val checked: Boolean) : AppRowMode
}

internal fun filterApps(apps: List<AppInfo>, query: String, channel: ConnectionType?): List<AppInfo> =
    apps.filter { app ->
        (channel == null || app.connectionType == channel) &&
            (query.isBlank() ||
                app.name.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true))
    }
