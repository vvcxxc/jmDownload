@echo off
REM One-command build for the JM downloader app.
setlocal
cd /d "%~dp0"

set "DEBUG_APK=app\build\outputs\apk\debug\app-debug.apk"
set "RELEASE_APK=app\build\outputs\apk\release\app-release.apk"

REM Build debug version first. Remove the old APK so Gradle's up-to-date check
REM cannot skip packaging and leave an old timestamp on the artifact.
echo Building debug version...
if exist "%DEBUG_APK%" move /y "%DEBUG_APK%" "%DEBUG_APK%.prev" >nul
call gradlew.bat :app:assembleDebug %*
if errorlevel 1 (
    if exist "%DEBUG_APK%.prev" move /y "%DEBUG_APK%.prev" "%DEBUG_APK%" >nul
    echo Debug build failed!
    pause
    exit /b 1
)
if exist "%DEBUG_APK%.prev" del /q "%DEBUG_APK%.prev" >nul

REM Build release version
echo.
echo Building release version...
if exist "%RELEASE_APK%" move /y "%RELEASE_APK%" "%RELEASE_APK%.prev" >nul
call gradlew.bat :app:assembleRelease %*
if errorlevel 1 (
    if exist "%RELEASE_APK%.prev" move /y "%RELEASE_APK%.prev" "%RELEASE_APK%" >nul
    echo Release build failed!
    pause
    exit /b 1
)
if exist "%RELEASE_APK%.prev" del /q "%RELEASE_APK%.prev" >nul

echo.
echo Build completed successfully!
for %%F in ("%DEBUG_APK%") do (
    echo Debug APK:   %%~fF
    echo   modified: %%~tF  size: %%~zF bytes
)
for %%F in ("%RELEASE_APK%") do (
    echo Release APK: %%~fF
    echo   modified: %%~tF  size: %%~zF bytes
)
endlocal