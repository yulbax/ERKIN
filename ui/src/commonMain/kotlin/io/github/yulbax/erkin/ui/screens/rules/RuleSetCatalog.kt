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
    ADS(Res.string.rule_sets_group_ads),
    TELEMETRY(Res.string.rule_sets_group_telemetry)
}

internal enum class RuleSetPreset(
    val group: RuleSetGroup,
    val label: StringResource,
    val url: String,
    val target: RuleTarget,
    val kind: RuleSetKind? = null
) {
    HAGEZI_LIGHT(RuleSetGroup.ADS, Res.string.rule_set_hagezi_light, "$HAGEZI/light-onlydomains.txt", RuleTarget.BLOCK),
    HAGEZI_NORMAL(RuleSetGroup.ADS, Res.string.rule_set_hagezi_normal, "$HAGEZI/multi-onlydomains.txt", RuleTarget.BLOCK),
    HAGEZI_PRO(RuleSetGroup.ADS, Res.string.rule_set_hagezi_pro, "$HAGEZI/pro-onlydomains.txt", RuleTarget.BLOCK),
    OISD_SMALL(RuleSetGroup.ADS, Res.string.rule_set_oisd_small, "https://small.oisd.nl/domainswild", RuleTarget.BLOCK),
    XIAOMI(RuleSetGroup.TELEMETRY, Res.string.rule_set_xiaomi, "$HAGEZI/native.xiaomi-onlydomains.txt", RuleTarget.BLOCK),
    SAMSUNG(RuleSetGroup.TELEMETRY, Res.string.rule_set_samsung, "$HAGEZI/native.samsung-onlydomains.txt", RuleTarget.BLOCK);

    val source: RuleListSource get() = RuleListSource(url, kind)

    companion object {
        fun of(url: String): RuleSetPreset? = entries.firstOrNull { it.url == url }
    }
}

private const val HAGEZI = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard"

@Composable
internal fun ruleSetName(url: String, state: RuleListState?): String {
    state?.name?.let { return it }
    RuleSetPreset.of(url)?.let { return stringResource(it.label) }
    RuleLists.countryOf(url)?.let { return stringResource(Res.string.rule_set_country, countryName(it)) }
    return state?.title ?: linkName(url)
}

internal fun linkName(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    return uri.path.orEmpty().substringAfterLast('/').ifEmpty { uri.host ?: url }
}

internal fun linkHost(url: String): String = runCatching { URI(url).host }.getOrNull() ?: url
