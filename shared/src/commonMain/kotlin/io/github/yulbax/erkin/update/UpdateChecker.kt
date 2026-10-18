package io.github.yulbax.erkin.update

import io.github.yulbax.erkin.util.VersionInfo
import io.github.yulbax.erkin.vpn.DownloadRoute

class UpdateChecker(
    private val versionInfo: VersionInfo,
    route: DownloadRoute = DownloadRoute(),
    private val latest: suspend () -> AppRelease? = { GitHubReleases.latest(route) }
) {
    val currentVersion: AppVersion?
        get() = versionInfo.appVersion?.let(AppVersion::parse)

    suspend fun check(): AppRelease? {
        val current = currentVersion ?: return null
        return latest()?.takeIf { it.version > current }
    }
}
