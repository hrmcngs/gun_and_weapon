#!/bin/bash
# build.sh / run_client.sh 共通の環境セットアップ。
# 単体では実行せず、各スクリプトから source して使う。
#
# 提供するもの:
#   PROJECT_DIR  … このリポジトリのルート（カレントもここに移動する）
#   GRADLE       … gradle 実行コマンド（WSL では cmd.exe 経由）
#   GRADLE_FLAGS … 全実行に付ける共通フラグ
#   run_gradle   … gradle タスクを実行する関数

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

# ForgeGradle は maven.minecraftforge.net の証明書検証で失敗することがあるため無効化。
GRADLE_FLAGS="-Dnet.minecraftforge.gradle.check.certs=false"

# --- JAVA_HOME（Forge 1.20.1 は JDK 17 必須）--------------------------------
# 既に JDK 17 が設定済みならそれを尊重する。
if [ -z "$JAVA_HOME" ] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"17'; then
    if [ -x /usr/libexec/java_home ]; then                       # macOS
        JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    fi
    if [ -z "$JAVA_HOME" ]; then                                 # Linux / WSL
        for candidate in \
            /usr/lib/jvm/java-17-openjdk-amd64 \
            /usr/lib/jvm/java-17-openjdk \
            /usr/lib/jvm/temurin-17-jdk-amd64
        do
            [ -d "$candidate" ] && JAVA_HOME="$candidate" && break
        done
    fi
    export JAVA_HOME
fi

if [ -z "$JAVA_HOME" ]; then
    echo "[warn] JDK 17 が見つかりません。PATH の java をそのまま使います。" >&2
    echo "       macOS: brew install openjdk@17 / Linux: apt install openjdk-17-jdk" >&2
else
    echo "JAVA_HOME: $JAVA_HOME"
fi

# --- gradle 実行方法の決定 ---------------------------------------------------
IS_WSL=""
grep -qi microsoft /proc/version 2>/dev/null && IS_WSL=1

if [ -z "$IS_WSL" ]; then
    # gradlew に実行権限が無い環境でも動くよう sh 経由で起動する。
    chmod +x ./gradlew 2>/dev/null || true
fi

run_gradle() {
    if [ -n "$IS_WSL" ]; then
        local win_dir
        win_dir="$(wslpath -w "$PROJECT_DIR")"
        cmd.exe /c "${win_dir}\\gradlew_wsl.bat" "$@"
    else
        sh ./gradlew "$@" $GRADLE_FLAGS
    fi
}

# --- 本体MOD (The four primitives and Weapons) 関連 --------------------------
# build.gradle と同じ値。変更する場合は両方揃えること。
MAW_ARTIFACT="the_four_primitives_and_weapons"
MAW_VERSION="1.20.1-local"
MAW_JAR="libs/local/${MAW_ARTIFACT}/${MAW_VERSION}/${MAW_ARTIFACT}-${MAW_VERSION}.jar"
MAIN_MOD_DIR="${MAIN_MOD_DIR:-$HOME/The-four-primitives-and-Weapons}"

# 本体MOD をローカルソースからビルドして libs/local に配置する。
#   $1 … gradle に渡す追加フラグ（--offline など）
build_main_mod() {
    local extra="$1"

    if [ ! -d "$MAIN_MOD_DIR" ]; then
        echo "[error] 本体MOD のソースディレクトリがありません: $MAIN_MOD_DIR" >&2
        echo "        MAIN_MOD_DIR 環境変数で別のパスを指定できます。" >&2
        return 1
    fi

    echo "==> 本体MOD をビルド: $MAIN_MOD_DIR"
    ( cd "$MAIN_MOD_DIR" && sh ./gradlew jar $extra $GRADLE_FLAGS )

    # build/libs から最新の jar を拾う (sources/dev/javadoc は除外)
    local built
    built="$(ls -t "$MAIN_MOD_DIR/build/libs/"*.jar 2>/dev/null \
             | grep -v -E '(-sources|-dev|-javadoc)\.jar$' | head -n1)"
    if [ -z "$built" ] || [ ! -f "$built" ]; then
        echo "[error] 本体MOD のビルドに失敗 (jar が生成されていません)" >&2
        return 1
    fi

    mkdir -p "$(dirname "$MAW_JAR")"
    cp -f "$built" "$MAW_JAR"
    echo "    ビルド完了: $built"
    echo "    コピー先  : $MAW_JAR"

    purge_maw_deobf_cache
}

# ForgeGradle の deobf キャッシュを消して本体MODの変更を確実に反映させる。
#   fg.deobf("local:...") は固定 version を引くため、jar の中身が変わっても
#   deobf 済みキャッシュが再利用されて変更が反映されないことがある。
purge_maw_deobf_cache() {
    local roots=(
        "$HOME/.gradle/caches/forge_gradle/deobf_dependencies/local/${MAW_ARTIFACT}"
        "$HOME/.gradle/caches/forge_gradle/mod_remap_repo/local/${MAW_ARTIFACT}"
    )
    local purged=0
    for root in "${roots[@]}"; do
        if [ -d "$root" ]; then
            find "$root" -mindepth 1 -maxdepth 1 -name "${MAW_VERSION}*" -exec rm -rf {} + 2>/dev/null && purged=1
        fi
    done
    [ "$purged" = 1 ] && echo "    deobf cache を invalidate (本体MOD の変更を確実に反映)"
    return 0
}
