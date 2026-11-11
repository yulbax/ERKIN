package io.github.yulbax.erkin.ui.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate

class IncomingServer(val raw: String, val fileName: String?)

class IncomingServers {
    private val pending = MutableStateFlow<IncomingServer?>(null)

    val next: StateFlow<IncomingServer?> = pending

    fun offer(server: IncomingServer) {
        pending.value = server
    }

    fun take(): IncomingServer? = pending.getAndUpdate { null }
}
