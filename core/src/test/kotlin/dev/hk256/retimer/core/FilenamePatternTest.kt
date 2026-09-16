package dev.hk256.retimer.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilenamePatternTest {
    private val zoneId = ZoneId.of("UTC")
    private val sample = Instant.parse("2024-01-31T23:59:59Z")

    @Test
    fun acceptsDateAndTimePattern() {
        assertNull(FilenamePattern.validate("yyyyMMdd_HHmmss"))
        assertTrue(FilenamePattern.includesTime("yyyyMMdd_HHmmss"))
    }

    @Test
    fun acceptsSingleLetterFieldsAndQuotedLiteral() {
        assertNull(FilenamePattern.validate("'IMG'_yyyy-M-d_HH-mm-ss"))
    }

    @Test
    fun acceptsTimestampWithLiteral() {
        assertNull(FilenamePattern.validate("'shot'_{unix}"))
    }

    @Test
    fun treatsBracketsAndQuestionMarkAsPlainText() {
        // 规则语言里没有正则，也没有通配：这些字符就是普通文字，一条规则只认一种写法。
        assertEquals("2024[01]31", FilenamePattern.preview("yyyy[MM]dd", sample, zoneId))
        assertEquals("2024?01?31", FilenamePattern.preview("yyyy?MM?dd", sample, zoneId))
    }

    @Test
    fun rejectsEmptyPattern() {
        assertNotNull(FilenamePattern.validate(""))
    }

    @Test
    fun rejectsUnquotedTextLetters() {
        assertNotNull(FilenamePattern.validate("IMG_yyyyMMdd"))
    }

    @Test
    fun rejectsUnterminatedQuote() {
        assertNotNull(FilenamePattern.validate("'IMG_yyyyMMdd"))
    }

    @Test
    fun rejectsUnsupportedTimestampToken() {
        assertNotNull(FilenamePattern.validate("{unix_s}"))
    }

    @Test
    fun rejectsRepeatedField() {
        assertNotNull(FilenamePattern.validate("yyyyMMdd_yyyyMMdd"))
    }

    @Test
    fun rejectsDateWithoutAllFields() {
        assertNotNull(FilenamePattern.validate("yyyy-MM"))
    }

    @Test
    fun rejectsTimestampMixedWithDate() {
        assertNotNull(FilenamePattern.validate("yyyyMMdd_{unix}"))
    }

    @Test
    fun previewsDatePattern() {
        assertEquals("20240131_235959", FilenamePattern.preview("yyyyMMdd_HHmmss", sample, zoneId))
        assertEquals("2024-1-31", FilenamePattern.preview("yyyy-M-d", sample, zoneId))
    }

    @Test
    fun previewsTimestampPattern() {
        assertEquals(
            "1704067200",
            FilenamePattern.preview("{unix}", Instant.parse("2024-01-01T00:00:00Z"), zoneId),
        )
    }

    @Test
    fun previewReturnsNullForInvalidPattern() {
        assertNull(FilenamePattern.preview("hhmmss", sample, zoneId))
    }

    @Test
    fun settingsTracksEnabledRules() {
        val rule = FilenameRule("user", "yyyy.MM.dd")
        val settings = FilenameRuleSettings().upsert(rule)

        assertEquals(FilenameRule.Defaults.size + 1, settings.rules.size)
        assertEquals(rule, settings.rules.last())
        assertTrue(settings.isEnabled(rule))
        assertEquals(FilenameRule.Defaults.size + 1, settings.enabledCount)
        assertTrue(settings.patternExists("yyyy.MM.dd", excludeId = null))
        assertFalse(settings.patternExists("yyyy.MM.dd", excludeId = "user"))
        assertTrue(settings.patternExists("yyyyMMdd"))

        val disabled = settings.setEnabled(rule, false)
        assertFalse(disabled.isEnabled(rule))
        assertEquals(FilenameRule.Defaults.size, disabled.enabledCount)
        assertEquals(setOf(rule.id), disabled.disabledRuleIds)

        // 初始规则和用户加的规则一样能删。
        assertEquals(FilenameRule.Defaults.size, settings.remove(rule).rules.size)
        assertEquals(settings.rules.size - 1, settings.remove(FilenameRule.Defaults.first()).rules.size)
    }
}
