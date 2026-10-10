package io.github.yulbax.erkin.vpn.core

interface TunPlatform {
    fun openTun(config: TunConfig): Int

    fun protectFd(fd: Int): Boolean

    fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): Int

    fun packageNameForUid(uid: Int): String

    fun uidForPackageName(packageName: String): Int
}
