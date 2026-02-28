#!/bin/bash
set -e

echo "=== Building Gun and Weapon ==="

if grep -qi microsoft /proc/version 2>/dev/null; then
    WIN_DIR=$(wslpath -w "$(pwd)")
    cmd.exe /c "${WIN_DIR}\\gradlew_wsl.bat" build
else
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
    ./gradlew build
fi

echo ""
echo "=== Build complete ==="
ls -lh build/libs/*.jar
