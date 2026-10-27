package io.github.yulbax.erkin.di

import android.content.Context
import androidx.room.Room
import io.github.yulbax.erkin.data.AppDao
import io.github.yulbax.erkin.data.AppDatabase
import io.github.yulbax.erkin.data.ConfigBackupRepository
import io.github.yulbax.erkin.data.RoomVpnSessionStore
import io.github.yulbax.erkin.data.SettingsDao
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.data.byedpi.ByeDpiHistory
import io.github.yulbax.erkin.data.profile.ProfileDao
import io.github.yulbax.erkin.data.rules.RoutingRuleRepository
import io.github.yulbax.erkin.rulelist.RuleListDatabase
import io.github.yulbax.erkin.update.UpdateChecker
import io.github.yulbax.erkin.update.UpdatePlatform
import io.github.yulbax.erkin.vpn.mihomo.AndroidRuleListStore
import io.github.yulbax.erkin.update.UpdateDownloader
import io.github.yulbax.erkin.util.AndroidDeviceIdentity
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.util.VersionInfo
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.SubscriptionProfileSource
import io.github.yulbax.erkin.data.profile.HttpSubscriptionProfileSource
import io.github.yulbax.erkin.vpn.DownloadRoute
import io.github.yulbax.erkin.vpn.ServerLatencyProbe
import io.github.yulbax.erkin.vpn.mihomo.MihomoLatencyTester
import io.github.yulbax.erkin.vpn.VpnCommandBus
import io.github.yulbax.erkin.vpn.VpnSessionFactory
import io.github.yulbax.erkin.vpn.VpnSessionStore
import io.github.yulbax.erkin.vpn.VpnStateRepository
import io.github.yulbax.erkin.vpn.ByeDpiRunner
import io.github.yulbax.erkin.vpn.ByeDpiSiteCheck
import io.github.yulbax.erkin.vpn.ByeDpiStrategyRunner
import io.github.yulbax.erkin.vpn.CiadpiProcess
import io.github.yulbax.erkin.vpn.mihomo.MihomoSiteTester
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import io.github.yulbax.erkin.vpn.AndroidTestNetworkSource
import io.github.yulbax.erkin.vpn.core.TestNetworkSource
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
    fun byeDpiHistory(database: AppDatabase, settingsRepository: SettingsRepository): ByeDpiHistory =
        ByeDpiHistory(database, settingsRepository)

    @Single
    fun testNetworkSource(context: Context): TestNetworkSource = AndroidTestNetworkSource(context.applicationContext)

    @Single
    fun byeDpiSiteCheck(context: Context, log: AppLog): ByeDpiSiteCheck =
        ByeDpiSiteCheck(MihomoSiteTester(context.applicationContext, log))

    @Single
    fun byeDpiStrategyRunner(context: Context, siteCheck: ByeDpiSiteCheck, log: AppLog): ByeDpiStrategyRunner {
        val binary = File(context.applicationInfo.nativeLibraryDir, ByeDpiRunner.BINARY_NAME)
        return ByeDpiStrategyRunner(
            newProcess = { CiadpiProcess(binary, null, log) },
            siteCheck = siteCheck,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            log = log
        )
    }

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
    fun downloadRoute(): DownloadRoute = DownloadRoute()

    @Single
    fun ruleListDatabase(context: Context, database: AppDatabase, route: DownloadRoute): RuleListDatabase =
        RuleListDatabase(AndroidRuleListStore(context.applicationContext), database.ruleListDao(), route)

    @Single
    fun vpnSessionFactory(
        store: VpnSessionStore,
        stateRepository: VpnStateRepository,
        ruleLists: RuleListDatabase,
        route: DownloadRoute,
        siteCheck: ByeDpiSiteCheck,
        log: AppLog
    ): VpnSessionFactory = VpnSessionFactory(store, stateRepository, ruleLists, route, siteCheck, log)

    @Single
    fun vpnSessionStore(database: AppDatabase, profileRepository: ProfileRepository): VpnSessionStore =
        RoomVpnSessionStore(database, profileRepository)

    @Single
    fun configBackupRepository(database: AppDatabase, profileRepository: ProfileRepository): ConfigBackupRepository =
        ConfigBackupRepository(database, profileRepository)

    @Single
    fun subscriptionProfileSource(context: Context, route: DownloadRoute): SubscriptionProfileSource =
        HttpSubscriptionProfileSource(route, AndroidDeviceIdentity.of(context))

    @Single
    fun vpnStateRepository(): VpnStateRepository = VpnStateRepository()

    @Single
    fun vpnCommandBus(): VpnCommandBus = VpnCommandBus()

    @Single
    fun updateChecker(versionInfo: VersionInfo, platform: UpdatePlatform, route: DownloadRoute): UpdateChecker =
        UpdateChecker(versionInfo, platform, route)

    @Single
    fun updateDownloader(route: DownloadRoute): UpdateDownloader = UpdateDownloader(route)

    @Single
    fun serverLatencyProbe(context: Context, store: VpnSessionStore, log: AppLog): ServerLatencyProbe =
        ServerLatencyProbe(store, MihomoLatencyTester(context.applicationContext, log))
}
