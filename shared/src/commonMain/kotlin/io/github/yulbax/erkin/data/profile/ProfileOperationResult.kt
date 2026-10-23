package io.github.yulbax.erkin.data.profile

sealed interface ProfileOperationResult {
    data class Success(val affected: Int = 1) : ProfileOperationResult
    data class SubscriptionSynced(val added: Int, val updated: Int, val removed: Int, val selectedDropped: Boolean) : ProfileOperationResult
    data object InvalidLink : ProfileOperationResult
    data object EmptySubscription : ProfileOperationResult
    data class FetchFailed(val cause: Throwable) : ProfileOperationResult
    data class Refused(val refusal: SubscriptionRefusal) : ProfileOperationResult
}
