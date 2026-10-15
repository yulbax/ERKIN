package io.github.yulbax.erkin.util

import android.content.Context
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.engine.BYEDPI_VERSION
import io.github.yulbax.erkin.vpn.ErkinVpnService
import io.github.yulbax.erkin.vpn.mihomo.MihomoRuntime
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihomocore.Mihomocore
import org.koin.core.annotation.Single

@Single(binds = [VpnLauncher::class])
class AndroidVpnLauncher(private val context: Context) : VpnLauncher {
    override fun start() = ErkinVpnService.start(context)
}

@Single(binds = [DiagnosticsSource::class])
class AndroidDiagnosticsSource(
    private val context: Context,
    private val erkinLog: ErkinLog,
    private val versionInfo: VersionInfo,
    private val profiles: ProfileRepository
) : DiagnosticsSource {
    override suspend fun collect(): String = Diagnostics.collect(context, erkinLog, versionInfo, profiles)

    override suspend fun clear() = withContext(Dispatchers.IO) {
        erkinLog.clear()
        runCatching { RandomAccessFile(MihomoRuntime.logFile(context), "rw").use { it.setLength(0) } }
        Unit
    }
}

@Single(binds = [VersionInfo::class])
class AndroidVersionInfo(private val context: Context) : VersionInfo {
    override val appVersion: String?
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()

    override val byeDpiVersion: String = BYEDPI_VERSION

    override suspend fun coreVersion(): String? = withContext(Dispatchers.IO) {
        runCatching { Mihomocore.version() }.getOrNull()
    }
}
