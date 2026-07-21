#!/bin/bash
# Gun and Weapon — 本体MODをビルドしてからクライアント起動
#
# 本体MOD (the_four_primitives_and_weapons) をローカルソースからビルドし、
# その jar を libs/local 配下に配置してから runClient を実行する。
# 本体MOD を編集中でも、その変更がそのままクライアントに反映される。
#
# 本体MOD をビルドし直す必要がない時は、より速い run_quick.sh を使うこと。
#
# 前提:
#   本体MOD のソースが ~/The-four-primitives-and-Weapons にあること。
#   環境変数 MAIN_MOD_DIR で別のパスを指定できる。
#
# 使い方:
#   bash run_client.sh           本体MODをビルドしてから実行
#   bash run_client.sh offline   オフライン実行（本体MODビルドも runClient も --offline）
set -e

source "$(dirname "$0")/scripts/common.sh"

EXTRA=""
for arg in "$@"; do
    case "$arg" in
        offline|-o|--offline) EXTRA="$EXTRA --offline" ;;
        *)                    EXTRA="$EXTRA $arg" ;;
    esac
done

# --- 本体MOD をビルドして libs/local に配置 ---------------------------------
build_main_mod "$EXTRA"

if [ ! -f "$MAW_JAR" ]; then
    echo "[error] 本体MOD jar の配置に失敗: $MAW_JAR" >&2
    exit 1
fi

echo ""
echo "==> addon runClient$EXTRA"
run_gradle runClient $EXTRA
