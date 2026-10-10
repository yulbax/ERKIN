package io.github.yulbax.erkin.util

import io.github.yulbax.erkin.proxy.ProxyProtocol

private val SENSITIVE_URI = Regex(
    "(?i)\\b(https?|${ProxyProtocol.allSchemes.joinToString("|")})://[^\\s)\\]}]+"
)
private val SENSITIVE_JSON_FIELD = Regex(
    """(?i)("(?:password|uuid|token|authorization|private_key)"\s*:\s*")(?:\\.|[^"\\])*(")"""
)
private val AUTHORIZATION_HEADER = Regex("(?im)\\b(authorization:\\s*)[^\\r\\n]+")

fun redactSensitiveData(value: String): String {
    val withoutUris = SENSITIVE_URI.replace(value) { match ->
        "${match.groupValues[1]}://<redacted>"
    }
    val withoutJsonSecrets = SENSITIVE_JSON_FIELD.replace(withoutUris) { match ->
        "${match.groupValues[1]}<redacted>${match.groupValues[2]}"
    }
    return AUTHORIZATION_HEADER.replace(withoutJsonSecrets) { match ->
        "${match.groupValues[1]}<redacted>"
    }
}
