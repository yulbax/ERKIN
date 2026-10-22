package io.github.yulbax.erkin.update

import io.github.yulbax.erkin.util.VersionInfo
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    @Test
    fun versionsCompareNumerically() {
        assertTrue(AppVersion.parse("v1.0.10")!! > AppVersion.parse("1.0.9")!!)
        assertTrue(AppVersion.parse("1.1")!! > AppVersion.parse("1.0.5")!!)
        assertEquals(0, AppVersion.parse("1.0")!!.compareTo(AppVersion.parse("1.0.0")!!))
        assertNull(AppVersion.parse("1.0.0-beta"))
    }

    @Test
    fun parsesLatestReleaseAndPicksAsset() {
        val release = GitHubReleases.parse(
            """
            {"tag_name":"v1.0.1","html_url":"https://github.com/yulbax/ERKIN/releases/tag/v1.0.1","draft":false,"prerelease":false,
             "body":"## ERKIN 1.0.1\r\n- Fixes\r\n",
             "assets":[
               {"name":"ERKIN-1.0.1-android-arm64-v8a.apk","browser_download_url":"https://example.com/a.apk","size":42,"digest":"sha256:abc"},
               {"name":"ERKIN-1.0.1-windows-x86_64.msi","browser_download_url":"https://example.com/w.msi","size":7,
                "label":"ERKIN-1.0.1-windows-x86_64.msi (1cdb23f)"}
             ]}
            """.trimIndent()
        )!!
        assertEquals("1.0.1", release.version.toString())
        assertEquals("## ERKIN 1.0.1\r\n- Fixes", release.notes)
        assertEquals("abc", release.asset("android-arm64-v8a.apk")!!.sha256)
        assertNull(release.asset("windows-x86_64.msi")!!.sha256)
        assertNull(release.asset("android-arm64-v8a.apk")!!.build)
        assertEquals("1cdb23f", release.asset("windows-x86_64.msi")!!.build)
        assertNull(release.asset("android-x86_64.apk"))
    }

    @Test
    fun ignoresPrereleases() {
        assertNull(GitHubReleases.parse("""{"tag_name":"v2.0.0","html_url":"x","prerelease":true}"""))
    }

    @Test
    fun reportsOnlyNewerReleases() = runBlocking {
        fun checker(installed: String) = UpdateChecker(FixedVersion(installed), Platform) {
            AppRelease(AppVersion.parse("1.0.1")!!, "page", emptyList())
        }
        assertEquals("1.0.1", checker("1.0.0").check()?.version.toString())
        assertNull(checker("1.0.1").check())
        assertNull(checker("1.1.0").check())
    }

    @Test
    fun reportsRebuildsOfTheInstalledVersionForThisPlatformOnly() = runBlocking {
        val release = AppRelease(
            AppVersion.parse("1.0.1")!!,
            "page",
            listOf(
                ReleaseAsset("ERKIN-1.0.1-$SUFFIX", "a", 1, null, build = "bbbbbbb"),
                ReleaseAsset("ERKIN-1.0.1-windows-x86_64.msi", "w", 1, null, build = "aaaaaaa")
            )
        )
        fun checker(installed: String, build: String?) = UpdateChecker(FixedVersion(installed, build), Platform) { release }
        val outdated = checker("1.0.1", "a".repeat(40))
        assertEquals("1.0.1 (bbbbbbb)", outdated.check()?.let(outdated::label))
        assertNull(checker("1.0.1", "b".repeat(40)).check())
        assertNull(checker("1.0.1", null).check())
        assertNull(checker("1.0.2", "a".repeat(40)).check())
        val unlabelled = AppRelease(release.version, "page", listOf(ReleaseAsset("ERKIN-1.0.1-$SUFFIX", "a", 1, null)))
        assertNull(UpdateChecker(FixedVersion("1.0.1", "a".repeat(40)), Platform) { unlabelled }.check())
    }

    private object Platform : UpdatePlatform {
        override val assetSuffix: String = SUFFIX
        override val downloadDir: File = File("build/update-test")
        override fun install(file: File) = Unit
    }

    private class FixedVersion(override val appVersion: String, override val build: String? = null) : VersionInfo {
        override val byeDpiVersion: String = ""

        override suspend fun coreVersion(): String? = null
    }

    private companion object {
        const val SUFFIX = "android-arm64-v8a.apk"
    }
}
