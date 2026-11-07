package io.github.yulbax.erkin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.yulbax.erkin.ui.res.Res
import io.github.yulbax.erkin.ui.res.unnamed_profile
import org.jetbrains.compose.resources.stringResource

class ServerNames(private val unnamed: String) {
    fun of(name: String): String = name.ifEmpty { unnamed }
}

@Composable
fun serverNames(): ServerNames {
    val unnamed = stringResource(Res.string.unnamed_profile)
    return remember(unnamed) { ServerNames(unnamed) }
}

@Composable
fun serverName(name: String): String = serverNames().of(name)
