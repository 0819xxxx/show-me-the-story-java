@echo off
chcp 65001 >nul 2>&1
title Show Me The Story

echo ============================================
echo   Show Me The Story - AI 小说生成器 (Java)
echo ============================================
echo.

:: Check Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未检测到 Java，请先安装 JDK 17+
    echo 下载地址: https://adoptium.net/
    pause
    exit /b 1
)

:: Check if JAR exists
set "JAR=target\show-me-the-story-1.0.0.jar"
if not exist "%JAR%" (
    echo [提示] 未找到 JAR 文件，开始编译...
    echo.
    call :build
    if %errorlevel% neq 0 (
        echo.
        echo [错误] 编译失败，请检查上方错误信息
        pause
        exit /b 1
    )
    echo.
    echo [成功] 编译完成
    echo.
)

:: Start application
echo [启动] 正在启动服务...
echo [访问] http://localhost:48090
echo.
echo 按 Ctrl+C 停止服务
echo ============================================
echo.

:: Open browser after 3 seconds delay
start "" cmd /c "timeout /t 3 /nobreak >nul && start http://localhost:48090"

:: Run the application
java -jar "%JAR%" %*
goto :eof

:build
    :: Check Maven
    where mvn >nul 2>&1
    if %errorlevel% equ 0 (
        echo [编译] 使用系统 Maven 编译...
        mvn package -Dexec.skip=true -q
        exit /b %errorlevel%
    )
    if exist "mvnw.cmd" (
        echo [编译] 使用 Maven Wrapper 编译...
        call mvnw.cmd package -Dexec.skip=true -q
        exit /b %errorlevel%
    )
    echo [错误] 未找到 Maven，请安装 Maven 3.9+
    exit /b 1
