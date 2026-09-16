package dev.hk256.retimer.media

import android.content.ContentResolver
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dev.hk256.retimer.core.ExifTime
import java.time.Instant
import java.time.ZoneId

/**
 * 从文件元数据里读拍摄时间。
 *
 * EXIF 的日期时间按「裸时间 + OFFSET_TIME」成对还原（见 [ExifTime]）：带偏移时得到
 * 拍摄地的真实时刻，与设备时区无关；没写偏移的旧文件才按 [zoneId]（一般是设备时区）解释。
 *
 * @param zoneId 无偏移信息时解释裸时间用的时区。
 */
class MediaMetadataReader(
    private val contentResolver: ContentResolver,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun readImageTime(uri: Uri): Instant? = runCatching {
        contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val exif = ExifInterface(descriptor.fileDescriptor)
            // 裸时间与其偏移按标签成对取：ORIGINAL 对 ORIGINAL，DIGITIZED 对 DIGITIZED，
            // 免得「DATETIME + ORIGINAL 的偏移」这种跨标签错配把时间对错。
            sequenceOf(
                ExifInterface.TAG_DATETIME_ORIGINAL to ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED to ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
                ExifInterface.TAG_DATETIME to ExifInterface.TAG_OFFSET_TIME,
            ).mapNotNull { (dateTag, offsetTag) ->
                exif.getAttribute(dateTag)?.let { date ->
                    ExifTime.parse(date, zoneId, exif.getAttribute(offsetTag))
                }
            }.firstOrNull()
        }
    }.getOrNull()
}
