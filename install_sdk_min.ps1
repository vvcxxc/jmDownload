$root = 'C:\Android'
$zip = Join-Path $root 'cmdline-tools.zip'
$extractRoot = Join-Path $root 'cmdline-tools-extract'
$log = Join-Path $root 'install_sdk_min.log'
Set-Content -Path $log -Value 'start'
if (Test-Path $extractRoot) { Remove-Item $extractRoot -Recurse -Force }
New-Item -ItemType Directory -Force -Path $extractRoot | Out-Null
Expand-Archive -Path $zip -DestinationPath $extractRoot -Force
Add-Content -Path $log -Value 'extracted'
Get-ChildItem -Path $extractRoot -Recurse | Select-Object -First 20 FullName | ForEach-Object { Add-Content -Path $log -Value $_ }
Add-Content -Path $log -Value 'done'
