package com.kiowx.deepcleaner.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.max

class MediaOptimizer(private val context: Context) {
    suspend fun optimize(
        items: List<CleanItem>,
        onProgress: suspend (done: Int, total: Int, name: String) -> Unit = { _, _, _ -> },
    ): OptimizeResult {
        val outputRoot = File(StorageAccess.roots(context).firstOrNull() ?: context.filesDir, "DeepCleanerOptimized")
        if (!outputRoot.mkdirs() && !outputRoot.isDirectory) return OptimizeResult(0, items.size, 0, 0)
        var completed = 0
        var failed = 0
        var originalBytes = 0L
        var outputBytes = 0L
        items.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            val source = item.file
            if (!source.isFile) {
                failed++
                return@forEachIndexed
            }
            val output = uniqueOutput(outputRoot, source)
            val ok = if (source.extension.lowercase(Locale.ROOT) in AdvancedScanner.VIDEO_EXTENSIONS) {
                withContext(Dispatchers.Main) { compressVideo(source, output) }
            } else {
                withContext(Dispatchers.IO) { compressImage(source, output) }
            }
            if (ok && output.isFile && output.length() in 1 until source.length()) {
                completed++
                originalBytes += source.length()
                outputBytes += output.length()
            } else {
                output.delete()
                failed++
            }
            onProgress(index + 1, items.size, item.name)
        }
        return OptimizeResult(completed, failed, originalBytes, outputBytes)
    }

    private fun compressImage(source: File, output: File): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
        val bitmap = BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
        val maxDimension = 2048
        val scale = (maxDimension.toFloat() / max(bitmap.width, bitmap.height)).coerceAtMost(1f)
        val resized = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        output.outputStream().buffered().use { resized.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        if (resized !== bitmap) resized.recycle()
        bitmap.recycle()
        output.length() > 0
    }.getOrDefault(false)

    @OptIn(UnstableApi::class)
    private suspend fun compressVideo(source: File, output: File): Boolean = suspendCancellableCoroutine { continuation ->
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) {
                if (continuation.isActive) continuation.resume(output.isFile && output.length() > 0)
            }

            override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                output.delete()
                if (continuation.isActive) continuation.resume(false)
            }
        }
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(listener)
            .build()
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source)))
            .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(720))))
            .build()
        continuation.invokeOnCancellation {
            transformer.cancel()
            output.delete()
        }
        runCatching { transformer.start(edited, output.absolutePath) }
            .onFailure { if (continuation.isActive) continuation.resume(false) }
    }

    private fun uniqueOutput(root: File, source: File): File {
        val video = source.extension.lowercase(Locale.ROOT) in AdvancedScanner.VIDEO_EXTENSIONS
        val extension = if (video) "mp4" else "jpg"
        var target = File(root, "${source.nameWithoutExtension}-optimized.$extension")
        var suffix = 1
        while (target.exists()) target = File(root, "${source.nameWithoutExtension}-optimized-$suffix.$extension").also { suffix++ }
        return target
    }
}
