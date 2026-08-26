package com.kiowx.deepcleaner.core

import android.content.Context
import android.content.Intent
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import java.io.File

class AppRepository(private val context: Context) {
    @Suppress("DEPRECATION")
    fun installedApps(): List<AppEntry> {
        val pm = context.packageManager
        val usageByPackage = if (hasUsageAccess()) {
            val now = System.currentTimeMillis()
            context.getSystemService(UsageStatsManager::class.java)
                .queryAndAggregateUsageStats(now - 365L * 86_400_000L, now)
        } else emptyMap()
        val storageStats = context.getSystemService(StorageStatsManager::class.java)
        val apps = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }
        return apps.asSequence()
            .filter { it.packageName != context.packageName }
            .mapNotNull { info -> runCatching { info.toEntry(pm, storageStats, usageByPackage[info.packageName]?.lastTimeUsed ?: 0) }.getOrNull() }
            .sortedWith(compareBy<AppEntry> { it.isSystem }.thenBy { it.name.lowercase() })
            .toList()
    }

    @Suppress("DEPRECATION")
    private fun ApplicationInfo.toEntry(pm: PackageManager, storageManager: StorageStatsManager, lastUsedAt: Long): AppEntry {
        val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getPackageInfo(packageName, 0)
        }
        val apkSize = listOfNotNull(sourceDir, *splitSourceDirs.orEmpty())
            .distinct()
            .sumOf { File(it).length().coerceAtLeast(0) }
        val stats = if (hasUsageAccess()) runCatching {
            storageManager.queryStatsForPackage(storageUuid ?: StorageManager.UUID_DEFAULT, packageName, Process.myUserHandle())
        }.getOrNull() else null
        return AppEntry(
            packageName = packageName,
            name = pm.getApplicationLabel(this).toString().ifBlank { packageName },
            version = packageInfo.versionName.orEmpty(),
            apkSize = apkSize,
            isSystem = flags and ApplicationInfo.FLAG_SYSTEM != 0,
            enabled = enabled,
            appBytes = stats?.appBytes ?: apkSize,
            dataBytes = stats?.dataBytes ?: -1,
            cacheBytes = stats?.cacheBytes ?: -1,
            lastUsedAt = lastUsedAt,
        )
    }

    fun launchIntent(packageName: String): Intent? = context.packageManager.getLaunchIntentForPackage(packageName)

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
