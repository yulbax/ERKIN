package io.github.yulbax.erkin.data.profile

import io.github.yulbax.erkin.proxy.ClashRulesReader
import io.github.yulbax.erkin.proxy.SubscriptionParser
import io.github.yulbax.erkin.proxy.protocol.decodeBase64Lenient
import io.github.yulbax.erkin.util.readBounded
import io.github.yulbax.erkin.vpn.DownloadRoute
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlin.time.Duration.Companion.milliseconds

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
                usage = SubscriptionHeaders.usage(body.userInfo),
                notes = SubscriptionNotes(
                    supportUrl = SubscriptionHeaders.link(body.supportUrl),
                    pageUrl = SubscriptionHeaders.link(body.pageUrl),
                    announce = SubscriptionHeaders.announce(body.announce),
                    updateHours = SubscriptionHeaders.updateHours(body.updateInterval)
                )
            ),
            rules = ClashRulesReader.read(body.text)
        )
    }

    private fun download(url: String, redirectsLeft: Int): Body {
        val reply = route.fetch(url) { connection ->
            connection.connectTimeout = TIMEOUT.inWholeMilliseconds.toInt()
            connection.readTimeout = TIMEOUT.inWholeMilliseconds.toInt()
            connection.instanceFollowRedirects = false
            connection.setRequestProperty(SubscriptionHeaders.USER_AGENT, SubscriptionHeaders.CLIENT)
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
                        userInfo = connection.getHeaderField(SubscriptionHeaders.USER_INFO),
                        supportUrl = connection.getHeaderField(SubscriptionHeaders.SUPPORT_URL),
                        pageUrl = connection.getHeaderField(SubscriptionHeaders.PAGE_URL),
                        announce = connection.getHeaderField(SubscriptionHeaders.ANNOUNCE),
                        updateInterval = connection.getHeaderField(SubscriptionHeaders.UPDATE_INTERVAL)
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
    private class Body(
        val text: String,
        val title: String?,
        val userInfo: String?,
        val supportUrl: String?,
        val pageUrl: String?,
        val announce: String?,
        val updateInterval: String?
    ) : Reply
    private class Redirect(val location: String) : Reply

    private companion object {
        val TIMEOUT = 15_000.milliseconds
        const val MAX_REDIRECTS = 5
        const val MAX_BYTES = 10L shl 20
    }
}

internal object SubscriptionHeaders {
    const val TITLE = "profile-title"
    const val USER_INFO = "subscription-userinfo"
    const val SUPPORT_URL = "support-url"
    const val PAGE_URL = "profile-web-page-url"
    const val ANNOUNCE = "announce"
    const val UPDATE_INTERVAL = "profile-update-interval"
    const val USER_AGENT = "User-Agent"
    const val CLIENT = "clash.meta"
    private const val BASE64_PREFIX = "base64:"
    private const val MAX_TITLE_LENGTH = 128
    private const val MAX_ANNOUNCE_LENGTH = 500
    private const val MAX_LINK_LENGTH = 2048
    private val LINK_SCHEMES = setOf("http", "https", "tg")
    private val UPDATE_HOURS = 1..720

    fun title(raw: String?): String? =
        decoded(raw)?.trim()?.take(MAX_TITLE_LENGTH)?.takeIf { it.isNotEmpty() }

    fun announce(raw: String?): String? =
        decoded(raw)?.filter { it == '\n' || !it.isISOControl() }?.trim()?.take(MAX_ANNOUNCE_LENGTH)?.takeIf { it.isNotEmpty() }

    fun link(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_LINK_LENGTH } ?: return null
        val scheme = runCatching { URI(value) }.getOrNull()?.takeIf { it.isAbsolute }?.scheme ?: return null
        return value.takeIf { scheme.lowercase() in LINK_SCHEMES }
    }

    fun updateHours(raw: String?): Int? = raw?.trim()?.toIntOrNull()?.takeIf { it in UPDATE_HOURS }

    private fun decoded(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        return if (value.startsWith(BASE64_PREFIX, ignoreCase = true)) {
            decodeBase64Lenient(value.substring(BASE64_PREFIX.length))
        } else {
            value
        }
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
