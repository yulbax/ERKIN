package io.github.yulbax.erkin.vpn

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface Command {
    val stopsSessionOnFailure: Boolean get() = true

    data object Start : Command
    data object Reload : Command
    data object Recover : Command
    data object CheckByeDpi : Command {
        override val stopsSessionOnFailure: Boolean get() = false
    }
    data object Stop : Command
}

class VpnCommandBus {
    private val mutableCommands = MutableSharedFlow<Command>(
        replay = 0,
        extraBufferCapacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val commands: Flow<Command> = mutableCommands.asSharedFlow()

    fun start() { mutableCommands.tryEmit(Command.Start) }
    fun reload() { mutableCommands.tryEmit(Command.Reload) }
    fun recover() { mutableCommands.tryEmit(Command.Recover) }
    fun checkByeDpi() { mutableCommands.tryEmit(Command.CheckByeDpi) }
    fun stop() { mutableCommands.tryEmit(Command.Stop) }

    private companion object {
        const val BUFFER_CAPACITY = 16
    }
}
