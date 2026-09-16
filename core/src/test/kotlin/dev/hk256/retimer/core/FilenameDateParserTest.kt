package dev.hk256.retimer.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilenameDateParserTest {
    private val zoneId = ZoneId.of("UTC")
    private val parser = FilenameDateParser(zoneId)

    @Test
    fun parsesDateAndTimeWithSeparators() {
        assertEquals(
            Instant.parse("2024-01-31T23:59:59Z"),
            parser.findBestMatch("IMG_2024-01-31_23-59-59.jpg")?.value,
        )
    }

    @Test
    fun parsesCompactDate() {
        assertEquals(
            Instant.parse("2024-01-31T00:00:00Z"),
            parser.findBestMatch("20240131_photo.png")?.value,
        )
    }

    @Test
    fun prefersTimeOverDateOnlyMatch() {
        val result = parser.findBestMatch("IMG_20240131_235959.jpg")

        assertEquals("yyyyMMdd_HHmmss", result?.rule?.pattern)
        assertEquals(Instant.parse("2024-01-31T23:59:59Z"), result?.value)
    }

    @Test
    fun parsesUnixMilliseconds() {
        val result =
            parser.findBestMatch(
                "export_1704067200000.jpg",
                listOf(FilenameRule(id = "{unix_ms}", pattern = "{unix_ms}")),
            )

        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), result?.value)
    }

    @Test
    fun rejectsInvalidDate() {
        assertNull(parser.findBestMatch("IMG_2024-02-31.jpg")?.value)
    }

    @Test
    fun defaultRulesRecognizeTheirOwnExamples() {
        // 列表上给用户看的"示例"必须真的能被这条规则解析出来：示例和匹配是同一条规则的两个方向。
        val sample = Instant.parse("2024-01-31T13:45:59Z")
        val expected = LocalDateTime.of(2024, 1, 31, 13, 45, 59).atZone(zoneId).toInstant()
        val timedRules = FilenameRule.Defaults.filter { FilenamePattern.includesTime(it.pattern) }

        assertTrue(timedRules.isNotEmpty(), "初始规则里至少要有一条带时间的规则")
        timedRules.forEach { rule ->
            val example =
                checkNotNull(FilenamePattern.preview(rule.pattern, sample, zoneId)) {
                    "规则没有示例：${rule.pattern}"
                }
            assertEquals(expected, parser.findBestMatch("IMG_$example.jpg")?.value, "规则：${rule.pattern}")
        }
    }

    @Test
    fun rulesWithoutCharacterClassMatchStrictly() {
        // 规则语言没有通配：写的是什么就只认什么，分隔符换成空格不再匹配。
        val rule = FilenameRule("user", "yyyyMMdd_HHmmss")

        assertEquals(
            Instant.parse("2024-01-31T23:59:59Z"),
            parser.findBestMatch("IMG_20240131_235959.jpg", listOf(rule))?.value,
        )
        assertNull(parser.findBestMatch("IMG_20240131 235959.jpg", listOf(rule)))
    }

    @Test
    fun parsesUserRule() {
        val result =
            parser.findBestMatch(
                "IMG_2024.01.31_edit.jpg",
                listOf(FilenameRule("user", "yyyy.MM.dd")),
            )

        assertEquals(Instant.parse("2024-01-31T00:00:00Z"), result?.value)
    }

    @Test
    fun parsesUserRuleWithQuotedLiteralAndTimestamp() {
        val result =
            parser.findBestMatch(
                "shot_1704067200.jpg",
                listOf(FilenameRule("user", "'shot'_{unix}")),
            )

        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), result?.value)
    }

    @Test
    fun parsesTwoDigitYearUserRule() {
        val result =
            parser.findBestMatch(
                "IMG_240131_235959.jpg",
                listOf(FilenameRule("user", "yyMMdd_HHmmss")),
            )

        assertEquals(Instant.parse("2024-01-31T23:59:59Z"), result?.value)
    }

    @Test
    fun ignoresInvalidUserRule() {
        // 只有年确定不了是哪一天，这种规则串既不能用，也不会参与匹配。
        val result =
            parser.findBestMatch(
                "IMG_20240131.jpg",
                listOf(FilenameRule("user", "yyyy")),
            )

        assertNull(result)
    }

    @Test
    fun onlyEnabledRulesMatch() {
        val rule = FilenameRule("user", "yyyy.MM.dd")
        val filename = "IMG_2024.01.31_edit.jpg"

        assertNull(parser.findBestMatch(filename, FilenameRule.Defaults))
        assertEquals(
            Instant.parse("2024-01-31T00:00:00Z"),
            parser.findBestMatch(filename, listOf(rule))?.value,
        )
    }
}
