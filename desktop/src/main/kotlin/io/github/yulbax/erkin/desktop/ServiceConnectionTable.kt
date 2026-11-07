package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.vpn.connections.ConnectionTableSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

class ServiceConnectionTable(private val service: DesktopService) : ConnectionTableSource {
    override suspend fun read(): String? = runInterruptible(Dispatchers.IO) { service.running()?.connections() }
}
