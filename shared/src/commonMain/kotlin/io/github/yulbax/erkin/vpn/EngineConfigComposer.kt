package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.RoutedApps
import io.github.yulbax.erkin.data.profile.ChainEntry
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProxyChain
import io.github.yulbax.erkin.data.profile.ServerKind
import io.github.yulbax.erkin.data.profile.isLink
import io.github.yulbax.erkin.data.profile.kind
import io.github.yulbax.erkin.rulelist.InstalledRuleList
import io.github.yulbax.erkin.vpn.core.BYEDPI_PROXY_TAG
import io.github.yulbax.erkin.vpn.core.EngineConfig
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.vpn.core.RuleTarget

object ProxyTag {
    private const val PREFIX = "p"

    fun of(profileId: Long): String = "$PREFIX$profileId"

    fun profileId(tag: String): Long? =
        tag.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.toLongOrNull()
}

data class AppliedConfig(
    val engineConfig: EngineConfig,
    val configName: String,
    val membershipKey: String,
    val sessionRequirements: Set<EngineRequirement>,
    val routingKey: String,
    val vpnActive: Boolean
) {
    val selectedTag: String get() = engineConfig.activeProxyTag
    val needsByeDpi: Boolean
        get() = engineConfig.byeDpiPackages.isNotEmpty() ||
            engineConfig.rules.any { it.target == RuleTarget.BYEDPI } ||
            engineConfig.proxies.any { it.dialerProxy == BYEDPI_PROXY_TAG }

    fun isStructuralChangeFrom(other: AppliedConfig): Boolean =
        membershipKey != other.membershipKey ||
            routingKey != other.routingKey ||
            sessionRequirements != other.sessionRequirements
}

object EngineConfigComposer {

    fun compose(
        inputs: SessionInputs,
        ownPackage: String,
        byeDpiPort: Int,
        geoIpReady: Boolean = false,
        ruleLists: (String) -> InstalledRuleList? = { null }
    ): AppliedConfig {
        val selected = inputs.selected
        check(selected != null || inputs.profiles.isEmpty()) { "No server selected" }
        val foreignApps = inputs.apps.filterNot { it.packageName == ownPackage }
        val routed = RoutedApps.from(foreignApps, hasServers = selected != null)
        check(!routed.isEmpty) {
            if (selected == null) NO_SERVERS_NO_BYEDPI else "No apps assigned to VPN or ByeDPI"
        }
        val lists = inputs.rules.filter { it.match == RuleMatch.LIST }.map { it.value }.distinct()
            .mapNotNull { url -> ruleLists(url)?.let { url to it } }.toMap()
        val rules = inputs.rules.filter { rule ->
            (geoIpReady || !rule.match.requiresGeoIp) && (rule.match != RuleMatch.LIST || rule.value in lists)
        }
        val proxies = selected?.let { proxiesFor(it, inputs.profiles) }.orEmpty()

        return AppliedConfig(
            engineConfig = EngineConfig(
                proxies = proxies,
                activeProxyTag = selected?.let { ProxyTag.of(it.id) }.orEmpty(),
                byeDpiPackages = routed.byeDpiPackages,
                vpnPackages = routed.vpnPackages,
                tunneledPackages = routed.tunneledPackages,
                byeDpiSocksPort = byeDpiPort,
                network = inputs.settings.networkOptions(),
                directPaths = routed.directPaths,
                rules = rules,
                ruleLists = lists.mapValues { it.value.kinds }
            ),
            configName = selected?.name?.ifBlank { "(unnamed)" } ?: "(ByeDPI only)",
            membershipKey = proxies.joinToString("|") { "${it.tag}:${it.descriptor}:${it.dialerProxy.orEmpty()}" },
            sessionRequirements = proxies.lastOrNull()?.sessionRequirements.orEmpty(),
            routingKey = foreignApps.sortedBy { it.packageName }
                .joinToString("|") { "${it.packageName}=${it.connectionType}" } +
                rules.joinToString("|", prefix = "#") { "${it.match.wire}:${it.value}=${it.target.wire}" } +
                lists.entries.joinToString("|", prefix = "#") { "${it.key}@${it.value.updatedAt}" },
            vpnActive = routed.hasVpn
        )
    }

    const val NO_SERVERS_NO_BYEDPI = "Add a server, or assign apps to ByeDPI to run without one"

    private fun proxiesFor(selected: ProfileEntity, profiles: List<ProfileEntity>): List<EngineProxy> =
        when (val kind = selected.kind) {
            is ServerKind.Link -> listOf(selected.engineProxy())
            is ServerKind.Chain -> chainProxies(selected, checkNotNull(kind.chain) { "Chain '${selected.name}' is broken" }, profiles)
        }

    private fun chainProxies(selected: ProfileEntity, chain: ProxyChain, profiles: List<ProfileEntity>): List<EngineProxy> {
        check(chain.isValid) { "Chain '${selected.name}' is broken" }
        fun server(id: Long) = checkNotNull(profiles.firstOrNull { it.id == id && it.isLink }) {
            "A server of chain '${selected.name}' was removed"
        }
        val proxies = mutableListOf<EngineProxy>()
        var dialer: String? = null
        chain.hops.forEach { hop ->
            dialer = when (hop) {
                ChainEntry.ByeDpi -> BYEDPI_PROXY_TAG
                is ChainEntry.Server -> server(hop.profileId).engineProxy().copy(dialerProxy = dialer).also(proxies::add).tag
            }
        }
        proxies += server(chain.exitId).engineProxy().copy(tag = ProxyTag.of(selected.id), dialerProxy = dialer)
        return proxies
    }

    fun ProfileEntity.engineProxy(): EngineProxy =
        protocol?.engineProxy(ProxyTag.of(id), outboundJson) ?: EngineProxy(ProxyTag.of(id), outboundJson)
}
