package io.github.yulbax.erkin.vpn.core

data class EngineConfig(
    val proxies: List<EngineProxy>,
    val activeProxyTag: String,
    val byeDpiPackages: List<String>,
    val vpnPackages: List<String>,
    val tunneledPackages: List<String>,
    val byeDpiSocksPort: Int,
    val network: NetworkOptions,
    val directPaths: List<String> = emptyList()
)

data class EngineProxy(
    val tag: String,
    val outboundDescriptor: String,
    val placement: EnginePlacement = EnginePlacement.OUTBOUND,
    val requirements: Set<EngineRequirement> = emptySet()
) {
    val sessionRequirements: Set<EngineRequirement>
        get() = requirements.filterTo(mutableSetOf()) { it.sessionWide }
}

enum class EnginePlacement { OUTBOUND, ENDPOINT }

enum class EngineRequirement(val sessionWide: Boolean) {
    TLS_FINGERPRINT(sessionWide = false),
    RESOLVE_BEFORE_DIAL(sessionWide = true),
    IPV4_ONLY(sessionWide = true)
}
