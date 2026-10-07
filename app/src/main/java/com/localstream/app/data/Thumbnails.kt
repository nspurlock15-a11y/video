package com.localstream.app.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Grabs a still frame from each video once and caches it as a small JPEG. */
object Thumbnails {
    private val limiter = Semaphore(2)

    suspend fun get(context: Context, videoUri: String): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "thumbs").apply { mkdirs() }
        val file = File(dir, sha1(videoUri) + ".jpg")
        if (file.length() > 0) return@withContext file
        limiter.withPermit {
            if (file.length() > 0) return@withPermit file
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(videoUri))
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                // 15% in avoids black frames and cold-open title cards.
                val atUs = if (durationMs > 0) durationMs * 150 else 60_000_000L
                val frame: Bitmap = (
                    if (Build.VERSION.SDK_INT >= 27) {
                        retriever.getScaledFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 270)
                    } else {
                        retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }
                    ) ?: return@withPermit null
                val scaled = if (frame.width > 480) {
                    Bitmap.createScaledBitmap(frame, 480, (480f * frame.height / frame.width).toInt().coerceAtLeast(1), true)
                } else {
                    frame
                }
                val tmp = File(dir, file.name + ".tmp")
                tmp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                tmp.renameTo(file)
                file
            } catch (e: Exception) {
                null
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
