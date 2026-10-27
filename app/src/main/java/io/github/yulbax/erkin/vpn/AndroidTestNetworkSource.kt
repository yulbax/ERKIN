package io.github.yulbax.erkin.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import io.github.yulbax.erkin.vpn.core.TestNetwork
import io.github.yulbax.erkin.vpn.core.TestNetworkSource

class AndroidTestNetworkSource(private val context: Context) : TestNetworkSource {
    override fun current(): TestNetwork {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return TestNetwork.Unknown
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return TestNetwork.Unknown
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TestNetwork.Wifi
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TestNetwork.Ethernet
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TestNetwork.Cellular(
                context.getSystemService(TelephonyManager::class.java)?.networkOperatorName.orEmpty()
            )
            else -> TestNetwork.Unknown
        }
    }
}
