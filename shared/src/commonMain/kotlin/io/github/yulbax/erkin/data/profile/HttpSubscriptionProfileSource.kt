package io.github.yulbax.erkin.data.profile

import io.github.yulbax.erkin.proxy.SubscriptionParser
import io.github.yulbax.erkin.proxy.protocol.decodeBase64Lenient
import io.github.yulbax.erkin.util.readBounded
import io.github.yulbax.erkin.vpn.DownloadRoute
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

class HttpSubscriptionProfileSource(
    private val route: DownloadRoute = DownloadRoute(),
    private val device: DeviceIdentity? = null
) : SubscriptionProfileSource {
    override suspend fun fetch(url: String): FetchedSubscription {
        val body = runInterruptible(Dispatchers.IO) { download(url, MAX_REDIRECTS) }
        return FetchedSubscription(
            profiles = SubscriptionParser.parseBody(body.text),
            info = SubscriptionInfo(
                title = SubscriptionHeaders.title(body.title),
                usage = SubscriptionHeaders.usage(body.userInfo)
            )
        )
    }

    private fun download(url: String, redirectsLeft: Int): Body {
        val reply = route.fetch(url) { connection ->
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            device?.headers?.forEach(connection::setRequestProperty)
            try {
                val code = connection.responseCode
                SubscriptionRefusal.of(code, connection::getHeaderField)?.let { throw SubscriptionRefusedException(it) }
                if (code in 300..399) {
                    Redirect(connection.getHeaderField("Location") ?: throw IOException("Redirect without location"))
                } else {
                    if (code !in 200..299) throw IOException("HTTP $code")
                    Body(
                        text = connection.inputStream.use { it.readBounded(MAX_BYTES).decodeToString() },
                        title = connection.getHeaderField(SubscriptionHeaders.TITLE),
                        userInfo = connection.getHeaderField(SubscriptionHeaders.USER_INFO)
                    )
                }
            } finally {
                connection.disconnect()
            }
        }
        return when (reply) {
            is Body -> reply
            is Redirect -> {
                if (redirectsLeft == 0) throw IOException("Too many redirects")
                download(URI(url).resolve(reply.location).toString(), redirectsLeft - 1)
            }
        }
    }

    private sealed interface Reply
    private class Body(val text: String, val title: String?, val userInfo: String?) : Reply
    private class Redirect(val location: String) : Reply

    private companion object {
        const val TIMEOUT_MS = 15_000
        const val MAX_REDIRECTS = 5
        const val MAX_BYTES = 10L shl 20
    }
}

internal object SubscriptionHeaders {
    const val TITLE = "profile-title"
    const val USER_INFO = "subscription-userinfo"
    private const val BASE64_PREFIX = "base64:"
    private const val MAX_TITLE_LENGTH = 128

    fun title(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        val text = if (value.startsWith(BASE64_PREFIX, ignoreCase = true)) {
            decodeBase64Lenient(value.substring(BASE64_PREFIX.length))
        } else {
            value
        }
        return text?.trim()?.take(MAX_TITLE_LENGTH)?.takeIf { it.isNotEmpty() }
    }

    fun usage(raw: String?): SubscriptionUsage? {
        val fields = raw.orEmpty().split(';').mapNotNull { part ->
            val key = part.substringBefore('=', "").trim().lowercase()
            val number = part.substringAfter('=', "").trim().toDoubleOrNull()?.toLong()
            if (key.isEmpty() || number == null || number < 0) null else key to number
        }.toMap()
        if (fields.keys.none { it in USAGE_KEYS }) return null
        return SubscriptionUsage(
            upload = fields["upload"] ?: 0,
            download = fields["download"] ?: 0,
            total = fields["total"] ?: 0,
            expire = fields["expire"] ?: 0
        )
    }

    private val USAGE_KEYS = setOf("upload", "download", "total", "expire")
}
