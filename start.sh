#!/usr/bin/env bash
set -e

echo "============================================"
echo "  Show Me The Story - AI 小说生成器 (Java)"
echo "============================================"
echo ""

# Check Java
if ! command -v java &>/dev/null; then
    echo "[错误] 未检测到 Java，请先安装 JDK 17+"
    echo "macOS:  brew install openjdk@17"
    echo "Ubuntu: sudo apt install openjdk-17-jdk"
    exit 1
fi

# Check if JAR exists
JAR="target/show-me-the-story-1.0.0.jar"
if [ ! -f "$JAR" ]; then
    echo "[提示] 未找到 JAR 文件，开始编译..."
    echo ""

    if command -v mvn &>/dev/null; then
        echo "[编译] 使用系统 Maven 编译..."
        mvn package -Dexec.skip=true -q
    elif [ -f "./mvnw" ]; then
        echo "[编译] 使用 Maven Wrapper 编译..."
        chmod +x ./mvnw
        ./mvnw package -Dexec.skip=true -q
    else
        echo "[错误] 未找到 Maven，请安装 Maven 3.9+"
        exit 1
    fi

    echo "[成功] 编译完成"
    echo ""
fi

# Open browser after delay (background)
(sleep 3 && (open http://localhost:48090 2>/dev/null || xdg-open http://localhost:48090 2>/dev/null || echo "请手动打开浏览器访问 http://localhost:48090")) &

# Start application
echo "[启动] 正在启动服务..."
echo "[访问] http://localhost:48090"
echo ""
echo "按 Ctrl+C 停止服务"
echo "============================================"
echo ""

java -jar "$JAR" "$@"
