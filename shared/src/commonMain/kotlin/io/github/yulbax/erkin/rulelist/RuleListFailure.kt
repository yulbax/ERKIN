package io.github.yulbax.erkin.rulelist

import java.io.IOException

enum class RuleListFailure(val wire: String) {
    DOWNLOAD("download"),
    NOT_A_LIST("not-a-list"),
    EMPTY("empty"),
    CORRUPT("corrupt"),
    WRONG_KIND("wrong-kind"),
    TOO_LARGE("too-large"),
    SAVE("save");

    companion object {
        fun fromWire(value: String?): RuleListFailure? = entries.firstOrNull { it.wire == value }
    }
}

class RuleListException(val failure: RuleListFailure, val detail: String? = null) : IOException(detail ?: failure.wire)

data class RuleListError(val failure: RuleListFailure, val detail: String?)
