@echo off
REM One-command debug build for the JM downloader app.
REM Uses the portable toolchain set up under C:\Android.
setlocal
set "JAVA_HOME=C:\Android\jdk-17.0.19+10"
set "ANDROID_HOME=C:\Android\sdk"
set "ANDROID_SDK_ROOT=C:\Android\sdk"
call "C:\Android\gradle\gradle-8.11.1\bin\gradle.bat" :app:assembleDebug --console=plain %*
echo.
echo APK: app\build\outputs\apk\debug\app-debug.apk
endlocal
