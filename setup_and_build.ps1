$ErrorActionPreference='Stop'
$root='C:\Android'
$jdkRoot=Join-Path $root 'jdk-17.0.12+7'
$sdkRoot=Join-Path $root 'sdk'
$javaExe=Join-Path $jdkRoot 'bin\java.exe'
$sdkManager=Join-Path $root 'cmdline-tools\bin\sdkmanager.bat'

New-Item -ItemType Directory -Force -Path $sdkRoot | Out-Null

if (-not (Test-Path $javaExe)) {
  throw "Java not found at $javaExe"
}
if (-not (Test-Path $sdkManager)) {
  throw "sdkmanager not found at $sdkManager"
}

$env:JAVA_HOME=$jdkRoot
$env:ANDROID_HOME=$sdkRoot
$env:ANDROID_SDK_ROOT=$sdkRoot
$env:PATH="$jdkRoot\bin;$env:PATH"

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_HOME=$env:ANDROID_HOME"

$javaVersion = & $javaExe -version 2>&1
Write-Host $javaVersion[0]

if (-not (Test-Path (Join-Path $sdkRoot 'platforms\android-35'))) {
  Write-Host 'Installing Android platform 35...'
  & $sdkManager --sdk_root=$sdkRoot "platforms;android-35" "build-tools;35.0.0" "platform-tools" | Tee-Object -FilePath (Join-Path $root 'sdk-install.log')
} else {
  Write-Host 'Android platform 35 already installed.'
}

Write-Host 'Building APK...'
Set-Location 'd:\360MoveData\Users\vvcxxc\Desktop\jmcomic-qqbot-wrapper-main'
& .\gradlew.bat :app:assembleDebug --console=plain
