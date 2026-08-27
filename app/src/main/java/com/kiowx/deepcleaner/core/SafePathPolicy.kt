package com.kiowx.deepcleaner.core

import java.io.File

class SafePathPolicy(
    roots: List<File>,
    private val extraExcludedPaths: Set<String> = emptySet(),
) {
    private val canonicalRoots = roots.mapNotNull { it.safeCanonical() }.distinct()
    private val protectedNames = setOf("android", ".deepcleanertrash", "deepcleaneroptimized", "deepcleanerarchive")

    fun canScan(path: File): Boolean {
        val canonical = path.safeCanonical() ?: return false
        if (canonicalRoots.none { canonical == it || canonical.startsWith("$it${File.separator}") }) return false
        if (extraExcludedPaths.any { canonical == it || canonical.startsWith("$it${File.separator}") }) return false
        val relative = canonicalRoots.firstNotNullOfOrNull { root ->
            if (canonical == root) "" else canonical.removePrefix("$root${File.separator}").takeIf { canonical.startsWith("$root${File.separator}") }
        } ?: return false
        val firstSegment = relative.substringBefore(File.separator).lowercase()
        return firstSegment !in protectedNames
    }

    fun canDelete(path: File): Boolean {
        val canonical = path.safeCanonical() ?: return false
        if (canonicalRoots.any { canonical == it }) return false
        return canScan(path) && path.parentFile != null
    }

    private fun File.safeCanonical(): String? = runCatching { canonicalFile.absolutePath.trimEnd(File.separatorChar) }.getOrNull()
}
