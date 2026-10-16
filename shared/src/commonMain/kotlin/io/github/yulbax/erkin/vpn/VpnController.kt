package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.util.Telemetry
import io.github.yulbax.erkin.vpn.core.EngineListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface VpnHost {
    fun onSessionStarting()

    fun onSessionStarted()

    fun allowsUserStop(): Boolean

    fun onSessionStopped(stopToken: Int?, stopHost: Boolean)
}

private sealed interface ActorCommand {
    data class Start(val token: Int?, val systemInitiated: Boolean) : ActorCommand
    data class Stop(val token: Int?, val force: Boolean) : ActorCommand
    data class ByeDpiExited(val run: Long, val code: Int) : ActorCommand
    data class Work(val command: Command, val generation: Long) : ActorCommand
}

class VpnController(
    private val scope: CoroutineScope,
    private val commandBus: VpnCommandBus,
    private val stateRepository: VpnStateRepository,
    private val host: VpnHost,
    private val log: AppLog,
    sessionFactory: (VpnController) -> VpnSession
) : EngineListener {

    private val controlCommands = Channel<ActorCommand>(Channel.BUFFERED)
    private val workCommands = Channel<ActorCommand.Work>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val session: VpnSession by lazy { sessionFactory(this) }
    private var actorJob: Job? = null
    private var latestToken: Int? = null

    fun launch() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            commandBus.commands.collect { command ->
                when (command) {
                    Command.Start -> start(token = null, systemInitiated = false)
                    Command.Stop -> stop(token = null, force = false)
                    else -> requestWork(command)
                }
            }
        }
        actorJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { actorLoop() }
    }

    fun start(token: Int?, systemInitiated: Boolean) = enqueueControl(ActorCommand.Start(token, systemInitiated))

    fun stop(token: Int?, force: Boolean) = enqueueControl(ActorCommand.Stop(token, force))

    fun requestWork(command: Command) {
        val work = ActorCommand.Work(command, session.generation)
        if (workCommands.trySend(work).isFailure) {
            log.w(TAG, "work command queue is unavailable: $command")
        }
    }

    fun onByeDpiExit(run: Long, code: Int) = enqueueControl(ActorCommand.ByeDpiExited(run, code))

    override fun onThroughput(uplinkBytesPerSec: Long, downlinkBytesPerSec: Long) =
        session.onThroughput(uplinkBytesPerSec, downlinkBytesPerSec)

    override fun onStopRequested() = stop(token = null, force = true)

    fun shutdown() {
        val finalState = prepareShutdown()
        runBlocking { teardown(finalState) }
    }

    fun shutdownInBackground() {
        val finalState = prepareShutdown()
        teardownScope.launch {
            try {
                teardown(finalState)
            } catch (t: Throwable) {
                log.e(TAG, "shutdown cleanup failed", t)
            }
        }
    }

    private fun prepareShutdown(): VpnState? {
        val needsCleanup = !session.isIdle
        val finalState = stateRepository.state.value.takeIf { it is VpnState.Error } ?: VpnState.Disconnected
        session.markStopping()
        controlCommands.close()
        workCommands.close()
        scope.cancel()
        return finalState.takeIf { needsCleanup }
    }

    private suspend fun teardown(finalState: VpnState?) {
        lifecycleLock.withLock {
            actorJob?.join()
            if (finalState != null) stopSession(finalState, stopToken = null, stopHost = false)
        }
    }

    private fun enqueueControl(command: ActorCommand) {
        if (controlCommands.trySend(command).isFailure) {
            log.e(TAG, "control command queue is unavailable: $command")
        }
    }

    private suspend fun actorLoop() {
        while (true) {
            val command = controlCommands.tryReceive().getOrNull() ?: select<ActorCommand?> {
                controlCommands.onReceiveCatching { it.getOrNull() }
                workCommands.onReceiveCatching { it.getOrNull() }
            } ?: return
            try {
                dispatch(command)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                handleFailure(command, t)
            }
        }
    }

    private suspend fun dispatch(command: ActorCommand) {
        when (command) {
            is ActorCommand.Start -> {
                command.token?.let { latestToken = it }
                startSession(command.systemInitiated)
            }
            is ActorCommand.Stop -> {
                command.token?.let { latestToken = it }
                requestStop(command)
            }
            is ActorCommand.ByeDpiExited -> session.onByeDpiExited(command.run, command.code)
            is ActorCommand.Work -> if (command.generation == session.generation) work(command.command)
        }
    }

    private suspend fun startSession(systemInitiated: Boolean) {
        if (!session.isIdle) return
        lifecycleLock.withLock {
            host.onSessionStarting()
            session.start(systemInitiated)
        }
        host.onSessionStarted()
    }

    private suspend fun work(command: Command) {
        when (command) {
            Command.Reload -> if (session.reload() == ReloadOutcome.SelectionRemoved) {
                stopSession(VpnState.Disconnected, latestToken, stopHost = true)
            }
            Command.Recover -> session.recover()
            Command.CheckByeDpi -> session.checkByeDpi()
            Command.Start, Command.Stop -> Unit
        }
    }

    private fun requestStop(command: ActorCommand.Stop) {
        if (!command.force && !session.isIdle && !host.allowsUserStop()) {
            log.i(TAG, "ignoring app stop while the platform keeps the VPN on")
            return
        }
        stopSession(VpnState.Disconnected, command.token ?: latestToken, stopHost = true)
    }

    private fun handleFailure(command: ActorCommand, error: Throwable) {
        val operation = when (command) {
            is ActorCommand.Start -> "start"
            is ActorCommand.Stop -> "stop"
            is ActorCommand.ByeDpiExited -> "ByeDPI restart"
            is ActorCommand.Work -> command.command.toString()
        }
        log.e(TAG, "$operation failed", error)
        runCatching {
            Telemetry.recordNonFatal(error, "VPN $operation failed: server=${session.configName}")
        }
        val stopsSession = command !is ActorCommand.Work || command.command.stopsSessionOnFailure
        if (!stopsSession) return
        runCatching {
            stopSession(
                finalState = VpnState.Error(error.message ?: "VPN $operation failed"),
                stopToken = latestToken,
                stopHost = true
            )
        }.onFailure { log.e(TAG, "cleanup after $operation failed", it) }
    }

    private fun stopSession(finalState: VpnState, stopToken: Int?, stopHost: Boolean) {
        while (workCommands.tryReceive().isSuccess) continue
        session.stop(finalState)
        host.onSessionStopped(stopToken, stopHost)
    }

    private companion object {
        const val TAG = "VpnController"

        val lifecycleLock = Mutex()
        val teardownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
