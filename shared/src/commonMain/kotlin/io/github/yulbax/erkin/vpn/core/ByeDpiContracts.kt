package io.github.yulbax.erkin.vpn.core

interface ByeDpiProcess {
    val isRunning: Boolean

    fun start(port: Int, args: List<String>, onUnexpectedExit: (code: Int) -> Unit)

    fun stop()
}

data class ByeDpiReachability(val reachable: Int, val total: Int)

fun interface ByeDpiQualityCheck {
    suspend fun check(socksPort: Int): ByeDpiReachability
}

object ByeDpiArgs {
    val DEFAULT: List<String> = listOf(
        "-d1", "-s1+s", "-s3+s", "-s6+s", "-s9+s", "-s12+s", "-s15+s", "-s20+s", "-s30+s", "-a1"
    )

    fun effective(line: String): String = line.trim().ifBlank { DEFAULT.joinToString(" ") }

    fun parse(line: String): List<String> {
        if (line.isBlank()) return DEFAULT
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var quote = ' '
        for (c in line) {
            when {
                quote != ' ' -> if (c == quote) quote = ' ' else sb.append(c)
                c == '"' || c == '\'' -> quote = c
                c.isWhitespace() -> if (sb.isNotEmpty()) { tokens.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
        }
        if (sb.isNotEmpty()) tokens.add(sb.toString())
        return tokens.ifEmpty { DEFAULT }
    }
}

private const val CELLULAR_PREFIX = "cellular:"

sealed interface TestNetwork {
    val wire: String

    data object Wifi : TestNetwork {
        override val wire: String = "wifi"
    }

    data object Ethernet : TestNetwork {
        override val wire: String = "ethernet"
    }

    data class Cellular(val operator: String) : TestNetwork {
        override val wire: String get() = CELLULAR_PREFIX + operator
    }

    data object Unknown : TestNetwork {
        override val wire: String = ""
    }

    companion object {
        fun fromWire(value: String): TestNetwork = when {
            value == Wifi.wire -> Wifi
            value == Ethernet.wire -> Ethernet
            value.startsWith(CELLULAR_PREFIX) -> Cellular(value.removePrefix(CELLULAR_PREFIX))
            else -> Unknown
        }
    }
}

fun interface TestNetworkSource {
    fun current(): TestNetwork
}
