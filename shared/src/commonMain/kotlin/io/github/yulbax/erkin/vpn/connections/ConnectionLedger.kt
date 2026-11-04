package io.github.yulbax.erkin.vpn.connections

import io.github.yulbax.erkin.data.ConnectionType

data class AddressActivity(
    val name: String,
    val active: Int,
    val connections: Int,
    val up: Long,
    val down: Long,
    val lastSeen: Long
)

data class HostActivity(
    val host: String,
    val route: ConnectionRoute,
    val origin: RouteOrigin,
    val purpose: ServicePurpose?,
    val active: Int,
    val connections: Int,
    val attempts: Int,
    val up: Long,
    val down: Long,
    val lastSeen: Long,
    val addresses: List<AddressActivity> = emptyList()
) {
    val blocked: Boolean get() = route.channel == ConnectionType.BLOCK
    val single: AddressActivity? get() = addresses.singleOrNull()
}

data class AppActivity(val app: String, val path: String, val hosts: List<HostActivity>) {
    val isService: Boolean get() = app == ConnectionLedger.SERVICE_APP
    val up: Long get() = hosts.sumOf { it.up }
    val down: Long get() = hosts.sumOf { it.down }
    val active: Int get() = hosts.sumOf { it.active }
    val connections: Int get() = hosts.sumOf { it.connections }
    val attempts: Int get() = hosts.sumOf { it.attempts }
    val traffic: Map<ConnectionType, Long>
        get() = hosts.groupBy { it.route.channel }.mapValues { (_, list) -> list.sumOf { it.up + it.down } }
}

object ConnectionLedger {
    const val SERVICE_APP = "erkin:service"

    private data class Key(val host: String, val route: ConnectionRoute, val origin: RouteOrigin, val purpose: ServicePurpose?)

    fun activity(table: ConnectionTable): List<AppActivity> =
        table.rows
            .groupBy { row -> appOf(row) }
            .map { (app, rows) ->
                val hosts = rows.groupBy(::keyOf).map { (key, group) ->
                    HostActivity(
                        host = key.host,
                        route = key.route,
                        origin = key.origin,
                        purpose = key.purpose,
                        active = group.sumOf { it.active },
                        connections = group.sumOf { it.connections }.toInt(),
                        attempts = if (key.route == ConnectionRoute.BLOCKED) group.sumOf { it.connections }.toInt() else 0,
                        up = group.sumOf { it.up },
                        down = group.sumOf { it.down },
                        lastSeen = group.maxOf { it.lastSeen },
                        addresses = group.groupBy { it.host.ifEmpty { it.ip } }.map { (name, rows) ->
                            AddressActivity(
                                name = name,
                                active = rows.sumOf { it.active },
                                connections = rows.sumOf { it.connections }.toInt(),
                                up = rows.sumOf { it.up },
                                down = rows.sumOf { it.down },
                                lastSeen = rows.maxOf { it.lastSeen }
                            )
                        }.sortedWith(compareByDescending<AddressActivity> { it.active }.thenByDescending { it.up + it.down }.thenByDescending { it.connections })
                    )
                }
                AppActivity(
                    app,
                    rows.firstNotNullOfOrNull { it.path.ifEmpty { null } }.orEmpty(),
                    hosts.sortedWith(compareByDescending<HostActivity> { it.active }.thenByDescending { it.up + it.down }.thenByDescending { it.attempts })
                )
            }
            .sortedWith(compareBy<AppActivity> { it.isService }.thenByDescending { it.up + it.down }.thenByDescending { it.attempts })

    private fun appOf(row: TrafficRow): String =
        if (ServicePurpose.of(row.inbound, row.inName) != null) SERVICE_APP else row.process.ifEmpty { row.path }

    private fun keyOf(row: TrafficRow): Key {
        val purpose = ServicePurpose.of(row.inbound, row.inName)
        return Key(
            host = ConnectionHosts.group(row.host, row.ip),
            route = ConnectionRoute.of(row.route),
            origin = if (purpose != null) RouteOrigin.APP_CHANNEL else RouteOrigin.of(row.rule),
            purpose = purpose
        )
    }
}
