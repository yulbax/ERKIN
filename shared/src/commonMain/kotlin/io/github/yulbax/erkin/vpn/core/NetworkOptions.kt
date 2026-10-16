package io.github.yulbax.erkin.vpn.core

data class NetworkOptions(
    val mtu: Int = 9000,
    val ipv6Mode: Ipv6Mode = Ipv6Mode.DISABLE,
    val dnsRemote: String = "1.1.1.1",
    val dnsDirect: String = "1.1.1.1",
    val sniff: Boolean = true,
    val bypassLan: Boolean = false,
    val blockQuic: Boolean = false,
    val preferredFingerprint: TlsFingerprint? = null
) {
    companion object {
        val DEFAULT = NetworkOptions()
        val MTU_RANGE = 1280..9000
    }
}
