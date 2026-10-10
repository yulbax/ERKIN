package io.github.yulbax.erkin.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object GitHubReleases {
    private const val LATEST_URL = "https://api.github.com/repos/yulbax/ERKIN/releases/latest"
    private const val TIMEOUT_MS = 15_000
    private const val SHA256_PREFIX = "sha256:"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(): AppRelease? = parse(runInterruptible(Dispatchers.IO) { download() })

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
            }
        )
    }

    private fun download(): String {
        val connection = URI(LATEST_URL).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.use { it.readBytes().decodeToString() }
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
