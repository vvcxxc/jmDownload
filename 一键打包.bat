@echo off
chcp 65001 >nul
title JM漫画下载器 - 一键打包

setlocal enabledelayedexpansion

echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║                    JM漫画下载器 - 一键打包                     ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.

if not exist "gradlew.bat" (
    echo 错误：未找到 gradlew.bat，请确保脚本在项目根目录运行！
    pause
    exit /b 1
)

echo [1/2] 正在构建 Debug 版本...
echo ------------------------------------------------------------
call gradlew.bat :app:assembleDebug
if %errorlevel% neq 0 (
    echo.
    echo ❌ Debug 版本构建失败！
    pause
    exit /b %errorlevel%
)

echo.
echo [2/2] 正在构建 Release 版本...
echo ------------------------------------------------------------
call gradlew.bat :app:assembleRelease
if %errorlevel% neq 0 (
    echo.
    echo ❌ Release 版本构建失败！
    pause
    exit /b %errorlevel%
)

echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║                      构建完成！                              ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.
echo 📦 Debug APK: app\build\outputs\apk\debug\app-debug.apk
echo 📦 Release APK: app\build\outputs\apk\release\app-release.apk
echo.
echo 按任意键打开输出目录...
pause >nul

start explorer.exe "app\build\outputs\apk"

endlocal