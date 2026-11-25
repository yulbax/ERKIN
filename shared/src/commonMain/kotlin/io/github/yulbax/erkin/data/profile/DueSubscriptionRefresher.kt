package io.github.yulbax.erkin.data.profile

import io.github.yulbax.erkin.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DueSubscriptionRefresher(
    private val repository: ProfileRepository,
    private val log: AppLog,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val failedAt = mutableMapOf<Long, Long>()

    suspend fun refreshDue() = mutex.withLock {
        val due = repository.groups.first().filter { SubscriptionSchedule.isDue(it, nowMs(), failedAt[it.id]) }
        due.forEach { group -> refresh(group.id) }
    }

    private suspend fun refresh(id: Long) {
        val result = try {
            repository.refreshSubscription(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(TAG, "scheduled refresh of subscription $id failed", e)
            failedAt[id] = nowMs()
            return
        }
        when (result) {
            is ProfileOperationResult.SubscriptionSynced, is ProfileOperationResult.Success -> {
                failedAt.remove(id)
                log.i(TAG, "scheduled refresh of subscription $id done")
            }
            ProfileOperationResult.EmptySubscription, ProfileOperationResult.InvalidLink,
            is ProfileOperationResult.FetchFailed, is ProfileOperationResult.Refused -> {
                failedAt[id] = nowMs()
                log.w(TAG, "scheduled refresh of subscription $id did not succeed: $result")
            }
        }
    }

    private companion object {
        const val TAG = "SubscriptionRefresh"
    }
}
