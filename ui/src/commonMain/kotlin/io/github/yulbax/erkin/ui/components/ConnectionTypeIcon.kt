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

sealed interface IconSource {
    data class Vector(val image: ImageVector) : IconSource
    data class Drawable(val resource: DrawableResource) : IconSource
}

class ConnectionTypeUi(val label: StringResource, val hint: StringResource, val icon: IconSource)

val ConnectionType.ui: ConnectionTypeUi
    get() = when (this) {
        ConnectionType.DIRECT -> ConnectionTypeUi(Res.string.connection_type_direct, Res.string.apps_hint_direct, IconSource.Vector(Icons.Filled.ArrowUpward))
        ConnectionType.BYEDPI -> ConnectionTypeUi(Res.string.connection_type_byedpi, Res.string.apps_hint_byedpi, IconSource.Drawable(Res.drawable.ic_byedpi))
        ConnectionType.VPN -> ConnectionTypeUi(Res.string.connection_type_vpn, Res.string.apps_hint_vpn, IconSource.Vector(Icons.Filled.VpnKey))
    }

@Composable
fun ConnectionType.label(): String = stringResource(ui.label)

@Composable
fun ConnectionTypeIcon(type: ConnectionType, modifier: Modifier = Modifier) {
    SourceIcon(type.ui.icon, contentDescription = type.label(), modifier = modifier)
}

@Composable
fun SourceIcon(source: IconSource, contentDescription: String?, modifier: Modifier = Modifier) {
    when (source) {
        is IconSource.Vector -> Icon(source.image, contentDescription = contentDescription, modifier = modifier)
        is IconSource.Drawable -> Icon(painterResource(source.resource), contentDescription = contentDescription, modifier = modifier)
    }
}
