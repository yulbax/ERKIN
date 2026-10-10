package io.github.yulbax.erkin.vpn

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnCommandBusTest {

    @Test
    fun commandEmittedWithoutSubscriberIsNotReplayed() = runBlocking {
        val bus = VpnCommandBus()

        bus.reload()

        assertNull(withTimeoutOrNull(100) { bus.commands.first() })
    }

    @Test
    fun activeSubscriberReceivesCommand() = runBlocking {
        val bus = VpnCommandBus()
        val received = async(start = CoroutineStart.UNDISPATCHED) { bus.commands.first() }

        bus.stop()

        assertEquals(Command.Stop, withTimeout(1_000) { received.await() })
    }

    @Test
    fun burstDropsOldCommandsButKeepsLatestCommand() = runBlocking {
        val bus = VpnCommandBus()
        val releaseCollector = CompletableDeferred<Unit>()
        val observed = mutableListOf<Command>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.commands.collect { command ->
                observed += command
                if (observed.size == 1) releaseCollector.await()
            }
        }

        bus.reload()
        repeat(100) { bus.recover() }
        bus.stop()
        releaseCollector.complete(Unit)

        withTimeout(1_000) {
            while (observed.lastOrNull() != Command.Stop) kotlinx.coroutines.yield()
        }
        collector.cancelAndJoin()

        assertEquals(Command.Stop, observed.last())
        assertTrue("burst buffer must remain bounded", observed.size <= 17)
    }
}
