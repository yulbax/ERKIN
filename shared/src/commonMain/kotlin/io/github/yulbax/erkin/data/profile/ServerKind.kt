package io.github.yulbax.erkin.data.profile

import io.github.yulbax.erkin.proxy.ProxyProtocol

sealed interface ServerKind {
    data class Link(val protocol: ProxyProtocol?) : ServerKind
    data class Chain(val chain: ProxyChain?) : ServerKind
}

val ProfileEntity.kind: ServerKind
    get() = when (type) {
        ProxyChain.TYPE -> ServerKind.Chain(ProxyChain.decode(outboundJson))
        else -> ServerKind.Link(protocol)
    }

val ProfileEntity.isLink: Boolean get() = kind is ServerKind.Link
