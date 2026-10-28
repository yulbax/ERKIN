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
import io.github.yulbax.erkin.data.byedpi.ByeDpiHistory
import io.github.yulbax.erkin.vpn.core.TestNetwork
import io.github.yulbax.erkin.vpn.core.TestNetworkSource
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.FetchedSubscription
import io.github.yulbax.erkin.data.profile.SubscriptionProfileSource
import io.github.yulbax.erkin.ui.components.UpdatePrompt
import io.github.yulbax.erkin.ui.di.uiModule
import io.github.yulbax.erkin.ui.screens.About
import io.github.yulbax.erkin.ui.screens.License
import io.github.yulbax.erkin.ui.screens.Logs
import io.github.yulbax.erkin.ui.screens.MainScreen
import io.github.yulbax.erkin.ui.screens.apps.Apps
import io.github.yulbax.erkin.ui.screens.byedpi.ByeDpi
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
import io.github.yulbax.erkin.util.LogFeed
import io.github.yulbax.erkin.util.VersionInfo
import io.github.yulbax.erkin.util.VpnLauncher
import io.github.yulbax.erkin.vpn.ByeDpiSiteCheck
import io.github.yulbax.erkin.vpn.ByeDpiStrategyRunner
import io.github.yulbax.erkin.vpn.ServerLatencyProbe
import io.github.yulbax.erkin.vpn.VpnCommandBus
import io.github.yulbax.erkin.vpn.VpnStateRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import io.github.yulbax.erkin.ui.screens.docs.DocArticle
import io.github.yulbax.erkin.ui.screens.docs.DocPage
import io.github.yulbax.erkin.ui.screens.docs.Docs
import io.github.yulbax.erkin.vpn.ByeDpiSites
import io.github.yulbax.erkin.vpn.SiteResult
import java.util.Locale
import org.junit.Assume.assumeTrue
import io.github.yulbax.erkin.ui.screens.docs.WRITTEN_DOC_LANGUAGES
import java.awt.ComponentOrientation
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.Surface

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
                    single { ByeDpiHistory(get(), get()) }
                    single { TestNetworkSource.fixed(TestNetwork.Wifi) }
                    single {
                        ByeDpiStrategyRunner(
                            newProcess = { error("no ciadpi in render tests") },
                            siteCheck = ByeDpiSiteCheck { _, _, _, _, _ -> emptyMap() },
                            scope = CoroutineScope(SupervisorJob()),
                            log = SilentLog
                        )
                    }
                    single<SubscriptionProfileSource> { NoSubscriptions }
                    single { ProfileRepository(get(), get(), get(), get()) }
                    single { ConfigBackupRepository(get(), get()) }
                    single { ServerLatencyProbe(RoomVpnSessionStore(get(), get())) { _, _, _, _ -> "{}" } }
                    single { VpnStateRepository() }
                    single { VpnCommandBus() }
                    single<VpnLauncher> { VpnLauncher { } }
                    single<AppLog> { SilentLog }
                    single<DiagnosticsSource> {
                        object : DiagnosticsSource {
                            override val feed = object : LogFeed {
                                override val entries = MutableStateFlow(
                                    List(40) { "[app] 10-17 16:01:47.924 I/VpnSession: line $it" } +
                                        "[core] 10-17 16:01:53.105 W: [TCP] dial DIRECT 172.19.0.1:54990 --> 172.19.0.2:853 error: i/o timeout" +
                                        "[byedpi] 10-17 16:01:48.607 I/ByeDpiRunner: byedpi started on port 34487"
                                )
                            }
                            override suspend fun collect() = "=== ERKIN diagnostics ===\n" + "line\n".repeat(400)
                            override suspend fun clear() = Unit
                        }
                    }
                    single<VersionInfo> { FakeVersionInfo }
                    single<InstalledAppsSource> { FakeInstalledApps(installedApps) }
                    single { UpdateChecker(get(), NoUpdatePlatform) { emptyList() } }
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
        render("byedpi", width = 420, height = 860) { ByeDpi() }
        render("settings", width = 420, height = 1400) { Settings() }
        render("logs", width = 420, height = 600) { Logs() }
        render("rules", width = 420, height = 860) { Rules(onOpenRuleSets = {}) }
        render("rule-sets", width = 420, height = 1900) { RuleSets() }
        render("about", width = 420, height = 600) { About() }
        render("license", width = 420, height = 600) { License() }
        render("docs", width = 420, height = 600) { Docs(onOpen = {}) }
        render("doc-byedpi", width = 420, height = 2000) { DocPage(DocArticle.BYEDPI) }
        render("doc-channels", width = 420, height = 1400) { DocPage(DocArticle.CHANNELS) }
    }

    @Test
    fun rendersDocScreenshots() {
        assumeTrue(System.getProperty(DOC_SCREENSHOTS) == "true")
        runBlocking {
            get<SettingsRepository>().update { it.copy(homeHintSeen = true, appsHintSeen = true) }
            seedStrategies()
        }
        val systemLocale = Locale.getDefault()
        try {
            WRITTEN_DOC_LANGUAGES.forEach { language ->
                val locale = Locale.forLanguageTag(language)
                Locale.setDefault(locale)
                val direction = if (ComponentOrientation.getOrientation(locale).isLeftToRight) LayoutDirection.Ltr else LayoutDirection.Rtl
                val dir = File(DOC_IMAGES, language).apply { mkdirs() }
                val screens = mapOf<String, Pair<Int, @Composable () -> Unit>>(
                    "main" to (600 to { MainScreen() }),
                    "apps" to (300 to { Surface { Apps(query = "") } }),
                    "byedpi" to (420 to { Surface { ByeDpi() } })
                )
                screens.forEach { (name, screen) ->
                    val (height, content) = screen
                    render(name, width = 400, height = height, dark = true, target = File(dir, "$name.webp")) {
                        CompositionLocalProvider(LocalLayoutDirection provides direction) { content() }
                    }
                }
            }
        } finally {
            Locale.setDefault(systemLocale)
        }
    }

    private suspend fun seedStrategies() {
        val history = get<ByeDpiHistory>()
        val sites = ByeDpiSites.QUICK.flatMap { group -> group.sites.map { it to group.name } }
        DOC_STRATEGIES.reversed().forEach { (args, reachable) ->
            history.remember(args)
            reachable?.let { count ->
                history.saveResults(args, TestNetwork.Wifi, sites.mapIndexed { index, (host, group) -> SiteResult(host, group, index < count) })
            }
        }
        history.apply(DOC_STRATEGIES.first().first)
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
            UpdatePrompt(UpdateViewModel(UpdateChecker(FakeVersionInfo, NoUpdatePlatform) { listOf(release) }, UpdateDownloader(), NoUpdatePlatform))
        }
    }

    private fun render(
        name: String,
        width: Int,
        height: Int,
        dark: Boolean = false,
        target: File = File(outputDir, "$name.png"),
        content: @Composable () -> Unit
    ) = runBlocking {
        withContext(Dispatchers.Main) {
            val owner = TestOwner()
            val scene = ImageComposeScene(width = width * 2, height = height * 2, density = Density(2f)) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalViewModelStoreOwner provides owner
                ) {
                    ERKINTheme(darkTheme = dark) { content() }
                }
            }
            try {
                val start = System.nanoTime()
                repeat(40) {
                    scene.render(System.nanoTime() - start)
                    delay(50)
                }
                val image = scene.render(System.nanoTime() - start)
                val encoded = if (target.extension == "webp") {
                    image.encodeToData(EncodedImageFormat.WEBP, WEBP_QUALITY)
                } else {
                    image.encodeToData(EncodedImageFormat.PNG)
                }
                val bytes = requireNotNull(encoded).bytes
                target.writeBytes(bytes)
                assertTrue(bytes.isNotEmpty())
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

    private companion object {
        const val DOC_SCREENSHOTS = "erkin.docScreenshots"
        const val WEBP_QUALITY = 90
        val DOC_IMAGES = File("src/commonMain/composeResources/files/docs/img")
        val DOC_STRATEGIES = listOf<Pair<String, Int?>>(
            "-o1 -d1 -a1 -At,r,s -s1 -d1 -s5+s -s10+s -s15+s -s20+s -r1+s -S -a1" to 32,
            "-f-200 -Qr -s3:5+sm -a1 -As -d1 -s4+sm -s8+sh -f-300 -d6+sh -a1" to 30,
            "-d1 -s1+s -s3+s -s6+s -s9+s -s12+s -s15+s -s20+s -s30+s -a1" to 19,
            "-n \"vk.com\" -Qr -f-204 -s1:5+sm -a1" to null
        )
    }

    private object SilentLog : AppLog {
        override fun i(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, t: Throwable?) = Unit
        override fun e(tag: String, message: String, t: Throwable?) = Unit
    }

    private object NoSubscriptions : SubscriptionProfileSource {
        override suspend fun fetch(url: String): FetchedSubscription = FetchedSubscription(emptyList())
    }

    private object NoUpdatePlatform : UpdatePlatform {
        override val assetSuffix: String = "windows-x86_64.msi"
        override val downloadDir: File = File("build/update-test")
        override fun install(file: File) = Unit
    }

    private object FakeVersionInfo : VersionInfo {
        override val appVersion: String = "1.3.0"
        override val build: String? = null
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
