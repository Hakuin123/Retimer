package dev.hk256.retimer.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimeTransformTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun uniformSetsEveryItemToTheSameInstant() {
        val items = listOf(item("a", Instant.parse("2020-01-01T01:00:00Z")), item("b"))
        val target = Instant.parse("2021-06-07T08:09:10Z")

        val result = TimeTransform.uniform(items, target)

        assertEquals(target, result["a"])
        assertEquals(target, result["b"])
    }

    @Test
    fun uniformDateKeepsEachItemTimeOfDay() {
        // 本地时间 2020-01-01 09:00 与 18:30
        val items = listOf(
            item("a", Instant.parse("2020-01-01T01:00:00Z")),
            item("b", Instant.parse("2020-01-01T10:30:00Z")),
        )

        val result = TimeTransform.uniformDate(items, LocalDate.of(2022, 3, 4), zone)

        assertEquals(Instant.parse("2022-03-04T01:00:00Z"), result["a"])
        assertEquals(Instant.parse("2022-03-04T10:30:00Z"), result["b"])
    }

    @Test
    fun uniformDateUsesMidnightWhenOriginalTimeIsUnknown() {
        val result = TimeTransform.uniformDate(listOf(item("a")), LocalDate.of(2022, 3, 4), zone)

        assertEquals(Instant.parse("2022-03-03T16:00:00Z"), result["a"])
    }

    @Test
    fun uniformTimeKeepsEachItemDateAndSkipsUnknownDates() {
        val items = listOf(item("a", Instant.parse("2020-01-01T01:00:00Z")), item("b"))

        val result = TimeTransform.uniformTime(items, LocalTime.of(7, 8, 9), zone)

        // 本地 2020-01-01 07:08:09（UTC+8）
        assertEquals(Instant.parse("2019-12-31T23:08:09Z"), result["a"])
        assertNull(result["b"])
    }

    @Test
    fun shiftFromEarliestKeepsIntervals() {
        val items = listOf(
            item("a", Instant.parse("2020-01-01T00:00:00Z")),
            item("b", Instant.parse("2020-01-01T02:30:00Z")),
        )
        val target = Instant.parse("2021-05-06T07:00:00Z")

        val result = TimeTransform.shiftFromEarliest(items, target)

        assertEquals(target, result["a"])
        assertEquals(Instant.parse("2021-05-06T09:30:00Z"), result["b"])
    }

    private fun item(name: String, metadata: Instant? = null) =
        MediaItem(
            id = name,
            displayName = "$name.jpg",
            kind = MediaKind.IMAGE,
            mimeType = "image/jpeg",
            metadataTime = metadata,
            fileModifiedTime = null,
        )
}
