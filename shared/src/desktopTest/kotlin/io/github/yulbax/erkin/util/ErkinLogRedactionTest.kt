package io.github.yulbax.erkin.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErkinLogRedactionTest {
    @Test
    fun redactsHttpUrlIncludingQuerySecrets() {
        val value = redactSensitiveData(
            "request failed for https://example.com/subscription?token=secret"
        )

        assertEquals("request failed for https://<redacted>", value)
        assertFalse(value.contains("secret"))
    }

    @Test
    fun redactsProxyLinksWithoutRemovingStackContext() {
        val value = redactSensitiveData(
            "IllegalStateException: vless://uuid@example.com:443?security=reality\n" +
                "\tat io.github.yulbax.erkin.Parser.parse(Parser.kt:10)"
        )

        assertTrue(value.startsWith("IllegalStateException: vless://<redacted>"))
        assertTrue(value.contains("Parser.kt:10"))
        assertFalse(value.contains("uuid"))
    }

    @Test
    fun redactsStructuredCredentialsAndAuthorizationHeaders() {
        val value = redactSensitiveData(
            "{\"server\":\"vpn.example\",\"uuid\":\"private-id\",\"password\":\"secret\"}\n" +
                "Authorization: Bearer access-token"
        )

        assertTrue(value.contains("\"server\":\"vpn.example\""))
        assertFalse(value.contains("private-id"))
        assertFalse(value.contains("secret"))
        assertFalse(value.contains("access-token"))
    }

    @Test
    fun redactsJsonSecretContainingEscapedQuote() {
        val value = redactSensitiveData(
            """{"password":"a\"SECRET","server":"vpn.example"}"""
        )

        assertFalse(value.contains("SECRET"))
        assertTrue(value.contains("\"password\":\"<redacted>\""))
        assertTrue(value.contains("\"server\":\"vpn.example\""))
    }
}
