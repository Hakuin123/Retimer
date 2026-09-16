package dev.hk256.retimer.core

interface MediaDateWriter {
    suspend fun write(item: MediaItem, target: java.time.Instant, targets: EditTargets): ProcessingResult
}

data class EditTargets(
    val fileModifiedTime: Boolean = true,
)

sealed interface ProcessingResult {
    data object Success : ProcessingResult
    data class Partial(val reason: String) : ProcessingResult
    data class Skipped(val reason: String) : ProcessingResult
    data class Failed(val reason: String) : ProcessingResult
}
