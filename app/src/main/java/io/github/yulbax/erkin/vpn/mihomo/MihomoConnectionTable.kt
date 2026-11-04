package io.github.yulbax.erkin.vpn.mihomo

import io.github.yulbax.erkin.vpn.connections.ConnectionTableSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import mihomocore.Mihomocore

class MihomoConnectionTable : ConnectionTableSource {
    override suspend fun read(): String = runInterruptible(Dispatchers.IO) { Mihomocore.connections() }
}
