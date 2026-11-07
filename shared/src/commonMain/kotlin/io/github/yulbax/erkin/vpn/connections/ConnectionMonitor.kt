package io.github.yulbax.erkin.vpn.connections

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.VpnStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.milliseconds

class ConnectionMonitor(
    private val source: ConnectionTableSource,
    private val states: VpnStateRepository,
    private val log: AppLog,
    scope: CoroutineScope
) {
    val activity: StateFlow<List<AppActivity>> = flow {
        while (true) {
            if (states.state.value.isActive) {
                try {
                    val raw = source.read()
                    emit(if (raw == null) emptyList() else ConnectionLedger.activity(ConnectionTable.parse(raw)))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    log.w(TAG, "connection table read failed", t)
                }
            } else {
                emit(emptyList())
            }
            delay(POLL_INTERVAL_MS.milliseconds)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private companion object {
        const val TAG = "Connections"
        const val POLL_INTERVAL_MS = 1_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
