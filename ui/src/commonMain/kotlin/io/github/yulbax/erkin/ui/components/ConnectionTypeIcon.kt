package io.github.yulbax.erkin.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.data.ConnectionType

@Composable
fun ConnectionType.label(): String = stringResource(
    when (this) {
        ConnectionType.DIRECT -> Res.string.connection_type_direct
        ConnectionType.BYEDPI -> Res.string.connection_type_byedpi
        ConnectionType.VPN -> Res.string.connection_type_vpn
    }
)

@Composable
fun ConnectionTypeIcon(type: ConnectionType, modifier: Modifier = Modifier) {
    val label = type.label()
    when (type) {
        ConnectionType.DIRECT ->
            Icon(Icons.Filled.ArrowUpward, contentDescription = label, modifier = modifier)
        ConnectionType.VPN ->
            Icon(Icons.Filled.VpnKey, contentDescription = label, modifier = modifier)
        ConnectionType.BYEDPI ->
            Icon(painterResource(Res.drawable.ic_byedpi), contentDescription = label, modifier = modifier)
    }
}
