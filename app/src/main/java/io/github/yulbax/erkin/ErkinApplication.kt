package io.github.yulbax.erkin

import android.app.Application
import io.github.yulbax.erkin.di.AppModule
import io.github.yulbax.erkin.ui.di.uiModule
import io.github.yulbax.erkin.util.Telemetry
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.KoinApplication
import org.koin.plugin.module.dsl.startKoin

@KoinApplication(modules = [AppModule::class])
class ErkinApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Telemetry.install()
        startKoin<ErkinApplication> {
            androidContext(this@ErkinApplication)
            modules(uiModule)
        }
    }
}