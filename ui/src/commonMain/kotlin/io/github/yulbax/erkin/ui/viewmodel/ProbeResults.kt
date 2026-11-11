package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.vpn.core.ProbeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ProbeResults {
    private val _results = MutableStateFlow<Map<Long, ProbeResult>>(emptyMap())
    val results: StateFlow<Map<Long, ProbeResult>> = _results.asStateFlow()

    fun update(transform: (Map<Long, ProbeResult>) -> Map<Long, ProbeResult>) = _results.update(transform)
}
