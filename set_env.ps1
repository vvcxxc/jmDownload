$root = 'C:\Android'
$javaHome = Join-Path $root 'jdk\jdk-17.0.12+7'
$sdkRoot = Join-Path $root 'sdk'
if (Test-Path $javaHome) {
  [Environment]::SetEnvironmentVariable('JAVA_HOME', $javaHome, 'User')
  $path = [Environment]::GetEnvironmentVariable('Path', 'User')
  if ($path -notlike '*'+$javaHome+'*') {
    [Environment]::SetEnvironmentVariable('Path', "$path;$javaHome\bin", 'User')
  }
}
if (Test-Path $sdkRoot) {
  [Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', $sdkRoot, 'User')
  [Environment]::SetEnvironmentVariable('ANDROID_HOME', $sdkRoot, 'User')
}
Write-Host 'ENV_SET'
