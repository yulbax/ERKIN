package io.github.yulbax.erkin.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionDeviceTest {

    @Test
    fun hwidFollowsThePanelFormat() {
        assertTrue(DeviceIdentity.isValidHwid("9774d56d682e549c"))
        assertTrue(DeviceIdentity.isValidHwid("123e4567-e89b-12d3-a456-426614174000"))
        assertFalse(DeviceIdentity.isValidHwid("short"))
        assertFalse(DeviceIdentity.isValidHwid("has space in it"))
        assertFalse(DeviceIdentity.isValidHwid("a".repeat(65)))
    }

    @Test
    fun blankDetailsAreNotSent() {
        val headers = DeviceIdentity("9774d56d682e549c", "Android", "16", "").headers

        assertEquals(mapOf("x-hwid" to "9774d56d682e549c", "x-device-os" to "Android", "x-ver-os" to "16"), headers)
    }

    @Test
    fun panelHeadersTellARefusalApart() {
        fun refusal(code: Int, vararg headers: Pair<String, String>) =
            SubscriptionRefusal.of(code) { name -> headers.toMap()[name] }

        assertEquals(SubscriptionRefusal.DEVICE_LIMIT, refusal(200, "x-hwid-max-devices-reached" to "true"))
        assertEquals(SubscriptionRefusal.DEVICE_LIMIT, refusal(404, "x-hwid-limit" to "true"))
        assertEquals(SubscriptionRefusal.HWID_REQUIRED, refusal(404, "x-hwid-not-supported" to "true"))
        assertNull(refusal(200, "x-hwid-active" to "true"))
        assertNull(refusal(200, "x-hwid-not-supported" to "true"))
    }
}
