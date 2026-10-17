package io.github.yulbax.erkin.vpn.mihomo

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.util.core
import mihomocore.Mihomocore
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.TimeZone

object MihomoRuntime {
    private const val DNS_PORT = 53
    private const val LEGACY_GEOIP = "geoip.metadb"
    private const val LEGACY_CORE_LOG = "core.log"

    @Volatile private var ready = false
    @Volatile private var privateDnsHost: String? = null
    @Volatile private var systemDnsServers = ""

    fun workDir(context: Context): File = File(context.filesDir, "work")

    fun systemDns(): SystemDns = privateDnsHost?.let { SystemDns.Tls(it) } ?: SystemDns.Local

    fun describeSystemDns(): String = "servers=[$systemDnsServers] privateDns=${privateDnsHost ?: "-"}"

    @Synchronized
    fun ensureSetup(context: Context, log: AppLog) {
        if (ready) return
        val appContext = context.applicationContext
        runCatching { go.Seq.setContext(appContext) }
        val workDir = workDir(appContext).apply { mkdirs() }
        File(workDir, LEGACY_GEOIP).delete()
        File(workDir, LEGACY_CORE_LOG).delete()
        Mihomocore.setup(workDir.absolutePath, TimeZone.getDefault().id) { level, message -> log.core(level, message) }
        observeSystemDns(appContext)
        ready = true
    }

    private fun observeSystemDns(context: Context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        connectivity.activeNetwork
            ?.takeUnless { connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
            ?.let { network -> publish(connectivity.getLinkProperties(network)) }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            @Volatile private var current: Network? = null

            override fun onAvailable(network: Network) {
                current = network
                publish(connectivity.getLinkProperties(network))
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                current = network
                publish(linkProperties)
            }

            override fun onLost(network: Network) {
                if (network != current) return
                current = null
                publish(null)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            connectivity.registerBestMatchingNetworkCallback(request, callback, Handler(Looper.getMainLooper()))
        } else {
            connectivity.requestNetwork(request, callback)
        }
    }

    private fun publish(linkProperties: LinkProperties?) {
        privateDnsHost = linkProperties?.privateDnsServerName?.takeIf { it.isNotBlank() }
        val servers = linkProperties?.dnsServers.orEmpty()
            .filterNot { it.isLinkLocalAddress }
            .mapNotNull { it.asDnsAddress() }
            .distinct()
        systemDnsServers = servers.joinToString(",")
        runCatching { Mihomocore.notifyDnsChanged(systemDnsServers) }
    }

    private fun InetAddress.asDnsAddress(): String? = when (this) {
        is Inet4Address -> "$hostAddress:$DNS_PORT"
        is Inet6Address -> "[${hostAddress?.substringBefore('%')}]:$DNS_PORT"
        else -> null
    }
}
