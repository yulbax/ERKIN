package io.github.yulbax.erkin.ui.screens.rules

import androidx.compose.runtime.Composable
import io.github.yulbax.erkin.rulelist.RuleListState
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.vpn.core.RuleListSource
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleSetKind
import io.github.yulbax.erkin.vpn.core.RuleTarget
import java.net.URI
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal enum class RuleSetGroup(val label: StringResource) {
    SERVICES(Res.string.rule_sets_group_services),
    ADS(Res.string.rule_sets_group_ads),
    TELEMETRY(Res.string.rule_sets_group_telemetry)
}

internal enum class RuleSetPreset(
    val group: RuleSetGroup,
    val label: StringResource,
    val url: String,
    val page: String,
    val target: RuleTarget,
    val kind: RuleSetKind? = null,
    val extra: List<RuleListSource> = emptyList()
) {
    YOUTUBE(RuleSetGroup.SERVICES, Res.string.rule_set_youtube, "$GEOSITE/youtube.mrs", "$GEOSITE_PAGE/youtube.list", RuleTarget.BYEDPI, RuleSetKind.DOMAIN),
    TELEGRAM(
        RuleSetGroup.SERVICES, Res.string.rule_set_telegram, "$GEOSITE/telegram.mrs", "$GEOSITE_PAGE/telegram.list", RuleTarget.VPN, RuleSetKind.DOMAIN,
        listOf(RuleListSource("$GEOIP/telegram.mrs", RuleSetKind.IPCIDR))
    ),
    DISCORD(RuleSetGroup.SERVICES, Res.string.rule_set_discord, "$GEOSITE/discord.mrs", "$GEOSITE_PAGE/discord.list", RuleTarget.BYEDPI, RuleSetKind.DOMAIN),
    HAGEZI_LIGHT(RuleSetGroup.ADS, Res.string.rule_set_hagezi_light, "$HAGEZI/light-onlydomains.txt", "$HAGEZI_PAGE#light", RuleTarget.BLOCK),
    HAGEZI_NORMAL(RuleSetGroup.ADS, Res.string.rule_set_hagezi_normal, "$HAGEZI/multi-onlydomains.txt", "$HAGEZI_PAGE#normal", RuleTarget.BLOCK),
    HAGEZI_PRO(RuleSetGroup.ADS, Res.string.rule_set_hagezi_pro, "$HAGEZI/pro-onlydomains.txt", "$HAGEZI_PAGE#pro", RuleTarget.BLOCK),
    OISD_SMALL(RuleSetGroup.ADS, Res.string.rule_set_oisd_small, "https://small.oisd.nl/domainswild", "https://oisd.nl", RuleTarget.BLOCK),
    XIAOMI(RuleSetGroup.TELEMETRY, Res.string.rule_set_xiaomi, "$HAGEZI/native.xiaomi-onlydomains.txt", "$HAGEZI_PAGE#native", RuleTarget.BLOCK),
    SAMSUNG(RuleSetGroup.TELEMETRY, Res.string.rule_set_samsung, "$HAGEZI/native.samsung-onlydomains.txt", "$HAGEZI_PAGE#native", RuleTarget.BLOCK);

    val source: RuleListSource get() = RuleListSource(url, kind)
    val sources: List<RuleListSource> get() = listOf(source) + extra

    companion object {
        fun of(url: String): RuleSetPreset? = entries.firstOrNull { it.url == url }

        fun extraOf(url: String): RuleSetPreset? = entries.firstOrNull { preset -> preset.extra.any { it.url == url } }
    }
}

private const val HAGEZI = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard"
private const val GEOSITE = "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/meta/geo/geosite"
private const val GEOIP = "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/meta/geo/geoip"
private const val HAGEZI_PAGE = "https://github.com/hagezi/dns-blocklists"
private const val GEOSITE_PAGE = "https://github.com/MetaCubeX/meta-rules-dat/blob/meta/geo/geosite"

@Composable
internal fun ruleSetName(url: String, state: RuleListState?): String {
    state?.name?.let { return it }
    RuleSetPreset.of(url)?.let { return stringResource(it.label) }
    RuleSetPreset.extraOf(url)?.let { return "${stringResource(it.label)} ($IP_SUFFIX)" }
    RuleLists.countryOf(url)?.let { return stringResource(Res.string.rule_set_country, countryName(it)) }
    return state?.title ?: linkName(url)
}

private const val IP_SUFFIX = "IP"

internal fun linkName(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    return uri.path.orEmpty().substringAfterLast('/').ifEmpty { uri.host ?: url }
}

internal fun linkHost(url: String): String = runCatching { URI(url).host }.getOrNull() ?: url
