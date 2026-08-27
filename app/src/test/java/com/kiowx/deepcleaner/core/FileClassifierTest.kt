package com.kiowx.deepcleaner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileClassifierTest {
    @get:Rule val temporary = TemporaryFolder()
    private val now = 2_000_000_000_000L

    @Test
    fun oldTemporaryFileIsSafeByDefault() {
        val file = temporary.newFile("download.crdownload").apply { setLastModified(now - 2 * 86_400_000L) }
        val match = FileClassifier.classify(file, now)
        assertEquals(CleanCategory.TEMPORARY, match?.category)
        assertTrue(match?.safeByDefault == true)
    }

    @Test
    fun installerRequiresExplicitSelection() {
        val file = temporary.newFile("release.apk").apply { setLastModified(now - 10 * 86_400_000L) }
        val match = FileClassifier.classify(file, now)
        assertEquals(CleanCategory.INSTALLERS, match?.category)
        assertFalse(match?.safeByDefault ?: true)
    }

    @Test
    fun recentLogIsNotClassified() {
        val file = temporary.newFile("important.log").apply { setLastModified(now - 60_000L) }
        assertNull(FileClassifier.classify(file, now))
    }

    @Test
    fun protectedAndroidTreeCannotBeScannedOrDeleted() {
        val root = temporary.newFolder("storage")
        val androidData = File(root, "Android/data/other.app/cache.tmp").apply { parentFile?.mkdirs(); writeText("x") }
        val policy = SafePathPolicy(listOf(root))
        assertFalse(policy.canScan(androidData))
        assertFalse(policy.canDelete(androidData))
        assertFalse(policy.canDelete(root))
    }

    @Test
    fun ordinarySharedFileCanBeDeletedAfterReview() {
        val root = temporary.newFolder("storage2")
        val file = File(root, "Download/old.tmp").apply { parentFile?.mkdirs(); writeText("x") }
        val policy = SafePathPolicy(listOf(root))
        assertTrue(policy.canScan(file))
        assertTrue(policy.canDelete(file))
    }

    @Test
    fun androidMediaRequiresExplicitPolicyButAndroidDataAlwaysStaysProtected() {
        val root = temporary.newFolder("storage3")
        val media = File(root, "Android/media/com.tencent.mm/cache/old.tmp").apply { parentFile?.mkdirs(); writeText("x") }
        val privateData = File(root, "Android/data/com.tencent.mm/cache/old.tmp").apply { parentFile?.mkdirs(); writeText("x") }
        val ordinaryPolicy = SafePathPolicy(listOf(root))
        val socialPolicy = SafePathPolicy(listOf(root), allowAndroidMedia = true)

        assertFalse(ordinaryPolicy.canScan(media))
        assertTrue(socialPolicy.canScan(media))
        assertTrue(socialPolicy.canDelete(media))
        assertFalse(socialPolicy.canScan(privateData))
        assertFalse(socialPolicy.canDelete(privateData))
    }
}
