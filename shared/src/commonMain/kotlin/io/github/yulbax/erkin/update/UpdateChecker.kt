package io.github.yulbax.erkin.update

import io.github.yulbax.erkin.util.VersionInfo
import io.github.yulbax.erkin.vpn.DownloadRoute

class UpdateChecker(
    private val versionInfo: VersionInfo,
    private val platform: UpdatePlatform,
    route: DownloadRoute = DownloadRoute(),
    private val releases: suspend () -> List<AppRelease> = { GitHubReleases.releases(route) }
) {
    val currentVersion: AppVersion?
        get() = versionInfo.appVersion?.let(AppVersion::parse)

    val currentLabel: String?
        get() = currentVersion?.let { version -> label(version, versionInfo.shortBuild) }

    suspend fun check(): AppRelease? {
        val current = currentVersion ?: return null
        val all = releases()
        val newer = all.filter { it.version > current }.sortedByDescending { it.version }
        val update = newer.firstOrNull()
            ?: all.firstOrNull { it.version.compareTo(current) == 0 && rebuilt(it) }
            ?: return null
        return update.copy(notes = changelog(newer.ifEmpty { listOf(update) }))
    }

    fun label(release: AppRelease): String = label(release.version, asset(release)?.build)

    private fun rebuilt(release: AppRelease): Boolean {
        val build = versionInfo.build ?: return false
        val asset = asset(release) ?: return false
        return asset.build != null && !asset.builtFrom(build)
    }

    private fun changelog(releases: List<AppRelease>): String =
        releases.singleOrNull()?.notes ?: releases
            .filter { it.notes.isNotBlank() }
            .joinToString("\n\n") { "## ${it.version}\n\n${it.notes}" }

    private fun asset(release: AppRelease): ReleaseAsset? = platform.assetSuffix?.let(release::asset)

    private fun label(version: AppVersion, build: String?): String =
        if (build == null) version.toString() else "$version ($build)"
}
