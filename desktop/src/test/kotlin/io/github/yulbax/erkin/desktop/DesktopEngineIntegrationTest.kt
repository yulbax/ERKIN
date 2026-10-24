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
import java.io.DataInputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

private const val TUN_TEST_HOST = "example.com"

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
    fun tunRoutesEachAppBundleToItsChannel() {
        val socket = System.getenv("ERKIN_TEST_SERVICE_SOCKET")?.let(::File)
        assumeTrue("set ERKIN_TEST_SERVICE_SOCKET to the socket of an erkin-service running as root", socket?.exists() == true)
        val curl = File("/usr/bin/curl")
        val tunneled = appBundle("Tunneled", curl)
        val direct = appBundle("Direct", curl)
        SocksRecorder().use { recorder ->
            val server = EngineProxy("p1", """{"type":"socks5","server":"127.0.0.1","port":${recorder.port}}""")
            val config = MihomoConfigBuilder.build(
                EngineConfig(
                    proxies = listOf(server),
                    activeProxyTag = "p1",
                    byeDpiPackages = emptyList(),
                    vpnPackages = listOf(tunneled.bundle.path),
                    tunneledPackages = listOf(tunneled.bundle.path),
                    byeDpiSocksPort = freeLoopbackPort(),
                    network = NetworkOptions(),
                    directPaths = listOf(direct.bundle.path)
                ),
                ProbeInbound(freeLoopbackPort(), randomToken(), randomToken()),
                MihomoPlatform.Desktop(listOf("ciadpi"), SystemDnsDetector.detect())
            )
            val systemDns = systemNameservers()
            ServiceClient.connect(checkNotNull(socket)).use { client ->
                client.start(config, "", "")
                try {
                    assertNotEquals("the tunneled app must not reach the site directly", "200", fetch(tunneled.executable))
                    assertTrue("the site must be resolved inside the tunnel: ${recorder.destinations()}", TUN_TEST_HOST in recorder.destinations())
                    val seen = recorder.destinations().count { it == TUN_TEST_HOST }
                    assertEquals("the direct app must bypass the server", "200", fetch(direct.executable))
                    assertEquals(seen, recorder.destinations().count { it == TUN_TEST_HOST })
                } finally {
                    client.stop()
                }
            }
            assertEquals("the system DNS must be restored once the tunnel stops", systemDns, systemNameservers())
        }
    }

    @Test
    fun serviceMeasuresServersWithoutARunningCore() {
        ServiceClient.connect(startService()).use(::assertLatencyTestWorks)
    }

    private fun assertLatencyTestWorks(client: ServiceClient) {
        val proxies = """[{"name":"p1","type":"direct"},{"name":"p2","type":"socks5","server":"127.0.0.1","port":${freeLoopbackPort()}}]"""
        val delays = ServerLatencyProbe.parse(client.urlTest(proxies, ProbeUrls.VPN, 10_000, ipv6 = true))
        assertTrue("a working server must be measured: $delays", delays[1L] is ProxyDelay.Measured)
        assertEquals(ProxyDelay.Failed, delays[2L])
    }

    private class Bundled(val bundle: File, val executable: File)

    private fun appBundle(name: String, program: File): Bundled {
        val bundle = File(workDir.canonicalFile, "$name.app")
        val executable = File(bundle, "Contents/MacOS/${program.name}")
        executable.parentFile.mkdirs()
        program.copyTo(executable)
        executable.setExecutable(true)
        return Bundled(bundle, executable)
    }

    private fun systemNameservers(): List<String> {
        val process = ProcessBuilder("scutil", "--dns").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readLines() }
        process.waitFor(10, TimeUnit.SECONDS)
        return output.map(String::trim).filter { it.startsWith("nameserver[") }
    }

    private fun fetch(curl: File): String {
        val process = ProcessBuilder(curl.path, "-s", "-o", "/dev/null", "-m", "15", "-w", "%{http_code}", "https://$TUN_TEST_HOST/")
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.waitFor(20, TimeUnit.SECONDS)
        return output
    }

    private class SocksRecorder : AutoCloseable {
        private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        private val seen = mutableListOf<String>()
        val port: Int get() = server.localPort

        init {
            thread(isDaemon = true, name = "socks-recorder") {
                while (!server.isClosed) {
                    val conn = runCatching { server.accept() }.getOrNull() ?: break
                    runCatching { conn.use(::record) }
                }
            }
        }

        fun destinations(): List<String> = synchronized(seen) { seen.toList() }

        private fun record(conn: java.net.Socket) {
            val input = DataInputStream(conn.getInputStream())
            val output = conn.getOutputStream()
            input.readUnsignedByte()
            input.skipBytes(input.readUnsignedByte())
            output.write(byteArrayOf(5, 0))
            input.readFully(ByteArray(3))
            val host = when (input.readUnsignedByte()) {
                1 -> InetAddress.getByAddress(ByteArray(4).also(input::readFully)).hostAddress
                4 -> InetAddress.getByAddress(ByteArray(16).also(input::readFully)).hostAddress
                else -> String(ByteArray(input.readUnsignedByte()).also(input::readFully))
            }
            synchronized(seen) { seen += host }
            output.write(byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0))
        }

        override fun close() = server.close()
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
