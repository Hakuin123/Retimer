package dev.hk256.retimer.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

/** 规则串里的一个日期字段。 */
enum class FilenamePatternField(
    val displayName: String,
) {
    YEAR("年"),
    MONTH("月"),
    DAY("日"),
    HOUR("时"),
    MINUTE("分"),
    SECOND("秒"),
    UNIX_SECONDS("时间戳（秒）"),
    UNIX_MILLIS("时间戳（毫秒）"),
    ;

    /** 时间戳字段：自己就能确定一个瞬间，不和年月日混用。 */
    val isUnix: Boolean get() = this == UNIX_SECONDS || this == UNIX_MILLIS

    val includesTime: Boolean
        get() =
            when (this) {
                YEAR, MONTH, DAY -> false
                HOUR, MINUTE, SECOND, UNIX_SECONDS, UNIX_MILLIS -> true
            }
}

/** 可以插进规则串里的记号：[token] 是要写进规则串的文字，[label] 是界面上给用户看的说明。 */
data class FilenamePatternToken(
    val token: String,
    val label: String,
)

/** 规则串里的一段：要么是原样匹配的文字，要么是一个日期字段。 */
sealed interface PatternSegment {
    /** 原样匹配的文字（含引号里的内容）。 */
    data class Literal(val text: String) : PatternSegment

    /** 一个日期字段；[width] 是它写了几位字母（写一位时匹配一位或两位数字）。 */
    data class Field(
        val field: FilenamePatternField,
        val width: Int,
    ) : PatternSegment
}

/** 规则串的解析结果。 */
private sealed interface ParseResult {
    data class Success(val segments: List<PatternSegment>) : ParseResult

    /** 解析不过去的原因，直接显示给用户。 */
    data class Failure(val reason: String) : ParseResult
}

/** 一次匹配：解析出来的时间，以及匹配到的文本长度。 */
data class PatternMatch(
    val value: Instant,
    val matchedTextLength: Int,
)

/**
 * 文件名解析规则的写法。
 *
 * 规则串由字面量与字段记号拼成：
 * - 字段：`yyyy` / `yy` 年、`MM` 月、`dd` 日、`HH` 时、`mm` 分、`ss` 秒；月日时分秒也可以只写一个字母
 *   （`M`、`d`、`H`、`m`、`s`），此时匹配一位或两位数字
 * - 时间戳：`{unix}` 秒、`{unix_ms}` 毫秒；时间戳自己能定位到一个瞬间，因此不能和年月日字段混用
 * - 普通文字写在单引号里（`'IMG'_yyyyMMdd`），单引号外的字母都会被当成字段记号
 *
 * 一条规则只对应一种写法，因此也只有一个示例：分隔符换一种写法就是另一条规则（见 [FilenameRule.Defaults]）。
 * 年月日必须同时出现（只有年份没法确定是哪一天），时分秒可以省略，省略时按 00:00:00 处理。
 */
object FilenamePattern {
    /** 界面上可以插入的记号，顺序即界面上的排列顺序。 */
    val tokens: List<FilenamePatternToken> =
        listOf(
            FilenamePatternToken("yyyy", "年"),
            FilenamePatternToken("yy", "年（两位）"),
            FilenamePatternToken("MM", "月"),
            FilenamePatternToken("dd", "日"),
            FilenamePatternToken("HH", "时"),
            FilenamePatternToken("mm", "分"),
            FilenamePatternToken("ss", "秒"),
            FilenamePatternToken("{unix}", "时间戳（秒）"),
            FilenamePatternToken("{unix_ms}", "时间戳（毫秒）"),
        )

    /** 检查规则串能不能用；不能用时返回原因，能用时返回 null。 */
    fun validate(pattern: String): String? {
        if (pattern.isEmpty()) return "规则不能为空"
        val segments =
            when (val parsed = parseSegments(pattern)) {
                is ParseResult.Failure -> return parsed.reason
                is ParseResult.Success -> parsed.segments
            }
        val fields = segments.filterIsInstance<PatternSegment.Field>().map { it.field }
        if (fields.isEmpty()) return "规则里至少需要一个日期字段或时间戳"

        val unixFields = fields.filter { it.isUnix }
        if (unixFields.size > 1) return "时间戳记号只能出现一个"
        if (unixFields.isNotEmpty() && fields.any { !it.isUnix }) return "时间戳不能和其他日期字段混用"

        val repeated =
            fields
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
        if (repeated.isNotEmpty()) {
            return repeated.entries.joinToString("、") { (field, count) ->
                "${field.displayName}记号出现了 $count 次"
            }
        }

        if (unixFields.isEmpty()) {
            val missing =
                listOf(FilenamePatternField.YEAR, FilenamePatternField.MONTH, FilenamePatternField.DAY)
                    .filterNot { it in fields }
            if (missing.isNotEmpty()) {
                return "需要同时包含年、月、日（缺少${missing.joinToString("、") { it.displayName }}），" +
                    "或改用 Unix 时间戳"
            }
        }
        return null
    }

    /**
     * 规则串的示例：用它来表示 [instant]，让用户看清这种写法长什么样。
     * 规则串不合法时返回 null。
     */
    fun preview(
        pattern: String,
        instant: Instant,
        zoneId: ZoneId,
    ): String? = compileOrNull(pattern)?.sample(instant, zoneId)

    /** 规则串里是否带时分秒（时间戳也算）。规则串不合法时按不带时间处理。 */
    fun includesTime(pattern: String): Boolean = compileOrNull(pattern)?.includesTime ?: false

    /**
     * 编译规则串；不合法时返回 null。
     *
     * 结果按规则串缓存：分析一批媒体时同一条规则会被每个文件用一遍，每次都重新解析、重建正则太浪费。
     * 缓存只增不减——规则串总共就那么几条，而且解析规则是纯函数，不存在过期问题。
     */
    internal fun compileOrNull(pattern: String): CompiledFilenamePattern? {
        compiledPatterns[pattern]?.let { return it }
        if (pattern in invalidPatterns) return null
        val result = compile(pattern)
        if (result == null) {
            invalidPatterns += pattern
        } else {
            compiledPatterns[pattern] = result
        }
        return result
    }

    private val compiledPatterns = ConcurrentHashMap<String, CompiledFilenamePattern>()

    private val invalidPatterns = ConcurrentHashMap.newKeySet<String>()

    private fun compile(pattern: String): CompiledFilenamePattern? {
        if (validate(pattern) != null) return null
        val segments =
            when (val parsed = parseSegments(pattern)) {
                is ParseResult.Failure -> return null
                is ParseResult.Success -> parsed.segments
            }
        val source =
            buildString {
                // 匹配到的文字要自成一段：前后不能还挨着数字，否则 `120240131` 里的半截数字也会被认成日期。
                if (segments.first().startsWithDigit()) append("(?<!\\d)")
                segments.forEach { segment ->
                    when (segment) {
                        is PatternSegment.Literal -> append(Regex.escape(segment.text))
                        is PatternSegment.Field -> append(segment.regex())
                    }
                }
                if (segments.last().endsWithDigit()) append("(?!\\d)")
            }
        return CompiledFilenamePattern(
            segments = segments,
            regex = Regex(source),
            includesTime =
                segments.filterIsInstance<PatternSegment.Field>().any { it.field.includesTime },
        )
    }
}

/** 编译好的规则串：正则负责找位置，字段顺序负责把数字换算成时间。 */
internal class CompiledFilenamePattern(
    private val segments: List<PatternSegment>,
    private val regex: Regex,
    val includesTime: Boolean,
) {
    /** 在文件名里找这段规则；找不到，或数字不是合法日期时返回 null。 */
    fun find(
        filename: String,
        zoneId: ZoneId,
    ): PatternMatch? {
        val match = regex.find(filename) ?: return null
        var year: Int? = null
        var month: Int? = null
        var day: Int? = null
        var hour = 0
        var minute = 0
        var second = 0
        var unixSeconds: Long? = null
        var unixMillis: Long? = null

        var groupIndex = 1
        for (segment in segments) {
            if (segment !is PatternSegment.Field) continue
            val text = match.groupValues[groupIndex++]
            when (segment.field) {
                FilenamePatternField.YEAR -> {
                    val value = text.toIntOrNull() ?: return null
                    // 两位年按 2000 年之后算：文件名里的年份不会早于 2000 年。
                    year = if (segment.width == 2) 2000 + value else value
                }
                FilenamePatternField.MONTH -> month = text.toIntOrNull() ?: return null
                FilenamePatternField.DAY -> day = text.toIntOrNull() ?: return null
                FilenamePatternField.HOUR -> hour = text.toIntOrNull() ?: return null
                FilenamePatternField.MINUTE -> minute = text.toIntOrNull() ?: return null
                FilenamePatternField.SECOND -> second = text.toIntOrNull() ?: return null
                FilenamePatternField.UNIX_SECONDS -> unixSeconds = text.toLongOrNull() ?: return null
                FilenamePatternField.UNIX_MILLIS -> unixMillis = text.toLongOrNull() ?: return null
            }
        }

        val value =
            runCatching {
                when {
                    unixSeconds != null -> Instant.ofEpochSecond(unixSeconds)
                    unixMillis != null -> Instant.ofEpochMilli(unixMillis)
                    else ->
                        LocalDateTime.of(
                            year ?: return@runCatching null,
                            month ?: return@runCatching null,
                            day ?: return@runCatching null,
                            hour,
                            minute,
                            second,
                        ).atZone(zoneId).toInstant()
                }
            }.getOrNull() ?: return null
        return PatternMatch(value, match.value.length)
    }

    /** 把 [instant] 按这段规则写成文字，用作界面上的示例。 */
    fun sample(
        instant: Instant,
        zoneId: ZoneId,
    ): String {
        val local = instant.atZone(zoneId)
        return buildString {
            segments.forEach { segment ->
                when (segment) {
                    is PatternSegment.Literal -> append(segment.text)
                    is PatternSegment.Field -> append(segment.sampleText(local, instant))
                }
            }
        }
    }
}

/** 一个字段按 [width] 位写出来的示例文字。 */
private fun PatternSegment.Field.sampleText(
    local: ZonedDateTime,
    instant: Instant,
): String =
    when (field) {
        FilenamePatternField.YEAR ->
            if (width == 2) (local.year % 100).pad(width) else local.year.toString()
        FilenamePatternField.MONTH -> local.monthValue.pad(width)
        FilenamePatternField.DAY -> local.dayOfMonth.pad(width)
        FilenamePatternField.HOUR -> local.hour.pad(width)
        FilenamePatternField.MINUTE -> local.minute.pad(width)
        FilenamePatternField.SECOND -> local.second.pad(width)
        FilenamePatternField.UNIX_SECONDS -> instant.epochSecond.toString()
        FilenamePatternField.UNIX_MILLIS -> instant.toEpochMilli().toString()
    }

private fun Int.pad(width: Int): String = toString().padStart(width, '0')

/** 字段在正则里对应的捕获组。 */
private fun PatternSegment.Field.regex(): String =
    when {
        field == FilenamePatternField.YEAR -> if (width == 4) "(\\d{4})" else "(\\d{2})"
        field == FilenamePatternField.UNIX_SECONDS -> "(\\d{10})"
        field == FilenamePatternField.UNIX_MILLIS -> "(\\d{13})"
        width == 1 -> "(\\d{1,2})"
        else -> "(\\d{2})"
    }

/** 这一段是否一定以数字开头（决定要不要加「前面不能是数字」的断言）。 */
private fun PatternSegment.startsWithDigit(): Boolean =
    when (this) {
        is PatternSegment.Field -> true
        is PatternSegment.Literal -> text.firstOrNull()?.isDigit() == true
    }

/** 这一段是否一定以数字结尾（决定要不要加「后面不能是数字」的断言）。 */
private fun PatternSegment.endsWithDigit(): Boolean =
    when (this) {
        is PatternSegment.Field -> true
        is PatternSegment.Literal -> text.lastOrNull()?.isDigit() == true
    }

/**
 * 把规则串拆成一段段。
 *
 * 单引号里是普通文字；`{...}` 是时间戳记号；连续的同一个字母是字段记号（`yyyy`、`MM`）；
 * 其余字符按字面量处理。
 */
private fun parseSegments(pattern: String): ParseResult {
    val segments = mutableListOf<PatternSegment>()
    val literal = StringBuilder()

    fun flushLiteral() {
        if (literal.isNotEmpty()) {
            segments += PatternSegment.Literal(literal.toString())
            literal.clear()
        }
    }

    var index = 0
    while (index < pattern.length) {
        val char = pattern[index]
        when {
            char == '\'' -> {
                val end = pattern.indexOf('\'', startIndex = index + 1)
                if (end < 0) return ParseResult.Failure("单引号没有闭合")
                literal.append(pattern, index + 1, end)
                index = end + 1
            }
            char == '{' -> {
                val end = pattern.indexOf('}', startIndex = index + 1)
                if (end < 0) return ParseResult.Failure("时间戳记号缺少右花括号")
                val token = pattern.substring(index, end + 1)
                val field =
                    when (token) {
                        "{unix}" -> FilenamePatternField.UNIX_SECONDS
                        "{unix_ms}" -> FilenamePatternField.UNIX_MILLIS
                        else -> return ParseResult.Failure("不支持的时间戳记号「$token」")
                    }
                flushLiteral()
                segments +=
                    PatternSegment.Field(
                        field = field,
                        width = if (field == FilenamePatternField.UNIX_MILLIS) 13 else 10,
                    )
                index = end + 1
            }
            char.isAsciiLetter() -> {
                val start = index
                while (index < pattern.length && pattern[index] == char) index++
                val field = fieldFor(char, width = index - start)
                if (field == null) {
                    return ParseResult.Failure(fieldError(char, pattern, start, index))
                }
                flushLiteral()
                segments += PatternSegment.Field(field, width = index - start)
            }
            else -> {
                literal.append(char)
                index++
            }
        }
    }
    flushLiteral()
    return ParseResult.Success(segments)
}

/** 同一个字母写了几位对应哪个字段；写了不支持的位数时返回 null。 */
private fun fieldFor(
    letter: Char,
    width: Int,
): FilenamePatternField? =
    when (letter) {
        // 年只能是两位或四位：一位的年份没法判断世纪，多写的字母也不是记号。
        'y' -> if (width == 4 || width == 2) FilenamePatternField.YEAR else null
        'M' -> FilenamePatternField.MONTH.takeIf { width <= 2 }
        'd' -> FilenamePatternField.DAY.takeIf { width <= 2 }
        'H' -> FilenamePatternField.HOUR.takeIf { width <= 2 }
        'm' -> FilenamePatternField.MINUTE.takeIf { width <= 2 }
        's' -> FilenamePatternField.SECOND.takeIf { width <= 2 }
        else -> null
    }

// 字段记号的报错文案。
//
// 报错时把连着的一串字母一起说出来（`IMG` 而不是 `I`），用户才能对上是哪一段；
// 已知字段的字母写多了（`MMM`）和完全不认识的字母（`IMG`）要分开讲，改法不一样。
private fun fieldError(
    letter: Char,
    pattern: String,
    start: Int,
    runEnd: Int,
): String {
    if (letter == 'y' && runEnd - start != 2 && runEnd - start != 4) {
        return "年份记号只能写成 yy 或 yyyy"
    }
    var end = runEnd
    while (end < pattern.length && pattern[end].isAsciiLetter()) end++
    val letters = pattern.substring(start, end)
    return when (letter) {
        'M', 'd', 'H', 'm', 's' -> "「$letters」不是有效的记号；月、日、时、分、秒最多用两个字母"
        else -> "不支持的记号「$letters」；如果是普通文字，请在两端加注单引号"
    }
}

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
