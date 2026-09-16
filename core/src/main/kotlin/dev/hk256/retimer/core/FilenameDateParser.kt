package dev.hk256.retimer.core

import java.time.Instant
import java.time.ZoneId

data class FilenameDateMatch(
    val value: Instant,
    val rule: FilenameRule,
    val matchedTextLength: Int,
)

/**
 * 按给定的规则在文件名里找日期。
 *
 * 规则串先编译成正则，再逐条去文件名里找；多条规则都能匹配时，先看是否带时间，
 * 再看匹配到的文字有多长，最后按列表顺序取靠前的那个。
 */
class FilenameDateParser(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun findBestMatch(
        filename: String,
        enabledRules: List<FilenameRule> = FilenameRule.Defaults,
    ): FilenameDateMatch? =
        enabledRules
            .mapIndexedNotNull { index, rule -> match(filename, rule)?.let { index to it } }
            .maxWithOrNull(
                compareBy<Pair<Int, FilenameDateMatch>> { it.second.rule.includesTime }
                    .thenBy { it.second.matchedTextLength }
                    .thenByDescending { it.first },
            )
            ?.second

    /** 一条规则匹配一次；规则串不合法（或在这条文件名里找不到）时返回 null。 */
    private fun match(
        filename: String,
        rule: FilenameRule,
    ): FilenameDateMatch? {
        val compiled = FilenamePattern.compileOrNull(rule.pattern) ?: return null
        val match = compiled.find(filename, zoneId) ?: return null
        return FilenameDateMatch(match.value, rule, match.matchedTextLength)
    }
}
