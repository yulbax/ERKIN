package io.github.yulbax.erkin.data.profile

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HttpSubscriptionDeviceTest {
    private var limitReached = false
    private val received = mutableMapOf<String, String?>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            listOf("x-hwid", "x-device-os", "x-ver-os", "x-device-model").forEach { received[it] = exchange.requestHeaders.getFirst(it) }
            if (limitReached) exchange.responseHeaders.add("x-hwid-max-devices-reached", "true")
            val body = "vless://00000000-0000-0000-0000-000000000000@a.example:443?security=tls#A".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        start()
    }
    private val url = "http://127.0.0.1:${server.address.port}/sub"
    private val device = DeviceIdentity("9774d56d682e549c", "Android", "16", "Pixel 8")

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun theSubscriptionRequestIntroducesTheDevice() = runBlocking {
        val fetched = HttpSubscriptionProfileSource(device = device).fetch(url)

        assertEquals(1, fetched.profiles.size)
        assertEquals(
            mapOf("x-hwid" to "9774d56d682e549c", "x-device-os" to "Android", "x-ver-os" to "16", "x-device-model" to "Pixel 8"),
            received
        )
    }

    @Test
    fun aReachedDeviceLimitIsReportedInsteadOfItsServers() = runBlocking {
        limitReached = true

        val refusal = runCatching { HttpSubscriptionProfileSource(device = device).fetch(url) }.exceptionOrNull()

        assertEquals(SubscriptionRefusal.DEVICE_LIMIT, (refusal as SubscriptionRefusedException).refusal)
        assertFalse(received.isEmpty())
    }
}
