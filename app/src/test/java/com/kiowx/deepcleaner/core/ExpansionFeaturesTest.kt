package com.kiowx.deepcleaner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExpansionFeaturesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun customRuleMatchesAllConfiguredConditions() {
        val now = 2_000_000_000_000L
        val file = File(temporary.newFolder("Download"), "old.log").apply {
            writeBytes(ByteArray(2048))
            setLastModified(now - 10L * 86_400_000L)
        }
        val rule = CustomCleanRule(
            id = "test",
            name = "旧日志",
            pathContains = "Download",
            extensions = setOf("log"),
            minimumBytes = 1024,
            olderThanDays = 7,
        )
        assertTrue(CustomRuleMatcher.matches(rule, file, now))
        assertFalse(CustomRuleMatcher.matches(rule.copy(extensions = setOf("tmp")), file, now))
        assertFalse(CustomRuleMatcher.matches(rule.copy(minimumBytes = 4096), file, now))
    }

    @Test
    fun reportMergeDeduplicatesPathsAndAccumulatesStatistics() {
        val item = CleanItem("a", "/tmp/a", "a", 100, 1, CleanCategory.TEMPORARY, "test")
        val first = ScanReport(listOf(item), 2, 200, 1, 0, 10)
        val second = ScanReport(listOf(item), 3, 300, 0, 1, 20)
        val merged = ExpansionScanner.mergeReports(first, second)
        assertEquals(1, merged.items.size)
        assertEquals(5, merged.scannedFiles)
        assertEquals(500, merged.scannedBytes)
        assertEquals(30, merged.elapsedMs)
    }

    @Test
    fun rootCleanerOnlyAcceptsExactAppCacheDirectories() {
        assertTrue(RootAccess.isAllowedCachePath("/data/user/0/com.example.app/cache"))
        assertTrue(RootAccess.isAllowedCachePath("/data/data/com.example.app/cache/"))
        assertFalse(RootAccess.isAllowedCachePath("/data/user/0/com.example.app/files"))
        assertFalse(RootAccess.isAllowedCachePath("/data/user/0/cache"))
        assertFalse(RootAccess.isAllowedCachePath("/data/user/0/com.example.app/cache/../../files"))
    }

    @Test
    fun bundledRemoteRulesHaveValidSignature() {
        val project = File(requireNotNull(System.getProperty("user.dir")))
        val rulesDirectory = listOf(File(project, "rules"), File(project, "../rules")).first(File::isDirectory)
        val payload = File(rulesDirectory, "clean-rules.json").readBytes()
        val signature = File(rulesDirectory, "clean-rules.json.sig").readText().trim()
        assertTrue(SignedRuleVerifier.verify(payload, signature, RuleUpdateRepository.PUBLIC_KEY_BASE64))
        assertFalse(SignedRuleVerifier.verify(payload + 0, signature, RuleUpdateRepository.PUBLIC_KEY_BASE64))
    }

    @Test
    fun riskLabelsKeepResidualAndPrivateCacheUnselected() {
        val residual = CleanItem("r", "/tmp/r", "r", 1, 0, CleanCategory.APP_RESIDUAL, "test")
        val root = CleanItem("root", "/data/user/0/a/cache", "a", 1, 0, CleanCategory.ROOT_CACHE, "test")
        assertFalse(residual.selected)
        assertFalse(root.selected)
        assertEquals(CleanRisk.HIGH, residual.risk)
        assertEquals(CleanRisk.MEDIUM, root.risk)
    }
}
