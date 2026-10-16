package io.github.yulbax.erkin.data.rules

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.yulbax.erkin.vpn.core.RoutingRule
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.vpn.core.RuleTarget
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Entity(tableName = "routing_rules")
data class RoutingRuleEntity(
    @Transient @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String = "",
    val value: String = "",
    val target: String = "",
    @Transient val position: Int = 0
) {
    fun toRule(): RoutingRule? {
        val match = RuleMatch.fromWire(type) ?: return null
        val target = RuleTarget.fromWire(target) ?: return null
        return RoutingRule(match, value, target)
    }
}
