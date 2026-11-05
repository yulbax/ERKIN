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

sealed interface AppFilter {
    fun matches(app: AppInfo): Boolean

    data class Channel(val type: ConnectionType) : AppFilter {
        override fun matches(app: AppInfo): Boolean = app.connectionType == type
    }

    data class Server(val serverId: Long) : AppFilter {
        override fun matches(app: AppInfo): Boolean = app.connectionType == ConnectionType.VPN && app.serverId == serverId
    }
}

internal fun ServerChoices.boundServers(apps: List<AppInfo>): List<Pair<Long, Int>> =
    apps.mapNotNull { app -> (current(app) as? CurrentServer.Bound)?.serverId }
        .groupingBy { it }
        .eachCount()
        .toList()
        .sortedWith(compareByDescending<Pair<Long, Int>> { it.second }.thenBy { it.first })

internal fun matchesAppQuery(name: String, packageName: String, query: String): Boolean =
    query.isBlank() || name.contains(query, ignoreCase = true) || packageName.contains(query, ignoreCase = true)

internal fun filterApps(apps: List<AppInfo>, query: String, filter: AppFilter?): List<AppInfo> =
    apps.filter { app -> (filter == null || filter.matches(app)) && matchesAppQuery(app.name, app.packageName, query) }
