# Blockbench でガンブレードを編集する

モデルが崩れた・見た目を変えたい場合は、Blockbench で直接編集できます。

## ✨ 剣モデルが唯一のソース

**編集するのは剣モード (`models/item/gunblade_sword.json`) と
剣テクスチャ (`textures/item/gunblade.png`) だけでOK。**

**Mod が起動するたびに**、剣モデルから銃側のアセットを生成して
`<gamedir>/tacz/gunblade_pack` へ書き出します:

| 生成されるもの | ソース | 実装 |
|---|---|---|
| 3Dモデル (形状・グループ・シリンダー) | 剣モデルの `elements`/`groups` | `TaczGeoGenerator` |
| 銃のテクスチャ | 剣テクスチャをコピー | `GunPackInstaller` |
| slot/hud アイコン | 剣の `display.gui` と同じ構図で描画 | `IconRenderer` |
| ドロップ時の大きさ | 剣の `display.ground.scale` | `GunPackInstaller` |
| 額縁の向き・大きさ | 剣の `display.fixed` (rotation は [-rx,-ry,rz] に変換) | 同上 |

→ **剣側 (モデル/テクスチャ/display) を編集してゲームを再起動するだけで
銃も全部追従**します。配布 jar でも同じ仕組みで動き、外部ツール (python 等) は不要。

※ これらの生成物はリポジトリには置いていません (毎起動時に作られるため)。
銃側の geo を直接見たい場合は、一度ゲームを起動して
`run/tacz/gunblade_pack/.../gunblade_geo.json` を開いてください。
骨格やカメラを恒久的に変えたい場合は `TaczGeoGenerator` を編集します。

- 剣モデルは Blockbench で **Java Block/Item** 形式として開く
- Outliner の `stock` / `body` / `blade` グループ構成が銃側のボーンになる
- **3×3×4 のキューブがあるとシリンダー扱い**になり、リロードで回転する
- ドロップ時・額縁の大きさ/向きは剣モデルの `display.ground` / `display.fixed`
  から自動で取り込まれる (Blockbench の Display タブで調整すればOK)

## 編集対象ファイル

| 対象 | ファイル | Blockbench形式 |
|---|---|---|
| **モデル本体** (剣・銃 共通) | `src/main/resources/assets/gun_and_weapon/models/item/gunblade_sword.json` | **Java Block/Item** |
| **テクスチャ** (剣・銃 共通) | `src/main/resources/assets/gun_and_weapon/textures/item/gunblade.png` (32x32) | — |
| リロード/inspectモーション | `.../gunblade_pack/assets/gun_and_weapon/animations/gunblade.animation.json` | Bedrock Animation |

## 開き方

1. Blockbench → `File > Open Model` → `gunblade_sword.json` を選択
   (Java Block/Item として開かれる)
2. 編集後は `Ctrl+S` (⌘S)
3. 反映: `bash run_quick.sh` (dev) / `bash build.sh` して jar を差し替え

起動時に銃側アセットが作り直されるので、追加の手順は不要です。

## ⚠️ 消してはいけないボーン

以下のボーンは TACZ が機能的に参照します。**名前変更・削除禁止**
(位置=pivotの調整はOK。むしろ調整用):

| ボーン | 役割 |
|---|---|
| `camera` / `idle_view` | 一人称の目の位置。**銃が画面で大きすぎ/小さすぎる時はここを動かす** (現在 `[2.8, 14.2, 15.5]`。上げると銃が下がって見える、後ろ(z+)に引くと小さく見える) |
| `iron_view` | エイム(ADS)時の視点。x=0 中央、y=照準線の高さ |
| `muzzle_flash` / `muzzle_pos` | マズルフラッシュ/弾の発射位置 (刃先) |
| `lefthand` / `lefthand_pos` / `righthand` / `righthand_pos` | プレイヤーの腕が付く位置 |
| `shell` | 薬莢の排出位置 |
| `positioning` / `fixed` / `ground` / `thirdperson_hand` | 額縁・ドロップ・三人称の表示位置 |
| `refit` / `refit_view` | 改造画面のカメラ |
| `root` / `gun_and_righthand` / `bullet_and_lefthand` / `constraint` | 骨格の親 (m870と同じ構成) |

モデル本体のキューブは `gun` ボーン配下の `stock` / `body` / `blade` に入っています。
ここは自由に編集してOK。

## シリンダー (`cylinder` ボーン)

リボルバー風リロードで回転する部分。`gun` の子として定義済みで、
**レシーバー中央の 3×3×4 ドラム型キューブ (origin [-1.5, 8.625, 2]) を割当済み**。
ピボット `[0, 10.125, 4]` = そのキューブの中心軸 (回転軸=Z=銃身方向)。

- 別のキューブをシリンダーにしたい場合は、Blockbench の Outliner で
  `cylinder` 配下へドラッグして移す (ピボットも合わせて調整)
- 新しくシリンダー型のキューブを追加してもOK (cylinder ボーンの下に作る)
- 回転して見た目が破綻しないよう、ピボットの周囲に対称に置くこと
- アニメーション側 (`gunblade.animation.json`) は配線済み:
  - `reload_empty`: 弾込め中にゆっくり180°→閉鎖時に勢いよく2回転
  - `reload_tactical`: 短縮版
  - `inspect`: 中盤にゆっくり1回転
- ピボットを動かした場合はアニメの回転中心も変わるので注意
- シリンダー判定は **サイズ 3×3×4 のキューブ** (`TaczGeoGenerator` が自動割当)。
  別のキューブにしたい場合はそのサイズに合わせるか、生成側を編集する

## ⚠️ UVの注意

- UVは面ごと指定 (per-face UV)。**負の uv_size (UV反転) は使わない**こと。
  Blockbench上で反転が必要になったら、テクスチャ側の絵を反転させる。
- テクスチャサイズを変える場合は `description.texture_width/height` も
  Blockbenchが自動更新するのでそのままでOK。

## 手の位置・リロードモーションの調整

手の位置はモデルではなく `gunblade.animation.json` の `static_idle` が決めます
(`lefthand`/`righthand` の rotation/position)。Blockbench の Animate タブで
一度ゲームを起動して生成された `run/tacz/gunblade_pack/.../gunblade_geo.json`
に対してこのアニメファイルを開けば、プレビューしながら
キーフレームを調整できます。リロード (`reload_empty`/`reload_tactical`) と
`inspect` も同ファイルです。

## MOD アイコン (logo.png) の再生成

ゲーム内 Mod 一覧と CurseForge 用のアイコンだけは静的ファイルなので、
モデルを変えたら手動で作り直します:
```
python3 tools/make_logo.py
```
→ `src/main/resources/logo.png` (128x128) と
`promo/curseforge_icon.png` (400x400) を再生成。

(銃の slot/hud アイコンは実行時に自動生成されるので手動作業は不要)

## 変換規約の詳細

座標系・回転符号・UV・必須ボーンなどの詳細は
[docs/GUNPACK_NOTES.md](docs/GUNPACK_NOTES.md) を参照。
