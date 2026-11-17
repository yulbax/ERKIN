package io.github.yulbax.erkin.vpn

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object ServerProbePolicy {
    const val TIMEOUT_MS = 5_000
    const val MISSES_TO_UNREACHABLE = 2
    const val FAILURES_TO_RECOVER = 4
    val CHECK_INTERVAL: Duration = 15.seconds
    val RETRY_DELAY: Duration = 3.seconds
}
