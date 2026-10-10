package io.github.yulbax.erkin.vpn.core

data class TunConfig(
    val mtu: Int,
    val inet4: List<TunAddress>,
    val inet6: List<TunAddress>,
    val autoRoute: Boolean,
    val dnsServers: List<String>,
    val includePackages: List<String>,
    val excludePackages: List<String>
)

data class TunAddress(val address: String, val prefix: Int)
