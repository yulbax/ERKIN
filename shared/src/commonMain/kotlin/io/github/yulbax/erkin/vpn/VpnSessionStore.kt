package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.SettingsEntity
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import kotlinx.coroutines.flow.Flow

data class SessionInputs(
    val selected: ProfileEntity?,
    val profiles: List<ProfileEntity>,
    val settings: SettingsEntity,
    val apps: List<App>
)

interface VpnSessionStore {
    suspend fun refreshProfiles()

    suspend fun load(): SessionInputs

    fun routingChanges(): Flow<Unit>

    suspend fun selectedSubscription(): ProfileEntity?

    suspend fun refreshSubscription(profile: ProfileEntity): ProfileOperationResult
}
