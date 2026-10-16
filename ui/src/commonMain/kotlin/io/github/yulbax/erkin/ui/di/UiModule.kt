package io.github.yulbax.erkin.ui.di

import io.github.yulbax.erkin.ui.viewmodel.AppsViewModel
import io.github.yulbax.erkin.ui.viewmodel.ConnectionViewModel
import io.github.yulbax.erkin.ui.viewmodel.ProfileViewModel
import io.github.yulbax.erkin.ui.viewmodel.RulesViewModel
import io.github.yulbax.erkin.ui.viewmodel.SettingsViewModel
import io.github.yulbax.erkin.ui.viewmodel.UpdateViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val uiModule = module {
    viewModelOf(::AppsViewModel)
    viewModelOf(::ConnectionViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::RulesViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::UpdateViewModel)
}
