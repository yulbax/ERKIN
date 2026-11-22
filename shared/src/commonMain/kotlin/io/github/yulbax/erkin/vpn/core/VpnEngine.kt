package io.github.yulbax.erkin.vpn.core

interface VpnEngine {
    val probeSocksPort: Int
    val probeUsername: String
    val probePassword: String

    fun start(config: EngineConfig)

    fun reloadRouting(config: EngineConfig)

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
    data object AccessDenied : ProbeResult
    data object NeedsConnection : ProbeResult

    companion object {
        private const val LOGIN_REJECTED = -2
        private const val ACCESS_DENIED = -3

        fun fromWire(value: Int): ProbeResult = when {
            value > 0 -> Answered(value)
            value == LOGIN_REJECTED -> LoginRejected
            value == ACCESS_DENIED -> AccessDenied
            else -> NoAnswer
        }
    }
}
