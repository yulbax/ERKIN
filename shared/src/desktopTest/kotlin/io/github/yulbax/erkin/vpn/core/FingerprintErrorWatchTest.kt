package io.github.yulbax.erkin.vpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FingerprintErrorWatchTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun anErrorFromBeforeTheCoreRestartNoLongerCounts() {
        val log = folder.newFile("box.log")
        val watch = FingerprintErrorWatch { log }
        watch.markCoreStart()
        log.appendText("ERROR tls: unsupported curve\n")
        assertTrue(watch.hasError())

        watch.markCoreStart()

        assertFalse(watch.hasError())
        log.appendText("ERROR tls: unsupported curve\n")
        assertTrue(watch.hasError())
    }

    @Test
    fun aTruncatedLogIsScannedFromTheStart() {
        val log = folder.newFile("box.log")
        val watch = FingerprintErrorWatch { log }
        log.writeText("INFO started\n".repeat(20))
        watch.markCoreStart()

        log.writeText("ERROR unsupported curve\n")

        assertTrue(watch.hasError())
    }
}
