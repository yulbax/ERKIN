package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.vpn.core.VpnEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ExitCountry {
    data object Pending : ExitCountry
    data object Unknown : ExitCountry
    data class Known(val code: String) : ExitCountry
}

internal class CountryLookup(
    private val engine: VpnEngine,
    private val channels: ChannelProbe,
    private val snapshot: MutableStateFlow<HealthSnapshot>,
    private val scope: CoroutineScope
) {
    private var lookup: Job? = null

    fun start() = look()

    fun onVpnUp() {
        if (snapshot.value.vpnCountry is ExitCountry.Known || lookup?.isActive == true) return
        look()
    }

    private fun look() {
        lookup = scope.launch {
            val found = channels.country(engine)?.let(ExitCountry::Known) ?: ExitCountry.Unknown
            snapshot.update { it.copy(vpnCountry = found) }
        }
    }
}
