package io.github.yulbax.erkin.vpn

import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

data class SocksCredentials(val username: String, val password: String)

data class SocksEndpoint(val port: Int, val credentials: SocksCredentials?)

object SocksHttp {
    private const val LOOPBACK = "127.0.0.1"

    suspend fun <T> request(
        socksPort: Int,
        url: String,
        method: String,
        timeoutMs: Int,
        credentials: SocksCredentials? = null,
        read: (HttpURLConnection) -> T
    ): T? = try {
        withTimeoutOrNull(timeoutMs.milliseconds) {
            runInterruptible(Dispatchers.IO) {
                val connection = open(url, SocksEndpoint(socksPort, credentials))
                connection.requestMethod = method
                connection.setRequestProperty("Connection", "close")
                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs
                connection.instanceFollowRedirects = false
                read(connection)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    fun open(url: String, endpoint: SocksEndpoint): HttpURLConnection {
        endpoint.credentials?.let { SocksAuth.register(endpoint.port, it) }
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(LOOPBACK, endpoint.port))
        return URI(url).toURL().openConnection(proxy) as HttpURLConnection
    }
}

private object SocksAuth : Authenticator() {
    private const val SOCKS = "SOCKS"
    private val credentials = ConcurrentHashMap<Int, PasswordAuthentication>()

    @Volatile private var installed = false

    fun register(port: Int, value: SocksCredentials) {
        credentials[port] = PasswordAuthentication(value.username, value.password.toCharArray())
        if (!installed) {
            synchronized(this) {
                if (!installed) {
                    setDefault(this)
                    installed = true
                }
            }
        }
    }

    override fun getPasswordAuthentication(): PasswordAuthentication? =
        credentials[requestingPort]?.takeIf { requestingProtocol.orEmpty().startsWith(SOCKS, ignoreCase = true) }
}
