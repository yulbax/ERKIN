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
    val build: String?
    val byeDpiVersion: String

    val shortBuild: String? get() = build?.take(SHORT_BUILD_LENGTH)

    suspend fun coreVersion(): String?
}

const val SHORT_BUILD_LENGTH = 7
