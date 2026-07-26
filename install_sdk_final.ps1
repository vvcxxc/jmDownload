$ErrorActionPreference = 'Stop'
$root = 'C:\Android'
$zip = Join-Path $root 'cmdline-tools.zip'
$extractRoot = Join-Path $root 'cmdline-tools-extract'
$sdkRoot = Join-Path $root 'sdk'
$latestRoot = Join-Path $sdkRoot 'cmdline-tools\latest'
$binDir = Join-Path $latestRoot 'bin'
$javaRoot = Join-Path $root 'jdk-17.0.12+7'
$javaExe = Join-Path $javaRoot 'bin\java.exe'
$log = Join-Path $root 'install_sdk_final.log'

Set-Content -Path $log -Value "start`n"
Add-Content -Path $log -Value "java_exe=$javaExe"

if (-not (Test-Path $javaExe)) {
  Add-Content -Path $log -Value 'java missing'
  throw 'java missing'
}

if (Test-Path $extractRoot) { Remove-Item $extractRoot -Recurse -Force }
New-Item -ItemType Directory -Force -Path $extractRoot | Out-Null

if (-not (Test-Path $zip)) {
  Add-Content -Path $log -Value 'sdk zip missing'
  throw 'sdk zip missing'
}

Add-Content -Path $log -Value "extracting $zip"
Expand-Archive -Path $zip -DestinationPath $extractRoot -Force

$sourceRoot = $null
foreach ($candidate in @((Join-Path $extractRoot 'cmdline-tools'), (Join-Path $extractRoot 'tools'))) {
  if (Test-Path $candidate) {
    $sourceRoot = $candidate
    break
  }
}

if (-not $sourceRoot) {
  Add-Content -Path $log -Value 'source root not found'
  throw 'source root not found'
}

Add-Content -Path $log -Value "source_root=$sourceRoot"

New-Item -ItemType Directory -Force -Path $sdkRoot | Out-Null
New-Item -ItemType Directory -Force -Path $latestRoot | Out-Null
New-Item -ItemType Directory -Force -Path $binDir | Out-Null

foreach ($item in Get-ChildItem -Path $sourceRoot) {
  $dest = Join-Path $latestRoot $item.Name
  if ($item.PSIsContainer) {
    if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
    Copy-Item -Path $item.FullName -Destination $dest -Recurse -Force
  } else {
    if (Test-Path $dest) { Remove-Item $dest -Force }
    Copy-Item -Path $item.FullName -Destination $dest -Force
  }
}

Add-Content -Path $log -Value 'copied source contents'

$sdkManager = Join-Path $binDir 'sdkmanager.bat'
if (-not (Test-Path $sdkManager)) {
  Add-Content -Path $log -Value 'sdkmanager missing'
  throw 'sdkmanager missing'
}

Add-Content -Path $log -Value "sdkmanager=$sdkManager"

$env:JAVA_HOME = $javaRoot
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:PATH = "$javaRoot\bin;$env:PATH"

Add-Content -Path $log -Value 'running java -version'
& $javaExe -version 2>&1 | Add-Content -Path $log

Add-Content -Path $log -Value 'running sdkmanager'
& $sdkManager --sdk_root=$sdkRoot 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools' 2>&1 | Add-Content -Path $log

Add-Content -Path $log -Value 'done'
