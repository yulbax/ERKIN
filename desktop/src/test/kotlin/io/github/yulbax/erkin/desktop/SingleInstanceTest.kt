package io.github.yulbax.erkin.desktop

import java.io.File
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
    private val socket = File(dir, "instance.sock")

    @After
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun aSecondLaunchWakesTheRunningInstanceAndBacksOff() {
        val activated = CountDownLatch(1)
        val running = SingleInstance(dir, socket)
        assertTrue(running.claim { activated.countDown() })

        assertFalse(SingleInstance(dir, socket).claim { fail("the second launch must not become the instance") })
        assertTrue(activated.await(2, TimeUnit.SECONDS))

        running.release()
        val next = SingleInstance(dir, socket)
        assertTrue(next.claim {})
        next.release()
    }
}
