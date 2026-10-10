package io.github.yulbax.erkin.update

import java.io.File

data class AppVersion(private val parts: List<Int>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val size = maxOf(parts.size, other.parts.size)
        for (index in 0 until size) {
            val diff = parts.getOrElse(index) { 0 }.compareTo(other.parts.getOrElse(index) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    override fun toString(): String = parts.joinToString(".")

    companion object {
        fun parse(text: String): AppVersion? {
            val parts = text.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: return null }
            return if (parts.isEmpty() || parts.any { it < 0 }) null else AppVersion(parts)
        }
    }
}

data class ReleaseAsset(val name: String, val url: String, val size: Long, val sha256: String?)

data class AppRelease(val version: AppVersion, val pageUrl: String, val assets: List<ReleaseAsset>) {
    fun asset(suffix: String): ReleaseAsset? = assets.firstOrNull { it.name == "ERKIN-$version-$suffix" }
}

interface UpdatePlatform {
    val assetSuffix: String?
    val downloadDir: File

    fun install(file: File)
}
