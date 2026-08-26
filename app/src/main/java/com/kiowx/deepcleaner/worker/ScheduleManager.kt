package com.kiowx.deepcleaner.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kiowx.deepcleaner.core.ScheduleFrequency
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ScheduleManager {
    private const val UNIQUE_WORK = "deep_cleaner_periodic_cleanup"

    fun configure(
        context: Context,
        enabled: Boolean,
        frequency: ScheduleFrequency,
        requireCharging: Boolean = true,
        requireIdle: Boolean = true,
    ) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val repeatDays = if (frequency == ScheduleFrequency.DAILY) 1L else 7L
        val now = ZonedDateTime.now()
        var next = now.withHour(3).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<AutoCleanWorker>(repeatDays, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next))
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(false)
                    .setRequiresCharging(requireCharging)
                    .setRequiresDeviceIdle(requireIdle)
                    .build(),
            )
            .build()
        manager.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
