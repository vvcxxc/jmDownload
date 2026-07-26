$zip = 'C:\Android\cmdline-tools.zip'
$dest = 'C:\Android\cmdline-tools-extract'
if (Test-Path $zip) {
  Write-Host 'ZIP_EXISTS'
  if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
  Expand-Archive -Path $zip -DestinationPath $dest -Force
  Write-Host 'EXTRACTED'
  Get-ChildItem $dest -Recurse | Select-Object -First 50 FullName | ForEach-Object { Write-Host $_ }
} else {
  Write-Host 'ZIP_MISSING'
}
