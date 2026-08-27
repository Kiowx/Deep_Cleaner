package com.kiowx.deepcleaner.worker

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kiowx.deepcleaner.DeepCleanerApp
import com.kiowx.deepcleaner.R
import com.kiowx.deepcleaner.core.AppPreferences
import com.kiowx.deepcleaner.core.CleanerEngine
import com.kiowx.deepcleaner.core.CleanProfile
import com.kiowx.deepcleaner.core.CleanRisk
import com.kiowx.deepcleaner.core.ExpansionScanner
import com.kiowx.deepcleaner.core.RootAccess
import com.kiowx.deepcleaner.core.StorageAccess
import com.kiowx.deepcleaner.core.TrashManager
import com.kiowx.deepcleaner.core.formatBytes
import com.kiowx.deepcleaner.core.HistoryRepository

class AutoCleanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!StorageAccess.hasAllFilesAccess()) return Result.failure()
        return runCatching {
            val preferences = AppPreferences(applicationContext)
            val storage = StorageAccess.snapshot(applicationContext)
            val usedPercent = (storage.usedFraction * 100).toInt()
            if (usedPercent < preferences.scheduleStorageThreshold) return@runCatching Result.success()
            val engine = CleanerEngine(applicationContext)
            val baseReport = when (preferences.cleanProfile) {
                CleanProfile.DOWNLOADS_ONLY -> engine.scanDownloads()
                else -> engine.scanJunk()
            }
            val expandedReport = if (preferences.rootModeEnabled) {
                runCatching {
                    ExpansionScanner.mergeReports(baseReport, RootAccess.scanCaches(applicationContext))
                }.getOrDefault(baseReport)
            } else baseReport
            val report = if (preferences.cleanProfile == CleanProfile.MAX_SPACE) {
                expandedReport.copy(items = expandedReport.items.map { item -> item.copy(selected = item.risk != CleanRisk.HIGH) })
            } else expandedReport
            val selected = report.items.filter { it.selected }
            if (preferences.scheduleScanOnly) {
                showNotification("自动扫描完成", "发现 ${selected.size} 项，共 ${formatBytes(selected.sumOf { it.size })}")
                return@runCatching Result.success()
            }
            val result = engine.deleteItems(
                selected,
                preferences.deleteMode,
                TrashManager(applicationContext),
            )
            preferences.lastCleanedBytes = result.releasedBytes
            preferences.lastCleanedAt = System.currentTimeMillis()
            HistoryRepository(applicationContext).record("自动清理 · ${preferences.cleanProfile.title}", result, preferences.deleteMode, selected)
            TrashManager(applicationContext).prune(preferences.trashRetentionDays, preferences.trashMaxMb * 1024L * 1024L)
            showNotification("自动清理完成", "已处理 ${result.deleted} 项，释放 ${formatBytes(result.releasedBytes)}")
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private fun showNotification(title: String, text: String) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val notification = NotificationCompat.Builder(applicationContext, DeepCleanerApp.CLEAN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java).notify(1001, notification)
    }
}
