$ProgressPreference='SilentlyContinue'
$root='C:\Android'
New-Item -ItemType Directory -Force -Path $root | Out-Null
$sdkZip = Join-Path $root 'commandlinetools.zip'
$jdkZip = Join-Path $root 'jdk.zip'
$sdkUrl='https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip'
$jdkUrl='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.12%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.12_7.zip'
Write-Host 'Downloading SDK...'
Invoke-WebRequest -Uri $sdkUrl -OutFile $sdkZip -TimeoutSec 600
Write-Host 'Downloading JDK...'
Invoke-WebRequest -Uri $jdkUrl -OutFile $jdkZip -TimeoutSec 600
Write-Host 'Extracting...'
Expand-Archive -Path $sdkZip -DestinationPath (Join-Path $root 'cmdline-tools') -Force
Expand-Archive -Path $jdkZip -DestinationPath (Join-Path $root 'jdk') -Force
Write-Host 'Done'
