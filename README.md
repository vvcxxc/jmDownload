# JM 下载 (Android)

一个极简的安卓 JMComic 下载 App：输入车号 → 排队 → 逐个下载 → **一本一个 zip** 存到你选的目录。纯手机、直连 JM、流量可用，不依赖电脑。

## 功能

- 输入框输车号（默认前缀 `JM`），也能直接粘贴带 `jm`/空格/链接的文本，自动识别车号。
- 选择保存位置（系统文件夹选择器，权限持久化）。
- 任务列表：先记录再**逐个串行下载**（前台服务，退到后台/锁屏也继续，流量可用）。
- 每本下完打包成 `JM{车号}-{标题}.zip` 存到所选目录。失败可重试、可删除。
- **下载重复校验**：输入 JM 号码后自动检查列表中是否有相同项目，根据状态提示或自动处理。
- **状态筛选 Tab**：支持全部、已下载、下载中、下载失败四种筛选方式。
- **本地导入**：支持从手机目录多选压缩包导入到任务列表。
- **搜索**：支持按标题/作者关键词和 JM 号搜索，可单选或批量加入下载列表。
- **阅读高亮**：列表项点击阅读后退出时，刚阅读的项会显示蓝色边框高亮。
- **阅读器**：支持翻页/条漫两种模式、底部缩略图、页码滑块、长按保存单页、记忆上次阅读页；导入的 zip 会按文件名自然排序并忽略 `__MACOSX` 等垃圾文件。
- **日志管理**：自动清除 7 天前的日志，新日志排在前面显示，日志文件大小有上限。
- **长按保存**：预览时长按图片弹出选项菜单，点击保存可将图片保存到相册。

## 技术

- Kotlin + Jetpack Compose（Material3）。`minSdk 26`，`compileSdk/targetSdk 35`。
- 下载引擎：[`io.github.jukomu:jmcomic-android-support`](https://central.sonatype.com/artifact/io.github.jukomu/jmcomic-android-support)（JM 移动 API，token 签名，无需梯子；图片下载 + 还原都由库完成，`AndroidImageProcessor` 用 Bitmap 还原）。
- 无 Room/额外持久化依赖：任务列表用 `org.json` 存到私有目录；保存目录 URI 用 SharedPreferences。
- 关键排除：`org.sejda.imageio:webp-imageio`（桌面 AWT 插件，安卓用不了）、`com.google.android:android`（老 stub）。

## 开发环境与工具链

### 环境要求

- **JDK**：Java 17 或更高版本
- **Android SDK**：建议安装最新版本
- **SDK 包**：`platform-tools`、`platforms;android-35`、`build-tools;35.0.0`
- **Gradle**：使用项目内的 `gradlew.bat`（无需手动安装）

### 环境配置

确保系统环境变量中包含：
- `JAVA_HOME`：指向 JDK 安装目录
- `ANDROID_HOME`（可选）：指向 Android SDK 安装目录

项目已配置使用腾讯镜像加速依赖下载，无需额外配置。

## 构建（无需 Android Studio）

### 一键构建

```bat
build.bat
```

该脚本会依次构建 debug 和 release 版本，并显示构建结果。

### 单独构建

构建 debug 版本：
```bat
gradlew.bat :app:assembleDebug
```

构建 release 版本：
```bat
gradlew.bat :app:assembleRelease
```

### 产物位置

- Debug APK：`app\build\outputs\apk\debug\app-debug.apk`
- Release APK：`app\build\outputs\apk\release\app-release.apk`

Release 默认启用 **R8 + 资源压缩**，APK 约 2.7 MB。jmcomic 库已通过 keep 规则保留；如果真机运行出现异常，可临时把 `app/build.gradle.kts` 里的 `isMinifyEnabled` / `isShrinkResources` 改回 `false` 排查。

### 运行单元测试

```bat
gradlew.bat :app:testDebugUnitTest
```

目前覆盖 `JmIdParser`、`FilenameSanitizer` 和 `PageOrdering`（自然排序/垃圾文件过滤）。

### 签名配置

项目已配置签名信息，确保版本更新时可以正常覆盖安装：

- **签名文件位置**：`keystore/release.jks`
- **配置文件**：`signing.properties`
- **debug 版本**：使用系统默认 debug.keystore
- **release 版本**：使用项目中的 release.jks

#### 构建 Release 版本

```bat
gradlew.bat :app:assembleRelease
```

产物：`app\build\outputs\apk\release\app-release.apk`

#### 签名信息（仅供参考）

| 项目 | 值 |
|-----|-----|
| 密钥库文件 | `keystore/release.jks` |
| 密钥库密码 | `android` |
| 密钥别名 | `release` |
| 密钥密码 | `android` |

## 运行测试 APK

如果要测试新包，推荐使用 `adb install -r` 替换安装，这样可以保留应用数据和任务列表：

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

如果必须卸载旧包，App 内部数据会被清除，重装后任务列表会丢失。

## 安装到手机

- 把 `app-debug.apk` 传到手机点击安装（需允许“未知来源”），或
- USB 连手机开 USB 调试后：`adb install -r app\build\outputs\apk\debug\app-debug.apk`

## 首次使用

1. 打开 App，允许通知。
2. 点“保存位置 → 选择”，选一个文件夹（建议在内部存储新建一个，如 `Download/jm`）。
3. 输入框输车号（如 `350234`）或粘贴链接 → “添加” → 自动开始下载。
4. 下完在所选目录能看到 `JM{车号}-{标题}.zip`。

## 常见问题与解决方案

### 1. Release 构建失败 - 签名文件找不到

**错误信息**：
```
Keystore file '...\app\keystore\release.jks' not found for signing config 'release'
```

**原因**：签名文件路径配置错误，Gradle 在 `app` 模块上下文中解析相对路径。

**解决方案**：在 `app/build.gradle.kts` 中使用 `rootProject.file()` 确保从项目根目录解析路径：
```kotlin
storeFile = rootProject.file(keystoreProperties.getProperty("storeFile", "./keystore/release.jks"))
```

### 2. 编译错误 - Composable 调用不在正确上下文

**错误信息**：
```
@Composable invocations can only happen from the context of a @Composable function
```

**原因**：`LocalContext.current` 在非 `@Composable` 上下文中使用（如点击事件 lambda 中）。

**解决方案**：在 `@Composable` 函数开头获取 `LocalContext.current`，然后在其他地方使用：
```kotlin
@Composable
fun MyComponent() {
    val context = LocalContext.current  // 在 @Composable 上下文中获取
    Button(onClick = {
        // 使用 context，而不是直接使用 LocalContext.current
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }) {
        Text("Copy")
    }
}
```

### 3. 编译错误 - break/continue 在 inline lambda 中

**错误信息**：
```
The feature "break continue in inline lambdas" is experimental and should be enabled explicitly
```

**原因**：`continue` 语句不能在 `runCatching` 等 lambda 内部使用。

**解决方案**：将 `continue` 语句移到 lambda 外部，或使用其他控制流方式：
```kotlin
// 错误示例
for (item in list) {
    runCatching {
        if (condition) continue  // 不能在 lambda 内使用 continue
    }
}

// 正确示例
for (item in list) {
    if (condition) continue  // 在循环中直接使用
    runCatching {
        // 业务逻辑
    }
}
```

### 4. 同时构建 Debug 和 Release 版本失败

**现象**：运行 `gradlew.bat :app:assembleDebug :app:assembleRelease` 时出现混乱或失败。

**原因**：并行构建多个版本可能导致缓存冲突或资源竞争。

**解决方案**：使用 `build.bat` 脚本，它会顺序构建两个版本：
```bat
build.bat
```

### 5. 签名文件不存在

**错误信息**：
```
Keystore file 'keystore/release.jks' not found
```

**解决方案**：运行以下命令创建签名文件：
```bat
mkdir keystore
keytool -genkey -v -keystore keystore/release.jks -alias release -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android,OU=Dev,O=JM,L=Beijing,S=Beijing,C=CN" -storepass android -keypass android
```

### 6. 构建成功但 APK 未生成

**现象**：Gradle 显示 `BUILD SUCCESSFUL` 但找不到 APK 文件。

**解决方案**：检查构建输出目录：
```bat
dir /s /b *.apk
```
APK 文件通常位于：
- Debug: `app\build\outputs\apk\debug\app-debug.apk`
- Release: `app\build\outputs\apk\release\app-release.apk`

### 7. 导入压缩包无提示

**现象**：导入压缩包后没有任何反馈。

**解决方案**：导入完成后会弹出 Snackbar 提示导入结果（成功数量、失败数量、重复跳过数量），同时日志中会记录详细信息。

### 8. 删除任务后下载仍在继续

**现象**：删除正在下载的任务后，下载仍在后台继续。

**解决方案**：删除任务时会自动检查是否正在下载，如果是则先取消下载再删除任务记录。

## 打包脚本

项目根目录提供了一键打包脚本：

```bat
一键打包.bat
```

该脚本会：
1. 构建 Debug 版本
2. 构建 Release 版本
3. 自动打开输出目录

脚本会在构建前先移走旧 APK，强制 Gradle 重新执行打包任务，避免所有任务 `UP-TO-DATE` 时 APK 修改时间不更新；构建结束后会打印两个 APK 的实际修改时间和大小。

### 10. 重启后导入的文件无法读取

**现象**：从手机目录导入的压缩包，当时能正常预览，关闭 App 再打开后提示"无法读取文件"。

**原因**：早期版本导入时直接存储了 SAF 文件选择器返回的临时 URI（如 `content://com.android.providers.downloads.documents/document/msf%3A...`），SAF 权限在 App 重启后失效，导致文件无法访问。

**解决方案**：当前版本已修复，导入文件时会自动复制到保存目录（用户选择的 SAF 树目录或默认 Downloads/JMComic 目录），存储持久化 URI。**注意**：旧版本导入的任务需删除后重新导入才能生效。

### 11. 导入时同名文件被跳过

**现象**：导入压缩包时，如果任务列表中已有相同 JM 车号的任务，会跳过不导入。

**解决方案**：如需重新导入，先删除列表中的旧任务，再导入即可。

## 已知/待打磨

- 首次真机验证要确认：图片**未错位**（还原正确）、流量下能连通 JM、大本子内存表现。
- 无 curl_cffi/梯子依赖；若某些网络下 API 域名不通，库会自动轮换域名。