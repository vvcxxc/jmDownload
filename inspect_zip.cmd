@echo off
setlocal
set ZIP=C:\Android\cmdline-tools.zip
set DEST=C:\Android\cmdline-tools-extract
if exist "%ZIP%" (
  echo ZIP_EXISTS > C:\Android\inspect_zip.log
  if exist "%DEST%" rmdir /s /q "%DEST%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP%' -DestinationPath '%DEST%' -Force"
  echo EXTRACTED >> C:\Android\inspect_zip.log
  dir /s /b "%DEST%" >> C:\Android\inspect_zip.log 2>&1
) else (
  echo ZIP_MISSING > C:\Android\inspect_zip.log
)
endlocal
