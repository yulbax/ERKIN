package io.github.yulbax.erkin.data

import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import io.github.yulbax.erkin.proxy.ParsedProfile
import kotlinx.serialization.Serializable

/** Stable external format. Room entities must not become part of this contract. */
@Serializable
data class AppConfigBackup(
    val version: Int = MIN_SUPPORTED_VERSION,
    val apps: Map<String, String>? = null,
    val settings: BackupSettings? = null,
    val profiles: List<BackupProfile>? = null,
    val rules: List<BackupRule>? = null
) {
    companion object {
        const val MIN_SUPPORTED_VERSION = 1
        const val CURRENT_VERSION = 5
        const val MAX_IMPORT_BYTES = 5L * 1024 * 1024
        const val MAX_ENTRIES_PER_SECTION = 10_000
    }
}

@Serializable
data class BackupSettings(
    val showSystemApps: Boolean = SETTINGS_DEFAULTS.showSystemApps,
    val byeDpiArgs: String = SETTINGS_DEFAULTS.byeDpiArgs,
    val mtu: Int = SETTINGS_DEFAULTS.mtu,
    val ipv6Mode: String = SETTINGS_DEFAULTS.ipv6Mode,
    val dnsRemote: String = SETTINGS_DEFAULTS.dnsRemote,
    val dnsDirect: String = SETTINGS_DEFAULTS.dnsDirect,
    val sniff: Boolean = SETTINGS_DEFAULTS.sniff,
    val bypassLan: Boolean = SETTINGS_DEFAULTS.bypassLan,
    val autoConnect: Boolean = SETTINGS_DEFAULTS.autoConnect,
    val preferredFingerprint: String = SETTINGS_DEFAULTS.preferredFingerprint,
    val homeHintSeen: Boolean = SETTINGS_DEFAULTS.homeHintSeen,
    val appsHintSeen: Boolean = SETTINGS_DEFAULTS.appsHintSeen,
    val newAppsConnectionType: String = SETTINGS_DEFAULTS.newAppsConnectionType,
    val rulesHintSeen: Boolean = SETTINGS_DEFAULTS.rulesHintSeen
)

private val SETTINGS_DEFAULTS = SettingsEntity()

@Serializable
data class BackupProfile(
    val name: String = "",
    val type: String = "",
    val link: String = "",
    val outboundJson: String = "",
    val selected: Boolean = false,
    val subscriptionUrl: String = ""
)

@Serializable
data class BackupRule(
    val type: String = "",
    val value: String = "",
    val target: String = ""
)

fun SettingsEntity.toBackupSettings() = BackupSettings(
    showSystemApps = showSystemApps,
    byeDpiArgs = byeDpiArgs,
    mtu = mtu,
    ipv6Mode = ipv6Mode,
    dnsRemote = dnsRemote,
    dnsDirect = dnsDirect,
    sniff = sniff,
    bypassLan = bypassLan,
    autoConnect = autoConnect,
    preferredFingerprint = preferredFingerprint,
    homeHintSeen = homeHintSeen,
    appsHintSeen = appsHintSeen,
    newAppsConnectionType = newAppsConnectionType,
    rulesHintSeen = rulesHintSeen
)

fun BackupSettings.toEntity() = SettingsEntity(
    showSystemApps = showSystemApps,
    byeDpiArgs = byeDpiArgs,
    mtu = mtu,
    ipv6Mode = ipv6Mode,
    dnsRemote = dnsRemote,
    dnsDirect = dnsDirect,
    sniff = sniff,
    bypassLan = bypassLan,
    autoConnect = autoConnect,
    preferredFingerprint = preferredFingerprint,
    homeHintSeen = homeHintSeen,
    appsHintSeen = appsHintSeen,
    newAppsConnectionType = newAppsConnectionType,
    rulesHintSeen = rulesHintSeen
)

fun ProfileEntity.toBackupProfile() = BackupProfile(
    name = name,
    type = type,
    link = link,
    outboundJson = outboundJson,
    selected = selected,
    subscriptionUrl = subscriptionUrl
)

fun BackupProfile.toEntity(parsed: ParsedProfile) = ProfileEntity(
    name = name,
    type = parsed.protocol.wire,
    link = link,
    outboundJson = parsed.proxyJson(),
    subscriptionUrl = subscriptionUrl
)

fun RoutingRuleEntity.toBackupRule() = BackupRule(type = type, value = value, target = target)

fun BackupRule.toEntity() = RoutingRuleEntity(type = type, value = value, target = target)
