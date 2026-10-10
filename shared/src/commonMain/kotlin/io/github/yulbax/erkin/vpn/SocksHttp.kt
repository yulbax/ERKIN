package io.github.yulbax.erkin.vpn

import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

data class SocksCredentials(val username: String, val password: String)

object SocksHttp {
    private val authenticatorMutex = Mutex()

    suspend fun <T> request(
        socksPort: Int,
        url: String,
        method: String,
        timeoutMs: Int,
        credentials: SocksCredentials? = null,
        read: (HttpURLConnection) -> T
    ): T? {
        suspend fun execute(): T? = try {
            withTimeoutOrNull(timeoutMs.milliseconds) {
                runInterruptible(Dispatchers.IO) {
                    val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
                    val connection = URI(url).toURL().openConnection(proxy) as HttpURLConnection
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

        if (credentials == null) return execute()
        return authenticatorMutex.withLock {
            val authentication = PasswordAuthentication(credentials.username, credentials.password.toCharArray())
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication = authentication
            })
            try {
                execute()
            } finally {
                Authenticator.setDefault(null)
            }
        }
    }
}
