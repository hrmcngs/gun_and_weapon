#!/bin/bash
# Gun and Weapon — ビルドスクリプト
#
# 使い方:
#   bash build.sh            通常ビルド（build/libs に jar を出力）
#   bash build.sh clean      クリーンしてからビルド
#   bash build.sh offline    ネットワークを使わずビルド（依存が取得済みの場合のみ）
#
# clean と offline は併用できる:
#   bash build.sh clean offline
set -e

source "$(dirname "$0")/scripts/common.sh"

DO_CLEAN=""
TASKS="build"
EXTRA=""

for arg in "$@"; do
    case "$arg" in
        clean)            DO_CLEAN=1 ;;
        offline|--offline) EXTRA="$EXTRA --offline" ;;
        *)                EXTRA="$EXTRA $arg" ;;
    esac
done

[ -n "$DO_CLEAN" ] && TASKS="clean build"

echo "=== Building Gun and Weapon ==="
echo "    tasks:$( echo " $TASKS" )$EXTRA"
echo ""

run_gradle $TASKS $EXTRA

echo ""
echo "=== Build complete ==="
ls -lh build/libs/*.jar
