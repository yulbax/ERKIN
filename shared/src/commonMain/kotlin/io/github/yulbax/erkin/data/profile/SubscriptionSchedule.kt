package io.github.yulbax.erkin.data.profile

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

object SubscriptionSchedule {
    const val DEFAULT_HOURS = 24
    private const val MIN_HOURS = 1
    private val retryAfter = 1.hours

    fun intervalHours(updateHours: Int?): Int = (updateHours ?: DEFAULT_HOURS).coerceAtLeast(MIN_HOURS)

    fun interval(updateHours: Int?): Duration = intervalHours(updateHours).hours

    fun isDue(group: ServerGroupEntity, nowMs: Long, failedAtMs: Long?): Boolean {
        if (group.kind !is GroupKind.Subscription) return false
        val sinceUpdate = (nowMs - group.updatedAt).coerceAtLeast(0)
        val sinceFailure = failedAtMs?.let { (nowMs - it).coerceAtLeast(0) }
        return sinceUpdate >= interval(group.updateHours).inWholeMilliseconds &&
            (sinceFailure == null || sinceFailure >= retryAfter.inWholeMilliseconds)
    }
}
