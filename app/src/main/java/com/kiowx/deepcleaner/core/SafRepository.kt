package com.kiowx.deepcleaner.core

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.ArrayDeque

class SafRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("deep_cleaner_saf", Context.MODE_PRIVATE)

    fun roots(): List<SafRoot> = preferences.getStringSet("roots", emptySet()).orEmpty().mapNotNull { raw ->
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return@mapNotNull null
        val document = DocumentFile.fromTreeUri(context, uri)
        SafRoot(uri.toString(), document?.name ?: uri.lastPathSegment ?: "外部存储")
    }.sortedBy(SafRoot::name)

    fun add(uri: Uri): SafRoot? {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val document = DocumentFile.fromTreeUri(context, uri) ?: return null
        if (!document.exists() || !document.canRead()) return null
        val values = preferences.getStringSet("roots", emptySet()).orEmpty().toMutableSet().apply { add(uri.toString()) }
        preferences.edit { putStringSet("roots", values) }
        return SafRoot(uri.toString(), document.name ?: "外部存储")
    }

    fun remove(uri: String) {
        val values = preferences.getStringSet("roots", emptySet()).orEmpty().toMutableSet().apply { remove(uri) }
        preferences.edit { putStringSet("roots", values) }
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uri),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    suspend fun analyze(onProgress: suspend (ScanProgress) -> Unit = {}): StorageAnalysis {
        val categoryBytes = mutableMapOf<StorageCategory, Long>()
        val categoryFiles = mutableMapOf<StorageCategory, Long>()
        val directoryBytes = mutableMapOf<String, Long>()
        val directoryFiles = mutableMapOf<String, Long>()
        var scanned = 0L
        var bytes = 0L
        roots().forEach { saved ->
            currentCoroutineContext().ensureActive()
            val root = DocumentFile.fromTreeUri(context, Uri.parse(saved.uri)) ?: return@forEach
            val stack = ArrayDeque<Pair<DocumentFile, String>>().apply { add(root to saved.name) }
            while (stack.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val (current, topDirectory) = stack.removeLast()
                val children = runCatching { current.listFiles().toList() }.getOrDefault(emptyList())
                children.forEach { child ->
                    currentCoroutineContext().ensureActive()
                    if (child.isDirectory && child.canRead()) {
                        stack.add(child to if (current == root) "${saved.name}/${child.name ?: "目录"}" else topDirectory)
                    } else if (child.isFile && child.canRead()) {
                        scanned++
                        val size = child.length().coerceAtLeast(0)
                        bytes += size
                        val extension = child.name?.substringAfterLast('.', "").orEmpty()
                        val category = StorageClassifier.categoryForExtension(extension)
                        categoryBytes[category] = categoryBytes.getOrDefault(category, 0) + size
                        categoryFiles[category] = categoryFiles.getOrDefault(category, 0) + 1
                        directoryBytes[topDirectory] = directoryBytes.getOrDefault(topDirectory, 0) + size
                        directoryFiles[topDirectory] = directoryFiles.getOrDefault(topDirectory, 0) + 1
                        if (scanned % 50L == 0L) onProgress(ScanProgress(child.name.orEmpty(), scanned, categoryBytes.size, bytes))
                    }
                }
            }
        }
        return StorageAnalysis(
            buckets = StorageCategory.entries.map { StorageBucket(it, categoryBytes.getOrDefault(it, 0), categoryFiles.getOrDefault(it, 0)) }
                .filter { it.files > 0 }.sortedByDescending(StorageBucket::bytes),
            directories = directoryBytes.map { (path, size) -> DirectoryUsage(path, size, directoryFiles.getOrDefault(path, 0)) }
                .sortedByDescending(DirectoryUsage::bytes).take(30),
            scannedFiles = scanned,
            scannedBytes = bytes,
        )
    }
}
