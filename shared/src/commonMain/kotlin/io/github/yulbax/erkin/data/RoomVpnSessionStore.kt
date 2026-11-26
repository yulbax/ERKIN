package io.github.yulbax.erkin.data

import io.github.yulbax.erkin.data.profile.DueSubscriptionRefresher
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.ServerGroupEntity
import io.github.yulbax.erkin.vpn.SessionInputs
import io.github.yulbax.erkin.vpn.VpnSessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomVpnSessionStore(
    private val database: AppDatabase,
    private val profileRepository: ProfileRepository,
    private val dueSubscriptions: DueSubscriptionRefresher,
    private val platformNewAppsChannel: ConnectionType
) : VpnSessionStore {

    override suspend fun refreshProfiles() = profileRepository.refreshDescriptors()

    override suspend fun load(): SessionInputs {
        val profileDao = database.profileDao()
        val settings = database.settingsDao().getSettings() ?: SettingsEntity()
        val selected = profileDao.getSelected()
        return SessionInputs(
            selected = selected,
            profiles = profileDao.getAll(),
            settings = settings,
            apps = database.appDao().getAllAppsSnapshot(),
            rules = database.routingRuleDao().getAll().mapNotNull { it.toRule() },
            newAppsChannel = settings.newAppsConnectionType(platformNewAppsChannel),
            subscriptionRules = selected?.groupId?.let { database.serverGroupDao().get(it) }?.appliedRules
        )
    }

    override fun routingChanges(): Flow<Unit> =
        combine(
            combine(
                database.profileDao().observeAll(),
                database.profileDao().observeSelected(),
                database.appDao().getAllApps(),
                database.routingRuleDao().observeAll(),
                database.settingsDao().observeSettings().map { it?.newAppsConnectionType }
            ) { profiles, selected, apps, rules, newApps -> listOf(profiles, selected, apps, rules, newApps) },
            database.serverGroupDao().observeAll().map { groups -> groups.map { Triple(it.id, it.rulesEnabled, it.rules) } }
        ) { routing, subscriptionRules -> listOf(routing, subscriptionRules) }
            .distinctUntilChanged()
            .map { }

    override fun byeDpiArgsChanges(): Flow<String> =
        database.settingsDao().observeSettings()
            .map { it?.byeDpiArgs.orEmpty() }
            .distinctUntilChanged()

    override suspend fun selectedGroup(): ServerGroupEntity? =
        database.profileDao().getSelected()?.groupId?.let { database.serverGroupDao().get(it) }

    override suspend fun refreshSubscription(url: String): ProfileOperationResult =
        profileRepository.refreshSubscription(url)

    override suspend fun refreshDueSubscriptions() = dueSubscriptions.refreshDue()

    override suspend fun select(profile: ProfileEntity) = profileRepository.select(profile)
}
