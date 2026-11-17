package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.vpn.core.VpnEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class CountryLookup(
    private val engine: VpnEngine,
    private val channels: ChannelProbe,
    private val snapshot: MutableStateFlow<HealthSnapshot>,
    private val scope: CoroutineScope
) {
    private var lookup: Job? = null

    fun onVpnUp() {
        if (snapshot.value.vpnCountry.isNotEmpty() || lookup?.isActive == true) return
        lookup = scope.launch {
            channels.country(engine)?.let { country -> snapshot.update { it.copy(vpnCountry = country) } }
        }
    }
}
