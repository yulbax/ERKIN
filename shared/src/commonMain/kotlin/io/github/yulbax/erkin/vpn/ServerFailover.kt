package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.data.profile.GroupKind
import io.github.yulbax.erkin.data.profile.runnableIn
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.ProbeResult

class ServerFailover(
    private val store: VpnSessionStore,
    private val checks: ServerChecks,
    private val log: AppLog
) {
    suspend fun replace(testAgain: Boolean): Boolean {
        val inputs = store.load()
        val serverId = inputs.selected?.id ?: return false
        val group = store.selectedGroup()
        if (group == null) {
            log.i(TAG, "server $serverId is not in a group; nothing to switch to")
            return false
        }
        val candidates = inputs.profiles.runnableIn(group.id).filter { it.id != serverId }
        val target = checks.firstAnswering(candidates.map { it.id })?.let { id -> candidates.first { it.id == id } }
        if (target == null) {
            log.w(TAG, "no other server of group ${group.id} answers; keeping server $serverId")
            return false
        }
        if (testAgain) {
            val result = checks.check(listOf(serverId))?.get(serverId)
            if (result is ProbeResult.Answered) {
                log.i(TAG, "server $serverId answers again ($result); keeping it")
                return false
            }
        }
        log.i(TAG, "switching from server $serverId to ${target.id}, the first server of group ${group.id} to answer")
        store.select(target)
        return true
    }

    suspend fun refreshAndReplace(): Boolean {
        refreshSubscription()
        return replace(testAgain = false)
    }

    suspend fun refreshSubscription() {
        val group = store.selectedGroup()
        (group?.kind as? GroupKind.Subscription)?.let { refresh(group.id, it.url) }
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

    private companion object {
        const val TAG = "ServerFailover"
    }
}
