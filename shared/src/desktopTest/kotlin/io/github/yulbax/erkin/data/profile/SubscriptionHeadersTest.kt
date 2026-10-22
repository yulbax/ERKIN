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
}
