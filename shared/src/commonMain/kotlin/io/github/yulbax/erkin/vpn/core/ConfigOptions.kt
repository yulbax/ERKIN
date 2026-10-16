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