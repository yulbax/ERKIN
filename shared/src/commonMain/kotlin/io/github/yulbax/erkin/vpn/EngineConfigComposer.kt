package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.ConnectionType
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
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleTarget

sealed interface ProxyRef {
    data class Profile(val id: Long) : ProxyRef

    data class Hop(val chainId: Long, val index: Int) : ProxyRef
}

object ProxyTag {
    private const val PREFIX = "p"
    private const val HOP = 'h'

    fun of(profileId: Long): String = "$PREFIX$profileId"

    fun hop(chainId: Long, index: Int): String = "${of(chainId)}$HOP$index"

    fun parse(tag: String): ProxyRef? {
        val body = tag.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX) ?: return null
        if (HOP !in body) return body.toLongOrNull()?.let(ProxyRef::Profile)
        val chainId = body.substringBefore(HOP).toLongOrNull() ?: return null
        val index = body.substringAfter(HOP).toIntOrNull() ?: return null
        return ProxyRef.Hop(chainId, index)
    }

    fun profileId(tag: String): Long? = (parse(tag) as? ProxyRef.Profile)?.id
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
        ruleLists: (String) -> InstalledRuleList? = { null }
    ): AppliedConfig {
        val selected = inputs.selected
        if (selected == null && inputs.profiles.isNotEmpty()) fail(VpnFailure.NoServerSelected)
        val foreignApps = inputs.apps.filterNot { it.packageName == ownPackage }
        val routed = RoutedApps.from(foreignApps, hasServers = selected != null)
        if (routed.isEmpty) fail(if (selected == null) VpnFailure.NoServers else VpnFailure.NoRoutedApps)
        val lists = inputs.rules.mapNotNull(RuleLists::url).distinct()
            .mapNotNull { url -> ruleLists(url)?.let { url to it } }.toMap()
        val rules = inputs.rules.filter { rule -> RuleLists.url(rule)?.let { it in lists } ?: true }
        val active = selected?.let { proxiesFor(it, inputs.profiles) }.orEmpty()
        val boundIds = foreignApps.filter { it.connectionType == ConnectionType.VPN }.mapNotNull { it.serverId } +
            rules.filter { it.target == RuleTarget.VPN }.mapNotNull { it.serverId }
        val bound = boundServers(boundIds, selected, inputs.profiles)
        val proxies = (active + bound.values.flatten()).distinctBy { it.tag }
        val vpnTargets = foreignApps.mapNotNull { app ->
            app.serverId?.takeIf { it in bound }?.let { app.packageName to ProxyTag.of(it) }
        }.toMap()

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
                ruleLists = lists.mapValues { it.value.kinds },
                vpnTargets = vpnTargets,
                directPackages = routed.directPackages,
                blockedPackages = routed.blockedPackages,
                serverTags = bound.keys.associateWith(ProxyTag::of)
            ),
            configName = selected?.name?.ifBlank { "(unnamed)" } ?: when {
                routed.hasByeDpi -> "(ByeDPI only)"
                routed.hasBlocked -> "(Block only)"
                else -> "(Direct only)"
            },
            membershipKey = proxies.joinToString("|") { "${it.tag}:${it.descriptor}:${it.dialerProxy.orEmpty()}" },
            sessionRequirements = (listOf(active) + bound.values).flatMapTo(mutableSetOf()) {
                it.lastOrNull()?.sessionRequirements.orEmpty()
            },
            routingKey = foreignApps.sortedBy { it.packageName }
                .joinToString("|") { "${it.packageName}=${it.connectionType}${it.serverId?.let { id -> "@$id" }.orEmpty()}" } +
                rules.joinToString("|", prefix = "#") { "${it.app?.let { app -> "$app~" }.orEmpty()}${it.match.wire}:${it.value}=${it.target.wire}${it.serverId?.let { id -> "@$id" }.orEmpty()}" } +
                lists.entries.joinToString("|", prefix = "#") { "${it.key}@${it.value.updatedAt}" },
            vpnActive = routed.hasVpn || (selected != null && rules.any { it.target == RuleTarget.VPN })
        )
    }

    private fun boundServers(
        serverIds: List<Long>,
        selected: ProfileEntity?,
        profiles: List<ProfileEntity>
    ): Map<Long, List<EngineProxy>> {
        if (selected == null) return emptyMap()
        return serverIds.asSequence()
            .filter { it != selected.id }
            .distinct()
            .mapNotNull { id -> profiles.firstOrNull { it.id == id } }
            .mapNotNull { profile -> runCatching { proxiesFor(profile, profiles) }.getOrNull()?.let { profile.id to it } }
            .toMap()
    }

    fun proxiesFor(selected: ProfileEntity, profiles: List<ProfileEntity>): List<EngineProxy> =
        when (val kind = selected.kind) {
            is ServerKind.Link -> listOf(selected.engineProxy())
            is ServerKind.Chain -> chainProxies(selected, kind.chain ?: fail(VpnFailure.BrokenChain(selected.name)), profiles)
        }

    private fun chainProxies(selected: ProfileEntity, chain: ProxyChain, profiles: List<ProfileEntity>): List<EngineProxy> {
        if (!chain.isValid) fail(VpnFailure.BrokenChain(selected.name))
        fun server(id: Long) = profiles.firstOrNull { it.id == id && it.isLink } ?: fail(VpnFailure.ChainServerRemoved(selected.name))
        val proxies = mutableListOf<EngineProxy>()
        var dialer: String? = null
        chain.hops.forEachIndexed { index, hop ->
            dialer = when (hop) {
                ChainEntry.ByeDpi -> BYEDPI_PROXY_TAG
                is ChainEntry.Server -> server(hop.profileId).engineProxy()
                    .copy(tag = ProxyTag.hop(selected.id, index), dialerProxy = dialer).also(proxies::add).tag
            }
        }
        proxies += server(chain.exitId).engineProxy().copy(tag = ProxyTag.of(selected.id), dialerProxy = dialer)
        return proxies
    }

    fun ProfileEntity.engineProxy(): EngineProxy =
        protocol?.engineProxy(ProxyTag.of(id), outboundJson) ?: EngineProxy(ProxyTag.of(id), outboundJson)
}
