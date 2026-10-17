package io.github.yulbax.erkin.data.rules

import io.github.yulbax.erkin.data.AppDatabase
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.vpn.core.RuleTarget
import kotlinx.coroutines.flow.Flow

class RoutingRuleRepository(database: AppDatabase) {
    private val dao = database.routingRuleDao()

    val rules: Flow<List<RoutingRuleEntity>> = dao.observeAll()

    suspend fun add(match: RuleMatch, raw: String, target: RuleTarget): Boolean {
        val value = match.normalize(raw) ?: return false
        dao.append(RoutingRuleEntity(type = match.wire, value = value, target = target.wire))
        return true
    }

    suspend fun update(rule: RoutingRuleEntity, match: RuleMatch, raw: String, target: RuleTarget): Boolean {
        val value = match.normalize(raw) ?: return false
        dao.update(rule.copy(type = match.wire, value = value, target = target.wire))
        return true
    }

    suspend fun setTarget(rule: RoutingRuleEntity, target: RuleTarget) {
        dao.update(rule.copy(target = target.wire))
    }

    suspend fun delete(rules: List<RoutingRuleEntity>) {
        dao.deleteMany(rules)
    }

    suspend fun deleteUsing(url: String) {
        dao.deleteMatching(RuleMatch.RULE_SET.wire, url)
    }

    suspend fun reorder(ids: List<Long>) {
        dao.reorder(ids)
    }
}
