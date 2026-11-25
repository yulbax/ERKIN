package io.github.yulbax.erkin.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionHeadersTest {
    @Test
    fun readsPlainAndBase64Titles() {
        assertEquals("My VPN", SubscriptionHeaders.title(" My VPN "))
        assertEquals("Мой VPN", SubscriptionHeaders.title("base64:0JzQvtC5IFZQTg=="))
        assertNull(SubscriptionHeaders.title("  "))
        assertNull(SubscriptionHeaders.title(null))
    }

    @Test
    fun readsTrafficAndExpiry() {
        assertEquals(
            SubscriptionUsage(upload = 100, download = 2_000, total = 10_737_418_240, expire = 1_900_000_000),
            SubscriptionHeaders.usage("upload=100; download=2000; total=10737418240; expire=1900000000")
        )
        assertEquals(SubscriptionUsage(0, 5, 0, 0), SubscriptionHeaders.usage("download=5"))
        assertNull(SubscriptionHeaders.usage("nonsense"))
        assertNull(SubscriptionHeaders.usage(null))
    }

    @Test
    fun linksAcceptOnlyWebAndTelegramSchemes() {
        assertEquals("https://t.example/support", SubscriptionHeaders.link(" https://t.example/support "))
        assertEquals("HTTP://t.example", SubscriptionHeaders.link("HTTP://t.example"))
        assertEquals("tg://resolve?domain=help", SubscriptionHeaders.link("tg://resolve?domain=help"))
        assertNull(SubscriptionHeaders.link("javascript:alert(1)"))
        assertNull(SubscriptionHeaders.link("file:///etc/passwd"))
        assertNull(SubscriptionHeaders.link("/relative/path"))
        assertNull(SubscriptionHeaders.link("  "))
        assertNull(SubscriptionHeaders.link(null))
        assertNull(SubscriptionHeaders.link("https://t.example/" + "a".repeat(2048)))
    }

    @Test
    fun announcementsDecodeBase64AndAreCapped() {
        assertEquals("Мой VPN", SubscriptionHeaders.announce("base64:0JzQvtC5IFZQTg=="))
        assertEquals("Works\nTomorrow", SubscriptionHeaders.announce(" Works\nTo\u0007morrow "))
        assertEquals(500, SubscriptionHeaders.announce("x".repeat(900))?.length)
        assertNull(SubscriptionHeaders.announce("   "))
        assertNull(SubscriptionHeaders.announce(null))
    }

    @Test
    fun updateIntervalsAreWholeHoursWithinAMonth() {
        assertEquals(2, SubscriptionHeaders.updateHours("2"))
        assertEquals(720, SubscriptionHeaders.updateHours(" 720 "))
        assertNull(SubscriptionHeaders.updateHours("0"))
        assertNull(SubscriptionHeaders.updateHours("721"))
        assertNull(SubscriptionHeaders.updateHours("abc"))
        assertNull(SubscriptionHeaders.updateHours(null))
    }
}
