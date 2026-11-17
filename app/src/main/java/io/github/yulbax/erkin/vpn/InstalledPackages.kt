package io.github.yulbax.erkin.vpn

import android.content.pm.PackageManager

fun PackageManager.isInstalled(packageName: String): Boolean =
    try {
        getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
