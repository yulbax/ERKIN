package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.util.core
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

sealed interface CoreEvent {
    data class LoginRejected(val serverId: Long) : CoreEvent
}

class CoreLog(private val log: AppLog) {
    private val events = MutableSharedFlow<CoreEvent>(extraBufferCapacity = 16)

    val all: SharedFlow<CoreEvent> = events

    fun line(level: String, message: String) {
        log.core(level, message)
        recognize(message)?.let(events::tryEmit)
    }

    private fun recognize(message: String): CoreEvent? =
        LOGIN_REJECTED.find(message)?.groupValues?.get(1)
            ?.let { (ProxyTag.parse(it) as? ProxyRef.Profile)?.id }
            ?.let(CoreEvent::LoginRejected)

    private companion object {
        val LOGIN_REJECTED = Regex("dial (p\\d+) .*AUTH_FAILED")
    }
}
