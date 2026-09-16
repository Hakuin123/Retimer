package dev.hk256.retimer.media

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import dev.hk256.retimer.core.EditTargets
import dev.hk256.retimer.core.MediaDateWriter
import dev.hk256.retimer.core.MediaItem
import dev.hk256.retimer.core.MediaKind
import dev.hk256.retimer.core.ProcessingResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay

class ExifImageDateWriter(
    private val contentResolver: ContentResolver,
    private val uriProvider: (MediaItem) -> android.net.Uri,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : MediaDateWriter {
    private val exifFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)
    private val offsetFormatter = DateTimeFormatter.ofPattern("XXX", Locale.US)

    override suspend fun write(
        item: MediaItem,
        target: Instant,
        targets: EditTargets,
    ): ProcessingResult {
        if (item.kind != MediaKind.IMAGE) return ProcessingResult.Skipped("仅支持图片 EXIF 写入")

        val uri = uriProvider(item)
        return try {
            val localTarget = target.atZone(zoneId)
            val date = exifFormatter.format(localTarget)
            val offset = offsetFormatter.format(localTarget)
            contentResolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
                val exif = ExifInterface(descriptor.fileDescriptor)
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, date)
                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, date)
                exif.setAttribute(ExifInterface.TAG_DATETIME, date)
                exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
                exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offset)
                exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
                exif.saveAttributes()
            } ?: return ProcessingResult.Failed("无法打开媒体写入描述符")

            val verified = contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                val exif = ExifInterface(descriptor.fileDescriptor)
                exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) == date
            } == true
            if (!verified) return ProcessingResult.Failed("写入后校验失败")

            // 先尝试直接改媒体库里的拍摄日期（有的设备接受），改不动也不代表图库没更新：
            // Android 14 起这个值由 MediaProvider 按文件元数据推导，所以必须读回来才知道。
            if (isMediaItemUri(uri)) {
                runCatching {
                    contentResolver.update(
                        uri,
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DATE_TAKEN, target.toEpochMilli())
                        },
                        null,
                        null,
                    )
                }
            }
            // 媒体库是异步跟着文件走的：文件写完后 MediaProvider 才重算这一格，
            // 刚写完立刻读会读到旧值。这里短暂重试几次，避免误报"未能更新"。
            var galleryUpdated = galleryDateMatches(uri, target)
            var waited = 0L
            while (!galleryUpdated && waited < GALLERY_UPDATE_TIMEOUT_MS) {
                delay(GALLERY_UPDATE_POLL_MS)
                waited += GALLERY_UPDATE_POLL_MS
                galleryUpdated = galleryDateMatches(uri, target)
            }
            // 没能做到的部分逐条说明，避免只给一个"部分成功"让人猜。
            val reasons = mutableListOf<String>()
            if (!galleryUpdated) reasons += "图库中的拍摄日期未能更新"
            if (targets.fileModifiedTime) {
                // 优先用媒体库里的真实路径：相册选择器给的路径是系统内部副本，改了也没意义。
                val path = mediaFilePath(uri) ?: item.filePath
                if (path.isNullOrBlank()) {
                    reasons += "存储位置不支持同步文件修改时间"
                } else {
                    val file = File(path)
                    val updated = file.setLastModified(target.toEpochMilli())
                    if (!updated || kotlin.math.abs(file.lastModified() - target.toEpochMilli()) > 1000L) {
                        reasons += "文件修改时间未能同步"
                    }
                }
            }
            if (reasons.isEmpty()) ProcessingResult.Success else ProcessingResult.Partial(reasons.joinToString("，"))
        } catch (error: Exception) {
            ProcessingResult.Failed(error.message ?: "EXIF 写入失败")
        }
    }

    /**
     * 图库记录的拍摄日期是否已经是 [target]。
     *
     * 读不到（不是媒体条目、没有读取权限、字段为空）时不判定为失败：数据库那格由
     * MediaProvider 维护，我们改的始终是文件里的元数据，不能凭一次 update 的结果下结论。
     */
    private fun galleryDateMatches(uri: android.net.Uri, target: Instant): Boolean {
        if (!isMediaItemUri(uri)) return false
        val taken =
            runCatching {
                contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
                }
            }.getOrNull() ?: return true
        // EXIF 只精确到秒，容一秒再比较，避免因为毫秒位不同而误报。
        return kotlin.math.abs(taken - target.toEpochMilli()) <= 1000L
    }

    /** 媒体库记录的真实文件路径；相册选择器的条目也能靠它绕开内部副本路径。 */
    private fun mediaFilePath(uri: android.net.Uri): String? {
        if (!isMediaItemUri(uri)) return null
        return runCatching {
            contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
    }
}

/** 等媒体库跟上文件改动的轮询间隔与等待上限。 */
private const val GALLERY_UPDATE_POLL_MS = 300L
private const val GALLERY_UPDATE_TIMEOUT_MS = 1500L
