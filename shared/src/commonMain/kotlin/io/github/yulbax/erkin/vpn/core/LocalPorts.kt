package io.github.yulbax.erkin.vpn.core

import java.net.InetAddress
import java.net.ServerSocket

fun freeLoopbackPort(): Int =
    ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }

fun randomToken(): String {
    val bytes = ByteArray(12)
    java.security.SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}
