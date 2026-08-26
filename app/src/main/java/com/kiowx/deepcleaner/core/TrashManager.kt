package com.kiowx.deepcleaner.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class TrashManager(private val context: Context) {
    private val indexFile = File(context.filesDir, "trash_index.json")
    private val lock = Any()

    fun list(): List<TrashRecord> = synchronized(lock) { readIndex().sortedByDescending(TrashRecord::deletedAt) }

    fun moveToTrash(source: File, knownSize: Long = source.length()): Boolean = synchronized(lock) {
        if (!source.exists()) return false
        val root = StorageAccess.roots(context).firstOrNull() ?: return false
        val id = "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        val targetDir = File(root, ".DeepCleanerTrash/$id")
        val target = File(targetDir, source.name.ifBlank { "item" })
        if (!targetDir.mkdirs() && !targetDir.isDirectory) return false

        val moved = runCatching {
            if (!source.renameTo(target)) {
                if (source.isDirectory) source.copyRecursively(target, overwrite = false)
                else source.copyTo(target, overwrite = false)
                val removed = if (source.isDirectory) source.deleteRecursively() else source.delete()
                if (!removed) {
                    target.deleteRecursively()
                    return@runCatching false
                }
            }
            true
        }.getOrDefault(false)
        if (!moved) {
            targetDir.deleteRecursively()
            return false
        }

        val records = readIndex().toMutableList()
        records += TrashRecord(id, source.absolutePath, target.absolutePath, source.name, knownSize, System.currentTimeMillis())
        writeIndex(records)
        true
    }

    fun restore(record: TrashRecord): Boolean = synchronized(lock) {
        val source = File(record.trashPath)
        if (!source.exists()) {
            removeRecord(record.id)
            return false
        }
        var target = File(record.originalPath)
        if (target.exists()) {
            val parent = target.parentFile ?: return false
            val base = target.nameWithoutExtension
            val ext = target.extension.takeIf(String::isNotEmpty)?.let { ".$it" }.orEmpty()
            var suffix = 1
            do {
                target = File(parent, "$base (已恢复 $suffix)$ext")
                suffix++
            } while (target.exists())
        }
        target.parentFile?.mkdirs()
        val restored = runCatching {
            if (!source.renameTo(target)) {
                if (source.isDirectory) source.copyRecursively(target) else source.copyTo(target)
                if (source.isDirectory) source.deleteRecursively() else source.delete()
            } else true
        }.getOrDefault(false)
        if (restored) {
            removeRecord(record.id)
            source.parentFile?.delete()
        }
        restored
    }

    fun permanentlyDelete(record: TrashRecord): Boolean = synchronized(lock) {
        val file = File(record.trashPath)
        val deleted = !file.exists() || runCatching {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }.getOrDefault(false)
        if (deleted) {
            removeRecord(record.id)
            file.parentFile?.delete()
        }
        deleted
    }

    fun empty(): DeleteResult = synchronized(lock) {
        val records = readIndex()
        var deleted = 0
        var failed = 0
        var released = 0L
        records.forEach { record ->
            val file = File(record.trashPath)
            val ok = !file.exists() || runCatching {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }.getOrDefault(false)
            if (ok) {
                deleted++
                released += record.size
            } else failed++
        }
        writeIndex(records.filter { File(it.trashPath).exists() })
        DeleteResult(deleted, failed, released)
    }

    fun prune(retentionDays: Int, maximumBytes: Long): DeleteResult = synchronized(lock) {
        val records = readIndex().sortedBy(TrashRecord::deletedAt).toMutableList()
        val cutoff = System.currentTimeMillis() - retentionDays.coerceIn(1, 90) * 86_400_000L
        var total = records.sumOf(TrashRecord::size)
        var deleted = 0
        var failed = 0
        var released = 0L
        val remaining = records.toMutableList()
        records.forEach { record ->
            if (record.deletedAt >= cutoff && total <= maximumBytes.coerceAtLeast(0)) return@forEach
            val target = File(record.trashPath)
            val ok = !target.exists() || runCatching { if (target.isDirectory) target.deleteRecursively() else target.delete() }.getOrDefault(false)
            if (ok) {
                remaining.removeAll { it.id == record.id }
                total -= record.size
                released += record.size
                deleted++
                target.parentFile?.delete()
            } else failed++
        }
        writeIndex(remaining)
        DeleteResult(deleted, failed, released)
    }

    private fun removeRecord(id: String) = writeIndex(readIndex().filterNot { it.id == id })

    private fun readIndex(): List<TrashRecord> = runCatching {
        if (!indexFile.isFile) return@runCatching emptyList()
        val array = JSONArray(indexFile.readText())
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    TrashRecord(
                        id = item.getString("id"),
                        originalPath = item.getString("originalPath"),
                        trashPath = item.getString("trashPath"),
                        name = item.optString("name"),
                        size = item.optLong("size"),
                        deletedAt = item.optLong("deletedAt"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun writeIndex(records: List<TrashRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("id", record.id)
                    .put("originalPath", record.originalPath)
                    .put("trashPath", record.trashPath)
                    .put("name", record.name)
                    .put("size", record.size)
                    .put("deletedAt", record.deletedAt),
            )
        }
        val temporary = File(indexFile.parentFile, "${indexFile.name}.tmp")
        temporary.writeText(array.toString())
        if (!temporary.renameTo(indexFile)) {
            indexFile.writeText(array.toString())
            temporary.delete()
        }
    }
}
