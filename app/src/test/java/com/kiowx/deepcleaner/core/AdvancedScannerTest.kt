package com.kiowx.deepcleaner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedScannerTest {
    @Test
    fun storageClassifierRecognizesCommonTypes() {
        assertEquals(StorageCategory.IMAGES, StorageClassifier.categoryForExtension("JPG"))
        assertEquals(StorageCategory.VIDEOS, StorageClassifier.categoryForExtension(".mp4"))
        assertEquals(StorageCategory.INSTALLERS, StorageClassifier.categoryForExtension("apk"))
        assertEquals(StorageCategory.DOCUMENTS, StorageClassifier.categoryForExtension("pdf"))
        assertEquals(StorageCategory.OTHER, StorageClassifier.categoryForExtension("unknown"))
    }

    @Test
    fun perceptualHashDistanceCountsChangedBits() {
        assertEquals(0, SimilarityMetrics.hammingDistance(0x55L, 0x55L))
        assertEquals(4, SimilarityMetrics.hammingDistance(0x0L, 0x0fL))
        assertTrue(SimilarityMetrics.hammingDistance(0xffffL, 0x0L) > 8)
        assertEquals(2.0, SimilarityMetrics.meanAbsoluteDifference(byteArrayOf(10, 20), byteArrayOf(12, 18)), 0.001)
        assertTrue(SimilarityMetrics.meanAbsoluteDifference(byteArrayOf(0), byteArrayOf(50)) > 18)
    }
}
