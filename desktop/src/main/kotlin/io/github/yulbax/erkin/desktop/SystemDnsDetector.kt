package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.vpn.mihomo.SystemDns
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object SystemDnsDetector {
    private val RESOLV_CONF = File("/etc/resolv.conf")
    private val RESOLVED_UPSTREAM = File("/run/systemd/resolve/resolv.conf")
    private val IP_LITERAL = Regex("[0-9.]+|[0-9a-fA-F:.]*:[0-9a-fA-F:.]*")

    private const val SCOPED_RESOLVERS = "DNS configuration (for scoped queries)"
    private val NAMESERVER = Regex("""nameserver\[\d+]\s*:\s*(\S+)""")

    fun detect(): SystemDns = when (DesktopPaths.os) {
        DesktopOs.Windows -> SystemDns.Local
        DesktopOs.MacOs -> fromScutil(scutilDns())
        DesktopOs.Linux -> fromResolvConf()
    }

    fun fromScutil(output: String): SystemDns =
        output.lineSequence()
            .map(String::trim)
            .takeWhile { it != SCOPED_RESOLVERS }
            .mapNotNull { NAMESERVER.matchEntire(it)?.groupValues?.get(1) }
            .filter { IP_LITERAL.matches(it) }
            .mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.let { SystemDns.Server(it.hostAddress) }
            ?: SystemDns.Unknown

    private fun scutilDns(): String = runCatching {
        val process = ProcessBuilder("scutil", "--dns").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor(5, TimeUnit.SECONDS)
        output
    }.getOrDefault("")

    private fun fromResolvConf(): SystemDns {
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
