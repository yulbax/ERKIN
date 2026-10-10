package io.github.yulbax.erkin.vpn

import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Process
import android.os.ParcelFileDescriptor
import io.github.yulbax.erkin.util.ErkinLog
import io.github.yulbax.erkin.vpn.core.TunConfig
import java.net.InetSocketAddress

class TunSession(
    private val service: VpnService,
    private val networkMonitor: DefaultNetworkMonitor,
    private val log: ErkinLog
) {
    private var tunInterface: ParcelFileDescriptor? = null
    private var signature: String? = null

    fun open(config: TunConfig): Int {
        if (VpnService.prepare(service) != null) error("android: missing vpn permission")

        val newSignature = signatureOf(config)
        val existing = tunInterface
        if (existing != null && newSignature == signature) return existing.fd

        val builder = service.Builder().setSession("ERKIN").setMtu(config.mtu).setMetered(false)
        config.inet4.forEach { builder.addAddress(it.address, it.prefix) }
        config.inet6.forEach { builder.addAddress(it.address, it.prefix) }
        if (config.autoRoute) {
            config.dnsServers.forEach { builder.addDnsServer(it) }
            builder.addRoute("0.0.0.0", 0)
            builder.addRoute("::", 0)
            config.includePackages.forEach { builder.addAllowedApplication(it) }
            config.excludePackages.forEach { builder.addDisallowedApplication(it) }
        }

        val pfd = builder.establish() ?: error("android: establish() failed")
        log.i(TAG, "tun established mtu=${config.mtu} include=${config.includePackages.size} exclude=${config.excludePackages.size}")
        runCatching { tunInterface?.close() }
        tunInterface = pfd
        signature = newSignature
        networkMonitor.currentNetwork()?.let {
            runCatching { service.setUnderlyingNetworks(arrayOf(it)) }
        }
        return pfd.fd
    }

    fun findConnectionOwner(ipProtocol: Int, source: InetSocketAddress, destination: InetSocketAddress): Int {
        val connectivity = service.getSystemService(ConnectivityManager::class.java)
        val uid = connectivity.getConnectionOwnerUid(ipProtocol, source, destination)
        if (uid == Process.INVALID_UID) error("connection owner not found")
        return uid
    }

    fun packageNameForUid(uid: Int): String =
        service.packageManager.getPackagesForUid(uid)?.firstOrNull() ?: error("no package for uid $uid")

    fun uidForPackageName(packageName: String): Int =
        service.packageManager.getApplicationInfo(packageName, 0).uid

    fun close() {
        try {
            tunInterface?.close()
        } finally {
            tunInterface = null
            signature = null
        }
    }

    private fun signatureOf(config: TunConfig): String = listOf(
        config.mtu.toString(),
        config.inet4.joinToString(",") { "${it.address}/${it.prefix}" },
        config.inet6.joinToString(",") { "${it.address}/${it.prefix}" },
        config.autoRoute.toString(),
        config.dnsServers.sorted().joinToString(","),
        config.includePackages.sorted().joinToString(","),
        config.excludePackages.sorted().joinToString(",")
    ).joinToString("|")

    private companion object {
        const val TAG = "TunSession"
    }
}
