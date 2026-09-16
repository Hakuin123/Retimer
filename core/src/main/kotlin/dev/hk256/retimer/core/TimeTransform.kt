package dev.hk256.retimer.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object TimeTransform {
    /** 把所有项目都设成同一个瞬间（统一设置日期和时间）。 */
    fun uniform(items: List<MediaItem>, target: Instant): Map<String, Instant> =
        items.associate { it.id to target }

    /**
     * 只统一日期：各项目保留自己原有的时分秒，原时间缺失时用 [fallbackTime]。
     *
     * 时分秒是"本地时间"，所以换算需要 [zoneId]。
     */
    fun uniformDate(
        items: List<MediaItem>,
        date: LocalDate,
        zoneId: ZoneId,
        fallbackTime: LocalTime = LocalTime.MIDNIGHT,
    ): Map<String, Instant> =
        items.associate { item ->
            val time = item.metadataTime?.atZone(zoneId)?.toLocalTime() ?: fallbackTime
            item.id to date.atTime(time).atZone(zoneId).toInstant()
        }

    /**
     * 只统一时间：各项目保留自己原有的年月日。
     *
     * 原日期缺失的项目无法只改时间（改出来会是另一个日子），因此不产生结果，
     * 由调用方按"未设置"显示，交给用户逐项编辑。
     */
    fun uniformTime(items: List<MediaItem>, time: LocalTime, zoneId: ZoneId): Map<String, Instant> =
        items
            .mapNotNull { item ->
                item.metadataTime?.atZone(zoneId)?.toLocalDate()?.let { date ->
                    item.id to date.atTime(time).atZone(zoneId).toInstant()
                }
            }
            .toMap()

    /** 按各项目与最早项目之间的间隔平移，把最早的项目放到 [targetForEarliest]。 */
    fun shiftFromEarliest(items: List<MediaItem>, targetForEarliest: Instant): Map<String, Instant> {
        val timedItems = items.mapNotNull { item -> item.metadataTime?.let { item to it } }
        val earliest = timedItems.minOfOrNull { it.second } ?: return emptyMap()
        return timedItems.associate { (item, original) ->
            item.id to targetForEarliest.plus(Duration.between(earliest, original))
        }
    }
}
