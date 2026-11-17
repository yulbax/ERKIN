package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.CiadpiProcess
import io.github.yulbax.erkin.vpn.ProbeUrls
import io.github.yulbax.erkin.vpn.ServerLatencyProbe
import io.github.yulbax.erkin.vpn.SocksHealthProbe
import io.github.yulbax.erkin.vpn.VpnFailure
import io.github.yulbax.erkin.vpn.VpnState
import io.github.yulbax.erkin.vpn.core.EngineConfig
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.core.ProbeResult
import io.github.yulbax.erkin.vpn.core.VpnEngine
import io.github.yulbax.erkin.vpn.core.freeLoopbackPort
import io.github.yulbax.erkin.vpn.core.randomToken
import io.github.yulbax.erkin.vpn.mihomo.MihomoConfigBuilder
import io.github.yulbax.erkin.vpn.mihomo.MihomoPlatform
import io.github.yulbax.erkin.vpn.mihomo.ProbeInbound
import java.io.File
import java.net.NetworkInterface
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

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
        processes += ProcessBuilder(binary("erkin-service").absolutePath, "run", "--socket", socket.absolutePath, "--work-dir", File(workDir, "svc").absolutePath)
            .redirectErrorStream(true).redirectOutput(File(workDir, "service.log")).start()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!socket.exists() && System.nanoTime() < deadline) Thread.sleep(50)
        assertTrue("service socket did not appear", socket.exists())
        return socket
    }

    @Test
    fun thePortableCoreStartsAgainAfterItDies() {
        val service = portableService(installedSocket = File(workDir, "none.sock"))
        val first = service.connect()
        assertEquals(File(workDir, "core").absoluteFile, service.hello?.workDir)
        assertSame("the portable core has one connection for everything", first, service.control())

        portableCores().forEach { it.destroyForcibly() }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (first.isOpen && System.nanoTime() < deadline) Thread.sleep(50)
        assertNull("a dead core must not look running", service.running())

        val second = service.connect()
        assertNotSame(first, second)
        assertNotNull(second.hello())
        second.close()
        assertTrue("the core must exit once the app closes its stdin", portableCores().all { it.onExit().get(10, TimeUnit.SECONDS) != null })
    }

    @Test
    fun thePortableCoreStaysOffWhileTheInstalledServiceRuns() {
        val service = portableService(installedSocket = startService())
        val failure = runCatching { service.connect() }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("installed ERKIN"))
        assertTrue(portableCores().isEmpty())
    }

    private fun portableService(installedSocket: File) = DesktopService.Portable(
        binary = binary("erkin-service"),
        workDir = File(workDir, "core"),
        protectSocket = File(workDir, "p.sock"),
        installedSocket = installedSocket,
        log = SilentLog
    )

    private fun portableCores(): List<ProcessHandle> = ProcessHandle.current().children()
        .filter { child -> processes.none { it.pid() == child.pid() } }
        .filter { it.info().command().map { command -> "erkin-service" in command }.orElse(false) }
        .toList()

    private fun binary(name: String): File = File(binDir, if (DesktopPaths.isWindows) "$name.exe" else name)

    @Test
    fun thePortableCoreRoutesAnAppThroughTheTun() {
        assumeTrue("set ERKIN_TEST_TUN where the core may create a TUN", System.getenv("ERKIN_TEST_TUN") != null)
        val curl = if (DesktopPaths.isWindows) "curl.exe" else "curl"
        val config = MihomoConfigBuilder.build(
            EngineConfig(listOf(EngineProxy("p1", """{"type":"direct"}""")), "p1", emptyList(), listOf(curl), listOf(curl), 1081, NetworkOptions()),
            ProbeInbound(freeLoopbackPort(), randomToken(), randomToken()),
            MihomoPlatform.Desktop(listOf(binary("ciadpi").name))
        )
        val service = portableService(installedSocket = File(workDir, "none.sock"))
        val client = service.control()
        client.start(config, "", "")
        assertTrue("the TUN must come up", waitFor { tunUp() })

        val fetch = ProcessBuilder(curl, "-s", "-o", if (DesktopPaths.isWindows) "NUL" else "/dev/null", "https://www.example.com").start()
        assertTrue(fetch.waitFor(30, TimeUnit.SECONDS))
        assertEquals(0, fetch.exitValue())
        assertTrue("the TUN must carry traffic through the core", checkNotNull(client.traffic()).down > 0)

        client.close()
        assertTrue("the core must exit once the app closes its stdin", portableCores().all { it.onExit().get(10, TimeUnit.SECONDS) != null })
        assertTrue("the TUN must go away with the core", waitFor { !tunUp() })
    }

    private fun tunUp(): Boolean = if (DesktopPaths.isWindows) {
        val query = ProcessBuilder("powershell", "-NoProfile", "-Command", "Get-NetAdapter -Name '${MihomoConfigBuilder.DESKTOP_INTERFACE_NAME}' -ErrorAction Stop")
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        query.waitFor() == 0
    } else {
        NetworkInterface.getByName(MihomoConfigBuilder.DESKTOP_INTERFACE_NAME) != null
    }

    private fun waitFor(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(250)
        }
        return false
    }

    @Test
    fun theServiceExitsOnceTheLastClientLeaves() {
        val socket = startService()
        val service = processes.single()
        ServiceClient.connect(socket).use { client ->
            Thread.sleep(12_000)
            assertTrue("the service must stay while a client is connected", service.isAlive)
            client.hello()
        }
        assertTrue("the service must exit once no client is left", service.waitFor(20, TimeUnit.SECONDS))
        assertEquals(0, service.exitValue())
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

        val service = DesktopService.Installed(socket, log = SilentLog)
        val client = service.connect()
        assertEquals(File(workDir, "svc").absoluteFile, service.hello?.workDir)
        assertTrue(service.hello?.core.orEmpty().isNotEmpty())

        val firstStart = runCatching { client.start(config, "", "") }.exceptionOrNull()
        assumeTrue(
            "this kernel cannot report socket owners (inet_diag/tcp_diag/udp_diag not loaded)",
            firstStart?.message?.contains(VpnFailure.PROCESS_LOOKUP_UNAVAILABLE) != true
        )
        firstStart?.let { throw it }
        client.stop()

        repeat(2) {
            client.start(config, "", "")
            assertNotNull(runCatching { client.start(config, "", "") }.exceptionOrNull())
            assertLatencyTestWorks(client)
            val country = runBlocking { withTimeout(30.seconds) { SocksHealthProbe(ProbeUrls.BYEDPI).country(FakeEngine(socksPort, user, pass)) } }
            assertEquals("country must resolve through the authenticated probe SOCKS", 2, country?.length)
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
    fun onlyTheControlConnectionHoldsTheTunnel() {
        val socket = startService()
        val config = """{"mode": "rule", "log-level": "warning", "proxies": [{"name": "p1", "type": "direct"}], "rules": ["MATCH,p1"]}"""
        val service = DesktopService.Installed(socket, log = SilentLog)
        val control = service.control()
        control.start(config, "", "")

        checkNotNull(service.running()).close()
        Thread.sleep(500)
        assertNotNull("the tunnel must survive a broken work connection", checkNotNull(service.running()).traffic())

        control.close()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (checkNotNull(service.running()).traffic() != null && System.nanoTime() < deadline) Thread.sleep(100)
        assertNull("the tunnel must stop once the control connection closes", checkNotNull(service.running()).traffic())
    }

    @Test
    fun ciadpiProcessStartsStopsAndReportsUnexpectedExit() {
        val ciadpi = CiadpiProcess(binary("ciadpi"), File(workDir, "protect.sock").absolutePath, SilentLog)
        val port = freeLoopbackPort()
        val exits = CountDownLatch(1)
        ciadpi.start(port, listOf("-d1")) { exits.countDown() }
        assertTrue(ciadpi.isRunning)
        assertTrue(waitForPort(port))
        ciadpi.stop()
        assertFalse(ciadpi.isRunning)
        assertFalse("intentional stop must not be reported as a crash", exits.await(2, TimeUnit.SECONDS))

        val crashing = CiadpiProcess(binary("ciadpi"), File(workDir, "protect.sock").absolutePath, SilentLog)
        val codes = mutableListOf<Int>()
        val crashed = CountDownLatch(1)
        crashing.start(port, listOf("--definitely-invalid-option")) { code -> codes += code; crashed.countDown() }
        assertTrue(crashed.await(10, TimeUnit.SECONDS))
        assertEquals(1, codes.size)
        crashing.stop()
    }

    @Test
    fun ciadpiSkipsOptionsItsBuildDoesNotSupport() {
        val ciadpi = CiadpiProcess(binary("ciadpi"), File(workDir, "protect.sock").absolutePath, SilentLog, unsupportedOptions = setOf("--definitely-invalid"))
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
        val results = ServerLatencyProbe.parse(client.urlTest(proxies, ProbeUrls.VPN, 10_000.milliseconds, ipv6 = true, runningOnly = false))
        assertTrue("a working server must be measured: $results", results[1L] is ProbeResult.Answered)
        assertEquals(ProbeResult.NoAnswer, results[2L])
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
        override val probePassword: String
    ) : VpnEngine {
        override fun start(config: EngineConfig) = Unit
        override fun reloadRouting(config: EngineConfig) = Unit
        override fun stop() = Unit
    }

    private object SilentLog : AppLog {
        override fun i(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, t: Throwable?) = Unit
        override fun e(tag: String, message: String, t: Throwable?) = Unit
    }

}
