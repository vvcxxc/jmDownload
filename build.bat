@echo off
REM One-command build for the JM downloader app.
setlocal

REM Build debug version first
echo Building debug version...
call gradlew.bat :app:assembleDebug %*
if %errorlevel% neq 0 (
    echo Debug build failed!
    pause
    exit /b %errorlevel%
)

REM Build release version
echo.
echo Building release version...
call gradlew.bat :app:assembleRelease %*
if %errorlevel% neq 0 (
    echo Release build failed!
    pause
    exit /b %errorlevel%
)

echo.
echo Build completed successfully!
echo Debug APK: app\build\outputs\apk\debug\app-debug.apk
echo Release APK: app\build\outputs\apk\release\app-release.apk
endlocal