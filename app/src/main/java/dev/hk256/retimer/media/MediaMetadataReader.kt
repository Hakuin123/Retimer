package dev.hk256.retimer.media

import android.content.ContentResolver
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MediaMetadataReader(
    private val contentResolver: ContentResolver,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)

    fun readImageTime(uri: Uri): Instant? = runCatching {
        contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val exif = ExifInterface(descriptor.fileDescriptor)
            sequenceOf(
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_DATETIME,
            ).mapNotNull(exif::getAttribute)
                .mapNotNull(::parseDate)
                .firstOrNull()
        }
    }.getOrNull()

    private fun parseDate(value: String): Instant? = runCatching {
        LocalDateTime.parse(value, formatter).atZone(zoneId).toInstant()
    }.getOrNull()
}
