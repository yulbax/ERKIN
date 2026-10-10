package io.github.yulbax.erkin.util

import android.content.Context
import android.os.Build
import io.github.yulbax.erkin.data.profile.ProfileRepository
import java.io.File
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
                "app" to "${versionInfo.appVersion ?: "unknown"} (core ${versionInfo.coreVersion() ?: "unknown"}, byedpi ${versionInfo.byeDpiVersion})",
                "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                "abi" to Build.SUPPORTED_ABIS.joinToString(",")
            ),
            appLog = erkinLog.dump(),
            boxLog = File(File(context.filesDir, "work"), "box.log"),
            profiles = profiles.profileDiagnostics()
        )
    }
}
