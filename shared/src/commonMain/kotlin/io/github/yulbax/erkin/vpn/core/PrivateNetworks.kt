package io.github.yulbax.erkin.vpn.core

import java.net.InetAddress

object PrivateNetworks {
    val CIDRS = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16", "100.64.0.0/10",
        "fc00::/7", "fe80::/10"
    )

    private val ranges = CIDRS.map { cidr ->
        InetAddress.getByName(cidr.substringBefore('/')).address to cidr.substringAfter('/').toInt()
    }

    fun contains(host: String): Boolean {
        val address = literal(host.removeSurrounding("[", "]")) ?: return false
        return ranges.any { (network, prefix) -> network.size == address.size && address.matches(network, prefix) }
    }

    private fun literal(host: String): ByteArray? {
        val canonical = RuleValues.Cidr.normalize(host)?.substringBefore('/') ?: return null
        return InetAddress.getByName(canonical).address
    }

    private fun ByteArray.matches(network: ByteArray, prefix: Int): Boolean {
        val whole = prefix / Byte.SIZE_BITS
        if ((0 until whole).any { this[it] != network[it] }) return false
        val rest = prefix % Byte.SIZE_BITS
        if (rest == 0) return true
        val mask = (0xFF shl (Byte.SIZE_BITS - rest)) and 0xFF
        return (this[whole].toInt() and mask) == (network[whole].toInt() and mask)
    }
}
