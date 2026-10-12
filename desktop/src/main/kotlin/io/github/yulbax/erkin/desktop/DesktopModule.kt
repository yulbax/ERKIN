package io.github.yulbax.erkin.desktop

import androidx.room.Room
import io.github.yulbax.erkin.data.AppDatabase
import io.github.yulbax.erkin.data.ConfigBackupRepository
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.InstalledAppsSource
import io.github.yulbax.erkin.data.RoomVpnSessionStore
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.data.profile.HttpSubscriptionProfileSource
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.SubscriptionProfileSource
import io.github.yulbax.erkin.update.UpdateChecker
import io.github.yulbax.erkin.update.UpdateDownloader
import io.github.yulbax.erkin.update.UpdatePlatform
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.util.DiagnosticsSource
import io.github.yulbax.erkin.util.FileAppLog
import io.github.yulbax.erkin.util.VersionInfo
import io.github.yulbax.erkin.util.VpnLauncher
import io.github.yulbax.erkin.vpn.ByeDpiSiteProbe
import io.github.yulbax.erkin.vpn.HealthMonitor
import io.github.yulbax.erkin.vpn.ProbeUrls
import io.github.yulbax.erkin.vpn.SocksHealthProbe
import io.github.yulbax.erkin.vpn.SocksSiteProbe
import io.github.yulbax.erkin.vpn.VpnCommandBus
import io.github.yulbax.erkin.vpn.VpnController
import io.github.yulbax.erkin.vpn.VpnSession
import io.github.yulbax.erkin.vpn.VpnSessionStore
import io.github.yulbax.erkin.vpn.VpnStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import org.koin.dsl.module

val desktopModule = module {
    single { FileAppLog(DesktopPaths.logFile) { level, tag, text -> println("${level.symbol}/$tag: $text") } }
    single<AppLog> { get<FileAppLog>() }

    single { AppDatabase.build(Room.databaseBuilder<AppDatabase>(name = DesktopPaths.database.absolutePath)) }
    single { get<AppDatabase>().appDao() }
    single { get<AppDatabase>().settingsDao() }
    single { get<AppDatabase>().profileDao() }
    single { SettingsRepository(get(), get()) }
    single<SubscriptionProfileSource> { HttpSubscriptionProfileSource() }
    single { ProfileRepository(get(), get(), get(), get()) }
    single { ConfigBackupRepository(get(), get()) }
    single<VpnSessionStore> { RoomVpnSessionStore(get(), get()) }

    single { VpnStateRepository() }
    single { VpnCommandBus() }
    single<ByeDpiSiteProbe> { SocksSiteProbe }
    single<DiagnosticsSource> { DesktopDiagnostics(get(), get(), get(), get()) }
    single {
        DesktopService(
            socket = DesktopPaths.serviceSocket,
            bundled = DesktopPaths.serviceBinary,
            appDir = DesktopPaths.installDir,
            expectedVersion = System.getProperty("erkin.version"),
            log = get()
        )
    }
    single<VersionInfo> { DesktopVersionInfo(get()) }
    single<InstalledAppsSource>(createdAtStart = true) {
        val settings = get<SettingsRepository>()
        ProcessInstalledApps(get(), DesktopPaths.ownExecutable, CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            settings.settings.first().newAppsConnectionType(ConnectionType.DIRECT)
        }
    }

    single {
        val log = get<AppLog>()
        val stateRepository = get<VpnStateRepository>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        VpnController(scope, get(), stateRepository, DesktopHost, log) { controller ->
            VpnSession(
                engine = ServiceEngine(
                    service = get(),
                    directProcesses = listOf(DesktopPaths.ciadpi.name),
                    delayProbeUrl = ProbeUrls.VPN,
                    listener = controller,
                    log = log
                ),
                byeDpi = CiadpiProcess(DesktopPaths.ciadpi, DesktopPaths.protectSocket, log),
                store = get(),
                stateRepository = stateRepository,
                health = HealthMonitor(stateRepository, log, SocksHealthProbe(ProbeUrls.VPN, ProbeUrls.BYEDPI)),
                quality = SocksSiteProbe,
                log = log,
                ownPackage = DesktopPaths.ownExecutable.orEmpty(),
                scope = scope,
                requestWork = controller::requestWork,
                onByeDpiExit = controller::onByeDpiExit
            )
        }
    }
    single<VpnLauncher> { DesktopVpnLauncher(get()) }
    single { UpdateChecker(get()) }
    single { UpdateDownloader() }
    single<UpdatePlatform> { DesktopUpdatePlatform(get()) }
}
