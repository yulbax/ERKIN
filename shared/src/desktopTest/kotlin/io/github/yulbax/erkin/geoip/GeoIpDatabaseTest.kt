package io.github.yulbax.erkin.geoip

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoIpDatabaseTest {
    @Test
    fun onlyFilesWithMaxMindMetadataCountAsADatabase() {
        val file = File.createTempFile("geoip", ".metadb")
        try {
            file.writeBytes(ByteArray(4096) + byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte()) + "MaxMind.com".toByteArray() + ByteArray(64))
            assertTrue(GeoIpDatabase.looksLikeMaxMindDb(file))
            file.writeText("<html>rate limited</html>")
            assertFalse(GeoIpDatabase.looksLikeMaxMindDb(file))
        } finally {
            file.delete()
        }
    }
}
