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
#
# 対話プロンプトは build.gradle 側 (Backpack-Arsenal と同じ仕組み):
#   1) version を聞く (空 Enter で gradle.properties の mod_version)
#   2) release type を聞く (b/a/r/rc/t)
#   最終的に "<version><suffix>" が jar 名と mods.toml の version になる。
#   スクリプト/CI から stdin 無しで実行した場合は既定値 (mod_version そのまま) で進む。
#   非対話で明示指定する場合:
#     sh gradlew build -Pmod_version_override=1.2.3-beta
#     sh gradlew build -Prelease_type=beta
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
