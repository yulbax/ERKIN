package io.github.yulbax.erkin.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.ui.res.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

sealed interface ChannelIcon {
    data class Vector(val image: ImageVector) : ChannelIcon
    data class Drawable(val resource: DrawableResource) : ChannelIcon
}

class ConnectionTypeUi(val label: StringResource, val hint: StringResource, val icon: ChannelIcon)

val ConnectionType.ui: ConnectionTypeUi
    get() = when (this) {
        ConnectionType.DIRECT -> ConnectionTypeUi(Res.string.connection_type_direct, Res.string.apps_hint_direct, ChannelIcon.Vector(Icons.Filled.ArrowUpward))
        ConnectionType.BYEDPI -> ConnectionTypeUi(Res.string.connection_type_byedpi, Res.string.apps_hint_byedpi, ChannelIcon.Drawable(Res.drawable.ic_byedpi))
        ConnectionType.VPN -> ConnectionTypeUi(Res.string.connection_type_vpn, Res.string.apps_hint_vpn, ChannelIcon.Vector(Icons.Filled.VpnKey))
    }

@Composable
fun ConnectionType.label(): String = stringResource(ui.label)

@Composable
fun ConnectionTypeIcon(type: ConnectionType, modifier: Modifier = Modifier) {
    val label = type.label()
    when (val icon = type.ui.icon) {
        is ChannelIcon.Vector -> Icon(icon.image, contentDescription = label, modifier = modifier)
        is ChannelIcon.Drawable -> Icon(painterResource(icon.resource), contentDescription = label, modifier = modifier)
    }
}
