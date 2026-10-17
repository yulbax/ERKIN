package io.github.yulbax.erkin.vpn.core

import java.security.MessageDigest

enum class RuleSetKind(val behavior: String) {
    DOMAIN("domain"),
    IPCIDR("ipcidr")
}

object RuleLists {
    const val DIRECTORY = "lists"
    private const val KEY_BYTES = 8

    fun key(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(KEY_BYTES).joinToString("") { "%02x".format(it) }

    fun fileName(url: String, kind: RuleSetKind): String = "${key(url)}.${kind.behavior}.txt"

    fun path(url: String, kind: RuleSetKind): String = "$DIRECTORY/${fileName(url, kind)}"

    fun providerName(url: String, kind: RuleSetKind): String = "list-${key(url)}-${kind.behavior}"
}
