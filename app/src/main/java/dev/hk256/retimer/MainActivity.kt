package dev.hk256.retimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.hk256.retimer.data.AppSettingsState
import dev.hk256.retimer.data.FilenameRuleState
import dev.hk256.retimer.data.UserPreferences
import dev.hk256.retimer.ui.MediaTimeFixerApp
import dev.hk256.retimer.ui.theme.MediaTimeFixerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 背景延伸到状态栏与手势导航区域，界面按系统内边距留出空间。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // 一份偏好设置同时喂给主题与文件名规则：两处都写同一个文件，不要开两份。
            val preferences = remember { UserPreferences(applicationContext) }
            val settings = remember { AppSettingsState(preferences) }
            val filenameRules = remember { FilenameRuleState(preferences) }
            MediaTimeFixerTheme(
                themeMode = settings.themeMode,
                themeColorMode = settings.themeColorMode,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    MediaTimeFixerApp(
                        settings = settings,
                        filenameRules = filenameRules,
                    )
                }
            }
        }
    }
}
