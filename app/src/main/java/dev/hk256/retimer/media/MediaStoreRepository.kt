package dev.hk256.retimer.media

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.net.toUri
import dev.hk256.retimer.core.MediaItem
import dev.hk256.retimer.core.MediaKind
import java.time.Instant

class MediaStoreRepository(
    private val contentResolver: ContentResolver,
) {
    /** 媒体条目地址：优先用条目自带的地址，否则按类型和 id 拼出来。 */
    fun uriFor(item: MediaItem): android.net.Uri {
        item.sourceUri?.let { return it.toUri() }
        val collection = when (item.kind) {
            MediaKind.IMAGE -> MediaStore.Images.Media.getContentUri("external")
            MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri("external")
        }
        return ContentUris.withAppendedId(collection, item.id.toLong())
    }

    fun mediaFromUri(uri: Uri): MediaItem? {
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
        } ?: uri.lastPathSegment ?: return null
        val mime = contentResolver.getType(uri) ?: return null
        val kind = when {
            mime.startsWith("image/", ignoreCase = true) -> MediaKind.IMAGE
            mime.startsWith("video/", ignoreCase = true) -> MediaKind.VIDEO
            else -> return null
        }
        val mediaStoreTimes = if (uri.authority == MediaStore.AUTHORITY) {
            contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.DATA),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    Triple(
                        cursor.getLong(0).takeIf { it > 0L }?.let(Instant::ofEpochMilli),
                        cursor.getLong(1).takeIf { it > 0L }?.let(Instant::ofEpochSecond),
                        cursor.getString(2),
                    )
                } else null
            }
        } else null
        return MediaItem(uri.toString(), name, kind, mime, mediaStoreTimes?.first, mediaStoreTimes?.second, uri.toString(), mediaStoreTimes?.third)
    }

    fun mediaFromTree(treeUri: Uri): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)

        fun visit(parentDocumentId: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
            contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idIndex) ?: continue
                    val name = cursor.getString(nameIndex) ?: continue
                    val mime = cursor.getString(mimeIndex) ?: continue
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        visit(documentId)
                        continue
                    }
                    val kind = when {
                        mime.startsWith("image/", ignoreCase = true) -> MediaKind.IMAGE
                        mime.startsWith("video/", ignoreCase = true) -> MediaKind.VIDEO
                        else -> continue
                    }
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    val modified = cursor.getLong(modifiedIndex).takeIf { it > 0L }?.let(Instant::ofEpochMilli)
                    result += MediaItem(uri.toString(), name, kind, mime, null, modified, uri.toString())
                }
            }
        }

        visit(rootDocumentId)
        return result
    }
}
