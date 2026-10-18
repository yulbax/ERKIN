package io.github.yulbax.erkin.update

import io.github.yulbax.erkin.util.readBounded
import java.io.IOException
import io.github.yulbax.erkin.vpn.DownloadRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object GitHubReleases {
    private const val LATEST_URL = "https://api.github.com/repos/yulbax/ERKIN/releases/latest"
    private const val TIMEOUT_MS = 15_000
    private const val MAX_BYTES = 1L shl 20
    private const val SHA256_PREFIX = "sha256:"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(route: DownloadRoute): AppRelease? = parse(runInterruptible(Dispatchers.IO) { download(route) })

    fun parse(body: String): AppRelease? {
        val release = json.decodeFromString<ReleaseJson>(body)
        if (release.draft || release.prerelease) return null
        val version = AppVersion.parse(release.tag) ?: return null
        return AppRelease(
            version = version,
            pageUrl = release.page,
            assets = release.assets.map { asset ->
                ReleaseAsset(
                    name = asset.name,
                    url = asset.url,
                    size = asset.size,
                    sha256 = asset.digest?.takeIf { it.startsWith(SHA256_PREFIX) }?.removePrefix(SHA256_PREFIX)
                )
            },
            notes = release.body.orEmpty().trim()
        )
    }

    private fun download(route: DownloadRoute): String = route.fetch(LATEST_URL) { connection ->
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            connection.inputStream.use { it.readBounded(MAX_BYTES).decodeToString() }
        } finally {
            connection.disconnect()
        }
    }

    @Serializable
    private class ReleaseJson(
        @SerialName("tag_name") val tag: String,
        @SerialName("html_url") val page: String,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val body: String? = null,
        val assets: List<AssetJson> = emptyList()
    )

    @Serializable
    private class AssetJson(
        val name: String,
        @SerialName("browser_download_url") val url: String,
        val size: Long = 0,
        val digest: String? = null
    )
}
