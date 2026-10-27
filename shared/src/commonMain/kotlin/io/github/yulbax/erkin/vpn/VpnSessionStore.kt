package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.SettingsEntity
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.data.profile.SubscriptionEntity
import io.github.yulbax.erkin.vpn.core.RoutingRule
import kotlinx.coroutines.flow.Flow

data class SessionInputs(
    val selected: ProfileEntity?,
    val profiles: List<ProfileEntity>,
    val settings: SettingsEntity,
    val apps: List<App>,
    val rules: List<RoutingRule> = emptyList()
)

interface VpnSessionStore {
    suspend fun refreshProfiles()

    suspend fun load(): SessionInputs

    fun routingChanges(): Flow<Unit>

    fun byeDpiArgsChanges(): Flow<String>

    suspend fun selectedSubscription(): SubscriptionEntity?

    suspend fun refreshSubscription(url: String): ProfileOperationResult
}
