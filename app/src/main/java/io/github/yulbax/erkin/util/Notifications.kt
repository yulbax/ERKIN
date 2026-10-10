package io.github.yulbax.erkin.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

fun Context.createNotificationChannel(id: String, name: String, importance: Int) {
    getSystemService(NotificationManager::class.java)
        .createNotificationChannel(NotificationChannel(id, name, importance))
}
