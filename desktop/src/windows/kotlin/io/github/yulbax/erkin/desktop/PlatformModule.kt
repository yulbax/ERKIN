package io.github.yulbax.erkin.desktop

import org.koin.dsl.module

val desktopSystem: DesktopSystem = WindowsSystem

val fileShell: FileShell = WindowsFileShell

val platformModule = module {
    single { desktopSystem }
    single { fileShell }
    single<ServiceHost> { WindowsServiceHost }
    single<DesktopPrograms> { WindowsPrograms }
    single<TrayHost> { WindowsTrayHost }
    single<WindowChrome> { WindowsWindowChrome }
    single<SystemDnsSource> { LocalSystemDns }
}
