package io.github.yulbax.erkin.vpn.singbox

import android.system.OsConstants
import libbox.NetworkInterfaceIterator
import libbox.StringIterator
import java.net.InterfaceAddress
import libbox.NetworkInterface as LibboxNetworkInterface
import java.net.NetworkInterface as JavaNetworkInterface

class StringArray(private val iterator: Iterator<String>) : StringIterator {
    override fun len(): Int = 0
    override fun hasNext(): Boolean = iterator.hasNext()
    override fun next(): String = iterator.next()
}

class InterfaceArray(private val iterator: Iterator<LibboxNetworkInterface>) : NetworkInterfaceIterator {
    override fun hasNext(): Boolean = iterator.hasNext()
    override fun next(): LibboxNetworkInterface = iterator.next()
}

fun systemInterfaces(): NetworkInterfaceIterator {
    val result = mutableListOf<LibboxNetworkInterface>()
    val interfaces = runCatching { JavaNetworkInterface.getNetworkInterfaces().toList() }.getOrDefault(emptyList())
    for (ni in interfaces) {
        val boxInterface = LibboxNetworkInterface()
        boxInterface.name = ni.name
        boxInterface.index = ni.index
        runCatching { boxInterface.mtu = ni.mtu }
        boxInterface.addresses = StringArray(ni.interfaceAddresses.map { it.toPrefix() }.iterator())

        var flags = 0
        runCatching {
            if (ni.isUp) flags = flags or OsConstants.IFF_UP or OsConstants.IFF_RUNNING
            if (ni.isLoopback) flags = flags or OsConstants.IFF_LOOPBACK
            if (ni.isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
            if (ni.supportsMulticast()) flags = flags or OsConstants.IFF_MULTICAST
        }
        boxInterface.flags = flags

        result.add(boxInterface)
    }
    return InterfaceArray(result.iterator())
}

private fun InterfaceAddress.toPrefix(): String {
    val host = (address.hostAddress ?: "").substringBefore('%')
    return "$host/$networkPrefixLength"
}
