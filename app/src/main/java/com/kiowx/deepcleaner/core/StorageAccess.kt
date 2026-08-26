package com.kiowx.deepcleaner.core

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File

object StorageAccess {
    fun hasAllFilesAccess(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    @Suppress("DEPRECATION")
    fun roots(context: Context): List<File> {
        val roots = linkedSetOf<File>()
        roots += Environment.getExternalStorageDirectory()
        context.getExternalFilesDirs(null).filterNotNull().forEach { appDir ->
            var current: File? = appDir
            while (current != null && current.name != "Android") current = current.parentFile
            current?.parentFile?.let(roots::add)
        }
        return roots.filter { it.exists() && it.canRead() }.distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
    }

    fun snapshot(context: Context): StorageSnapshot {
        val root = roots(context).firstOrNull() ?: context.filesDir
        return runCatching {
            val stat = StatFs(root.absolutePath)
            StorageSnapshot(total = stat.totalBytes, available = stat.availableBytes)
        }.getOrDefault(StorageSnapshot())
    }
}

