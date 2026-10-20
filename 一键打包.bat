@echo off
chcp 65001 >nul
title JM漫画下载器 - 一键打包

setlocal enabledelayedexpansion
cd /d "%~dp0"

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

set "DEBUG_APK=app\build\outputs\apk\debug\app-debug.apk"
set "RELEASE_APK=app\build\outputs\apk\release\app-release.apk"

echo [1/2] 正在构建 Debug 版本...
echo ------------------------------------------------------------
rem Gradle 对没变化的构建会跳过 packageXxx，APK 时间不会更新。
rem 先移走旧 APK，强制本次重新打包；失败时再恢复。
if exist "%DEBUG_APK%" move /y "%DEBUG_APK%" "%DEBUG_APK%.prev" >nul
call gradlew.bat :app:assembleDebug
if errorlevel 1 (
    if exist "%DEBUG_APK%.prev" move /y "%DEBUG_APK%.prev" "%DEBUG_APK%" >nul
    echo.
    echo ❌ Debug 版本构建失败！
    pause
    exit /b 1
)
if not exist "%DEBUG_APK%" (
    echo.
    echo ❌ Debug 构建成功但未找到 APK！
    pause
    exit /b 1
)
if exist "%DEBUG_APK%.prev" del /q "%DEBUG_APK%.prev" >nul

echo.
echo [2/2] 正在构建 Release 版本...
echo ------------------------------------------------------------
if exist "%RELEASE_APK%" move /y "%RELEASE_APK%" "%RELEASE_APK%.prev" >nul
call gradlew.bat :app:assembleRelease
if errorlevel 1 (
    if exist "%RELEASE_APK%.prev" move /y "%RELEASE_APK%.prev" "%RELEASE_APK%" >nul
    echo.
    echo ❌ Release 版本构建失败！
    pause
    exit /b 1
)
if not exist "%RELEASE_APK%" (
    echo.
    echo ❌ Release 构建成功但未找到 APK！
    pause
    exit /b 1
)
if exist "%RELEASE_APK%.prev" del /q "%RELEASE_APK%.prev" >nul

echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║                      构建完成！                              ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.
for %%F in ("%DEBUG_APK%") do (
    echo 📦 Debug APK:   %%~fF
    echo    修改时间: %%~tF    大小: %%~zF bytes
)
for %%F in ("%RELEASE_APK%") do (
    echo 📦 Release APK: %%~fF
    echo    修改时间: %%~tF    大小: %%~zF bytes
)
echo.
echo 按任意键打开输出目录...
pause >nul

start explorer.exe "app\build\outputs\apk"

endlocal