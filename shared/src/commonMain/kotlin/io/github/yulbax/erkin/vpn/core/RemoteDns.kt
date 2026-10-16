package io.github.yulbax.erkin.vpn.core

import java.net.URI

object RemoteDns {
    private val SCHEMES = setOf("tls", "https", "quic", "udp", "tcp")
    private const val DEFAULT_SCHEME = "tls"
    private const val MAX_LENGTH = 255

    fun nameserver(value: String): String? {
        val input = value.trim()
        if (input.isEmpty() || input.length > MAX_LENGTH || input.any { it.isWhitespace() || it == '#' }) return null
        val url = if ("://" in input) input else "$DEFAULT_SCHEME://${if (':' in input && '[' !in input) "[$input]" else input}"
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in SCHEMES || uri.host.isNullOrEmpty()) return null
        return url
    }

    fun isValid(value: String): Boolean = nameserver(value) != null
}
