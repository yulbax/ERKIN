package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.util.AppLog
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreLogTest {
    private val written = mutableListOf<String>()
    private val coreLog = CoreLog(object : AppLog {
        override fun i(tag: String, message: String) { written += message }
        override fun w(tag: String, message: String, t: Throwable?) { written += message }
        override fun e(tag: String, message: String, t: Throwable?) { written += message }
    })

    @Test
    fun everyLineIsWrittenAndARejectedLoginNamesItsServer() = runBlocking {
        val next = async { withTimeout(2_000) { coreLog.all.first() } }
        yield()

        coreLog.line("warning", "dial p9 (match IPCIDR/10.0.0.0/8) --> 10.0.0.1:22 error: make OpenVPN handshake: context deadline exceeded")
        coreLog.line("warning", "dial p3h1 (match Match/) --> a:443 error: openvpn authentication failed: AUTH_FAILED")
        coreLog.line("warning", "dial p9 (match ProcessName/ssh) --> 10.0.0.1:22 error: openvpn authentication failed: AUTH_FAILED")

        assertEquals(CoreEvent.LoginRejected(9), next.await())
        assertEquals(3, written.size)
    }
}
