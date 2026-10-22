package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.update.UpdatePlatform
import io.github.yulbax.erkin.util.DiagnosticsReport
import io.github.yulbax.erkin.util.DiagnosticsSource
import io.github.yulbax.erkin.util.LogFeed
import io.github.yulbax.erkin.util.FileAppLog
import io.github.yulbax.erkin.util.VersionInfo
import io.github.yulbax.erkin.util.VpnLauncher
import io.github.yulbax.erkin.vpn.VpnController
import io.github.yulbax.erkin.vpn.VpnHost
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlin.system.exitProcess
import kotlinx.coroutines.withContext

object DesktopHost : VpnHost {
    override fun onSessionStarting() = Unit

    override fun onSessionStarted() = Unit

    override fun allowsUserStop(): Boolean = true

    override fun onSessionStopped(stopToken: Int?, stopHost: Boolean) = Unit
}

class DesktopVpnLauncher(private val controller: VpnController) : VpnLauncher {
    override fun start() {
        controller.start(token = null, systemInitiated = false)
    }
}

class DesktopDiagnostics(
    private val log: FileAppLog,
    private val service: DesktopService,
    private val versionInfo: VersionInfo,
    private val profiles: ProfileRepository
) : DiagnosticsSource {
    override val feed: LogFeed get() = log

    override suspend fun collect(): String = withContext(Dispatchers.IO) {
        DiagnosticsReport.build(
            environment = listOf(
                "app" to "${versionInfo.appVersion ?: "unknown"} (build ${versionInfo.shortBuild ?: "unknown"}, core ${versionInfo.coreVersion() ?: "unknown"}, byedpi ${versionInfo.byeDpiVersion})",
                "os" to "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})",
                "java" to System.getProperty("java.version"),
                "service" to (service.peek()?.version ?: "not running")
            ),
            log = log.dump(),
            profiles = profiles.profileDiagnostics()
        )
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        log.clear()
        service.clearCoreLog()
    }
}

class DesktopVersionInfo(private val service: DesktopService) : VersionInfo {
    override val appVersion: String? = System.getProperty("erkin.version")
    override val build: String? = System.getProperty("erkin.build")?.ifBlank { null }
    override val byeDpiVersion: String = System.getProperty("erkin.byedpi.version") ?: "unknown"

    override suspend fun coreVersion(): String? = withContext(Dispatchers.IO) {
        service.peek()?.core?.ifBlank { null }
    }
}

class DesktopUpdatePlatform(private val controller: VpnController) : UpdatePlatform {
    override val assetSuffix: String? = System.getProperty("erkin.updateAsset")?.ifBlank { null }
    override val downloadDir: File = File(System.getProperty("java.io.tmpdir"), "erkin-updates")

    override fun install(file: File) {
        ProcessBuilder("msiexec", "/i", file.absolutePath).start()
        controller.shutdown()
        exitProcess(0)
    }
}
