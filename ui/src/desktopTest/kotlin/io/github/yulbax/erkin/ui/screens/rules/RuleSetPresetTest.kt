package io.github.yulbax.erkin.ui.screens.rules

import io.github.yulbax.erkin.vpn.core.RuleSetKind
import io.github.yulbax.erkin.vpn.core.RuleTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleSetPresetTest {

    @Test
    fun everyListOfEveryPresetHasItsOwnLink() {
        val links = RuleSetPreset.entries.flatMap { preset -> preset.sources.map { it.url } }

        assertEquals(links.size, links.toSet().size)
    }

    @Test
    fun telegramBringsItsAddressesAlongWithItsDomains() {
        val telegram = RuleSetPreset.TELEGRAM

        assertEquals(listOf(RuleSetKind.DOMAIN, RuleSetKind.IPCIDR), telegram.sources.map { it.kind })
        assertEquals(telegram, RuleSetPreset.extraOf(telegram.extra.single().url))
        assertNull(RuleSetPreset.extraOf(telegram.url))
    }

    @Test
    fun servicesStartOnTheChannelThatUsuallyWorksForThem() {
        assertEquals(
            mapOf(RuleSetPreset.YOUTUBE to RuleTarget.BYEDPI, RuleSetPreset.TELEGRAM to RuleTarget.VPN, RuleSetPreset.DISCORD to RuleTarget.BYEDPI),
            RuleSetPreset.entries.filter { it.group == RuleSetGroup.SERVICES }.associateWith { it.target }
        )
    }
}
