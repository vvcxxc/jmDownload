# JM 下载 (Android)

一个极简的安卓 JMComic 下载 App：输入车号 → 排队 → 逐个下载 → **一本一个 zip** 存到你选的目录。纯手机、直连 JM、流量可用，不依赖电脑。

## 功能

- 输入框输车号（默认前缀 `JM`），也能直接粘贴带 `jm`/空格/链接的文本，自动识别车号。
- 选择保存位置（系统文件夹选择器，权限持久化）。
- 任务列表：先记录再**逐个串行下载**（前台服务，退到后台/锁屏也继续，流量可用）。
- 每本下完打包成 `JM{车号}-{标题}.zip` 存到所选目录。失败可重试、可删除。

## 技术

- Kotlin + Jetpack Compose（Material3）。`minSdk 26`，`compileSdk/targetSdk 35`。
- 下载引擎：[`io.github.jukomu:jmcomic-android-support`](https://central.sonatype.com/artifact/io.github.jukomu/jmcomic-android-support)（JM 移动 API，token 签名，无需梯子；图片下载 + 还原都由库完成，`AndroidImageProcessor` 用 Bitmap 还原）。
- 无 Room/额外持久化依赖：任务列表用 `org.json` 存到私有目录；保存目录 URI 用 SharedPreferences。
- 关键排除：`org.sejda.imageio:webp-imageio`（桌面 AWT 插件，安卓用不了）、`com.google.android:android`（老 stub）。

## 构建（本机便携工具链，无需 Android Studio）

工具链已装在 `C:\Android`：JDK 17、Android SDK（platform-35 / build-tools 35 / platform-tools）、Gradle 8.11.1。

一键构建：

```bat
build.bat
```

或用 wrapper（走腾讯镜像）：

```bat
gradlew.bat :app:assembleDebug
```

产物：`app\build\outputs\apk\debug\app-debug.apk`

## 安装到手机

- 把 `app-debug.apk` 传到手机点击安装（需允许“未知来源”），或
- USB 连手机开 USB 调试后：`adb install -r app\build\outputs\apk\debug\app-debug.apk`

## 首次使用

1. 打开 App，允许通知。
2. 点“保存位置 → 选择”，选一个文件夹（建议在内部存储新建一个，如 `Download/jm`）。
3. 输入框输车号（如 `350234`）或粘贴链接 → “添加” → 自动开始下载。
4. 下完在所选目录能看到 `JM{车号}-{标题}.zip`。

## 已知/待打磨

- 首次真机验证要确认：图片**未错位**（还原正确）、流量下能连通 JM、大本子内存表现。
- 无 curl_cffi/梯子依赖；若某些网络下 API 域名不通，库会自动轮换域名。
