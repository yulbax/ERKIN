package io.github.yulbax.erkin.desktop

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SingleInstanceTest {
    private val dir = Files.createTempDirectory("erkin-instance").toFile()

    @After
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun aSecondLaunchWakesTheRunningInstanceAndBacksOff() {
        val activated = CountDownLatch(1)
        val running = SingleInstance(dir)
        assertTrue(running.claim { activated.countDown() })

        assertFalse(SingleInstance(dir).claim { fail("the second launch must not become the instance") })
        assertTrue(activated.await(2, TimeUnit.SECONDS))

        running.release()
        val next = SingleInstance(dir)
        assertTrue(next.claim {})
        next.release()
    }
}
