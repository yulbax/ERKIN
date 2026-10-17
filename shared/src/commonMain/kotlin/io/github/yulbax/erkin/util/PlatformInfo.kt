package io.github.yulbax.erkin.util

fun interface VpnLauncher {
    fun start()
}

interface DiagnosticsSource {
    val feed: LogFeed

    suspend fun collect(): String

    suspend fun clear()
}

interface VersionInfo {
    val appVersion: String?
    val byeDpiVersion: String

    suspend fun coreVersion(): String?
}
