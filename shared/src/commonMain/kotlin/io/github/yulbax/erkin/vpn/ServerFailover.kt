package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.data.profile.SubscriptionEntity
import io.github.yulbax.erkin.data.profile.isLink
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.ProxyDelay

class ServerFailover(
    private val store: VpnSessionStore,
    private val latency: ServerLatencyProbe,
    private val log: AppLog
) {
    suspend fun switchToFastest() {
        val subscription = store.selectedSubscription()
        if (subscription == null) {
            log.i(TAG, "the selected server is not from a subscription; nothing to switch to")
            return
        }
        refresh(subscription)
        val inputs = store.load()
        val candidates = inputs.profiles.filter { it.isLink && it.subscriptionId == subscription.id }
        val ids = candidates.map { it.id }.toSet()
        val delays = latency.measure(ids).filterKeys { it in ids }
        val target = fastest(delays)?.let { id -> candidates.first { it.id == id } }
        when {
            target == null -> log.w(TAG, "no server of subscription ${subscription.id} answers; keeping the current one")
            target.id == inputs.selected?.id -> log.i(TAG, "the current server answers the latency test; keeping it")
            else -> {
                log.i(TAG, "switching to the fastest server ${target.id} (${delays[target.id]})")
                store.select(target)
            }
        }
    }

    private suspend fun refresh(subscription: SubscriptionEntity) {
        log.i(TAG, "refreshing subscription ${subscription.id}")
        when (val result = store.refreshSubscription(subscription.url)) {
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

        fun fastest(delays: Map<Long, ProxyDelay>): Long? =
            delays.mapNotNull { (id, delay) -> (delay as? ProxyDelay.Measured)?.let { id to it.ms } }
                .minByOrNull { it.second }
                ?.first
    }
}
