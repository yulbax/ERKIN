package io.github.yulbax.erkin.ui.screens.rules

import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.vpn.core.RuleMatch
import org.jetbrains.compose.resources.StringResource

internal class RuleMatchUi(
    val label: StringResource,
    val shortLabel: StringResource,
    val example: StringResource
)

internal val RuleMatch.ui: RuleMatchUi
    get() = when (this) {
        RuleMatch.DOMAIN_SUFFIX -> RuleMatchUi(Res.string.rule_type_suffix, Res.string.rule_type_suffix_short, Res.string.rule_example_suffix)
        RuleMatch.DOMAIN -> RuleMatchUi(Res.string.rule_type_domain, Res.string.rule_type_domain_short, Res.string.rule_example_domain)
        RuleMatch.DOMAIN_KEYWORD -> RuleMatchUi(Res.string.rule_type_keyword, Res.string.rule_type_keyword_short, Res.string.rule_example_keyword)
        RuleMatch.IP_CIDR -> RuleMatchUi(Res.string.rule_type_ip, Res.string.rule_type_ip_short, Res.string.rule_example_ip)
    }
