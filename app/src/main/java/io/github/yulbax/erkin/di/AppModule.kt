package io.github.yulbax.erkin.di

import android.content.Context
import androidx.room.Room
import io.github.yulbax.erkin.data.AppDao
import io.github.yulbax.erkin.data.AppDatabase
import io.github.yulbax.erkin.data.ConfigBackupRepository
import io.github.yulbax.erkin.data.RoomVpnSessionStore
import io.github.yulbax.erkin.data.SettingsDao
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.data.profile.ProfileDao
import io.github.yulbax.erkin.data.rules.RoutingRuleRepository
import io.github.yulbax.erkin.rulelist.RuleListDatabase
import io.github.yulbax.erkin.update.UpdateChecker
import io.github.yulbax.erkin.vpn.mihomo.AndroidRuleListStore
import io.github.yulbax.erkin.update.UpdateDownloader
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.util.VersionInfo
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.SubscriptionProfileSource
import io.github.yulbax.erkin.data.profile.HttpSubscriptionProfileSource
import io.github.yulbax.erkin.vpn.SocksSiteProbe
import io.github.yulbax.erkin.vpn.ByeDpiSiteProbe
import io.github.yulbax.erkin.vpn.ServerLatencyProbe
import io.github.yulbax.erkin.vpn.mihomo.MihomoLatencyTester
import io.github.yulbax.erkin.vpn.VpnCommandBus
import io.github.yulbax.erkin.vpn.VpnSessionFactory
import io.github.yulbax.erkin.vpn.VpnSessionStore
import io.github.yulbax.erkin.vpn.VpnStateRepository
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("io.github.yulbax.erkin")
class AppModule {
    @Single
    fun database(context: Context): AppDatabase = AppDatabase.build(
        Room.databaseBuilder<AppDatabase>(
            context = context.applicationContext,
            name = context.getDatabasePath(AppDatabase.FILE_NAME).absolutePath
        )
    )

    @Single
    fun appDao(database: AppDatabase): AppDao = database.appDao()

    @Single
    fun settingsDao(database: AppDatabase): SettingsDao = database.settingsDao()

    @Single
    fun profileDao(database: AppDatabase): ProfileDao = database.profileDao()

    @Single
    fun settingsRepository(database: AppDatabase, settingsDao: SettingsDao): SettingsRepository =
        SettingsRepository(database, settingsDao)

    @Single
    fun profileRepository(
        database: AppDatabase,
        profileDao: ProfileDao,
        subscriptionSource: SubscriptionProfileSource,
        log: AppLog
    ): ProfileRepository = ProfileRepository(database, profileDao, subscriptionSource, log)

    @Single
    fun routingRuleRepository(database: AppDatabase): RoutingRuleRepository = RoutingRuleRepository(database)

    @Single
    fun ruleListDatabase(context: Context, database: AppDatabase): RuleListDatabase =
        RuleListDatabase(AndroidRuleListStore(context.applicationContext), database.ruleListDao())

    @Single
    fun vpnSessionFactory(
        store: VpnSessionStore,
        stateRepository: VpnStateRepository,
        ruleLists: RuleListDatabase,
        log: AppLog
    ): VpnSessionFactory = VpnSessionFactory(store, stateRepository, ruleLists, log)

    @Single
    fun vpnSessionStore(database: AppDatabase, profileRepository: ProfileRepository): VpnSessionStore =
        RoomVpnSessionStore(database, profileRepository)

    @Single
    fun configBackupRepository(database: AppDatabase, profileRepository: ProfileRepository): ConfigBackupRepository =
        ConfigBackupRepository(database, profileRepository)

    @Single
    fun byeDpiSiteProbe(): ByeDpiSiteProbe = SocksSiteProbe

    @Single
    fun subscriptionProfileSource(): SubscriptionProfileSource = HttpSubscriptionProfileSource()

    @Single
    fun vpnStateRepository(): VpnStateRepository = VpnStateRepository()

    @Single
    fun vpnCommandBus(): VpnCommandBus = VpnCommandBus()

    @Single
    fun updateChecker(versionInfo: VersionInfo): UpdateChecker = UpdateChecker(versionInfo)

    @Single
    fun updateDownloader(): UpdateDownloader = UpdateDownloader()

    @Single
    fun serverLatencyProbe(context: Context, store: VpnSessionStore): ServerLatencyProbe =
        ServerLatencyProbe(store, MihomoLatencyTester(context.applicationContext))
}
