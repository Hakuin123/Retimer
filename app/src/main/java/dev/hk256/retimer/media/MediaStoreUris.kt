package dev.hk256.retimer.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore

/**
 * 把各种来源的媒体 URI 统一转换成 MediaStore 的“媒体条目 URI”。
 *
 * [MediaStore.createWriteRequest] 只接受形如
 * `content://media/<volume>/{images|video|audio}/media/<id>` 的 URI，也就是按 _ID
 * 指向具体媒体条目的地址；MediaProvider 侧（MediaProvider#createRequest）会逐个校验，
 * 不满足时报 `IllegalArgumentException: All requested items must be Media items`。
 *
 * 而界面上三个选媒体入口拿到的 URI 都不是这个形式：
 * - 系统相册选择器（photo picker）：`content://media/picker/<user>/<authority>/media/<id>`，
 *   authority 同样是 `media`，所以只靠 authority 判断会误以为是合法 URI，实际它是只读 URI；
 * - 文件选择器 / 文件夹选择器（SAF）：`content://<documents-provider>/...`。
 *
 * 所以请求写入授权之前必须先在这里做转换。
 */
class MediaStoreUris(context: Context) {

    private val context = context.applicationContext
    private val resolver: ContentResolver = this.context.contentResolver

    /**
     * 把原始 URI 转换成 MediaStore 的媒体条目 URI；无法转换时返回 null，
     * 调用方应改为直接写文件（SAF 授权）或提示用户。
     *
     * @param filePath 已知的文件绝对路径，作为最后的兜底查询依据。
     */
    fun resolve(raw: Uri?, filePath: String? = null): Uri? {
        if (raw != null) {
            if (isMediaItemUri(raw)) return raw
            if (isPickerUri(raw)) {
                pickerUriToMediaItemUri(raw)?.let { return it }
            } else {
                documentUriToMediaItemUri(raw)?.let { return it }
            }
        }
        return filePath?.takeIf { it.isNotBlank() }?.let(::mediaItemUriForPath)
    }

    /** photo picker 的 URI：`content://media/picker/<user>/<authority>/media/<id>`。 */
    fun isPickerUri(uri: Uri?): Boolean =
        uri != null &&
            uri.authority == MediaStore.AUTHORITY &&
            uri.pathSegments.firstOrNull() == PICKER_SEGMENT

    /**
     * 取媒体库里的真实文件名。
     *
     * photo picker 交给应用的是一份系统内部副本，它的 DISPLAY_NAME 和 `_data`
     * 都指向那份副本（形如 `40.jpg`），不是原始文件名；反查回媒体条目后要用
     * 媒体库里的名字，否则文件名解析、"打开方式"里显示的都会是这串编号。
     */
    fun displayNameFor(raw: Uri?, fallback: String): String {
        val picker = raw?.takeIf(::isPickerUri) ?: return fallback
        val item = pickerUriToMediaItemUri(picker) ?: return fallback
        return queryString(item, MediaStore.MediaColumns.DISPLAY_NAME)?.takeIf { it.isNotBlank() } ?: fallback
    }

    /**
     * photo picker 的 URI 只授予读取权限，系统也没有提供它到媒体库条目的转换
     * （[MediaStore.getMediaUri] 只支持 SAF 的文档 URI），所以它根本不能用于请求写入授权。
     *
     * 但它允许查询 `_data` 与 [_size]/[MediaStore.MediaColumns.DATE_TAKEN] 等列：
     * 先看 `_data` 能否直接对应媒体库条目（部分设备不会做脱敏），
     * 否则用「大小 + 拍摄时间 + 类型 + 宽高」反查唯一匹配的条目。
     * 只要匹配结果不唯一就放弃，避免把日期写到别的文件上。
     */
    private fun pickerUriToMediaItemUri(uri: Uri): Uri? {
        queryString(uri, MediaStore.MediaColumns.DATA)?.let { path ->
            mediaItemUriForPath(path)?.let { return it }
        }
        return mediaItemUriForPickerColumns(uri)
    }

    private fun mediaItemUriForPickerColumns(uri: Uri): Uri? {
        val info = pickerItemInfo(uri) ?: return null
        val size = info.size ?: return null

        val conditions = mutableListOf(
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)",
            "${MediaStore.MediaColumns.SIZE} = ?",
        )
        val arguments = mutableListOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            size.toString(),
        )
        info.dateTaken?.let {
            conditions += "${MediaStore.MediaColumns.DATE_TAKEN} = ?"
            arguments += it.toString()
        }
        info.mimeType?.let {
            conditions += "${MediaStore.MediaColumns.MIME_TYPE} = ?"
            arguments += it
        }
        info.width?.let {
            conditions += "${MediaStore.MediaColumns.WIDTH} = ?"
            arguments += it.toString()
        }
        info.height?.let {
            conditions += "${MediaStore.MediaColumns.HEIGHT} = ?"
            arguments += it.toString()
        }

        return runCatching {
            resolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE),
                conditions.joinToString(" AND "),
                arguments.toTypedArray(),
                null,
            )?.use { cursor ->
                // 只有唯一匹配才敢用：宁可报错，也不能改到别的媒体上。
                if (cursor.count != 1) return@use null
                cursor.moveToFirst()
                val collection = mediaCollectionFor(cursor.getInt(1)) ?: return@use null
                mediaItemUri(MediaStore.VOLUME_EXTERNAL, collection, cursor.getLong(0))
            }
        }.getOrNull()
    }

    private fun pickerItemInfo(uri: Uri): PickerItemInfo? =
        runCatching {
            resolver.query(uri, PICKER_COLUMNS, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                PickerItemInfo(
                    size = cursor.longOrNull(0),
                    dateTaken = cursor.longOrNull(1),
                    mimeType = cursor.stringOrNull(2),
                    width = cursor.intOrNull(3),
                    height = cursor.intOrNull(4),
                )
            }
        }.getOrNull()

    /**
     * SAF 的文档 URI 交给 [MediaStore.getMediaUri] 转换。它返回的是 files 集合
     * （`content://media/<volume>/file/<id>`），仍然不是媒体条目，需要再按 media_type
     * 换成对应的媒体集合。files / images / video 共用同一张表的 _ID，可以直接改写路径。
     */
    private fun documentUriToMediaItemUri(uri: Uri): Uri? {
        val converted = runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull() ?: return null
        if (isMediaItemUri(converted)) return converted
        val mediaType = queryInt(converted, MediaStore.Files.FileColumns.MEDIA_TYPE) ?: return null
        val collection = mediaCollectionFor(mediaType) ?: return null
        val id = runCatching { ContentUris.parseId(converted) }.getOrNull() ?: return null
        return mediaItemUri(volumeOf(converted), collection, id)
    }

    /** 用文件绝对路径在媒体库中查回 _ID。 */
    private fun mediaItemUriForPath(path: String): Uri? =
        runCatching {
            resolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE),
                "${MediaStore.Files.FileColumns.DATA} = ?",
                arrayOf(path),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val collection = mediaCollectionFor(cursor.getInt(1)) ?: continue
                    return@use mediaItemUri(MediaStore.VOLUME_EXTERNAL, collection, cursor.getLong(0))
                }
                null
            }
        }.getOrNull()

    private fun mediaCollectionFor(mediaType: Int): String? =
        when (mediaType) {
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> IMAGES_SEGMENT
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> VIDEO_SEGMENT
            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> AUDIO_SEGMENT
            else -> null
        }

    private fun volumeOf(uri: Uri): String =
        uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() } ?: MediaStore.VOLUME_EXTERNAL

    private fun mediaItemUri(volume: String, collection: String, id: Long): Uri =
        Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(MediaStore.AUTHORITY)
            .appendPath(volume)
            .appendPath(collection)
            .appendPath(MEDIA_SEGMENT)
            .appendPath(id.toString())
            .build()

    private fun queryString(uri: Uri, column: String): String? = queryValue(uri, column) { it.getString(0) }

    private fun queryInt(uri: Uri, column: String): Int? =
        queryValue(uri, column) { if (it.isNull(0)) null else it.getInt(0) }

    private fun <T> queryValue(uri: Uri, column: String, read: (Cursor) -> T?): T? =
        runCatching {
            resolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) read(cursor) else null
            }
        }.getOrNull()

    private fun Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)

    private fun Cursor.intOrNull(index: Int): Int? = if (isNull(index)) null else getInt(index)

    private fun Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

    /** photo picker 允许查询的列（`PickerMediaColumns`），与 [PICKER_COLUMNS] 顺序一致。 */
    private data class PickerItemInfo(
        val size: Long?,
        val dateTaken: Long?,
        val mimeType: String?,
        val width: Int?,
        val height: Int?,
    )

    private companion object {
        const val PICKER_SEGMENT = "picker"
        const val IMAGES_SEGMENT = "images"
        const val VIDEO_SEGMENT = "video"
        const val AUDIO_SEGMENT = "audio"

        val PICKER_COLUMNS = arrayOf(
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
        )
    }
}

/** `content://media/<volume>/<collection>/media/<id>` 共 4 段。 */
private const val MEDIA_ITEM_SEGMENT_COUNT = 4
private const val MEDIA_SEGMENT = "media"
private val MEDIA_COLLECTIONS = setOf("images", "video", "audio")

/**
 * 是否是可以直接交给 [MediaStore.createWriteRequest] 的媒体条目 URI，
 * 也就是 `content://media/<volume>/<collection>/media/<id>`。
 *
 * 卷名不做限制：同一个媒体在相册选择器那条路线上是 `external`，
 * 在 [MediaStore.getMediaUri]（SAF 选中的文件）那条路线上是 `external_primary`。
 */
internal fun isMediaItemUri(uri: Uri?): Boolean {
    if (uri == null || uri.scheme != ContentResolver.SCHEME_CONTENT) return false
    if (uri.authority != MediaStore.AUTHORITY) return false
    val segments = uri.pathSegments
    if (segments.size != MEDIA_ITEM_SEGMENT_COUNT) return false
    if (segments[1] !in MEDIA_COLLECTIONS) return false
    if (segments[2] != MEDIA_SEGMENT) return false
    return segments[3].toLongOrNull() != null
}
