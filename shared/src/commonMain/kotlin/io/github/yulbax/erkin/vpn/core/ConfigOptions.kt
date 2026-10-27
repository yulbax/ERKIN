package io.github.yulbax.erkin.vpn.core

enum class Ipv6Mode(val wire: String) {
    DISABLE("disable"),
    ENABLE("enable");

    val ipv6Enabled: Boolean get() = this != DISABLE

    companion object {
        private val LEGACY_ENABLED = setOf("prefer", "only")

        fun fromWire(value: String?): Ipv6Mode =
            entries.firstOrNull { it.wire == value } ?: if (value in LEGACY_ENABLED) ENABLE else DISABLE

        fun isKnownWire(value: String): Boolean = entries.any { it.wire == value } || value in LEGACY_ENABLED
    }
}

enum class TlsFingerprint(val wire: String) {
    CHROME("chrome"),
    FIREFOX("firefox"),
    EDGE("edge"),
    SAFARI("safari"),
    IOS("ios"),
    ANDROID("android"),
    RANDOM("random"),
    RANDOMIZED("randomized");

    companion object {
        fun fromWire(value: String?): TlsFingerprint? =
            value?.let { v -> entries.firstOrNull { it.wire == v } }
    }
}

enum class DnsTransport(val wire: String) {
    TLS("tls"),
    UDP("udp");

    val encrypted: Boolean get() = this == TLS

    companion object {
        fun of(encrypted: Boolean): DnsTransport = if (encrypted) TLS else UDP

        fun fromWire(value: String?): DnsTransport = entries.firstOrNull { it.wire == value } ?: TLS

        fun isKnownWire(value: String): Boolean = entries.any { it.wire == value }
    }
}

data class ByeDpiDns(val address: String, val transport: DnsTransport) {
    fun nameserver(): String {
        val host = if (':' in address) "[$address]" else address
        return "${transport.wire}://$host"
    }

    companion object {
        val DEFAULT = ByeDpiDns("1.1.1.1", DnsTransport.TLS)

        fun normalizeAddress(raw: String): String? {
            val input = raw.trim().lowercase()
            if ('/' in input) return null
            return input.takeIf { RuleValues.Cidr.normalize(it) != null }
        }
    }
}
