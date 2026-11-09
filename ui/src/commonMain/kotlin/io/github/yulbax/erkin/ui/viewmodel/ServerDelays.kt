package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.vpn.core.ProxyDelay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ServerDelays {
    private val _delays = MutableStateFlow<Map<Long, ProxyDelay>>(emptyMap())
    val delays: StateFlow<Map<Long, ProxyDelay>> = _delays.asStateFlow()

    fun update(transform: (Map<Long, ProxyDelay>) -> Map<Long, ProxyDelay>) = _delays.update(transform)
}
