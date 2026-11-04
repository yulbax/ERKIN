package io.github.yulbax.erkin.vpn

sealed interface VpnFailure {
    data object NoServerSelected : VpnFailure

    data object NoServers : VpnFailure

    data object NoRoutedApps : VpnFailure

    data class BrokenChain(val chain: String) : VpnFailure

    data class ChainServerRemoved(val chain: String) : VpnFailure

    data class ByeDpiCrashed(val code: Int) : VpnFailure

    data object MissingVpnPermission : VpnFailure

    data object TunUnavailable : VpnFailure

    data object ProcessLookupUnavailable : VpnFailure

    data class Other(val message: String) : VpnFailure

    companion object {
        const val PROCESS_LOOKUP_UNAVAILABLE = "process-lookup-unavailable"

        fun of(error: Throwable): VpnFailure {
            (error as? VpnFailureException)?.let { return it.failure }
            val message = error.message.orEmpty()
            return when {
                PROCESS_LOOKUP_UNAVAILABLE in message -> ProcessLookupUnavailable
                else -> Other(message.ifEmpty { error::class.simpleName.orEmpty() })
            }
        }
    }
}

class VpnFailureException(val failure: VpnFailure) : IllegalStateException(failure.toString())

fun fail(failure: VpnFailure): Nothing = throw VpnFailureException(failure)
