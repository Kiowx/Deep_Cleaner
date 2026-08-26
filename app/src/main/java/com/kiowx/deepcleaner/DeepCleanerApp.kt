package com.kiowx.deepcleaner

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class DeepCleanerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CLEAN_CHANNEL_ID,
                "自动清理",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Deep Cleaner 定时清理结果" },
        )
    }

    companion object { const val CLEAN_CHANNEL_ID = "deep_cleaner_auto_clean" }
}
