package io.github.yulbax.erkin.util

import io.github.yulbax.erkin.data.executableName
import io.github.yulbax.erkin.vpn.core.RuleValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedHelpersTest {

    @Test
    fun addressesAreToldApartFromDomains() {
        assertTrue(RuleValues.isIpAddress("18.191.0.198"))
        assertTrue(RuleValues.isIpAddress("2606:4700::1111"))
        assertFalse(RuleValues.isIpAddress("googlevideo.com"))
        assertFalse(RuleValues.isIpAddress("1.2.3"))
    }

    @Test
    fun anExecutableIsNamedByItsFile() {
        assertEquals("game.exe", executableName("C:\\Games\\game.exe"))
        assertEquals("firefox", executableName("/usr/lib/firefox/firefox"))
        assertEquals("org.telegram", executableName("org.telegram"))
    }

    @Test
    fun speedAndSizeUseTheSameUnits() {
        assertEquals("${formatBytes(1_500_000)}/s", formatRate(1_500_000))
        assertEquals("1.4 MB", formatBytes(1_500_000))
        assertEquals("512 B/s", formatRate(512))
    }
}
