package io.github.yulbax.erkin.vpn.core

const val VPN_DELAY_TIMEOUT_MS = 6_000

interface VpnEngine {
    val probeSocksPort: Int
    val probeUsername: String
    val probePassword: String

    fun start(config: EngineConfig)

    fun reloadRouting(config: EngineConfig)

    suspend fun measureVpnDelay(): Int?

    fun byeDpiProbePort(byeDpiPort: Int): Int = byeDpiPort

    fun hasFingerprintError(): Boolean

    fun stop()
}

interface EngineListener {
    fun onThroughput(uplinkBytesPerSec: Long, downlinkBytesPerSec: Long)
    fun onStopRequested()
}

fun interface DefaultInterfaceListener {
    fun onDefaultInterfaceChanged(name: String, index: Int)
}

sealed interface ProxyDelay {
    data class Measured(val ms: Int) : ProxyDelay
    data object Failed : ProxyDelay
}
