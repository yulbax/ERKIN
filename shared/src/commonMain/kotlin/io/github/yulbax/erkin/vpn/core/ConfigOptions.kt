package io.github.yulbax.erkin.vpn.core

enum class Ipv6Mode(val wire: String, val dnsStrategy: String) {
    DISABLE("disable", "ipv4_only"),
    ENABLE("enable", "prefer_ipv4"),
    PREFER("prefer", "prefer_ipv6"),
    ONLY("only", "ipv6_only");

    val ipv6Enabled: Boolean get() = this != DISABLE

    companion object {
        fun fromWire(value: String?): Ipv6Mode =
            entries.firstOrNull { it.wire == value } ?: DISABLE
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