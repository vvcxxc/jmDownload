@echo off
setlocal
set ROOT=C:\Android
set JDK_ROOT=%ROOT%\jdk-17.0.12+7
set SDK_ROOT=%ROOT%\sdk
set JAVA_EXE=%JDK_ROOT%\bin\java.exe
set SDKMANAGER=%ROOT%\cmdline-tools\bin\sdkmanager.bat

if not exist "%JAVA_EXE%" (
  echo Java not found at %JAVA_EXE%
  exit /b 1
)

if not exist "%SDKMANAGER%" (
  echo sdkmanager not found at %SDKMANAGER%
  exit /b 1
)

set JAVA_HOME=%JDK_ROOT%
set ANDROID_HOME=%SDK_ROOT%
set ANDROID_SDK_ROOT=%SDK_ROOT%
set PATH=%JDK_ROOT%\bin;%PATH%

echo JAVA_HOME=%JAVA_HOME%
echo ANDROID_HOME=%ANDROID_HOME%

"%JAVA_EXE%" -version

if not exist "%SDK_ROOT%\platforms\android-35" (
  echo Installing Android platform 35...
  call "%SDKMANAGER%" --sdk_root="%SDK_ROOT%" "platforms;android-35" "build-tools;35.0.0" "platform-tools"
) else (
  echo Android platform 35 already installed.
)

cd /d d:\360MoveData\Users\vvcxxc\Desktop\jmcomic-qqbot-wrapper-main
call gradlew.bat :app:assembleDebug --console=plain

if exist app\build\outputs\apk\debug\app-debug.apk (
  echo APK_READY
  echo app\build\outputs\apk\debug\app-debug.apk
) else (
  echo APK_MISSING
)

endlocal
