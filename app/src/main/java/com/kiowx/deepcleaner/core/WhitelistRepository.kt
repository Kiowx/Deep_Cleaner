package com.kiowx.deepcleaner.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

class WhitelistRepository(context: Context) {
    private val file = File(context.filesDir, "whitelist.json")
    private val lock = Any()

    fun list(): List<WhitelistEntry> = synchronized(lock) { read() }

    fun add(type: WhitelistType, rawValue: String): WhitelistEntry? = synchronized(lock) {
        val value = normalize(type, rawValue) ?: return null
        val entries = read().toMutableList()
        entries.firstOrNull { it.type == type && it.value.equals(value, ignoreCase = true) }?.let { return it }
        val entry = WhitelistEntry(UUID.randomUUID().toString(), type, value, System.currentTimeMillis())
        entries += entry
        write(entries)
        entry
    }

    fun remove(id: String) = synchronized(lock) { write(read().filterNot { it.id == id }) }

    fun pathEntries(): Set<String> = list().asSequence()
        .filter { it.type == WhitelistType.PATH }
        .mapNotNull { runCatching { File(it.value).canonicalPath }.getOrNull() }
        .toSet()

    fun isProtected(file: File): Boolean {
        val entries = list()
        val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        val extension = file.extension.lowercase(Locale.ROOT)
        return entries.any { entry ->
            when (entry.type) {
                WhitelistType.PATH -> canonical == entry.value || canonical.startsWith("${entry.value}${File.separator}")
                WhitelistType.EXTENSION -> extension == entry.value.removePrefix(".").lowercase(Locale.ROOT)
                WhitelistType.APP -> canonical.replace('\\', '/').contains("/Android/data/${entry.value}/", ignoreCase = true) ||
                    canonical.replace('\\', '/').contains("/Android/media/${entry.value}/", ignoreCase = true)
            }
        }
    }

    private fun normalize(type: WhitelistType, raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return when (type) {
            WhitelistType.PATH -> runCatching { File(trimmed).canonicalPath }.getOrNull()
            WhitelistType.EXTENSION -> trimmed.removePrefix(".").lowercase(Locale.ROOT).takeIf { it.matches(Regex("[a-z0-9]{1,12}")) }
            WhitelistType.APP -> trimmed.takeIf { it.matches(Regex("[A-Za-z0-9_.]{3,255}")) }
        }
    }

    private fun read(): List<WhitelistEntry> = runCatching {
        if (!file.isFile) return@runCatching emptyList()
        val array = JSONArray(file.readText())
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    WhitelistEntry(
                        id = item.getString("id"),
                        type = runCatching { WhitelistType.valueOf(item.getString("type")) }.getOrDefault(WhitelistType.PATH),
                        value = item.getString("value"),
                        createdAt = item.optLong("createdAt"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun write(entries: List<WhitelistEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().put("id", entry.id).put("type", entry.type.name).put("value", entry.value).put("createdAt", entry.createdAt))
        }
        atomicWrite(file, array.toString())
    }
}

internal fun atomicWrite(target: File, content: String) {
    target.parentFile?.mkdirs()
    val temporary = File(target.parentFile, "${target.name}.tmp")
    temporary.writeText(content)
    if (!temporary.renameTo(target)) {
        target.writeText(content)
        temporary.delete()
    }
}
