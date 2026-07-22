# TACZ ガンパック製作で得た知見 (gunblade)

Chuzume氏の Java JSON モデルを TACZ (Bedrock geo) に移植する過程でハマった点の記録。

## モデル変換 (Java JSON → Bedrock geo)

変換スクリプト: [tools/convert_gunblade_geo.py](../tools/convert_gunblade_geo.py)
(座標系・骨格・UV変換すべて込み)。

検証は [tools/tacz_emu.py](../tools/tacz_emu.py) — **TACZ の BedrockModel.java /
BedrockCubePerFace.java (GitHub MCModderAnchor/TACZ 1.20.1) を忠実に再現した
オフラインレンダラ**。ゲームを起動せずに変換結果を目視検証できる。
m870 を描画して正しく見えること (=エミュレータ自体の正しさ) を確認済み。

- **座標系**: X軸ミラー+センタリング `origin_x = 8 - to_x`。y/z はそのまま
  (銃身ラインを m870 に合わせて y を -3.375 シフト)。
  TACZ パーサは y を内部で反転する (convertOrigin: `pivot_y - origin_y - size_y`)。
- **回転符号** (実機+ソース確認済み): Java の `rotation.angle` は
  X軸 `-a` / Y軸 `-a` / Z軸 `+a` に変換。符号を間違えると遠いピボットを持つ
  回転キューブ (ストック等) が本体から分離して飛ぶ。
  TACZ の回転適用順は poseStack で Z→Y→X。
- **UV反転の正規化**: Java モデルの反転UV (u1>u2 等) は正方向に正規化している
  (絵柄は鏡像になるがこのドット絵では見分け不可)。
  ※TACZ の BedrockPolygon は数式上は負の uv_size も処理できるが、
  安全のため正規化を維持。
- **面のUV割当** (BedrockCubePerFace 準拠): 各面の頂点順は固定で、
  vertex0←(u2,v1) / vertex1←(u1,v1) / vertex2←(u1,v2) / vertex3←(u2,v2)。
  east↔west は入れ替え、north/south/up/down は u 反転で対応。

## 骨格 (必須ボーン)

動作実績のある公式銃 (m870) の骨格をそのまま流用するのが安全:

```
root → bullet_and_lefthand → lefthand → lefthand_pos
     → gun_and_righthand → gun(キューブ), righthand → righthand_pos,
                           muzzle_flash, muzzle_pos, shell, constraint
camera / views(idle_view, iron_view, refit_view) / positioning(fixed, ground, thirdperson_hand)
```

- **camera / idle_view**: 一人称の目の位置。**ストックの中に置く**
  (m870: 銃後端 z=23 に対し camera z=10)。銃の完全後方に置くと
  画面いっぱいの巨大表示になる。背の高いモデルは背骨より上に
  (gunblade: `[2.8, 14.2, 15.5]`、モデル最高点 y=12.6)。
- **iron_view**: エイム時の視点。x=0 (中央) + 照準線の高さ。

## 手の配置 (static_idle)

手の位置は geo ではなく**アニメーションの `static_idle`** (1フレーム常駐) が決める。
これが無いと手がボーン素位置に放置されて変な場所に出る。
m870 の static_idle の rotation/scale を流用し、position の z だけ
自分の銃のグリップ位置に合わせる。
リロード等で手を動かすアニメは static ポーズ基準の絶対値で書くこと。

## デバッグ手法

- `GUNBLADE_AUTOSHOT=1` で起動すると 5秒毎に自動スクショ
  (`run/screenshots/gunblade_debug_N.png`)。
- `run/debug_cmd.txt` に書くと毎tick実行される:
  `fp`/`tp`/`tpf` (視点), `shot` (即スクショ), `/コマンド` (サーバーコマンド)。
- ワールド自動参加: `QUICKPLAY_WORLD="New World" bash run_client.sh`
- 実装: `gun_and_weapon.debug.DebugScreenshotter` (環境変数なしでは完全に無効)。

## その他

- ガンパックは TACZ 1.1.x 公式API `ResourceManager.registerExportResource` で登録。
  毎起動時に `<gamedir>/tacz/gunblade_pack` へ削除→再コピーされる。
- パック形式: `gunpack.meta.json` は `{"namespace": "gun_and_weapon"}` のみ。
  レガシー形式 (description/version...) は読み込まれない。
- display json の `slot`/`hud` テクスチャが無いとアイコンがミッシングテクスチャになる。
  アイコンは `make_geo2.py` 系のスクリプトでモデルから横視点レンダリングして生成。
- サウンドは他パックの実在ファイルを参照できる (例: `tacz:m870/m870_shoot`)。
  参照先の .ogg が実在するか jar 内 `tacz_sounds/` を確認すること。

## 統合アイテム (単一ID + NBTモード切替)

`gun_and_weapon:gunblade_sword` は TACZ の `ModernKineticGunItem` を継承した
統合アイテム (`GunbladeItem`)。NBT `gunblade:mode` (melee/ranged) で切替。

- **melee**: `getGunId` がダミーID (`gunblade_melee_dummy`) を返す
  → TACZ の index 解決が失敗し、銃描画/射撃/HUD が素通り。
  ※ null を返すと TACZ の `isSame` が NPE でクラッシュするのでダミーID必須
- TACZ は IGun 所持中 `RenderHandEvent` と クリック入力を無条件キャンセルする
  → melee 時は LOWEST 優先度 + receiveCanceled で取り消して
  バニラ描画/攻撃を復元 (`GunbladeClientInput`)
- モデルは `GunbladeInventoryModel` のラッパーで切替:
  melee=剣クアッド全コンテキスト / ranged=GUIのみ剣・他は BEWLR (TACZ)
- shoot/startReload/startBolt/fireSelect/melee は melee 時 no-op オーバーライド
- 旧形式 (tacz:modern_kinetic_gun) は [F] 切替時に統合アイテムへ自動移行
