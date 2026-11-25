package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.ui.viewmodel.IncomingServers
import io.github.yulbax.erkin.ui.platform.appsCanBypass
import androidx.room.Room
import io.github.yulbax.erkin.data.AppDatabase
import io.github.yulbax.erkin.data.ConfigBackupRepository
import io.github.yulbax.erkin.data.InstalledAppsSource
import io.github.yulbax.erkin.data.RoomVpnSessionStore
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.data.byedpi.ByeDpiHistory
import io.github.yulbax.erkin.data.byedpi.ByeDpiSiteLists
import io.github.yulbax.erkin.vpn.core.TestNetwork
import io.github.yulbax.erkin.vpn.core.TestNetworkSource
import io.github.yulbax.erkin.data.profile.HttpSubscriptionProfileSource
import io.github.yulbax.erkin.data.profile.DueSubscriptionRefresher
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.SubscriptionProfileSource
import io.github.yulbax.erkin.data.rules.RoutingRuleRepository
import io.github.yulbax.erkin.rulelist.RuleListDatabase
import io.github.yulbax.erkin.update.UpdateChecker
import io.github.yulbax.erkin.update.UpdateDownloader
import io.github.yulbax.erkin.update.UpdatePlatform
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.util.DiagnosticsSource
import io.github.yulbax.erkin.util.FileAppLog
import io.github.yulbax.erkin.util.LogSource
import io.github.yulbax.erkin.util.VersionInfo
import io.github.yulbax.erkin.util.VpnLauncher
import io.github.yulbax.erkin.vpn.CiadpiProcess
import io.github.yulbax.erkin.vpn.ByeDpiSiteCheck
import io.github.yulbax.erkin.vpn.ByeDpiStrategyRunner
import io.github.yulbax.erkin.vpn.DownloadRoute
import io.github.yulbax.erkin.vpn.ProbeResults
import io.github.yulbax.erkin.vpn.ProbeUrls
import io.github.yulbax.erkin.vpn.ServerChecks
import io.github.yulbax.erkin.vpn.ServerLatencyProbe
import io.github.yulbax.erkin.vpn.connections.ConnectionMonitor
import io.github.yulbax.erkin.vpn.VpnCommandBus
import io.github.yulbax.erkin.vpn.VpnController
import io.github.yulbax.erkin.vpn.VpnSessionFactory
import io.github.yulbax.erkin.vpn.VpnSessionStore
import io.github.yulbax.erkin.vpn.VpnStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import org.koin.dsl.module

val desktopModule = module {
    includes(platformModule)
    single { FileAppLog(DesktopPaths.logFile) { level, tag, text -> println("${level.symbol}/$tag: $text") } }
    single<AppLog> { get<FileAppLog>() }

    single { AppDatabase.build(Room.databaseBuilder<AppDatabase>(name = DesktopPaths.database.absolutePath), appsCanBypass) }
    single { get<AppDatabase>().appDao() }
    single { get<AppDatabase>().settingsDao() }
    single { get<AppDatabase>().profileDao() }
    single { SettingsRepository(get(), get()) }
    single { ByeDpiHistory(get(), get()) }
    single { TestNetworkSource.fixed(TestNetwork.Unknown) }
    single { ByeDpiSiteLists(get()) }
    single { ByeDpiSiteCheck(ServiceSiteTester(get()), get<ByeDpiSiteLists>()::active) }
    single {
        val log = get<AppLog>()
        ByeDpiStrategyRunner(
            newProcess = {
                CiadpiProcess(DesktopPaths.ciadpi, get<ServiceHost>().protectSocket.absolutePath, log, get<DesktopSystem>().ciadpiUnsupportedOptions)
            },
            siteCheck = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            log = log
        )
    }
    single { DownloadRoute() }
    single<SubscriptionProfileSource> { HttpSubscriptionProfileSource(get(), DesktopDeviceIdentity.load(get<DesktopSystem>().deviceOsName)) }
    single { ProfileRepository(get(), get(), get(), get()) }
    single { ConfigBackupRepository(get(), get(), get(), appsCanBypass) }
    single { RoutingRuleRepository(get()) }
    single { DueSubscriptionRefresher(get(), get()) }
    single<VpnSessionStore> { RoomVpnSessionStore(get(), get(), get(), get<DesktopSystem>().newAppsChannel) }

    single { VpnStateRepository() }
    single { IncomingServers() }
    single { VpnCommandBus() }
    single<DiagnosticsSource> { DesktopDiagnostics(get(), get(), get(), get()) }
    single<DesktopService> {
        val host = get<ServiceHost>()
        if (host.runsServiceItself) {
            DesktopService.Portable(
                command = host.serviceCommand,
                workDir = DesktopPaths.coreDir,
                protectSocket = host.protectSocket,
                installedSocket = DesktopPaths.serviceSocket,
                log = get()
            )
        } else {
            DesktopService.Installed(socket = DesktopPaths.serviceSocket, host = host, log = get())
        }
    }
    single<VersionInfo> { DesktopVersionInfo(get()) }
    single { RuleListDatabase(DesktopRuleListStore(get()), get<AppDatabase>().ruleListDao(), get()) }
    single { ServerLatencyProbe(get(), { get<VpnStateRepository>().stats.value.byeDpiPort }, ServiceLatencyTester(get())) }
    single { ConnectionMonitor(ServiceConnectionTable(get()), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single<InstalledAppsSource>(createdAtStart = true) {
        val settings = get<SettingsRepository>()
        ProcessInstalledApps(
            appDao = get(),
            own = DesktopPaths.ownApp,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            hostProcesses = { get<DesktopService>().running()?.processes().orEmpty() },
            programs = get()
        ) {
            settings.settings.first().newAppsConnectionType(get<DesktopSystem>().newAppsChannel)
        }
    }

    single { ProbeResults() }
    single { ServerChecks(get(), get(), get()) }
    single { VpnSessionFactory(get(), get(), get(), get(), get<ByeDpiSiteCheck>(), get(), get()) }
    single {
        val log = get<AppLog>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val sessions = get<VpnSessionFactory>()
        val service = get<DesktopService>()
        VpnController(scope, get(), get(), DesktopHost, log) { controller ->
            sessions.create(
                controller = controller,
                scope = scope,
                engine = ServiceEngine(
                    service = service,
                    directProcesses = listOf(DesktopPaths.ciadpi.name),
                    desktopOs = get<DesktopSystem>().desktopOs,
                    systemDns = get(),
                    listener = controller,
                    log = log
                ),
                byeDpi = CiadpiProcess(
                    DesktopPaths.ciadpi,
                    get<ServiceHost>().protectSocket.absolutePath,
                    log.of(LogSource.BYEDPI),
                    get<DesktopSystem>().ciadpiUnsupportedOptions
                ),
                byeDpiHealthProbeUrl = ProbeUrls.BYEDPI,
                ownPackage = DesktopPaths.ownExecutable.orEmpty()
            )
        }
    }
    single<VpnLauncher> { DesktopVpnLauncher(get()) }
    single { UpdateChecker(get(), get(), get()) }
    single { UpdateDownloader(get()) }
    single<UpdatePlatform> { DesktopUpdatePlatform(get(), get()) }
}
