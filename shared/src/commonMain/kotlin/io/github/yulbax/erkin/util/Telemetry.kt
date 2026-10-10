package io.github.yulbax.erkin.util


object Telemetry {

    fun install() = Unit

    fun logVpnToggle(connect: Boolean) = Unit

    fun logServerSelected(protocol: String) = Unit

    fun recordNonFatal(throwable: Throwable, context: String? = null) = Unit

    fun breadcrumb(message: String) = Unit
}
