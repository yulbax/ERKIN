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
import kotlinx.coroutines.flow.WhileSubscribed
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
            delay(POLL_INTERVAL)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    private companion object {
        const val TAG = "Connections"
        val POLL_INTERVAL = 1_000.milliseconds
        val STOP_TIMEOUT = 5_000.milliseconds
    }
}
