$ErrorActionPreference = 'Stop'
$root = 'C:\Android'
$zip = Join-Path $root 'cmdline-tools.zip'
$sdkRoot = Join-Path $root 'sdk'
$extractRoot = Join-Path $root 'cmdline-tools-extract'
$latestRoot = Join-Path $sdkRoot 'cmdline-tools\latest'
$binDir = Join-Path $latestRoot 'bin'
$javaRoot = Join-Path $root 'jdk-17.0.12+7'
$javaExe = Join-Path $javaRoot 'bin\java.exe'

New-Item -ItemType Directory -Force -Path $sdkRoot | Out-Null
New-Item -ItemType Directory -Force -Path $binDir | Out-Null

if (-not (Test-Path $javaExe)) {
  throw "JDK java.exe not found: $javaExe"
}

if (Test-Path $extractRoot) {
  Remove-Item $extractRoot -Recurse -Force
}

if (Test-Path $zip) {
  Expand-Archive -Path $zip -DestinationPath $extractRoot -Force
  Write-Host 'Archive extracted.'
} else {
  throw "SDK zip not found: $zip"
}

$sourceRoot = Join-Path $extractRoot 'cmdline-tools'
if (-not (Test-Path $sourceRoot)) {
  $sourceRoot = Join-Path $extractRoot 'tools'
}
if (-not (Test-Path $sourceRoot)) {
  throw "No cmdline-tools directory found inside archive: $extractRoot"
}

Copy-Item -Path (Join-Path $sourceRoot '*') -Destination $latestRoot -Recurse -Force
Write-Host "Copied SDK contents to $latestRoot"

$sdkManager = Join-Path $binDir 'sdkmanager.bat'
if (-not (Test-Path $sdkManager)) {
  throw "sdkmanager.bat not found at $sdkManager"
}

$env:JAVA_HOME = $javaRoot
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:PATH = "$javaRoot\bin;$env:PATH"

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_HOME=$env:ANDROID_HOME"

& $javaExe -version

& $sdkManager --sdk_root=$sdkRoot 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'

Write-Host 'SDK install completed.'
