package io.github.yulbax.erkin.desktop

import org.koin.dsl.module

val desktopSystem: DesktopSystem = LinuxSystem

val fileShell: FileShell = LinuxFileShell

val platformModule = module {
    single { desktopSystem }
    single { fileShell }
    single<ServiceHost> { LinuxServiceHost }
    single<DesktopPrograms> { LinuxPrograms }
    single<TrayHost> { LinuxTrayHost }
    single<WindowChrome> { LinuxWindowChrome }
    single<SystemDnsSource> { ResolvConfDns }
}
