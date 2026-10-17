package io.github.yulbax.erkin.data.rules

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rule_lists")
data class RuleListEntity(
    @PrimaryKey val source: String,
    val title: String? = null,
    val entries: Long = 0,
    val failure: String? = null,
    val failureDetail: String? = null
)
