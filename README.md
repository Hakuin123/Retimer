# Retimer 

[![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-11%2B-green.svg)](https://developer.android.com/)

---

Retimer 是一款用于修复媒体日期和文件时间戳的开源工具，拯救你混乱的相册排序。目前支持 Android 平台。

> [!IMPORTANT]
> - 本项目仍处于早期开发中，项目主要代码由 DeepSeek V4.1 Flash 完成，**请务必在使用本项目前做好数据备份！**
> - 目前仅支持常见图片格式，对视频格式的支持将在未来加入

## 功能

- 自动解析：支持三种方式：
    - 元数据：用 `ExifInterface` 读取 EXIF 信息（优先级：`DateTimeOriginal`→`DateTimeDigitized`→`DateTime`）
    - 文件名：按规则正则在文件名中匹配时间，支持自定义规则
    - 修改时间：文件修改时间
- 手动编辑时间：可以逐项设置时间，也可按需统一设置
- 批量元数据修复：一键将日期写入图片的 Exif 信息和视频的系统属性中
- 修改结果实时同步至系统相册 MediaStore，确保第三方相册应用正确排序

## 技术架构

- Kotlin + Jetpack Compose
- 模块化设计：
    - `:core`：纯 Kotlin 逻辑层，包含与平台无关的媒体模型、文件名日期解析、时间转换和单元测试
    - `:app`：Android 表现层，处理 Android 专用的元数据和 MediaStore 实现
- 设计语言：Material 3 Expressive，支持动态色彩

### 一张照片的处理流程

1. 选入：通过相册选择器/SAF 选中后，从媒体库读取信息（`MediaItem`），反查出可写的媒体条目地址
2. 按所选来源生成候选时间
3. 编辑：`TimeTransform` 算出目标 `Instant`，可以手动编辑覆盖
4. 修复（写入）：`createWriteRequest` 获取批量写入授权 → 写入 EXIF 信息（`DateTimeOriginal`、`DateTimeDigitized` 和 `DateTime`）→ 回读校验 → 尝试更新媒体库 `DATE_TAKEN` 并轮询 1.5 秒等其异步跟上 → 可选用真实路径 `setLastModified` 同步文件修改时间
5. 结果：每项返回 成功/部分成功/跳过/失败，失败原因逐条说明并汇总

## 构建

- JDK: 17+
- Android SDK: API 30 (Android 11) or higher
- Build System: Gradle 8.4+

```bash
# 测试 core 模块逻辑
./gradlew :core:test

# 构建 Debug APK
./gradlew :app:assembleDebug
```

对于 Windows，请使用 `gradlew.bat`。Debug APK 会生成在 `app/build/outputs/apk/debug/` 中。

## 许可证

本项目采用 [GNU General Public License v3.0 or later](LICENSE) 授权。
