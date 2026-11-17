package io.github.yulbax.erkin.vpn

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object ServerProbePolicy {
    val TIMEOUT: Duration = 5_000.milliseconds
    const val MISSES_TO_UNREACHABLE = 2
    const val FAILURES_TO_RECOVER = 4
    val CHECK_INTERVAL: Duration = 15_000.milliseconds
    val RETRY_DELAY: Duration = 3_000.milliseconds
}
