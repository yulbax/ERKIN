package io.github.yulbax.erkin.ui.components

import androidx.compose.runtime.Composable
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.vpn.VpnFailure
import org.jetbrains.compose.resources.stringResource

@Composable
fun VpnFailure.text(): String = when (this) {
    VpnFailure.NoServerSelected -> stringResource(Res.string.error_no_server_selected)
    VpnFailure.NoServers -> stringResource(Res.string.error_no_servers)
    VpnFailure.NoRoutedApps -> stringResource(Res.string.error_no_routed_apps)
    is VpnFailure.BrokenChain -> stringResource(Res.string.error_broken_chain, chain)
    is VpnFailure.ChainServerRemoved -> stringResource(Res.string.error_chain_server_removed, chain)
    is VpnFailure.ByeDpiCrashed -> stringResource(Res.string.error_byedpi_crashed, code)
    VpnFailure.MissingVpnPermission -> stringResource(Res.string.error_vpn_permission)
    VpnFailure.TunUnavailable -> stringResource(Res.string.error_tun_unavailable)
    VpnFailure.ProcessLookupUnavailable -> stringResource(Res.string.error_process_lookup)
    VpnFailure.LoginRejected -> stringResource(Res.string.login_wrong)
    is VpnFailure.Other -> message
}
