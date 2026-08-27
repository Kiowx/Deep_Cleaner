package com.kiowx.deepcleaner.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

class CustomRuleRepository(context: Context) {
    private val file = File(context.filesDir, "custom_clean_rules.json")
    private val lock = Any()

    fun list(): List<CustomCleanRule> = synchronized(lock) { read(file) }

    fun add(
        name: String,
        pathContains: String,
        extensions: String,
        minimumMb: Int,
        olderThanDays: Int,
        safeByDefault: Boolean,
    ): CustomCleanRule? = synchronized(lock) {
        val normalizedName = name.trim().take(40).takeIf(String::isNotEmpty) ?: return null
        val parsedExtensions = extensions.split(',', '，', ' ', ';').asSequence()
            .map { it.trim().removePrefix(".").lowercase(Locale.ROOT) }
            .filter { it.matches(Regex("[a-z0-9]{1,12}")) }
            .toSet()
        if (pathContains.isBlank() && parsedExtensions.isEmpty()) return null
        val rule = CustomCleanRule(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            pathContains = pathContains.trim().take(120),
            extensions = parsedExtensions,
            minimumBytes = minimumMb.coerceIn(0, 8192) * 1024L * 1024L,
            olderThanDays = olderThanDays.coerceIn(0, 3650),
            safeByDefault = safeByDefault,
        )
        write(file, (read(file) + rule).takeLast(200))
        rule
    }

    fun setEnabled(id: String, enabled: Boolean) = synchronized(lock) {
        write(file, read(file).map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun remove(id: String) = synchronized(lock) { write(file, read(file).filterNot { it.id == id }) }

    fun replace(rules: List<CustomCleanRule>) = synchronized(lock) {
        write(file, rules.filter { it.name.isNotBlank() }.distinctBy(CustomCleanRule::id).take(200))
    }

    companion object {
        fun read(file: File): List<CustomCleanRule> = runCatching {
            if (!file.isFile) return@runCatching emptyList()
            fromJson(JSONArray(file.readText()))
        }.getOrDefault(emptyList())

        fun fromJson(array: JSONArray): List<CustomCleanRule> = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val extensions = item.optJSONArray("extensions")?.let { values ->
                    buildSet { for (i in 0 until values.length()) values.optString(i).takeIf(String::isNotBlank)?.let(::add) }
                }.orEmpty()
                val name = item.optString("name").trim()
                if (name.isBlank()) continue
                add(
                    CustomCleanRule(
                        id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = name.take(40),
                        pathContains = item.optString("pathContains").take(120),
                        extensions = extensions.map { it.lowercase(Locale.ROOT).removePrefix(".") }.toSet(),
                        minimumBytes = item.optLong("minimumBytes").coerceIn(0, 8L * 1024 * 1024 * 1024),
                        olderThanDays = item.optInt("olderThanDays").coerceIn(0, 3650),
                        enabled = item.optBoolean("enabled", true),
                        safeByDefault = item.optBoolean("safeByDefault", false),
                        source = item.optString("source", "本地"),
                    ),
                )
            }
        }

        fun toJson(rules: List<CustomCleanRule>): JSONArray = JSONArray().apply {
            rules.forEach { rule ->
                put(
                    JSONObject()
                        .put("id", rule.id).put("name", rule.name).put("pathContains", rule.pathContains)
                        .put("extensions", JSONArray(rule.extensions.sorted()))
                        .put("minimumBytes", rule.minimumBytes).put("olderThanDays", rule.olderThanDays)
                        .put("enabled", rule.enabled).put("safeByDefault", rule.safeByDefault).put("source", rule.source),
                )
            }
        }

        private fun write(file: File, rules: List<CustomCleanRule>) = atomicWrite(file, toJson(rules).toString())
    }
}

class StorageTrendRepository(context: Context) {
    private val file = File(context.filesDir, "storage_trends.json")
    private val lock = Any()

    fun list(): List<StorageTrendPoint> = synchronized(lock) { read().sortedBy(StorageTrendPoint::timestamp) }

    fun record(snapshot: StorageSnapshot, now: Long = System.currentTimeMillis()): Unit = synchronized(lock) {
        if (snapshot.total <= 0) return
        val entries = read().toMutableList()
        val previous = entries.maxByOrNull(StorageTrendPoint::timestamp)
        val enoughTime = previous == null || now - previous.timestamp >= 6 * 60 * 60 * 1000L
        val meaningfulChange = previous == null || kotlin.math.abs(snapshot.used - previous.usedBytes) >= 16L * 1024 * 1024
        if (!enoughTime && !meaningfulChange) return
        entries += StorageTrendPoint(now, snapshot.used, snapshot.available)
        write(entries.sortedByDescending(StorageTrendPoint::timestamp).take(90).sortedBy(StorageTrendPoint::timestamp))
    }

    fun clear() = synchronized(lock) { write(emptyList()) }

    private fun read(): List<StorageTrendPoint> = runCatching {
        if (!file.isFile) return@runCatching emptyList()
        val array = JSONArray(file.readText())
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(StorageTrendPoint(item.optLong("timestamp"), item.optLong("usedBytes"), item.optLong("availableBytes")))
            }
        }
    }.getOrDefault(emptyList())

    private fun write(entries: List<StorageTrendPoint>) {
        val array = JSONArray()
        entries.forEach { array.put(JSONObject().put("timestamp", it.timestamp).put("usedBytes", it.usedBytes).put("availableBytes", it.availableBytes)) }
        atomicWrite(file, array.toString())
    }
}

data class ConfigImportResult(val rules: Int, val whitelist: Int)

class ConfigRepository(private val context: Context) {
    private val preferences = AppPreferences(context)
    private val rules = CustomRuleRepository(context)
    private val whitelist = WhitelistRepository(context)

    fun export(): String {
        val settings = JSONObject()
            .put("themeMode", preferences.themeMode.name)
            .put("deleteMode", preferences.deleteMode.name)
            .put("haptics", preferences.haptics)
            .put("largeFileMb", preferences.largeFileMb)
            .put("cleanProfile", preferences.cleanProfile.name)
            .put("scheduleEnabled", preferences.scheduleEnabled)
            .put("scheduleFrequency", preferences.scheduleFrequency.name)
            .put("scheduleRequireCharging", preferences.scheduleRequireCharging)
            .put("scheduleRequireIdle", preferences.scheduleRequireIdle)
            .put("scheduleScanOnly", preferences.scheduleScanOnly)
            .put("scheduleStorageThreshold", preferences.scheduleStorageThreshold)
            .put("trashRetentionDays", preferences.trashRetentionDays)
            .put("trashMaxMb", preferences.trashMaxMb)
        val protected = JSONArray().apply {
            whitelist.list().forEach { entry ->
                put(JSONObject().put("id", entry.id).put("type", entry.type.name).put("value", entry.value).put("createdAt", entry.createdAt))
            }
        }
        return JSONObject()
            .put("format", "deep-cleaner-config")
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("settings", settings)
            .put("rules", CustomRuleRepository.toJson(rules.list()))
            .put("whitelist", protected)
            .toString(2)
    }

    fun import(raw: String): ConfigImportResult {
        require(raw.length <= 1_048_576) { "配置文件过大" }
        val root = JSONObject(raw)
        require(root.optString("format") == "deep-cleaner-config") { "不是 Deep Cleaner 配置文件" }
        val importedRules = CustomRuleRepository.fromJson(root.optJSONArray("rules") ?: JSONArray())
        val importedWhitelist = buildList {
            val array = root.optJSONArray("whitelist") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val type = runCatching { WhitelistType.valueOf(item.optString("type")) }.getOrNull() ?: continue
                val value = item.optString("value").takeIf(String::isNotBlank) ?: continue
                add(WhitelistEntry(item.optString("id").ifBlank { UUID.randomUUID().toString() }, type, value, item.optLong("createdAt")))
            }
        }
        val settings = root.optJSONObject("settings") ?: JSONObject()
        runCatching { preferences.themeMode = ThemeMode.valueOf(settings.optString("themeMode")) }
        runCatching { preferences.deleteMode = DeleteMode.valueOf(settings.optString("deleteMode")) }
        runCatching { preferences.cleanProfile = CleanProfile.valueOf(settings.optString("cleanProfile")) }
        if (settings.has("haptics")) preferences.haptics = settings.optBoolean("haptics", true)
        if (settings.has("largeFileMb")) preferences.largeFileMb = settings.optInt("largeFileMb", 256)
        if (settings.has("scheduleEnabled")) preferences.scheduleEnabled = settings.optBoolean("scheduleEnabled")
        runCatching { preferences.scheduleFrequency = ScheduleFrequency.valueOf(settings.optString("scheduleFrequency")) }
        if (settings.has("scheduleRequireCharging")) preferences.scheduleRequireCharging = settings.optBoolean("scheduleRequireCharging", true)
        if (settings.has("scheduleRequireIdle")) preferences.scheduleRequireIdle = settings.optBoolean("scheduleRequireIdle", true)
        if (settings.has("scheduleScanOnly")) preferences.scheduleScanOnly = settings.optBoolean("scheduleScanOnly", true)
        if (settings.has("scheduleStorageThreshold")) preferences.scheduleStorageThreshold = settings.optInt("scheduleStorageThreshold", 85)
        if (settings.has("trashRetentionDays")) preferences.trashRetentionDays = settings.optInt("trashRetentionDays", 30)
        if (settings.has("trashMaxMb")) preferences.trashMaxMb = settings.optInt("trashMaxMb", 2048)
        rules.replace(importedRules)
        whitelist.replace(importedWhitelist)
        return ConfigImportResult(importedRules.size, importedWhitelist.size)
    }
}
