package io.github.yulbax.erkin.data

import io.github.yulbax.erkin.data.profile.ChainEntry
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProxyChain
import io.github.yulbax.erkin.data.profile.ServerKind
import io.github.yulbax.erkin.data.profile.isLink
import io.github.yulbax.erkin.data.profile.kind
import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import io.github.yulbax.erkin.proxy.LinkParser
import io.github.yulbax.erkin.util.readBounded
import io.github.yulbax.erkin.vpn.core.Ipv6Mode
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.core.RemoteDns
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.vpn.core.RuleTarget
import io.github.yulbax.erkin.vpn.core.TlsFingerprint
import java.io.InputStream
import java.net.URI
import kotlinx.serialization.json.Json

object BackupCodec {
    private const val MAX_PACKAGE_NAME_LENGTH = 255
    private const val MAX_PROFILE_NAME_LENGTH = 512
    private const val MAX_LINK_LENGTH = 16 * 1024
    private const val MAX_OUTBOUND_LENGTH = 256 * 1024
    private const val MAX_BYEDPI_ARGS_LENGTH = 16 * 1024
    private const val MAX_DNS_LENGTH = 255

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun encode(backup: AppConfigBackup): String =
        json.encodeToString(AppConfigBackup.serializer(), backup)

    fun decode(text: String): AppConfigBackup? =
        runCatching { json.decodeFromString(AppConfigBackup.serializer(), text) }
            .getOrNull()
            ?.let { it.copy(rules = it.rules?.map(RoutingRuleEntity::upgraded)) }
            ?.takeIf(::isValid)

    fun read(input: InputStream): AppConfigBackup? =
        runCatching { input.readBounded(AppConfigBackup.MAX_IMPORT_BYTES).decodeToString() }
            .getOrNull()
            ?.let(::decode)

    fun isValid(backup: AppConfigBackup): Boolean {
        if (backup.version !in AppConfigBackup.MIN_SUPPORTED_VERSION..AppConfigBackup.CURRENT_VERSION) {
            return false
        }
        if (backup.apps == null && backup.settings == null && backup.profiles == null && backup.rules == null) return false
        if ((backup.apps?.size ?: 0) > AppConfigBackup.MAX_ENTRIES_PER_SECTION) return false
        if ((backup.profiles?.size ?: 0) > AppConfigBackup.MAX_ENTRIES_PER_SECTION) return false
        if ((backup.rules?.size ?: 0) > AppConfigBackup.MAX_ENTRIES_PER_SECTION) return false
        if ((backup.appServers?.size ?: 0) > AppConfigBackup.MAX_ENTRIES_PER_SECTION) return false
        if ((backup.profiles?.count { it.selected } ?: 0) > 1) return false
        if (backup.apps?.any(::isInvalidAppBackup) == true) return false
        if (backup.settings?.let(::isValidSettingsBackup) == false) return false
        if (backup.profiles?.let(::isValidProfilesBackup) == false) return false
        if (backup.rules?.any { !isValidRuleBackup(it) } == true) return false
        return true
    }

    private fun isInvalidAppBackup(entry: Map.Entry<String, String>): Boolean {
        val packageName = entry.key
        return packageName.isBlank() ||
            packageName.length > MAX_PACKAGE_NAME_LENGTH ||
            packageName.any(Char::isWhitespace) ||
            ConnectionType.fromWire(entry.value) == null
    }

    private fun isValidSettingsBackup(settings: SettingsEntity): Boolean =
        settings.mtu in NetworkOptions.MTU_RANGE &&
            settings.byeDpiArgs.length <= MAX_BYEDPI_ARGS_LENGTH &&
            Ipv6Mode.isKnownWire(settings.ipv6Mode) &&
            isValidDns(settings.dnsRemote) && RemoteDns.isValid(settings.dnsRemote) &&
            isValidDns(settings.dnsDirect) &&
            (settings.newAppsConnectionType.isBlank() || ConnectionType.fromWire(settings.newAppsConnectionType) != null) &&
            (settings.preferredFingerprint.isBlank() ||
                TlsFingerprint.fromWire(settings.preferredFingerprint) != null)

    private fun isValidRuleBackup(rule: RoutingRuleEntity): Boolean {
        val match = RuleMatch.fromWire(rule.type) ?: return false
        if (RuleTarget.fromWire(rule.target) == null) return false
        return match.normalize(rule.value) == rule.value
    }

    private fun isValidDns(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_DNS_LENGTH && value.none(Char::isWhitespace)

    private fun isValidProfilesBackup(backups: List<ProfileBackup>): Boolean {
        val linkIds = backups.map { it.toEntity() }.filter { it.isLink && it.id != 0L }.map { it.id }
        if (linkIds.size != linkIds.toSet().size) return false
        return backups.all { backup ->
            val profile = backup.toEntity()
            when (val kind = profile.kind) {
                is ServerKind.Link -> isValidSubscriptionUrl(backup.subscriptionUrl) && isValidProfileBackup(profile)
                is ServerKind.Chain -> isValidChainBackup(profile, kind.chain, linkIds.toSet())
            }
        }
    }

    private fun isValidChainBackup(profile: ProfileEntity, chain: ProxyChain?, linkIds: Set<Long>): Boolean {
        if (chain == null || profile.name.isBlank() || profile.name.length > MAX_PROFILE_NAME_LENGTH) return false
        return chain.isValid && chain.serverIds.all { it in linkIds }
    }

    private fun isValidProfileBackup(profile: ProfileEntity): Boolean {
        if (profile.name.isBlank() || profile.name.length > MAX_PROFILE_NAME_LENGTH) return false
        if (profile.link.isBlank() || profile.link.length > MAX_LINK_LENGTH) return false
        if (profile.outboundJson.isBlank() || profile.outboundJson.length > MAX_OUTBOUND_LENGTH) return false

        val parsed = LinkParser.parse(profile.link) ?: return false
        if (parsed.protocol.wire != profile.type) return false
        val server = parsed.protocol.handler.server(parsed.proxy) ?: return false
        return server.host.isNotBlank() && server.port in 1..65535
    }

    private fun isValidSubscriptionUrl(value: String): Boolean {
        if (value.isBlank()) return true
        if (value.length > MAX_LINK_LENGTH) return false
        return runCatching {
            val uri = URI(value)
            uri.scheme?.lowercase() in setOf("http", "https") && !uri.rawAuthority.isNullOrBlank()
        }.getOrDefault(false)
    }
}
