package com.kiowx.deepcleaner.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class HistoryRepository(context: Context) {
    private val file = File(context.filesDir, "clean_history.json")
    private val lock = Any()

    fun list(): List<CleanHistoryRecord> = synchronized(lock) { read().sortedByDescending(CleanHistoryRecord::timestamp) }

    fun record(source: String, result: DeleteResult, mode: DeleteMode) = synchronized(lock) {
        val entry = CleanHistoryRecord(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            source = source,
            deleted = result.deleted,
            failed = result.failed,
            releasedBytes = result.releasedBytes,
            mode = mode,
        )
        write((listOf(entry) + read()).distinctBy(CleanHistoryRecord::id).take(200))
    }

    fun clear() = synchronized(lock) { write(emptyList()) }

    private fun read(): List<CleanHistoryRecord> = runCatching {
        if (!file.isFile) return@runCatching emptyList()
        val array = JSONArray(file.readText())
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    CleanHistoryRecord(
                        id = item.getString("id"),
                        timestamp = item.optLong("timestamp"),
                        source = item.optString("source", "手动清理"),
                        deleted = item.optInt("deleted"),
                        failed = item.optInt("failed"),
                        releasedBytes = item.optLong("releasedBytes"),
                        mode = runCatching { DeleteMode.valueOf(item.optString("mode")) }.getOrDefault(DeleteMode.PERMANENT),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun write(entries: List<CleanHistoryRecord>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().put("id", entry.id).put("timestamp", entry.timestamp).put("source", entry.source)
                    .put("deleted", entry.deleted).put("failed", entry.failed).put("releasedBytes", entry.releasedBytes)
                    .put("mode", entry.mode.name),
            )
        }
        atomicWrite(file, array.toString())
    }
}
