package io.github.yulbax.erkin.data.profile

data class DeviceIdentity(
    val hwid: String,
    val os: String,
    val osVersion: String,
    val model: String
) {
    val headers: Map<String, String>
        get() = mapOf(HWID to hwid, DEVICE_OS to os, OS_VERSION to osVersion, DEVICE_MODEL to model)
            .filterValues { it.isNotBlank() }

    companion object {
        const val HWID = "x-hwid"
        const val DEVICE_OS = "x-device-os"
        const val OS_VERSION = "x-ver-os"
        const val DEVICE_MODEL = "x-device-model"
        private val HWID_FORMAT = Regex("[A-Za-z0-9=-]{10,64}")

        fun isValidHwid(value: String): Boolean = HWID_FORMAT.matches(value)
    }
}

enum class SubscriptionRefusal {
    DEVICE_LIMIT,
    HWID_REQUIRED;

    companion object {
        private const val MAX_DEVICES_REACHED = "x-hwid-max-devices-reached"
        private const val LIMIT = "x-hwid-limit"
        private const val NOT_SUPPORTED = "x-hwid-not-supported"

        fun of(code: Int, header: (String) -> String?): SubscriptionRefusal? {
            fun flag(name: String) = header(name).equals("true", ignoreCase = true)
            return when {
                flag(MAX_DEVICES_REACHED) || flag(LIMIT) -> DEVICE_LIMIT
                code !in 200..399 && flag(NOT_SUPPORTED) -> HWID_REQUIRED
                else -> null
            }
        }
    }
}

class SubscriptionRefusedException(val refusal: SubscriptionRefusal) : Exception("Subscription refused: $refusal")
