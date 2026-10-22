package io.github.yulbax.erkin.util

import android.content.Context
import android.os.Build
import io.github.yulbax.erkin.data.profile.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Diagnostics {
    suspend fun collect(
        context: Context,
        erkinLog: ErkinLog,
        versionInfo: VersionInfo,
        profiles: ProfileRepository
    ): String = withContext(Dispatchers.IO) {
        DiagnosticsReport.build(
            environment = listOf(
                "app" to "${versionInfo.appVersion ?: "unknown"} (build ${versionInfo.shortBuild ?: "unknown"}, core ${versionInfo.coreVersion() ?: "unknown"}, byedpi ${versionInfo.byeDpiVersion})",
                "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                "abi" to Build.SUPPORTED_ABIS.joinToString(",")
            ),
            log = erkinLog.dump(),
            profiles = profiles.profileDiagnostics()
        )
    }
}
