package io.github.yulbax.erkin.vpn.connections

import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.vpn.ProxyTag
import io.github.yulbax.erkin.vpn.ProxyRef
import io.github.yulbax.erkin.vpn.core.BuiltInOutbound
import io.github.yulbax.erkin.vpn.core.RuleValues
import io.github.yulbax.erkin.vpn.mihomo.MihomoConfigBuilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun interface ConnectionTableSource {
    suspend fun read(): String
}

@Serializable
data class TrafficRow(
    val process: String = "",
    val path: String = "",
    val host: String = "",
    val ip: String = "",
    val inbound: String = "",
    val inName: String = "",
    val route: String = "",
    val rule: String = "",
    val payload: String = "",
    val up: Long = 0,
    val down: Long = 0,
    val active: Int = 0,
    val connections: Long = 0,
    val lastSeen: Long = 0
)

@Serializable
data class ConnectionTable(val rows: List<TrafficRow> = emptyList()) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(raw: String): ConnectionTable = json.decodeFromString(serializer(), raw)
    }
}

data class ConnectionRoute(val channel: ConnectionType, val server: ProxyRef? = null) {
    val serverId: Long? get() = (server as? ProxyRef.Profile)?.id

    companion object {
        val BLOCKED = ConnectionRoute(ConnectionType.BLOCK)

        fun of(exit: String): ConnectionRoute = when (BuiltInOutbound.fromTag(exit)) {
            BuiltInOutbound.DIRECT -> ConnectionRoute(ConnectionType.DIRECT)
            BuiltInOutbound.REJECT -> BLOCKED
            BuiltInOutbound.BYEDPI -> ConnectionRoute(ConnectionType.BYEDPI)
            null -> if (exit.isEmpty()) ConnectionRoute(ConnectionType.DIRECT) else ConnectionRoute(ConnectionType.VPN, ProxyTag.parse(exit))
        }
    }
}

enum class ServicePurpose {
    CORE, REQUEST;

    companion object {
        private const val CORE_INBOUND = "Inner"
        private val PROBE_INBOUNDS = setOf(MihomoConfigBuilder.PROBE_LISTENER, MihomoConfigBuilder.BYEDPI_PROBE_LISTENER)

        fun of(inbound: String, inName: String): ServicePurpose? = when {
            inName in PROBE_INBOUNDS -> REQUEST
            inbound == CORE_INBOUND -> CORE
            else -> null
        }
    }
}

enum class RouteOrigin {
    APP_CHANNEL, RULE, APP_RULE;

    companion object {
        private val APP_MATCHES = setOf("ProcessName", "ProcessPath", "Match")
        private const val LOGIC_AND = "AND"

        fun of(rule: String): RouteOrigin = when (rule) {
            LOGIC_AND -> APP_RULE
            in APP_MATCHES, "" -> APP_CHANNEL
            else -> RULE
        }
    }
}

object ConnectionHosts {
    private val SECOND_LEVEL = setOf("co", "com", "net", "org", "gov", "edu", "ac", "or", "ne", "go")

    fun group(host: String, ip: String): String {
        val name = host.trim().trimEnd('.').lowercase()
        if (name.isEmpty()) return ip
        if (RuleValues.isIpAddress(name)) return name
        val labels = name.split('.')
        if (labels.size <= 2) return name
        val keep = if (labels[labels.size - 2] in SECOND_LEVEL && labels.last().length == 2) 3 else 2
        return labels.takeLast(keep).joinToString(".")
    }
}
