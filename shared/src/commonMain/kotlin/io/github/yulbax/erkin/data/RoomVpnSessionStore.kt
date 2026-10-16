package io.github.yulbax.erkin.data

import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.vpn.SessionInputs
import io.github.yulbax.erkin.vpn.VpnSessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomVpnSessionStore(
    private val database: AppDatabase,
    private val profileRepository: ProfileRepository
) : VpnSessionStore {

    override suspend fun refreshProfiles() = profileRepository.refreshDescriptors()

    override suspend fun load(): SessionInputs {
        val profileDao = database.profileDao()
        return SessionInputs(
            selected = profileDao.getSelected(),
            profiles = profileDao.getAll(),
            settings = database.settingsDao().getSettings() ?: SettingsEntity(),
            apps = database.appDao().getAllAppsSnapshot(),
            rules = database.routingRuleDao().getAll().mapNotNull { it.toRule() }
        )
    }

    override fun routingChanges(): Flow<Unit> =
        combine(
            database.profileDao().observeAll(),
            database.profileDao().observeSelected(),
            database.appDao().getAllApps(),
            database.routingRuleDao().observeAll()
        ) { profiles, selected, apps, rules -> listOf(profiles, selected, apps, rules) }
            .distinctUntilChanged()
            .map { }

    override suspend fun selectedSubscription(): ProfileEntity? =
        database.profileDao().getSelected()?.takeIf { it.subscriptionUrl.isNotBlank() }

    override suspend fun refreshSubscription(profile: ProfileEntity): ProfileOperationResult =
        profileRepository.refreshSubscription(profile)
}
