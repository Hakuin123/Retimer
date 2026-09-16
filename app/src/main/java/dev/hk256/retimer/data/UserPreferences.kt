package dev.hk256.retimer.data

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import dev.hk256.retimer.core.FilenameRule
import dev.hk256.retimer.core.FilenameRuleSettings
import org.json.JSONArray
import org.json.JSONObject

/** 应用主题模式。 */
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** 应用主题颜色来源。 */
enum class AppThemeColorMode {
    SYSTEM,
    DEFAULT,
}

/** 应用语言。当前只有简体中文，先保留稳定的语言代码供后续扩展。 */
enum class AppLanguage(
    val tag: String,
    val displayName: String,
) {
    SIMPLIFIED_CHINESE("zh-CN", "简体中文"),
    ;

    companion object {
        val Default = SIMPLIFIED_CHINESE

        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: Default
    }
}

/**
 * 用户偏好的持久化。
 *
 * 选项的开关代表用户一贯的处理方式，设置页的外观选项也需要跨重启保留。
 */
class UserPreferences(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** 是否同时改文件在存储里的时间戳（需要「所有文件访问权限」）。 */
    var syncFileModifiedTime: Boolean
        get() = preferences.getBoolean(KEY_SYNC_FILE_MODIFIED_TIME, false)
        set(value) = preferences.edit { putBoolean(KEY_SYNC_FILE_MODIFIED_TIME, value) }

    /** 是否覆盖文件里已经存在的日期字段。 */
    var overwriteExistingDateFields: Boolean
        get() = preferences.getBoolean(KEY_OVERWRITE_EXISTING_DATE_FIELDS, false)
        set(value) = preferences.edit { putBoolean(KEY_OVERWRITE_EXISTING_DATE_FIELDS, value) }

    var themeMode: AppThemeMode
        get() =
            preferences.getString(KEY_THEME_MODE, null)
                ?.let { stored -> runCatching { AppThemeMode.valueOf(stored) }.getOrNull() }
                ?: AppThemeMode.SYSTEM
        set(value) = preferences.edit { putString(KEY_THEME_MODE, value.name) }

    var themeColorMode: AppThemeColorMode
        get() =
            preferences.getString(KEY_THEME_COLOR_MODE, null)
                ?.let { stored -> runCatching { AppThemeColorMode.valueOf(stored) }.getOrNull() }
                ?: AppThemeColorMode.SYSTEM
        set(value) = preferences.edit { putString(KEY_THEME_COLOR_MODE, value.name) }

    var language: AppLanguage
        get() = AppLanguage.fromTag(preferences.getString(KEY_LANGUAGE, null))
        set(value) = preferences.edit { putString(KEY_LANGUAGE, value.tag) }

    /** 是否隐藏未选中的底部导航标签；选中项标签始终保留。 */
    var hideUnselectedNavLabels: Boolean
        get() = preferences.getBoolean(KEY_HIDE_UNSELECTED_NAV_LABELS, false)
        set(value) = preferences.edit { putBoolean(KEY_HIDE_UNSELECTED_NAV_LABELS, value) }

    /** 文件名解析规则的设置：整份规则列表与关掉了哪些规则。 */
    var filenameRuleSettings: FilenameRuleSettings
        get() = decodeFilenameRuleSettings(preferences.getString(KEY_FILENAME_RULE_SETTINGS, null))
        set(value) =
            preferences.edit {
                putString(KEY_FILENAME_RULE_SETTINGS, encodeFilenameRuleSettings(value))
            }

    private companion object {
        const val PREFERENCES_NAME = "media_time_fixer"
        const val KEY_SYNC_FILE_MODIFIED_TIME = "advanced.sync_file_modified_time"
        const val KEY_OVERWRITE_EXISTING_DATE_FIELDS = "advanced.overwrite_existing_date_fields"
        const val KEY_THEME_MODE = "appearance.theme_mode"
        const val KEY_THEME_COLOR_MODE = "appearance.theme_color_mode"
        const val KEY_LANGUAGE = "appearance.language"
        const val KEY_HIDE_UNSELECTED_NAV_LABELS = "appearance.hide_unselected_nav_labels"
        const val KEY_FILENAME_RULE_SETTINGS = "filename.rule_settings"
    }
}

/*
 * 文件解析规则设置的存盘形式。
 *
 * 规则串与名称都是用户随手输的文字，用分隔符拼接很容易被里面的字符撞坏，
 * 所以存成 JSON：多一层解析开销，换来"用户输什么都不会读坏设置"。
 */
private const val JSON_RULES = "rules"
private const val JSON_DISABLED_IDS = "disabledRuleIds"
private const val JSON_ID = "id"
private const val JSON_PATTERN = "pattern"

private fun encodeFilenameRuleSettings(settings: FilenameRuleSettings): String {
    val rules =
        JSONArray().apply {
            settings.rules.forEach { rule ->
                put(
                    JSONObject()
                        .put(JSON_ID, rule.id)
                        .put(JSON_PATTERN, rule.pattern),
                )
            }
        }
    return JSONObject()
        .put(JSON_RULES, rules)
        .put(JSON_DISABLED_IDS, JSONArray(settings.disabledRuleIds.toList()))
        .toString()
}

/** 存盘内容读不出来时退回默认设置：设置读坏了不该让界面打不开。 */
private fun decodeFilenameRuleSettings(stored: String?): FilenameRuleSettings {
    if (stored == null) return FilenameRuleSettings()
    return runCatching {
        val root = JSONObject(stored)
        val rules =
            root.optJSONArray(JSON_RULES)
                ?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        val item = array.optJSONObject(index) ?: return@mapNotNull null
                        val id = item.optString(JSON_ID).takeIf(String::isNotEmpty)
                        val pattern = item.optString(JSON_PATTERN).takeIf(String::isNotEmpty)
                        if (id == null || pattern == null) {
                            null
                        } else {
                            FilenameRule(id = id, pattern = pattern)
                        }
                    }
                }.orEmpty()
        val disabledIds =
            root.optJSONArray(JSON_DISABLED_IDS)
                ?.let { array ->
                    (0 until array.length())
                        .mapNotNull { index -> array.optString(index).takeIf(String::isNotEmpty) }
                        .toSet()
                }.orEmpty()
        // 列表读成空的话退回初始规则：一份空列表看起来就像功能坏了。
        FilenameRuleSettings(
            rules = rules.ifEmpty { FilenameRule.Defaults },
            disabledRuleIds = disabledIds,
        )
    }.getOrElse { FilenameRuleSettings() }
}

/**
 * 应用级偏好状态。
 *
 * SharedPreferences 本身不是 Compose 状态；设置页写入后需要立刻驱动主题和导航栏重组，
 * 因此这里用一份可变状态作为界面读取入口，并在每次修改时同步写回持久化层。
 */
@Stable
class AppSettingsState(preferences: UserPreferences) {

    private val preferences = preferences

    var themeMode by mutableStateOf(preferences.themeMode)
        private set

    var themeColorMode by mutableStateOf(preferences.themeColorMode)
        private set

    var language by mutableStateOf(preferences.language)
        private set

    var hideUnselectedNavLabels by mutableStateOf(preferences.hideUnselectedNavLabels)
        private set

    fun updateThemeMode(value: AppThemeMode) {
        themeMode = value
        preferences.themeMode = value
    }

    fun updateThemeColorMode(value: AppThemeColorMode) {
        themeColorMode = value
        preferences.themeColorMode = value
    }

    fun updateLanguage(value: AppLanguage) {
        language = value
        preferences.language = value
    }

    fun updateHideUnselectedNavLabels(value: Boolean) {
        hideUnselectedNavLabels = value
        preferences.hideUnselectedNavLabels = value
    }
}

/**
 * 文件名解析规则的设置状态。
 *
 * 与 [AppSettingsState] 同一套做法：SharedPreferences 不是 Compose 状态，
 * 这里用一份可变状态作为界面读取入口，并在每次修改时同步写回持久化层。
 */
@Stable
class FilenameRuleState(preferences: UserPreferences) {

    private val preferences = preferences

    var settings by mutableStateOf(preferences.filenameRuleSettings)
        private set

    /** 打开或关闭一条规则。 */
    fun setEnabled(
        rule: FilenameRule,
        enabled: Boolean,
    ) {
        update(settings.setEnabled(rule, enabled))
    }

    /** 新增或覆盖一条规则；初始规则改过之后也走这里。 */
    fun save(rule: FilenameRule) {
        update(settings.upsert(rule))
    }

    /** 删除一条规则；初始规则和用户加的规则一样能删。 */
    fun remove(rule: FilenameRule) {
        update(settings.remove(rule))
    }

    private fun update(value: FilenameRuleSettings) {
        settings = value
        preferences.filenameRuleSettings = value
    }
}
