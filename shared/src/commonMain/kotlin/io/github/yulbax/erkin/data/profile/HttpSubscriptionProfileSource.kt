package io.github.yulbax.erkin.data.profile

import io.github.yulbax.erkin.proxy.ParsedProfile
import io.github.yulbax.erkin.proxy.SubscriptionParser
import io.github.yulbax.erkin.util.readBounded
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

class HttpSubscriptionProfileSource : SubscriptionProfileSource {
    override suspend fun fetch(url: String): List<ParsedProfile> =
        SubscriptionParser.parseBody(runInterruptible(Dispatchers.IO) { download(url, MAX_REDIRECTS) })

    private fun download(url: String, redirectsLeft: Int): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = false
        try {
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location") ?: throw IOException("Redirect without location")
                if (redirectsLeft == 0) throw IOException("Too many redirects")
                return download(URI(url).resolve(location).toString(), redirectsLeft - 1)
            }
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.use { it.readBounded(MAX_BYTES).decodeToString() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
        const val MAX_REDIRECTS = 5
        const val MAX_BYTES = 10L shl 20
    }
}
