package io.github.yulbax.erkin.vpn.core

const val VPN_DELAY_TIMEOUT_MS = 6_000

interface VpnEngine {
    val probeSocksPort: Int
    val probeUsername: String
    val probePassword: String

    fun start(config: EngineConfig)

    fun reloadRouting(config: EngineConfig)

    suspend fun probeVpn(): ProbeResult

    suspend fun probe(tag: String): ProbeResult

    fun byeDpiProbePort(byeDpiPort: Int): Int = byeDpiPort

    fun stop()
}

interface EngineListener {
    fun onThroughput(uplinkBytesPerSec: Long, downlinkBytesPerSec: Long)
    fun onStopRequested()
}

fun interface DefaultInterfaceListener {
    fun onDefaultInterfaceChanged(name: String, index: Int)
}

sealed interface ProbeResult {
    data class Answered(val ms: Int) : ProbeResult
    data object NoAnswer : ProbeResult
    data object LoginRejected : ProbeResult
    data object NeedsConnection : ProbeResult

    companion object {
        private const val LOGIN_REJECTED = -2

        fun fromWire(value: Int): ProbeResult = when {
            value > 0 -> Answered(value)
            value == LOGIN_REJECTED -> LoginRejected
            else -> NoAnswer
        }
    }
}
