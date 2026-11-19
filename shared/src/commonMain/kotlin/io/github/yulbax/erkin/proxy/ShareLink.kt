package io.github.yulbax.erkin.proxy

data class ShareLink(
    val scheme: String,
    val body: String,
    val user: String?,
    val password: String?,
    val host: String,
    val portSpec: String?,
    val path: String,
    val query: Map<String, String>,
    val name: String?
) {
    val port: Int? get() = portSpec?.let { INTEGER.find(it) }?.value?.toIntOrNull()

    val userInfo: String? get() = user?.let { name -> password?.let { "$name:$it" } ?: name }

    fun format(): String {
        val label = name?.let { "#${encode(it)}" }.orEmpty()
        if (host.isEmpty() && body.isNotEmpty()) return "$scheme://$body$label"
        val credentials = user?.let { "${encode(it)}${password?.let { secret -> ":${encode(secret)}" }.orEmpty()}@" }.orEmpty()
        val address = if (':' in host) "[$host]" else host
        val port = portSpec?.let { ":$it" }.orEmpty()
        val parameters = query.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        val queryText = if (query.isEmpty()) "" else "?$parameters"
        return "$scheme://$credentials$address$port$path$queryText$label"
    }

    companion object {
        private val INTEGER = Regex("\\d+")
        private const val SCHEME_SEPARATOR = "://"
        private const val UNRESERVED = "-._~"
        private const val HEX = "0123456789ABCDEF"

        fun parse(input: String): ShareLink? {
            val schemeEnd = input.indexOf(SCHEME_SEPARATOR)
            if (schemeEnd <= 0) return null
            val scheme = input.substring(0, schemeEnd).lowercase()
            if (!scheme.all { it.isAsciiAlphanumeric() || it in "+-." }) return null

            val rest = input.substring(schemeEnd + SCHEME_SEPARATOR.length)
            val hash = rest.indexOf('#')
            val rawBody = if (hash < 0) rest else rest.substring(0, hash)
            val name = if (hash < 0) null else rest.substring(hash + 1).substringBefore('\r').substringBefore('\n')
                .trim().takeIf { it.isNotEmpty() }?.let(::decode)

            val body = rawBody.replace("&amp;", "&")
            val authorityEnd = body.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) body.length else it }
            val authority = body.substring(0, authorityEnd)
            val tail = body.substring(authorityEnd)
            val queryStart = tail.indexOf('?')
            val path = if (queryStart < 0) tail else tail.substring(0, queryStart)
            val queryText = if (queryStart < 0) "" else tail.substring(queryStart + 1)

            val at = authority.lastIndexOf('@')
            val userInfo = if (at < 0) null else authority.substring(0, at)
            val hostPort = authority.substring(at + 1)
            val colon = userInfo?.indexOf(':') ?: -1
            val user = userInfo?.let { decode(if (colon < 0) it else it.substring(0, colon)) }
            val password = if (colon < 0) null else decode(userInfo!!.substring(colon + 1))
            val (host, portSpec) = splitHostPort(hostPort)

            return ShareLink(scheme, rawBody, user, password, host, portSpec, path, parseQuery(queryText), name)
        }

        private fun splitHostPort(hostPort: String): Pair<String, String?> {
            if (hostPort.startsWith("[")) {
                val close = hostPort.indexOf(']')
                if (close < 0) return hostPort.substring(1) to null
                val port = hostPort.substring(close + 1).removePrefix(":").takeIf { it.isNotEmpty() }
                return hostPort.substring(1, close) to port
            }
            val colon = hostPort.lastIndexOf(':')
            if (colon < 0) return hostPort to null
            val spec = hostPort.substring(colon + 1)
            return if (spec.isNotEmpty() && spec.all { it.isDigit() || it == ',' || it == '-' }) {
                hostPort.substring(0, colon) to spec
            } else {
                hostPort to null
            }
        }

        private fun parseQuery(text: String): Map<String, String> {
            val result = LinkedHashMap<String, String>()
            text.split('&').forEach { pair ->
                val key = decode(pair.substringBefore('=').replace('+', ' '))
                if (key.isNotEmpty() && key !in result) {
                    result[key] = decode(pair.substringAfter('=', "").replace('+', ' '))
                }
            }
            return result
        }

        private fun decode(text: String): String {
            val bytes = ArrayList<Byte>()
            val literal = StringBuilder()
            fun flush() {
                literal.toString().encodeToByteArray().forEach { bytes.add(it) }
                literal.clear()
            }
            var index = 0
            while (index < text.length) {
                val hex = if (text[index] == '%') hexByte(text, index + 1) else null
                if (hex != null) {
                    flush()
                    bytes.add(hex.toByte())
                    index += 3
                } else {
                    literal.append(text[index])
                    index++
                }
            }
            flush()
            return bytes.toByteArray().decodeToString()
        }

        private fun hexByte(text: String, start: Int): Int? {
            val high = text.getOrNull(start)?.digitToIntOrNull(16) ?: return null
            val low = text.getOrNull(start + 1)?.digitToIntOrNull(16) ?: return null
            return high * 16 + low
        }

        private fun encode(text: String): String = buildString {
            text.encodeToByteArray().forEach { byte ->
                val code = byte.toInt() and 0xFF
                val char = code.toChar()
                if (code < 0x80 && (char.isAsciiAlphanumeric() || char in UNRESERVED)) {
                    append(char)
                } else {
                    append('%').append(HEX[code shr 4]).append(HEX[code and 0xF])
                }
            }
        }

        private fun Char.isAsciiAlphanumeric(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
    }
}
