package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.HealthSnapshot
import io.github.yulbax.erkin.vpn.ProbeParams
import io.github.yulbax.erkin.vpn.ProbeUrls
import io.github.yulbax.erkin.vpn.ServerLatencyProbe
import io.github.yulbax.erkin.vpn.SocksHealthProbe
import io.github.yulbax.erkin.vpn.VpnState
import io.github.yulbax.erkin.vpn.core.EngineConfig
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.core.ProxyDelay
import io.github.yulbax.erkin.vpn.core.VPN_DELAY_TIMEOUT_MS
import io.github.yulbax.erkin.vpn.core.VpnEngine
import io.github.yulbax.erkin.vpn.core.freeLoopbackPort
import io.github.yulbax.erkin.vpn.core.randomToken
import io.github.yulbax.erkin.vpn.mihomo.MihomoConfigBuilder
import io.github.yulbax.erkin.vpn.mihomo.MihomoPlatform
import io.github.yulbax.erkin.vpn.mihomo.ProbeInbound
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class DesktopEngineIntegrationTest {
    private val binDir = System.getenv("ERKIN_TEST_BIN_DIR")?.let(::File)
    private val workDir = Files.createTempDirectory("erkin-desktop-test").toFile()
    private val processes = mutableListOf<Process>()

    @Before
    fun requireBinaries() {
        assumeTrue("set ERKIN_TEST_BIN_DIR to a directory with erkin-service and ciadpi", binDir?.isDirectory == true)
    }

    @After
    fun cleanup() {
        processes.forEach { it.destroy(); it.waitFor(10, TimeUnit.SECONDS) }
        workDir.deleteRecursively()
    }

    private fun startService(): File {
        val socket = File(workDir, "s.sock")
        processes += ProcessBuilder(File(binDir, "erkin-service").absolutePath, "run", "--socket", socket.absolutePath, "--work-dir", File(workDir, "svc").absolutePath)
            .redirectErrorStream(true).redirectOutput(File(workDir, "service.log")).start()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!socket.exists() && System.nanoTime() < deadline) Thread.sleep(50)
        assertTrue("service socket did not appear", socket.exists())
        return socket
    }

    @Test
    fun windowsStyleConfigPassesServiceCheck() {
        val probe = ProbeInbound(2080, "user", "pass")
        val vless = EngineProxy("p1", """{"type":"vless","server":"a.example","port":443,"udp":true,"uuid":"b5e6f6c2-6f5e-4c57-9a1c-1a9a2e2c3d4e","tls":true,"servername":"a.example"}""")
        val config = MihomoConfigBuilder.build(
            EngineConfig(listOf(vless), "p1", listOf("Discord.exe"), listOf("Telegram.exe"), listOf("Discord.exe", "Telegram.exe"), 1081, NetworkOptions()),
            probe,
            MihomoPlatform.Desktop(listOf("ciadpi.exe"), byeDpiProbePort = 3080)
        )
        val noServers = MihomoConfigBuilder.build(
            EngineConfig(emptyList(), "", listOf("Discord.exe"), emptyList(), listOf("Discord.exe"), 1081, NetworkOptions(), listOf("C:\\venv\\python.exe")),
            probe,
            MihomoPlatform.Desktop(listOf("ciadpi.exe"))
        )
        ServiceClient.connect(startService()).use { client ->
            client.check(config)
            client.check(noServers)
            val failure = runCatching { client.check("""{"proxies": [{"name": "x", "type": "no-such-type"}]}""") }.exceptionOrNull()
            assertNotNull("an invalid config must be rejected", failure)
        }
    }

    @Test
    fun serviceRunsTheCoreAndStopsItWhenTheAppDisconnects() {
        val socket = startService()
        val socksPort = freeLoopbackPort()
        val user = randomToken()
        val pass = randomToken()
        val config = """
            {
              "mode": "rule",
              "log-level": "warning",
              "listeners": [{"name": "probe-in", "type": "socks", "listen": "127.0.0.1", "port": $socksPort,
                             "users": [{"username": "$user", "password": "$pass"}]}],
              "proxies": [{"name": "p1", "type": "direct"}],
              "rules": ["MATCH,p1"]
            }
        """.trimIndent()

        val service = DesktopService(socket, bundled = File(binDir, "erkin-service"), appDir = null, expectedVersion = null, log = SilentLog)
        val client = service.connect()
        assertEquals(File(workDir, "svc").absoluteFile, service.hello?.workDir)
        assertTrue(service.hello?.core.orEmpty().isNotEmpty())

        val firstStart = runCatching { client.start(config, "", "") }.exceptionOrNull()
        assumeTrue(
            "this kernel cannot report socket owners (inet_diag/tcp_diag/udp_diag not loaded)",
            firstStart?.message?.contains(VpnState.PROCESS_LOOKUP_UNAVAILABLE) != true
        )
        firstStart?.let { throw it }
        client.stop()

        repeat(2) {
            client.start(config, "", "")
            assertNotNull(runCatching { client.start(config, "", "") }.exceptionOrNull())
            assertLatencyTestWorks(client)
            assertNotNull(client.delay("p1", ProbeUrls.VPN, 10_000))
            val snapshot: HealthSnapshot = runBlocking {
                withTimeout(30.seconds) {
                    val engine = FakeEngine(socksPort, user, pass) {
                        client.delay("p1", ProbeUrls.VPN, VPN_DELAY_TIMEOUT_MS)
                    }
                    SocksHealthProbe(ProbeUrls.BYEDPI)
                        .observe(ProbeParams(engine, byeDpiPort = null, vpnActive = true))
                        .first()
                }
            }
            assertTrue("health urltest of the active server failed", snapshot.vpnUp)
            assertEquals("country must resolve through the authenticated probe SOCKS", 2, snapshot.vpnCountry.length)
            assertTrue("traffic must be counted", checkNotNull(client.traffic()).down > 0)
            client.stop()
            assertNull(client.traffic())
        }

        client.start(config, "", "")
        client.close()
        ServiceClient.connect(socket).use { other ->
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (other.traffic() != null && System.nanoTime() < deadline) Thread.sleep(100)
            assertNull("the tunnel must stop once the app that started it disconnects", other.traffic())
        }
    }

    @Test
    fun ciadpiProcessStartsStopsAndReportsUnexpectedExit() {
        val ciadpi = CiadpiProcess(File(binDir, "ciadpi"), File(workDir, "protect.sock"), SilentLog)
        val port = freeLoopbackPort()
        val exits = CountDownLatch(1)
        ciadpi.start(port, listOf("-d1")) { exits.countDown() }
        assertTrue(ciadpi.isRunning)
        assertTrue(waitForPort(port))
        ciadpi.stop()
        assertFalse(ciadpi.isRunning)
        assertFalse("intentional stop must not be reported as a crash", exits.await(2, TimeUnit.SECONDS))

        val crashing = CiadpiProcess(File(binDir, "ciadpi"), File(workDir, "protect.sock"), SilentLog)
        val codes = mutableListOf<Int>()
        val crashed = CountDownLatch(1)
        crashing.start(port, listOf("--definitely-invalid-option")) { code -> codes += code; crashed.countDown() }
        assertTrue(crashed.await(10, TimeUnit.SECONDS))
        assertEquals(1, codes.size)
        crashing.stop()
    }

    @Test
    fun ciadpiSkipsOptionsItsBuildDoesNotSupport() {
        val ciadpi = CiadpiProcess(File(binDir, "ciadpi"), File(workDir, "protect.sock"), SilentLog, unsupportedOptions = setOf("--definitely-invalid"))
        val port = freeLoopbackPort()
        val exits = CountDownLatch(1)
        ciadpi.start(port, listOf("-d1", "--definitely-invalid")) { exits.countDown() }
        assertTrue(waitForPort(port))
        assertFalse("an unsupported option must not crash ciadpi", exits.await(1, TimeUnit.SECONDS))
        ciadpi.stop()
    }

    @Test
    fun serviceMeasuresServersWithoutARunningCore() {
        ServiceClient.connect(startService()).use(::assertLatencyTestWorks)
    }

    private fun assertLatencyTestWorks(client: ServiceClient) {
        val proxies = """[{"name":"p1","type":"direct"},{"name":"p2","type":"socks5","server":"127.0.0.1","port":${freeLoopbackPort()}}]"""
        val delays = ServerLatencyProbe.parse(client.urlTest(proxies, ProbeUrls.VPN, 10_000))
        assertTrue("a working server must be measured: $delays", delays[1L] is ProxyDelay.Measured)
        assertEquals(ProxyDelay.Failed, delays[2L])
    }

    private fun waitForPort(port: Int): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (runCatching { java.net.Socket("127.0.0.1", port).close() }.isSuccess) return true
            Thread.sleep(100)
        }
        return false
    }

    private class FakeEngine(
        override val probeSocksPort: Int,
        override val probeUsername: String,
        override val probePassword: String,
        private val measure: () -> Int?
    ) : VpnEngine {
        override fun start(config: EngineConfig) = Unit
        override fun reloadRouting(config: EngineConfig) = Unit
        override suspend fun measureVpnDelay(): Int? = measure()
        override fun stop() = Unit
    }

    private object SilentLog : AppLog {
        override fun i(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, t: Throwable?) = Unit
        override fun e(tag: String, message: String, t: Throwable?) = Unit
    }

}
