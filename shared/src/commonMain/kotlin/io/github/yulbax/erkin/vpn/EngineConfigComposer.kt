package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.RoutedApps
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.vpn.core.EngineConfig
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.EngineRequirement
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
        get() = engineConfig.byeDpiPackages.isNotEmpty() || engineConfig.rules.any { it.target == RuleTarget.BYEDPI }

    fun isStructuralChangeFrom(other: AppliedConfig): Boolean =
        membershipKey != other.membershipKey ||
            routingKey != other.routingKey ||
            sessionRequirements != other.sessionRequirements
}

object EngineConfigComposer {

    fun compose(inputs: SessionInputs, ownPackage: String, byeDpiPort: Int, geoIpReady: Boolean = false): AppliedConfig {
        val selected = inputs.selected
        check(selected != null || inputs.profiles.isEmpty()) { "No server selected" }
        val foreignApps = inputs.apps.filterNot { it.packageName == ownPackage }
        val routed = RoutedApps.from(foreignApps, hasServers = selected != null)
        check(!routed.isEmpty) {
            if (selected == null) NO_SERVERS_NO_BYEDPI else "No apps assigned to VPN or ByeDPI"
        }
        val rules = inputs.rules.filter { geoIpReady || !it.match.requiresGeoIp }

        return AppliedConfig(
            engineConfig = EngineConfig(
                proxies = listOfNotNull(selected).map { it.engineProxy() },
                activeProxyTag = selected?.let { ProxyTag.of(it.id) }.orEmpty(),
                byeDpiPackages = routed.byeDpiPackages,
                vpnPackages = routed.vpnPackages,
                tunneledPackages = routed.tunneledPackages,
                byeDpiSocksPort = byeDpiPort,
                network = inputs.settings.networkOptions(),
                directPaths = routed.directPaths,
                rules = rules
            ),
            configName = selected?.name?.ifBlank { "(unnamed)" } ?: "(ByeDPI only)",
            membershipKey = selected?.let { "${it.id}:${it.outboundJson}" }.orEmpty(),
            sessionRequirements = selected?.engineProxy()?.sessionRequirements.orEmpty(),
            routingKey = foreignApps.sortedBy { it.packageName }
                .joinToString("|") { "${it.packageName}=${it.connectionType}" } +
                rules.joinToString("|", prefix = "#") { "${it.match.wire}:${it.value}=${it.target.wire}" },
            vpnActive = routed.hasVpn
        )
    }

    const val NO_SERVERS_NO_BYEDPI = "Add a server, or assign apps to ByeDPI to run without one"

    fun ProfileEntity.engineProxy(): EngineProxy =
        protocol?.engineProxy(ProxyTag.of(id), outboundJson) ?: EngineProxy(ProxyTag.of(id), outboundJson)
}
