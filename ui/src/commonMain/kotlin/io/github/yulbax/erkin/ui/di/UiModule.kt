package io.github.yulbax.erkin.ui.di

import io.github.yulbax.erkin.ui.viewmodel.AppsViewModel
import io.github.yulbax.erkin.ui.viewmodel.ByeDpiViewModel
import io.github.yulbax.erkin.ui.viewmodel.ChainsViewModel
import io.github.yulbax.erkin.ui.viewmodel.ConnectionViewModel
import io.github.yulbax.erkin.ui.viewmodel.ConnectionsViewModel
import io.github.yulbax.erkin.ui.viewmodel.ProfileViewModel
import io.github.yulbax.erkin.ui.viewmodel.RuleListRefreshViewModel
import io.github.yulbax.erkin.ui.viewmodel.RuleSetsViewModel
import io.github.yulbax.erkin.ui.viewmodel.RulesViewModel
import io.github.yulbax.erkin.ui.viewmodel.ServerDelays
import io.github.yulbax.erkin.ui.viewmodel.SettingsViewModel
import io.github.yulbax.erkin.ui.viewmodel.SiteListsViewModel
import io.github.yulbax.erkin.ui.viewmodel.UpdateViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val uiModule = module {
    singleOf(::ServerDelays)
    viewModelOf(::AppsViewModel)
    viewModelOf(::ByeDpiViewModel)
    viewModelOf(::ChainsViewModel)
    viewModelOf(::ConnectionViewModel)
    viewModelOf(::ConnectionsViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::RuleListRefreshViewModel)
    viewModelOf(::RuleSetsViewModel)
    viewModelOf(::RulesViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::SiteListsViewModel)
    viewModelOf(::UpdateViewModel)
}
