package io.github.yulbax.erkin.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "apps")
data class App(
    @PrimaryKey val packageName: String,
    val name: String,
    val isSystemApp: Boolean,
    val connectionType: ConnectionType = ConnectionType.VPN,
    val path: String? = null
)

fun isExecutablePath(id: String): Boolean = '/' in id || '\\' in id

enum class ConnectionType(val wire: String) {
    DIRECT("DIRECT"), BYEDPI("BYEDPI"), VPN("VPN");

    companion object {
        fun fromWire(value: String): ConnectionType? = entries.firstOrNull { it.wire == value }
    }
}

data class RoutedApps(
    val byeDpiPackages: List<String>,
    val vpnPackages: List<String>,
    val directPaths: List<String> = emptyList()
) {
    val tunneledPackages: List<String> get() = byeDpiPackages + vpnPackages
    val hasByeDpi: Boolean get() = byeDpiPackages.isNotEmpty()
    val hasVpn: Boolean get() = vpnPackages.isNotEmpty()
    val isEmpty: Boolean get() = !hasByeDpi && !hasVpn

    companion object {
        fun from(apps: List<App>, hasServers: Boolean = true): RoutedApps {
            val direct = if (hasServers) apps.packagesOf(ConnectionType.DIRECT) else apps.packagesOf(ConnectionType.DIRECT) + apps.packagesOf(ConnectionType.VPN)
            return RoutedApps(
                byeDpiPackages = apps.packagesOf(ConnectionType.BYEDPI),
                vpnPackages = if (hasServers) apps.packagesOf(ConnectionType.VPN) else emptyList(),
                directPaths = direct.filter(::isExecutablePath)
            )
        }

        private fun List<App>.packagesOf(type: ConnectionType): List<String> =
            filter { it.connectionType == type }.map { it.packageName }
    }
}
