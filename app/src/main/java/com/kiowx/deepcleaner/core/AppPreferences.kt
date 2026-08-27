package com.kiowx.deepcleaner.core

import android.content.Context
import androidx.core.content.edit

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("deep_cleaner_settings", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = enumValueOrDefault(prefs.getString("theme", null), ThemeMode.SYSTEM)
        set(value) { prefs.edit { putString("theme", value.name) } }

    var deleteMode: DeleteMode
        get() = enumValueOrDefault(prefs.getString("delete_mode", null), DeleteMode.PERMANENT)
        set(value) { prefs.edit { putString("delete_mode", value.name) } }

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) { prefs.edit { putBoolean("haptics", value) } }

    var largeFileMb: Int
        get() = prefs.getInt("large_file_mb", 256).coerceIn(10, 8192)
        set(value) { prefs.edit { putInt("large_file_mb", value.coerceIn(10, 8192)) } }

    var cleanProfile: CleanProfile
        get() = enumValueOrDefault(prefs.getString("clean_profile", null), CleanProfile.SAFE)
        set(value) { prefs.edit { putString("clean_profile", value.name) } }

    var rootModeEnabled: Boolean
        get() = prefs.getBoolean("root_mode_enabled", false)
        set(value) { prefs.edit { putBoolean("root_mode_enabled", value) } }

    var scheduleEnabled: Boolean
        get() = prefs.getBoolean("schedule_enabled", false)
        set(value) { prefs.edit { putBoolean("schedule_enabled", value) } }

    var scheduleFrequency: ScheduleFrequency
        get() = enumValueOrDefault(prefs.getString("schedule_frequency", null), ScheduleFrequency.WEEKLY)
        set(value) { prefs.edit { putString("schedule_frequency", value.name) } }

    var scheduleRequireCharging: Boolean
        get() = prefs.getBoolean("schedule_charging", true)
        set(value) { prefs.edit { putBoolean("schedule_charging", value) } }

    var scheduleRequireIdle: Boolean
        get() = prefs.getBoolean("schedule_idle", true)
        set(value) { prefs.edit { putBoolean("schedule_idle", value) } }

    var scheduleScanOnly: Boolean
        get() = prefs.getBoolean("schedule_scan_only", true)
        set(value) { prefs.edit { putBoolean("schedule_scan_only", value) } }

    var scheduleStorageThreshold: Int
        get() = prefs.getInt("schedule_storage_threshold", 85).coerceIn(50, 98)
        set(value) { prefs.edit { putInt("schedule_storage_threshold", value.coerceIn(50, 98)) } }

    var trashRetentionDays: Int
        get() = prefs.getInt("trash_retention_days", 30).coerceIn(1, 90)
        set(value) { prefs.edit { putInt("trash_retention_days", value.coerceIn(1, 90)) } }

    var trashMaxMb: Int
        get() = prefs.getInt("trash_max_mb", 2048).coerceIn(128, 16_384)
        set(value) { prefs.edit { putInt("trash_max_mb", value.coerceIn(128, 16_384)) } }

    var lastCleanedBytes: Long
        get() = prefs.getLong("last_cleaned_bytes", 0)
        set(value) { prefs.edit { putLong("last_cleaned_bytes", value.coerceAtLeast(0)) } }

    var lastCleanedAt: Long
        get() = prefs.getLong("last_cleaned_at", 0)
        set(value) { prefs.edit { putLong("last_cleaned_at", value) } }

    var lastScanBytes: Long
        get() = prefs.getLong("last_scan_bytes", 0).coerceAtLeast(0)
        set(value) { prefs.edit { putLong("last_scan_bytes", value.coerceAtLeast(0)) } }

    var lastScanAt: Long
        get() = prefs.getLong("last_scan_at", 0)
        set(value) { prefs.edit { putLong("last_scan_at", value) } }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, fallback: T): T =
        runCatching { enumValueOf<T>(raw.orEmpty()) }.getOrDefault(fallback)
}
