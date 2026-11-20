@echo off
setlocal
call gradlew.bat build
set "MIAU_EXIT=%ERRORLEVEL%"
endlocal & exit /b %MIAU_EXIT%
