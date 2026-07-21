#!/bin/bash
# Gun and Weapon — 手元の本体MOD jar のままクライアント起動（最速）
#
# 本体MOD のビルドはしない。本体MOD の変更を反映したい時は run_client.sh を使う。
# TaCZ は libs/local にベンダリング済みなので offline でも起動できる。
#
# 使い方:
#   bash run_quick.sh           実行
#   bash run_quick.sh offline   オフライン実行
set -e

source "$(dirname "$0")/scripts/common.sh"

EXTRA=""
for arg in "$@"; do
    case "$arg" in
        offline|-o|--offline) EXTRA="$EXTRA --offline" ;;
        *)                    EXTRA="$EXTRA $arg" ;;
    esac
done

if [ ! -f "$MAW_JAR" ]; then
    echo "[error] 本体MOD jar がありません: $MAW_JAR" >&2
    echo "        bash run_client.sh を実行して本体MODをビルドしてください。" >&2
    exit 1
fi

echo "本体MOD jar: $MAW_JAR (最新化はしない)"
echo ""
echo "==> addon runClient$EXTRA"
run_gradle runClient $EXTRA
