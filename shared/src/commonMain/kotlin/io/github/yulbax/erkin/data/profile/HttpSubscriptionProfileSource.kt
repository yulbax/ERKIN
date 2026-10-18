package io.github.yulbax.erkin.data.profile

import io.github.yulbax.erkin.proxy.ParsedProfile
import io.github.yulbax.erkin.proxy.SubscriptionParser
import io.github.yulbax.erkin.util.readBounded
import io.github.yulbax.erkin.vpn.DownloadRoute
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

class HttpSubscriptionProfileSource(private val route: DownloadRoute = DownloadRoute()) : SubscriptionProfileSource {
    override suspend fun fetch(url: String): List<ParsedProfile> =
        SubscriptionParser.parseBody(runInterruptible(Dispatchers.IO) { download(url, MAX_REDIRECTS) })

    private fun download(url: String, redirectsLeft: Int): String {
        val reply = route.fetch(url) { connection ->
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    Redirect(connection.getHeaderField("Location") ?: throw IOException("Redirect without location"))
                } else {
                    if (code !in 200..299) throw IOException("HTTP $code")
                    Body(connection.inputStream.use { it.readBounded(MAX_BYTES).decodeToString() })
                }
            } finally {
                connection.disconnect()
            }
        }
        return when (reply) {
            is Body -> reply.text
            is Redirect -> {
                if (redirectsLeft == 0) throw IOException("Too many redirects")
                download(URI(url).resolve(reply.location).toString(), redirectsLeft - 1)
            }
        }
    }

    private sealed interface Reply
    private class Body(val text: String) : Reply
    private class Redirect(val location: String) : Reply

    private companion object {
        const val TIMEOUT_MS = 15_000
        const val MAX_REDIRECTS = 5
        const val MAX_BYTES = 10L shl 20
    }
}
