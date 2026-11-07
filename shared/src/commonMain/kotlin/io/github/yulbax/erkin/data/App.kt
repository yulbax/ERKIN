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
    BLOCK("BLOCK"), BYPASS("BYPASS"), DIRECT("DIRECT"), BYEDPI("BYEDPI"), VPN("VPN", needsServer = true);

    companion object {
        val FOR_NEW_APPS: List<ConnectionType> = listOf(BYPASS, DIRECT, BYEDPI, VPN)

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
        fun from(apps: List<App>, hasServers: Boolean = true): RoutedApps {
            val vpn = apps.packagesOf(ConnectionType.VPN)
            val bypass = apps.packagesOf(ConnectionType.BYPASS) + if (hasServers) emptyList() else vpn
            val direct = apps.packagesOf(ConnectionType.DIRECT)
            return RoutedApps(
                byeDpiPackages = apps.packagesOf(ConnectionType.BYEDPI),
                vpnPackages = if (hasServers) vpn else emptyList(),
                directPaths = (direct + bypass).filter(::isExecutablePath),
                directPackages = direct.filterNot(::isExecutablePath),
                blockedPackages = apps.packagesOf(ConnectionType.BLOCK)
            )
        }

        private fun List<App>.packagesOf(type: ConnectionType): List<String> =
            filter { it.connectionType == type }.map { it.packageName }
    }
}
