package io.github.yulbax.erkin.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "apps")
data class App(
    @PrimaryKey val packageName: String,
    val name: String,
    val isSystemApp: Boolean,
    val connectionType: ConnectionType = ConnectionType.VPN,
    val path: String? = null,
    val serverId: Long? = null
)

fun isExecutablePath(id: String): Boolean = '/' in id || '\\' in id

fun executableName(id: String): String = id.substringAfterLast('/').substringAfterLast('\\')

enum class ConnectionType(val wire: String, val needsServer: Boolean = false) {
    BLOCK("BLOCK"), DIRECT("DIRECT"), BYEDPI("BYEDPI"), VPN("VPN", needsServer = true);

    companion object {
        val FOR_NEW_APPS: List<ConnectionType> = listOf(DIRECT, BYEDPI, VPN)

        fun fromWire(value: String): ConnectionType? = entries.firstOrNull { it.wire == value }
    }
}

data class RoutedApps(
    val byeDpiPackages: List<String>,
    val vpnPackages: List<String>,
    val directPaths: List<String> = emptyList(),
    val directPackages: List<String> = emptyList(),
    val blockedPackages: List<String> = emptyList()
) {
    val tunneledPackages: List<String> get() = blockedPackages + byeDpiPackages + vpnPackages + directPackages
    val hasByeDpi: Boolean get() = byeDpiPackages.isNotEmpty()
    val hasVpn: Boolean get() = vpnPackages.isNotEmpty()
    val hasBlocked: Boolean get() = blockedPackages.isNotEmpty()
    val isEmpty: Boolean get() = !hasByeDpi && !hasVpn && !hasBlocked
    val runsWithoutServer: Boolean get() = hasByeDpi || hasBlocked

    companion object {
        fun from(apps: List<App>, hasServers: Boolean = true, tunnelDirectApps: Boolean = false, ruleApps: Set<String> = emptySet()): RoutedApps {
            val direct = if (hasServers) apps.packagesOf(ConnectionType.DIRECT) else apps.packagesOf(ConnectionType.DIRECT) + apps.packagesOf(ConnectionType.VPN)
            return RoutedApps(
                byeDpiPackages = apps.packagesOf(ConnectionType.BYEDPI),
                vpnPackages = if (hasServers) apps.packagesOf(ConnectionType.VPN) else emptyList(),
                directPaths = direct.filter(::isExecutablePath),
                directPackages = direct.filterNot(::isExecutablePath).filter { tunnelDirectApps || it in ruleApps },
                blockedPackages = apps.packagesOf(ConnectionType.BLOCK)
            )
        }

        private fun List<App>.packagesOf(type: ConnectionType): List<String> =
            filter { it.connectionType == type }.map { it.packageName }
    }
}
