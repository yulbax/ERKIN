package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.profile.DueSubscriptionRefresher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

class SubscriptionRefreshViewModel(refresher: DueSubscriptionRefresher) : ViewModel() {
    init {
        viewModelScope.launch {
            while (isActive) {
                refresher.refreshDue()
                delay(RECHECK)
            }
        }
    }

    private companion object {
        val RECHECK = 30.minutes
    }
}
