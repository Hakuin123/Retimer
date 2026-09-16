package dev.hk256.retimer.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * EXIF 日期时间字符串的换算。
 *
 * EXIF 的 `yyyy:MM:dd HH:mm:ss` 是拍摄地墙上时钟的时间，本身不带时区；真实时刻要靠单独
 * 记录的 OFFSET_TIME 标签（如 `+09:00`）才知道。有偏移就按它还原——结果与设备时区无关，
 * 换了时区再读也不会漂；偏移缺失或写坏了才退回 [fallback]（一般是设备时区）。
 */
object ExifTime {
    private val formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)

    /**
     * 把一个 EXIF 日期时间字符串换算成 [Instant]；字符串不合法时返回 null。
     *
     * [offset] 是 OFFSET_TIME 标签的内容；解析不出来不影响整体——退回 [fallback] 解释。
     */
    fun parse(
        value: String,
        fallback: ZoneId,
        offset: String? = null,
    ): Instant? = runCatching {
        val local = LocalDateTime.parse(value, formatter)
        val zoneOffset =
            offset
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { runCatching { ZoneOffset.of(it) }.getOrNull() }
        when (zoneOffset) {
            null -> local.atZone(fallback).toInstant()
            else -> local.atOffset(zoneOffset).toInstant()
        }
    }.getOrNull()
}
