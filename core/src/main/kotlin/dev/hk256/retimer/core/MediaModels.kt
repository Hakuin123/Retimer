package dev.hk256.retimer.core

import java.time.Instant

enum class MediaKind {
    IMAGE,
    VIDEO,
}

data class MediaItem(
    val id: String,
    val displayName: String,
    val kind: MediaKind,
    val mimeType: String,
    val metadataTime: Instant?,
    val fileModifiedTime: Instant?,
    val sourceUri: String? = null,
    val filePath: String? = null,
)

/** 一个候选时间：值，加一句给用户看的来源说明。 */
data class DateCandidate(
    val value: Instant?,
    val reason: String? = null,
)
