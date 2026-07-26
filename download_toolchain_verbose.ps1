$ProgressPreference='SilentlyContinue'
$root='C:\Android'
New-Item -ItemType Directory -Force -Path $root | Out-Null
$sdkZip=Join-Path $root 'commandlinetools.zip'
$jdkZip=Join-Path $root 'jdk.zip'
$sdkUrl='https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip'
$jdkUrl='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.12%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.12_7.zip'
$log=Join-Path $root 'download.log'
"start" | Set-Content $log
Write-Host 'Downloading SDK tools...'
try { Invoke-WebRequest -Uri $sdkUrl -OutFile $sdkZip -TimeoutSec 1800; "sdk_ok" | Add-Content $log } catch { "sdk_fail:$($_.Exception.Message)" | Add-Content $log }
Write-Host 'Downloading JDK...'
try { Invoke-WebRequest -Uri $jdkUrl -OutFile $jdkZip -TimeoutSec 1800; "jdk_ok" | Add-Content $log } catch { "jdk_fail:$($_.Exception.Message)" | Add-Content $log }
if (Test-Path $sdkZip) { "sdk_size=$((Get-Item $sdkZip).Length)" | Add-Content $log }
if (Test-Path $jdkZip) { "jdk_size=$((Get-Item $jdkZip).Length)" | Add-Content $log }
