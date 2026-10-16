package io.github.yulbax.erkin.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.yulbax.erkin.vpn.core.Ipv6Mode
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.core.TlsFingerprint
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Entity(tableName = "settings")
data class SettingsEntity(
    @Transient @PrimaryKey val id: Int = 1,
    val showSystemApps: Boolean = false,
    val byeDpiArgs: String = "",
    @Transient val tunStack: String = "gvisor",
    val mtu: Int = NetworkOptions.DEFAULT.mtu,
    val ipv6Mode: String = NetworkOptions.DEFAULT.ipv6Mode.wire,
    val dnsRemote: String = NetworkOptions.DEFAULT.dnsRemote,
    val dnsDirect: String = NetworkOptions.DEFAULT.dnsDirect,
    val sniff: Boolean = NetworkOptions.DEFAULT.sniff,
    val bypassLan: Boolean = NetworkOptions.DEFAULT.bypassLan,
    val autoConnect: Boolean = false,
    val preferredFingerprint: String = NetworkOptions.DEFAULT.preferredFingerprint?.wire ?: "",
    val homeHintSeen: Boolean = false,
    val appsHintSeen: Boolean = false,
    val newAppsConnectionType: String = "",
    val rulesHintSeen: Boolean = false,
    val blockQuic: Boolean = NetworkOptions.DEFAULT.blockQuic
) {
    fun newAppsConnectionType(platformDefault: ConnectionType): ConnectionType =
        ConnectionType.fromWire(newAppsConnectionType) ?: platformDefault

    fun networkOptions(): NetworkOptions = NetworkOptions(
        mtu = mtu,
        ipv6Mode = Ipv6Mode.fromWire(ipv6Mode),
        dnsRemote = dnsRemote,
        dnsDirect = dnsDirect,
        sniff = sniff,
        bypassLan = bypassLan,
        blockQuic = blockQuic,
        preferredFingerprint = TlsFingerprint.fromWire(preferredFingerprint)
    )

    fun withNetworkOptions(options: NetworkOptions): SettingsEntity = copy(
        mtu = options.mtu,
        ipv6Mode = options.ipv6Mode.wire,
        dnsRemote = options.dnsRemote,
        dnsDirect = options.dnsDirect,
        sniff = options.sniff,
        bypassLan = options.bypassLan,
        blockQuic = options.blockQuic,
        preferredFingerprint = options.preferredFingerprint?.wire ?: ""
    )
}
