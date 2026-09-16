package dev.hk256.retimer.core

/**
 * 一条文件名解析规则。
 *
 * 列表里只有这一种东西：预制规则只是预先放好的几条，和用户自己加的规则没有区别，
 * 一样能改、能删、能关。[id] 是它的身份（关掉了哪几条按 id 记），[pattern] 是它认的写法
 * （写法见 [FilenamePattern]），同时也是列表上显示的那行规则。
 */
data class FilenameRule(
    val id: String,
    val pattern: String,
) {
    val includesTime: Boolean get() = FilenamePattern.includesTime(pattern)

    companion object {
        /**
         * 初次打开时的列表内容：这几条只是"先放进去"的规则，之后整份列表都由用户维护。
         *
         * 一条规则只对应一种写法、也只有一个示例，所以"分隔符可以写成 `-`、`_` 或空格"这种事不靠
         * 规则语言本身去表达，而是把常见的写法一条条列出来——相机文件名里常见的那几种都在这里
         */
        val Defaults: List<FilenameRule> =
            listOf(
                // 日期部分不带分隔符的写法
                defaultRule("yyyyMMddHHmmss"),
                defaultRule("yyyyMMdd_HHmmss"),
                defaultRule("yyyyMMdd-HHmmss"),
                // 日期部分带分隔符的写法
                defaultRule("yyyy-MM-dd_HH-mm-ss"),
                defaultRule("yyyy-MM-dd-HH-mm-ss"),
                defaultRule("yyyy_MM_dd_HHmmss"),
                defaultRule("yyyy_MM_dd_HH_mm_ss"),
                // 只有日期，以及两种时间戳
                defaultRule("yyyyMMdd"),
                defaultRule("{unix}"),
                defaultRule("{unix_ms}"),
            )

        /** 初始规则的 id 就用写法本身：初始规则之间不会重复，写法本身就是最直观的标识。 */
        private fun defaultRule(pattern: String): FilenameRule = FilenameRule(id = pattern, pattern = pattern)
    }
}

/**
 * 文件名解析规则的设置：整份规则列表 + 关掉了哪些规则。
 *
 * 列表顺序就是界面上的顺序：初始规则在前，之后是用户按添加顺序排下来的。
 */
data class FilenameRuleSettings(
    val rules: List<FilenameRule> = FilenameRule.Defaults,
    val disabledRuleIds: Set<String> = emptySet(),
) {
    val enabledRules: List<FilenameRule> get() = rules.filter(::isEnabled)

    val enabledCount: Int get() = rules.count(::isEnabled)

    fun isEnabled(rule: FilenameRule): Boolean = rule.id !in disabledRuleIds

    /** 打开或关闭一条规则。 */
    fun setEnabled(
        rule: FilenameRule,
        enabled: Boolean,
    ): FilenameRuleSettings =
        copy(
            disabledRuleIds =
                if (enabled) disabledRuleIds - rule.id else disabledRuleIds + rule.id,
        )

    /**
     * 新增或覆盖一条规则（同一个 id 只留一份）。
     *
     * 规则挪到列表末尾：刚编辑过的排在最后，用户能直接看到改动后的样子。
     * 启用状态不动——编辑一条已经关掉的规则，不应该顺手把它打开。
     */
    fun upsert(rule: FilenameRule): FilenameRuleSettings =
        copy(rules = rules.filterNot { it.id == rule.id } + rule)

    /** 删除一条规则；初始规则和自定义规则一样能删。 */
    fun remove(rule: FilenameRule): FilenameRuleSettings =
        copy(
            rules = rules.filterNot { it.id == rule.id },
            disabledRuleIds = disabledRuleIds - rule.id,
        )

    /** [pattern] 是否已经被别的规则用了；[excludeId] 是正在编辑的那条。 */
    fun patternExists(
        pattern: String,
        excludeId: String? = null,
    ): Boolean = rules.any { it.pattern == pattern && it.id != excludeId }
}
