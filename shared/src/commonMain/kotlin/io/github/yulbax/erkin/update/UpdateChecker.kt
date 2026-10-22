package io.github.yulbax.erkin.update

import io.github.yulbax.erkin.util.VersionInfo
import io.github.yulbax.erkin.vpn.DownloadRoute

class UpdateChecker(
    private val versionInfo: VersionInfo,
    private val platform: UpdatePlatform,
    route: DownloadRoute = DownloadRoute(),
    private val latest: suspend () -> AppRelease? = { GitHubReleases.latest(route) }
) {
    val currentVersion: AppVersion?
        get() = versionInfo.appVersion?.let(AppVersion::parse)

    val currentLabel: String?
        get() = currentVersion?.let { version -> label(version, versionInfo.shortBuild) }

    suspend fun check(): AppRelease? {
        val current = currentVersion ?: return null
        return latest()?.takeIf { it.version > current || it.version.compareTo(current) == 0 && rebuilt(it) }
    }

    fun label(release: AppRelease): String = label(release.version, asset(release)?.build)

    private fun rebuilt(release: AppRelease): Boolean {
        val build = versionInfo.build ?: return false
        val asset = asset(release) ?: return false
        return asset.build != null && !asset.builtFrom(build)
    }

    private fun asset(release: AppRelease): ReleaseAsset? = platform.assetSuffix?.let(release::asset)

    private fun label(version: AppVersion, build: String?): String =
        if (build == null) version.toString() else "$version ($build)"
}
