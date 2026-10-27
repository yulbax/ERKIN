package io.github.yulbax.erkin.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import io.github.yulbax.erkin.vpn.core.TestNetwork
import io.github.yulbax.erkin.vpn.core.TestNetworkSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class AndroidTestNetworkSource(private val context: Context) : TestNetworkSource {
    override val network: Flow<TestNetwork> = callbackFlow {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.toTestNetwork())
            }

            override fun onLost(network: Network) {
                trySend(TestNetwork.Unknown)
            }
        }
        trySend(connectivity?.getNetworkCapabilities(connectivity.activeNetwork)?.toTestNetwork() ?: TestNetwork.Unknown)
        connectivity?.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity?.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun NetworkCapabilities.toTestNetwork(): TestNetwork = when {
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TestNetwork.Wifi
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TestNetwork.Ethernet
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TestNetwork.Cellular(
            context.getSystemService(TelephonyManager::class.java)?.networkOperatorName.orEmpty()
        )
        else -> TestNetwork.Unknown
    }
}
