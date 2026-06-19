@echo off
REM Maven Wrapper script for Windows
REM Downloads and runs Maven if not already available

setlocal

set "MAVEN_PROJECTBASEDIR=%~dp0"
set "MAVEN_WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties"

if exist "%MAVEN_WRAPPER_PROPERTIES%" (
    for /f "tokens=1,* delims==" %%a in ('findstr "distributionUrl" "%MAVEN_WRAPPER_PROPERTIES%"') do set "MAVEN_DIST_URL=%%b"
)

set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.6"

if not exist "%MAVEN_HOME%" (
    echo Downloading Maven...
    mkdir "%MAVEN_HOME%" 2>nul
    set "TMPFILE=%TEMP%\maven-dist.zip"
    curl -sL "%MAVEN_DIST_URL%" -o "%TMPFILE%"
    powershell -Command "Expand-Archive -Path '%TMPFILE%' -DestinationPath '%USERPROFILE%\.m2\wrapper\dists' -Force"
    del /f "%TMPFILE%" 2>nul
)

set "MAVEN_BIN="
for /r "%USERPROFILE%\.m2\wrapper\dists" %%f in (mvn.cmd) do (
    if not defined MAVEN_BIN set "MAVEN_BIN=%%f"
)

if not defined MAVEN_BIN (
    echo ERROR: Could not find Maven executable
    exit /b 1
)

"%MAVEN_BIN%" %*
