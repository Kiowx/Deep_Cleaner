package com.kiowx.deepcleaner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialFileClassifierTest {
    @Test
    fun qqCacheIsSafeByDefault() {
        val match = SocialFileClassifier.classify(SocialPlatform.QQ, "/Tencent/MobileQQ/diskcache/entry.tmp")
        assertEquals(CleanCategory.QQ_CACHE, match?.category)
        assertTrue(match?.selected == true)
    }

    @Test
    fun qqReceivedDocumentRequiresExplicitSelection() {
        val match = SocialFileClassifier.classify(SocialPlatform.QQ, "/Tencent/QQfile_recv/report.pdf")
        assertEquals(CleanCategory.QQ_FILES, match?.category)
        assertFalse(match?.selected ?: true)
    }

    @Test
    fun wechatEncryptedImageIsMediaAndNotSelected() {
        val match = SocialFileClassifier.classify(SocialPlatform.WECHAT, "/Tencent/MicroMsg/account/image2/ab/photo.dat")
        assertEquals(CleanCategory.WECHAT_MEDIA, match?.category)
        assertFalse(match?.selected ?: true)
    }

    @Test
    fun unknownWechatDatabaseIsNotOfferedForDeletion() {
        assertNull(SocialFileClassifier.classify(SocialPlatform.WECHAT, "/Tencent/MicroMsg/account/EnMicroMsg.db"))
    }
}
