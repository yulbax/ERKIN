package io.github.yulbax.erkin.vpn.core

data class EngineConfig(
    val proxies: List<EngineProxy>,
    val activeProxyTag: String,
    val byeDpiPackages: List<String>,
    val vpnPackages: List<String>,
    val tunneledPackages: List<String>,
    val byeDpiSocksPort: Int,
    val network: NetworkOptions,
    val directPaths: List<String> = emptyList(),
    val rules: List<RoutingRule> = emptyList(),
    val ruleLists: Map<String, Set<RuleSetKind>> = emptyMap(),
    val vpnTargets: Map<String, String> = emptyMap()
)

data class EngineProxy(
    val tag: String,
    val descriptor: String,
    val requirements: Set<EngineRequirement> = emptySet(),
    val dialerProxy: String? = null
) {
    val sessionRequirements: Set<EngineRequirement>
        get() = requirements.filterTo(mutableSetOf()) { it.sessionWide }
}

const val BYEDPI_PROXY_TAG = "byedpi"

enum class EngineRequirement(val sessionWide: Boolean) {
    TLS_FINGERPRINT(sessionWide = false),
    IPV4_ONLY(sessionWide = true)
}
