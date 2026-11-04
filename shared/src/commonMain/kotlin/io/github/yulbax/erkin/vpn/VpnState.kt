package io.github.yulbax.erkin.vpn

sealed interface VpnState {
    val isActive: Boolean get() = this is Verifying || this is Connected || this is VpnDown

    val isBusy: Boolean get() = this is Connecting || this is Verifying

    data object Disconnected : VpnState

    data object Connecting : VpnState

    data object Verifying : VpnState

    data class Connected(val latencyMs: Int) : VpnState

    data object VpnDown : VpnState

    data class Error(val failure: VpnFailure) : VpnState

}
