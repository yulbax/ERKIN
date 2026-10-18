package io.github.yulbax.erkin.vpn

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.ServerSocket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadRouteTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val body = "direct".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        start()
    }
    private val url = "http://127.0.0.1:${server.address.port}/file"

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun withoutATunnelTheDownloadGoesDirect() {
        assertEquals("direct", DownloadRoute().fetch(url) { it.inputStream.use { stream -> stream.readBytes().decodeToString() } })
    }

    @Test
    fun anUnreachableTunnelFallsBackToADirectDownload() {
        val closedPort = ServerSocket(0).use { it.localPort }
        val route = DownloadRoute().apply { tunnel = SocksEndpoint(closedPort, SocksCredentials("u", "p")) }

        assertEquals("direct", route.fetch(url) { it.inputStream.use { stream -> stream.readBytes().decodeToString() } })
    }
}
