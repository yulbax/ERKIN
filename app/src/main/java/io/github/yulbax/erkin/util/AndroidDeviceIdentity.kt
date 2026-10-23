package io.github.yulbax.erkin.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import io.github.yulbax.erkin.data.profile.DeviceIdentity
import java.util.UUID

object AndroidDeviceIdentity {
    private const val PREFS = "device"
    private const val KEY_HWID = "hwid"
    private const val OS = "Android"

    fun of(context: Context): DeviceIdentity = DeviceIdentity(
        hwid = androidId(context) ?: generated(context),
        os = OS,
        osVersion = Build.VERSION.RELEASE.orEmpty(),
        model = Build.MODEL.orEmpty()
    )

    @SuppressLint("HardwareIds")
    private fun androidId(context: Context): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf(DeviceIdentity::isValidHwid)

    private fun generated(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_HWID, null)?.takeIf(DeviceIdentity::isValidHwid)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_HWID, it).apply() }
    }
}
