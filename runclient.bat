@echo off
setlocal
pushd "%~dp0"
if not defined APPDATA (
  echo APPDATA is not defined. Pass the assets directory directly with Gradle.
  popd
  exit /b 1
)
if not defined MIMEOW_NOTIFICATION_ZIG (
  if exist "%CD%\build\ci-tools\zig-windows-x86_64-0.13.0\zig.exe" (
    set "MIMEOW_NOTIFICATION_ZIG=%CD%\build\ci-tools\zig-windows-x86_64-0.13.0\zig.exe"
  )
)
if not defined MIMEOW_NOTIFICATION_ZIG (
  for /f "delims=" %%I in ('where zig 2^>nul') do (
    if not defined MIMEOW_NOTIFICATION_ZIG set "MIMEOW_NOTIFICATION_ZIG=%%I"
  )
)
if not defined MIAU_MINECRAFT set "MIAU_MINECRAFT=%APPDATA%\.minecraft"
if not defined MIAU_ASSETS set "MIAU_ASSETS=%MIAU_MINECRAFT%\assets"
call gradlew.bat runClient -PclientAssetsDir="%MIAU_ASSETS%" -PclientGameDir="%MIAU_MINECRAFT%" %*
set "MIAU_EXIT=%ERRORLEVEL%"
popd
endlocal & exit /b %MIAU_EXIT%
