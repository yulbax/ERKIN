package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ServerKind
import io.github.yulbax.erkin.data.profile.SubscriptionEntity
import io.github.yulbax.erkin.data.profile.kind

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

data class ServerLabel(val name: String, val isChain: Boolean)

sealed interface CurrentServer {
    data object None : CurrentServer
    data object Main : CurrentServer
    data class Bound(val serverId: Long) : CurrentServer
}

class ServerChoices(
    val servers: List<ProfileEntity> = emptyList(),
    val subscriptions: Map<Long, SubscriptionEntity> = emptyMap()
) {
    private val byId = servers.associateBy { it.id }
    private val labels = servers.associate { it.id to labelOf(it) }

    val main: ProfileEntity? = servers.firstOrNull { it.selected }
    val vpnEnabled: Boolean = servers.isNotEmpty()
    val canChoose: Boolean = servers.size > 1

    fun current(app: AppInfo): CurrentServer = when {
        app.connectionType != ConnectionType.VPN -> CurrentServer.None
        else -> customServer(app)?.let { CurrentServer.Bound(it.id) } ?: CurrentServer.Main
    }

    fun labelFor(app: AppInfo): ServerLabel? =
        (current(app) as? CurrentServer.Bound)?.let { labels[it.serverId] }

    fun subscriptionOf(serverId: Long): Long? = byId[serverId]?.subscriptionId

    private fun customServer(app: AppInfo): ProfileEntity? =
        byId[app.serverId]?.takeUnless { it.selected }

    private fun labelOf(server: ProfileEntity): ServerLabel = when (val kind = server.kind) {
        is ServerKind.Link -> ServerLabel(server.name, isChain = false)
        is ServerKind.Chain -> ServerLabel(
            name = kind.chain?.let { byId[it.exitId] }?.name?.ifEmpty { null } ?: server.name,
            isChain = true
        )
    }
}

sealed interface ServerPickerRow {
    val key: Any

    data class Main(val name: String, val checked: Boolean) : ServerPickerRow {
        override val key: Any get() = "main"
    }

    data class Subscription(val id: Long, val name: String, val count: Int, val open: Boolean) : ServerPickerRow {
        override val key: Any get() = "subscription:$id"
    }

    data class Server(val id: Long, val name: String, val checked: Boolean, val indented: Boolean = false) : ServerPickerRow {
        override val key: Any get() = id
    }
}

internal fun serverPickerRows(
    choices: ServerChoices,
    query: String,
    expanded: Set<Long>,
    current: CurrentServer
): List<ServerPickerRow> {
    fun row(server: ProfileEntity, indented: Boolean = false) = ServerPickerRow.Server(
        id = server.id,
        name = server.name,
        checked = current == CurrentServer.Bound(server.id),
        indented = indented
    )
    val needle = query.trim()
    if (needle.isNotEmpty()) {
        return choices.servers.filter { it.name.contains(needle, ignoreCase = true) }.map { row(it) }
    }
    val main = choices.main?.let { ServerPickerRow.Main(it.name, checked = current == CurrentServer.Main) }
    return listOfNotNull(main) + serverListItems(choices.servers, choices.main?.id, choices.subscriptions).flatMap { item ->
        when (item) {
            is ServerListItem.Server -> listOf(row(item.profile))
            is ServerListItem.Subscription -> {
                val open = item.id in expanded
                val members = if (open) item.members.map { row(it, indented = true) } else emptyList()
                listOf(ServerPickerRow.Subscription(item.id, item.name, item.members.size, open)) + members
            }
        }
    }
}
