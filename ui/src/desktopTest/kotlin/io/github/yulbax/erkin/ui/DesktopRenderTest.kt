package io.github.yulbax.erkin.ui

import io.github.yulbax.erkin.data.rules.RoutingRuleRepository
import io.github.yulbax.erkin.data.rules.RuleListEntity
import io.github.yulbax.erkin.rulelist.InstalledSet
import io.github.yulbax.erkin.rulelist.RuleListDatabase
import io.github.yulbax.erkin.rulelist.RuleListFailure
import io.github.yulbax.erkin.rulelist.RuleListStore
import io.github.yulbax.erkin.ui.screens.rules.RuleSets
import io.github.yulbax.erkin.ui.screens.rules.Rules
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.vpn.core.RuleSetFormat
import io.github.yulbax.erkin.vpn.core.RuleSetKind
import io.github.yulbax.erkin.vpn.core.RuleTarget
import java.nio.file.Files
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.room.Room
import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.AppDatabase
import io.github.yulbax.erkin.data.ConfigBackupRepository
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.InstalledApp
import io.github.yulbax.erkin.data.InstalledAppsSource
import io.github.yulbax.erkin.data.RoomVpnSessionStore
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.SubscriptionProfileSource
import io.github.yulbax.erkin.proxy.ParsedProfile
import io.github.yulbax.erkin.ui.components.UpdatePrompt
import io.github.yulbax.erkin.ui.di.uiModule
import io.github.yulbax.erkin.ui.screens.About
import io.github.yulbax.erkin.ui.screens.License
import io.github.yulbax.erkin.ui.screens.Logs
import io.github.yulbax.erkin.ui.screens.MainScreen
import io.github.yulbax.erkin.ui.screens.apps.Apps
import io.github.yulbax.erkin.ui.screens.settings.Settings
import io.github.yulbax.erkin.ui.theme.ERKINTheme
import io.github.yulbax.erkin.ui.viewmodel.UpdateViewModel
import io.github.yulbax.erkin.update.AppRelease
import io.github.yulbax.erkin.update.AppVersion
import io.github.yulbax.erkin.update.ReleaseAsset
import io.github.yulbax.erkin.update.UpdateChecker
import io.github.yulbax.erkin.update.UpdateDownloader
import io.github.yulbax.erkin.update.UpdatePlatform
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.util.DiagnosticsSource
import io.github.yulbax.erkin.util.VersionInfo
import io.github.yulbax.erkin.util.VpnLauncher
import io.github.yulbax.erkin.vpn.ByeDpiSiteProbe
import io.github.yulbax.erkin.vpn.ServerLatencyProbe
import io.github.yulbax.erkin.vpn.SiteGroup
import io.github.yulbax.erkin.vpn.SiteResult
import io.github.yulbax.erkin.vpn.VpnCommandBus
import io.github.yulbax.erkin.vpn.VpnStateRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class DesktopRenderTest {
    private lateinit var database: AppDatabase
    private val outputDir = File("build/screenshots").apply { mkdirs() }

    @Before
    fun setUp() {
        database = AppDatabase.build(Room.inMemoryDatabaseBuilder<AppDatabase>())
        val installedApps = listOf(
            InstalledApp("telegram.exe", "Telegram", isSystemApp = false, isLaunchable = true),
            InstalledApp("chrome.exe", "Google Chrome", isSystemApp = false, isLaunchable = true),
            InstalledApp("discord.exe", "Discord", isSystemApp = false, isLaunchable = true)
        )
        runBlocking {
            database.appDao().upsertApps(
                listOf(
                    App("telegram.exe", "Telegram", false, ConnectionType.VPN),
                    App("discord.exe", "Discord", false, ConnectionType.BYEDPI)
                )
            )
        }
        startKoin {
            modules(
                module {
                    single { database }
                    single { database.appDao() }
                    single { database.settingsDao() }
                    single { database.profileDao() }
                    single { SettingsRepository(get(), get()) }
                    single<SubscriptionProfileSource> { NoSubscriptions }
                    single { ProfileRepository(get(), get(), get(), get()) }
                    single { ConfigBackupRepository(get(), get()) }
                    single { ServerLatencyProbe(RoomVpnSessionStore(get(), get())) { _, _, _, _ -> "{}" } }
                    single { VpnStateRepository() }
                    single { VpnCommandBus() }
                    single<VpnLauncher> { VpnLauncher { } }
                    single<ByeDpiSiteProbe> { NoSiteProbe }
                    single<AppLog> { SilentLog }
                    single<DiagnosticsSource> {
                        object : DiagnosticsSource {
                            override suspend fun collect() = "=== ERKIN diagnostics ===\n" + "line\n".repeat(400)
                            override suspend fun clear() = Unit
                        }
                    }
                    single<VersionInfo> { FakeVersionInfo }
                    single<InstalledAppsSource> { FakeInstalledApps(installedApps) }
                    single { UpdateChecker(get()) { null } }
                    single { UpdateDownloader() }
                    single<UpdatePlatform> { NoUpdatePlatform }
                    single { RoutingRuleRepository(get()) }
                    single { RuleListDatabase(ruleLists, database.ruleListDao()) }
                },
                uiModule
            )
        }
        runBlocking {
            get<ProfileRepository>().add("vless://id@example.com:443?security=tls#Amsterdam")
            get<ProfileRepository>().add("trojan://pass@example.org:443#Frankfurt")
            val ads = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/multi-onlydomains.txt"
            val custom = "https://example.com/lists/blocked.txt"
            database.ruleListDao().upsert(RuleListEntity(ads, title = "HaGeZi's Multi NORMAL", entries = 164947))
            database.ruleListDao().upsert(RuleListEntity(custom, failure = RuleListFailure.NOT_A_LIST.wire))
            database.ruleListDao().upsert(RuleListEntity(RuleLists.country("RU").url, entries = 25194, kind = RuleSetKind.IPCIDR.behavior))
            File(ruleLists.dir, RuleLists.fileName(ads, RuleSetKind.DOMAIN)).writeText("")
            File(ruleLists.dir, RuleLists.fileName(RuleLists.country("RU").url, RuleSetKind.IPCIDR)).writeText("")
            val rules = get<RoutingRuleRepository>()
            rules.add(RuleMatch.RULE_SET, ads, RuleTarget.BLOCK)
            rules.add(RuleMatch.RULE_SET, RuleLists.country("RU").url, RuleTarget.DIRECT)
            rules.add(RuleMatch.RULE_SET, custom, RuleTarget.VPN)
            rules.add(RuleMatch.DOMAIN_SUFFIX, "example.org", RuleTarget.BYEDPI)
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        database.close()
        ruleLists.dir.deleteRecursively()
    }

    @Test
    fun rendersEveryScreenOnDesktop() {
        render("main", width = 420, height = 860) { MainScreen() }
        render("main-wide", width = 1100, height = 700) { MainScreen() }
        render("apps", width = 420, height = 860) { Apps(query = "") }
        render("settings", width = 420, height = 1400) { Settings() }
        render("logs", width = 420, height = 600) { Logs() }
        render("rules", width = 420, height = 860) { Rules(onOpenRuleSets = {}) }
        render("rule-sets", width = 420, height = 1900) { RuleSets() }
        render("about", width = 420, height = 600) { About() }
        render("license", width = 420, height = 600) { License() }
    }

    @Test
    fun rendersUpdatePrompt() {
        val release = AppRelease(
            version = AppVersion.parse("1.4.0")!!,
            pageUrl = "https://github.com/yulbax/ERKIN/releases/tag/v1.4.0",
            assets = listOf(ReleaseAsset("ERKIN-1.4.0-windows-x86_64.msi", "https://example.com", 1, null)),
            notes = "## ERKIN 1.4.0\n\n### Improvements\n- Faster **server test**\n- Smaller download"
        )
        render("update", width = 420, height = 600) {
            UpdatePrompt(UpdateViewModel(UpdateChecker(FakeVersionInfo) { release }, UpdateDownloader(), NoUpdatePlatform))
        }
    }

    private fun render(name: String, width: Int, height: Int, content: @Composable () -> Unit) = runBlocking {
        withContext(Dispatchers.Main) {
            val owner = TestOwner()
            val scene = ImageComposeScene(width = width * 2, height = height * 2, density = Density(2f)) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalViewModelStoreOwner provides owner
                ) {
                    ERKINTheme(darkTheme = false) { content() }
                }
            }
            try {
                val start = System.nanoTime()
                repeat(40) {
                    scene.render(System.nanoTime() - start)
                    delay(50)
                }
                val image = scene.render(System.nanoTime() - start)
                val png = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes
                File(outputDir, "$name.png").writeBytes(png)
                assertTrue(png.isNotEmpty())
            } finally {
                scene.close()
                owner.viewModelStore.clear()
            }
        }
    }

    private inline fun <reified T : Any> get(): T = org.koin.core.context.GlobalContext.get().get()

    private class TestOwner : LifecycleOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore = ViewModelStore()
    }

    private val ruleLists = TestRuleLists()

    private class TestRuleLists : RuleListStore {
        val dir: File = Files.createTempDirectory("rule-lists").toFile()
        override fun installed(fileName: String): File = File(dir, fileName)
        override fun stagingDir(): File = dir
        override suspend fun install(key: String, source: File, format: RuleSetFormat, kind: RuleSetKind?) =
            InstalledSet(kind ?: RuleSetKind.DOMAIN, 0)
        override suspend fun remove(fileName: String) = Unit
    }

    private object SilentLog : AppLog {
        override fun i(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, t: Throwable?) = Unit
        override fun e(tag: String, message: String, t: Throwable?) = Unit
    }

    private object NoSubscriptions : SubscriptionProfileSource {
        override suspend fun fetch(url: String): List<ParsedProfile> = emptyList()
    }

    private object NoSiteProbe : ByeDpiSiteProbe {
        override fun probe(socksPort: Int, groups: List<SiteGroup>): Flow<SiteResult> = emptyFlow()
    }

    private object NoUpdatePlatform : UpdatePlatform {
        override val assetSuffix: String = "windows-x86_64.msi"
        override val downloadDir: File = File("build/update-test")
        override fun install(file: File) = Unit
    }

    private object FakeVersionInfo : VersionInfo {
        override val appVersion: String = "1.3.0"
        override val byeDpiVersion: String = "17.3"
        override suspend fun coreVersion(): String = "1.13.16"
    }

    private class FakeInstalledApps(apps: List<InstalledApp>) : InstalledAppsSource {
        override val installedApps: StateFlow<List<InstalledApp>> = MutableStateFlow(apps)
        override val isLoading: StateFlow<Boolean> = MutableStateFlow(false)
        override val error: StateFlow<String?> = MutableStateFlow(null)
        override fun retry() = Unit
    }
}
