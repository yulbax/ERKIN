package io.github.yulbax.erkin.vpn

enum class RecoveryStep { RECONNECT, SWITCH_SERVER }

class RecoveryLadder(private val reconnectsBeforeSwitch: Int = RECONNECTS_BEFORE_SWITCH) {
    @Volatile private var failedReconnects = 0

    fun next(): RecoveryStep {
        if (failedReconnects < reconnectsBeforeSwitch) {
            failedReconnects++
            return RecoveryStep.RECONNECT
        }
        failedReconnects = 0
        return RecoveryStep.SWITCH_SERVER
    }

    fun reset() {
        failedReconnects = 0
    }

    companion object {
        const val RECONNECTS_BEFORE_SWITCH = 3
    }
}
