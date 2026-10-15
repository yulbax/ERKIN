package io.github.yulbax.erkin.vpn.mihomo

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import mihomocore.Mihomocore
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.TimeZone

object MihomoRuntime {
    private const val DNS_PORT = 53

    @Volatile private var ready = false

    fun workDir(context: Context): File = File(context.filesDir, "work")

    fun logFile(context: Context): File = File(workDir(context), "core.log")

    @Synchronized
    fun ensureSetup(context: Context) {
        if (ready) return
        val appContext = context.applicationContext
        runCatching { go.Seq.setContext(appContext) }
        val workDir = workDir(appContext).apply { mkdirs() }
        Mihomocore.setup(workDir.absolutePath, logFile(appContext).absolutePath, TimeZone.getDefault().id)
        observeSystemDns(appContext)
        ready = true
    }

    private fun observeSystemDns(context: Context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        connectivity.activeNetwork?.let { network -> publish(connectivity.getLinkProperties(network)) }
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                publish(linkProperties)
            }

            override fun onLost(network: Network) {
                publish(null)
            }
        })
    }

    private fun publish(linkProperties: LinkProperties?) {
        val servers = linkProperties?.dnsServers.orEmpty()
            .filterNot { it.isLinkLocalAddress }
            .mapNotNull { it.asDnsAddress() }
            .distinct()
        runCatching { Mihomocore.notifyDnsChanged(servers.joinToString(",")) }
    }

    private fun InetAddress.asDnsAddress(): String? = when (this) {
        is Inet4Address -> "$hostAddress:$DNS_PORT"
        is Inet6Address -> "[${hostAddress?.substringBefore('%')}]:$DNS_PORT"
        else -> null
    }
}
