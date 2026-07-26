$root = 'C:\Android'
New-Item -ItemType Directory -Force -Path $root | Out-Null
$jdkZip = Join-Path $root 'jdk.zip'
$cmdZip = Join-Path $root 'cmdline-tools.zip'

if (Test-Path $jdkZip) {
  Expand-Archive -Path $jdkZip -DestinationPath $root -Force
}
if (Test-Path $cmdZip) {
  Expand-Archive -Path $cmdZip -DestinationPath $root -Force
}

$jdkDirs = Get-ChildItem -Path $root -Directory | Where-Object { $_.Name -like 'jdk*' }
if ($jdkDirs) {
  $jdkDir = $jdkDirs | Select-Object -First 1 -ExpandProperty FullName
  Write-Host "JDK_DIR=$jdkDir"
}

if (Test-Path (Join-Path $root 'cmdline-tools')) {
  Write-Host 'CMDLINE_TOOLS_EXTRACTED'
}

Get-ChildItem -Path $root | Select-Object Name,Length,LastWriteTime | Format-Table -AutoSize
