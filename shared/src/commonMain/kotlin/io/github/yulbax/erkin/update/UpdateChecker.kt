package io.github.yulbax.erkin.update

import io.github.yulbax.erkin.util.VersionInfo

class UpdateChecker(
    private val versionInfo: VersionInfo,
    private val latest: suspend () -> AppRelease? = GitHubReleases::latest
) {
    val currentVersion: AppVersion?
        get() = versionInfo.appVersion?.let(AppVersion::parse)

    suspend fun check(): AppRelease? {
        val current = currentVersion ?: return null
        return latest()?.takeIf { it.version > current }
    }
}
