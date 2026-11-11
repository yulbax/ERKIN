package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.data.profile.GroupKind
import io.github.yulbax.erkin.data.profile.isLink
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.ProbeResult

class ServerFailover(
    private val store: VpnSessionStore,
    private val latency: ServerLatencyProbe,
    private val log: AppLog
) {
    suspend fun switchToFastest() {
        val group = store.selectedGroup()
        if (group == null) {
            log.i(TAG, "the selected server is not in a group; nothing to switch to")
            return
        }
        when (val kind = group.kind) {
            is GroupKind.Subscription -> refresh(group.id, kind.url)
            GroupKind.Local -> Unit
        }
        val inputs = store.load()
        val candidates = inputs.profiles.filter { it.isLink && it.groupId == group.id }
        val ids = candidates.map { it.id }.toSet()
        val results = latency.measure(ids).filterKeys { it in ids }
        val target = fastest(results)?.let { id -> candidates.first { it.id == id } }
        when {
            target == null -> log.w(TAG, "no server of group ${group.id} answers; keeping the current one")
            target.id == inputs.selected?.id -> log.i(TAG, "the current server answers the latency test; keeping it")
            else -> {
                log.i(TAG, "switching to the fastest server ${target.id} (${results[target.id]})")
                store.select(target)
            }
        }
    }

    private suspend fun refresh(id: Long, url: String) {
        log.i(TAG, "refreshing subscription $id")
        when (val result = store.refreshSubscription(url)) {
            is ProfileOperationResult.SubscriptionSynced ->
                log.i(TAG, "subscription refreshed: +${result.added} ~${result.updated} -${result.removed}")
            is ProfileOperationResult.Success -> Unit
            ProfileOperationResult.EmptySubscription ->
                log.i(TAG, "subscription refresh returned no servers; keeping the stored list")
            is ProfileOperationResult.FetchFailed ->
                log.w(TAG, "subscription refresh failed; keeping the stored list", result.cause)
            ProfileOperationResult.InvalidLink ->
                log.w(TAG, "subscription refresh rejected an invalid link; keeping the stored list")
            is ProfileOperationResult.Refused ->
                log.w(TAG, "subscription refused this device: ${result.refusal}; keeping the stored list")
        }
    }

    companion object {
        private const val TAG = "ServerFailover"

        fun fastest(results: Map<Long, ProbeResult>): Long? =
            results.mapNotNull { (id, result) -> (result as? ProbeResult.Answered)?.let { id to it.ms } }
                .minByOrNull { it.second }
                ?.first
    }
}
