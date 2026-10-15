package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.vpn.mihomo.SystemDns
import java.io.File
import java.net.InetAddress

object SystemDnsDetector {
    private val RESOLV_CONF = File("/etc/resolv.conf")
    private val RESOLVED_UPSTREAM = File("/run/systemd/resolve/resolv.conf")
    private val IP_LITERAL = Regex("[0-9.]+|[0-9a-fA-F:.]*:[0-9a-fA-F:.]*")

    fun detect(): SystemDns {
        if (DesktopPaths.isWindows) return SystemDns.Local
        val configured = nameservers(RESOLV_CONF)
        if (configured.isEmpty() || configured.any { !it.isLoopbackAddress }) return SystemDns.Local
        return nameservers(RESOLVED_UPSTREAM)
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.let { SystemDns.Server(it.hostAddress) }
            ?: SystemDns.Unknown
    }

    private fun nameservers(file: File): List<InetAddress> =
        runCatching { file.readLines() }.getOrDefault(emptyList())
            .map { it.trim().split(Regex("\\s+")) }
            .filter { it.size >= 2 && it[0] == "nameserver" }
            .map { it[1].substringBefore('%') }
            .filter { IP_LITERAL.matches(it) }
            .mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
}
