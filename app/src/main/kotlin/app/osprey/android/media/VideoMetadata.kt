package app.osprey.android.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

// Android's own documented format for MediaMetadataRetriever.METADATA_KEY_DATE.
private val VIDEO_DATE_FORMAT = SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

/**
 * Video-container creation time for [uri], via [MediaMetadataRetriever] -- no ffmpeg/ffprobe
 * dependency needed on Android (unlike desktop's [app.osprey.desktop.media.probeVideoCreationTime]):
 * the platform's own media framework already reads a video's container metadata directly off a
 * SAF `Uri`. Returns null if [uri] carries no such metadata or isn't a readable video.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
fun probeVideoCreationTime(context: Context, uri: Uri): LocalDateTime? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE) ?: return null
        VIDEO_DATE_FORMAT.parse(raw)?.let { LocalDateTime.ofInstant(it.toInstant(), ZoneId.of("UTC")) }
    } catch (e: Exception) {
        null
    } finally {
        retriever.release()
    }
}

/**
 * One representative frame of the video at [uri], for perceptual hashing -- the same role
 * desktop's `ffmpeg`-piped frame plays, via the platform's own decoder instead.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
fun extractVideoFrameBitmap(context: Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (e: Exception) {
        null
    } finally {
        retriever.release()
    }
}
