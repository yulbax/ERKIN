package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.vpn.core.ProbeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ProbeResults {
    private val _results = MutableStateFlow<Map<Long, ProbeResult>>(emptyMap())
    val results: StateFlow<Map<Long, ProbeResult>> = _results.asStateFlow()
    private val _compared = MutableStateFlow<Set<Long>>(emptySet())
    val compared: StateFlow<Set<Long>> = _compared.asStateFlow()

    fun update(transform: (Map<Long, ProbeResult>) -> Map<Long, ProbeResult>) = _results.update(transform)

    fun compare(ids: Set<Long>) {
        _compared.value = ids
    }

    companion object {
        fun fastest(results: Map<Long, ProbeResult>, compared: Set<Long>): Long? = results
            .filterKeys { it in compared }
            .mapNotNull { (id, result) -> (result as? ProbeResult.Answered)?.let { id to it.ms } }
            .minByOrNull { it.second }
            ?.first
    }
}
