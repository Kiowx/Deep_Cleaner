package com.kiowx.deepcleaner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {
    private val validManifest = """
        {
          "format": "deep-cleaner-update",
          "schemaVersion": 1,
          "versionCode": 5,
          "versionName": "1.2.0",
          "minSdk": 26,
          "apkUrl": "https://github.com/Kiowx/Deep_Cleaner/releases/download/v1.2.0/Deep-Cleaner-1.2.0-Android16-release.apk",
          "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
          "changelog": ["新增自动更新", "修复扫描问题"],
          "mandatory": false
        }
    """.trimIndent()

    @Test
    fun parsesTrustedGitHubReleaseManifest() {
        val info = UpdateManifestParser.parse(validManifest)
        assertEquals(5, info.versionCode)
        assertEquals("1.2.0", info.versionName)
        assertTrue(info.changelog.contains("新增自动更新"))
        assertTrue(UpdateManifestParser.isNewer(info, 4, 36))
        assertFalse(UpdateManifestParser.isNewer(info, 5, 36))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsApkOutsideProjectRelease() {
        UpdateManifestParser.parse(validManifest.replace("github.com/Kiowx/Deep_Cleaner", "example.com/downloads"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidSha256() {
        UpdateManifestParser.parse(validManifest.replace("a".repeat(64), "not-a-hash"))
    }
}
