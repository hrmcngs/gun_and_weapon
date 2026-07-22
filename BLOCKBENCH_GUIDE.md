# Blockbench でガンブレードを編集する

モデルが崩れた・見た目を変えたい場合は、Blockbench で直接編集できます。

## ✨ 剣モデルが唯一のソース

**編集するのは剣モード (`models/item/gunblade_sword.json`) と
剣テクスチャ (`textures/item/gunblade.png`) だけでOK。**

銃側への反映は**二重**に行われる:

1. **実行時 (本命)**: Mod が起動するたびに剣モデルから以下を生成して
   `<gamedir>/tacz/gunblade_pack` へ書き出す:
   - **3Dモデル** (`TaczGeoGenerator`): 形状・グループ・シリンダー
   - **テクスチャ**: 剣テクスチャをコピー
   - **slot/hud アイコン** (`IconRenderer`): 剣の `display.gui` と同じ構図で描画
   - **ドロップ時の大きさ**: 剣の `display.ground.scale` から
   - **額縁の向き・大きさ**: 剣の `display.fixed` から
     (rotation [rx,ry,rz] → TACZ側 [-rx,-ry,rz] に自動変換)

   → **剣側 (モデル/テクスチャ/display) を編集してゲームを再起動するだけで
   銃も全部追従** (配布 jar でも同じ仕組みで動く。python 不要)
2. ビルド時: gradle が `tools/convert_gunblade_geo.py` + `tools/make_icons.py`
   を実行し、リポジトリ内の生成物 (geo / uvテクスチャ / slot・hudアイコン) を
   更新する (エミュレータ検証や Blockbench 直接閲覧用 + アイコン生成)。

※ Java 版 (`TaczGeoGenerator`) と Python 版 (`tools/convert_gunblade_geo.py`) は
同一の変換規約で、出力の一致を検証済み。骨格やカメラを変える時は両方更新すること。

- 剣モデルは Blockbench で **Java Block/Item** 形式として開く
- Outliner の `stock` / `body` / `blade` グループ構成が銃側のボーンになる
- **3×3×4 のキューブがあるとシリンダー扱い**になり、リロードで回転する
- 銃側の geo を直接編集した場合、次のビルドで上書きされるので注意
  (骨格・カメラ等を恒久的に変えたい場合は `tools/convert_gunblade_geo.py` を編集)

ドロップ時・額縁内の大きさは剣モード (バニラ既定: ground 0.25 / fixed 0.5)
に揃えてあります (`gunblade_display.json` の `transform.scale`)。

## 編集対象ファイル

| 対象 | ファイル | Blockbench形式 |
|---|---|---|
| 射撃モード (TACZ銃) | `src/main/resources/assets/gun_and_weapon/custom/gunblade_pack/assets/gun_and_weapon/geo_models/gun/gunblade_geo.json` | **Bedrock Model** |
| 銃テクスチャ | 同 `.../textures/gun/uv/gunblade.png` (32x32) | — |
| 近接モード (剣) | `src/main/resources/assets/gun_and_weapon/models/item/gunblade_sword.json` | **Java Block/Item** |
| 剣テクスチャ | `src/main/resources/assets/gun_and_weapon/textures/item/gunblade.png` | — |
| リロード/inspectモーション | `.../gunblade_pack/assets/gun_and_weapon/animations/gunblade.animation.json` | Bedrock Animation |

## 開き方 (射撃モードの銃)

1. Blockbench → `File > Open Model` → `gunblade_geo.json` を選択
   (Bedrock Model として開かれる)
2. 左下の Textures パネル → `Import Texture` → `textures/gun/uv/gunblade.png` を選択
3. 編集後は `Ctrl+S` (⌘S) — 同じファイルに Bedrock 形式のまま保存される

反映: `bash build.sh` → ゲーム側の `tacz/gunblade_pack` フォルダを削除して起動
(dev環境なら `bash run_quick.sh` だけでOK。TACZが毎起動時に再コピーする)

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
- `tools/convert_gunblade_geo.py` で再生成しても、3×3×4 キューブが
  自動的に cylinder へ再割当される

## ⚠️ UVの注意

- UVは面ごと指定 (per-face UV)。**負の uv_size (UV反転) は使わない**こと。
  Blockbench上で反転が必要になったら、テクスチャ側の絵を反転させる。
- テクスチャサイズを変える場合は `description.texture_width/height` も
  Blockbenchが自動更新するのでそのままでOK。

## 手の位置・リロードモーションの調整

手の位置はモデルではなく `gunblade.animation.json` の `static_idle` が決めます
(`lefthand`/`righthand` の rotation/position)。Blockbench の Animate タブで
`gunblade_geo.json` に対してこのアニメファイルを開けば、プレビューしながら
キーフレームを調整できます。リロード (`reload_empty`/`reload_tactical`) と
`inspect` も同ファイルです。

## アイコン (slot/hud) の再生成

モデルを変えたらアイコンも作り直し:
```
python3 tools/make_icons.py
```
(横視点レンダリングで slot 64x64 / hud 180x60 を自動生成)

## ゲームを起動せずに見た目を確認する

```
python3 tools/tacz_emu.py   # を import して使う。例は docs/GUNPACK_NOTES.md 参照
```
TACZの描画コードを忠実に再現したレンダラで、geo を画像に描画できます。
UV崩れ・部品の分離はゲームを起動しなくてもここで発見できます。

## ゼロから作り直す場合

Chuzume氏のオリジナル (Java JSON) から変換し直すには:
```
python3 tools/convert_gunblade_geo.py
```
座標系・回転符号・骨格・カメラ設定込みで `gunblade_geo.json` を再生成します。
変換規約の詳細は [docs/GUNPACK_NOTES.md](docs/GUNPACK_NOTES.md)。
