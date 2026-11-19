package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.ProtocolHandler
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject

class CoreOnlyHandler(override val coreType: String) : ProtocolHandler {
    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}
