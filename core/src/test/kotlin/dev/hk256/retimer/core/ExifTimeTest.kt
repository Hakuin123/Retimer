package dev.hk256.retimer.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExifTimeTest {
    private val fallback = ZoneId.of("Asia/Shanghai")

    @Test
    fun offsetTagsPinTheExactInstant() {
        // 东京拍的 13:45:59（UTC+9）应该是 04:45:59Z，与解释用的时区无关。
        assertEquals(
            Instant.parse("2024-01-31T04:45:59Z"),
            ExifTime.parse("2024:01:31 13:45:59", ZoneId.of("UTC"), "+09:00"),
        )
    }

    @Test
    fun utcOffsetWrittenAsZIsAccepted() {
        // 旧版本写入端会用 "Z" 表示 UTC，读回来必须还是同一时刻。
        assertEquals(
            Instant.parse("2024-01-31T13:45:59Z"),
            ExifTime.parse("2024:01:31 13:45:59", fallback, "Z"),
        )
    }

    @Test
    fun utcOffsetWrittenAsPlusZeroIsAccepted() {
        // 写入端现在用 "+00:00" 表示 UTC（EXIF 规范的 ±HH:MM 写法）。
        assertEquals(
            Instant.parse("2024-01-31T13:45:59Z"),
            ExifTime.parse("2024:01:31 13:45:59", fallback, "+00:00"),
        )
    }

    @Test
    fun missingOffsetFallsBackToGivenZone() {
        assertEquals(
            Instant.parse("2024-01-31T05:45:59Z"),
            ExifTime.parse("2024:01:31 13:45:59", fallback),
        )
    }

    @Test
    fun blankOffsetFallsBackToGivenZone() {
        assertEquals(
            Instant.parse("2024-01-31T05:45:59Z"),
            ExifTime.parse("2024:01:31 13:45:59", fallback, "  "),
        )
    }

    @Test
    fun unparsableOffsetFallsBackToGivenZone() {
        assertEquals(
            Instant.parse("2024-01-31T05:45:59Z"),
            ExifTime.parse("2024:01:31 13:45:59", fallback, "+99:00"),
        )
    }

    @Test
    fun resultDoesNotDependOnFallbackWhenOffsetIsPresent() {
        val value = "2024:01:31 13:45:59"
        assertEquals(
            ExifTime.parse(value, ZoneId.of("UTC"), "+09:00"),
            ExifTime.parse(value, fallback, "+09:00"),
        )
    }

    @Test
    fun unparsableValueReturnsNull() {
        assertNull(ExifTime.parse("2024-01-31 13:45:59", fallback))
        assertNull(ExifTime.parse("not a date", fallback))
        assertNull(ExifTime.parse("2024:13:31 13:45:59", fallback))
    }
}
