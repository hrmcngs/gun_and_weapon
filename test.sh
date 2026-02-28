#!/bin/bash
set -e

echo "=== Building Gun and Weapon ==="

if grep -qi microsoft /proc/version 2>/dev/null; then
    WIN_DIR=$(wslpath -w "$(pwd)")
    cmd.exe /c "${WIN_DIR}\\gradlew_wsl.bat" build
    echo ""
    echo "=== Launching Minecraft Client ==="
    cmd.exe /c "${WIN_DIR}\\gradlew_wsl.bat" runClient
else
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
    ./gradlew build
    echo ""
    echo "=== Launching Minecraft Client ==="
    ./gradlew runClient
fi
