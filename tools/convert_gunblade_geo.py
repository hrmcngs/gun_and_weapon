#!/usr/bin/env python3
"""剣モデル (gunblade_sword.json) から TACZ 銃 geo を生成する。
   剣モデルが唯一のソース: Blockbench で剣を編集 → 本スクリプトで銃側へ反映。
   - テクスチャは元の32x32そのまま (UV座標系を実績ある状態に戻す)
   - 骨格・カメラ・positioning は m870 (動作実績) の値をそのまま使用
   - 銃身ライン y を m870 に合わせるため全キューブを dy=-3.375 シフト
   - 腕キューブなし (m870と同様、TACZ側がプレイヤーの腕を描画)"""
import json, os, shutil

# 単一ソース: 剣モード (近接) の Java モデル。これを編集すれば銃側も追従する。
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = f"{REPO}/src/main/resources/assets/gun_and_weapon/models/item/gunblade_sword.json"
TEX = f"{REPO}/src/main/resources/assets/gun_and_weapon/textures/item/gunblade.png"
PACK = f"{REPO}/src/main/resources/assets/gun_and_weapon/custom/gunblade_pack/assets/gun_and_weapon"

DY = -3.375  # 銃身ライン 12.5 -> 9.125 (m870と同じ高さ)

src = json.load(open(SRC))
els = src["elements"]
TW, TH = src.get("texture_size", [32, 32])
SU, SV = TW / 16.0, TH / 16.0

FLIP_U = {"north", "south", "up", "down"}
SWAP = {"east": "west", "west": "east"}

def conv_face(name, face):
    u1, v1, u2, v2 = face["uv"]
    if name in FLIP_U:
        u1, u2 = u2, u1
    # Bedrock/TACZ は負の uv_size (UV反転) 非対応のため常に正方向に正規化する。
    # 反転が必要な面は絵柄が鏡像になるが、テクセル自体は正しい領域を指す。
    lu, hu = min(u1, u2), max(u1, u2)
    lv, hv = min(v1, v2), max(v1, v2)
    return {"uv": [round(lu*SU,4), round(lv*SV,4)], "uv_size": [round((hu-lu)*SU,4), round((hv-lv)*SV,4)]}

def conv_element(e):
    f, t = e["from"], e["to"]
    cube = {
        "origin": [round(8 - t[0],5), round(f[1]+DY,5), round(f[2],5)],
        "size": [round(t[0]-f[0],5), round(t[1]-f[1],5), round(t[2]-f[2],5)],
    }
    rot = e.get("rotation")
    if rot and rot.get("angle"):
        a, ax, o = rot["angle"], rot["axis"], rot["origin"]
        cube["pivot"] = [round(8-o[0],5), round(o[1]+DY,5), round(o[2],5)]
        cube["rotation"] = {"x": [-a,0,0], "y": [0,-a,0], "z": [0,0,a]}[ax]
    cube["uv"] = {SWAP.get(n, n): conv_face(n, fc) for n, fc in e["faces"].items()}
    return cube

groups = src.get("groups", [])
bones = [
    # ===== m870 (動作実績) と同じ骨格構造 =====
    {"name": "root", "pivot": [0, 8, 3]},
    {"name": "bullet_and_lefthand", "parent": "root", "pivot": [0, 7.625, -1.6]},
    {"name": "lefthand", "parent": "bullet_and_lefthand", "pivot": [-6, 19, 0]},
    {"name": "lefthand_pos", "parent": "lefthand", "pivot": [0, 8, 0]},
    {"name": "gun_and_righthand", "parent": "root", "pivot": [0, 7, 8]},
    {"name": "constraint", "parent": "gun_and_righthand", "pivot": [-0.225, 10.325, -8.825]},
    {"name": "muzzle_flash", "parent": "gun_and_righthand", "pivot": [0, 9.125, -14.5]},
    {"name": "shell", "parent": "gun_and_righthand", "pivot": [0, 9.6, 4]},
    {"name": "gun", "parent": "gun_and_righthand", "pivot": [0, 7, 8]},
    # リボルバーシリンダー用ボーン (リロードアニメが回転させる)。
    # キューブは Blockbench で body から移す。ピボット=銃身軸上。
    {"name": "cylinder", "parent": "gun", "pivot": [0, 9.125, 3], "cubes": []},
]
used = set()
for g in groups:
    bones.append({"name": g["name"], "parent": "gun", "pivot": [0, 7, 8],
                  "cubes": [conv_element(els[i]) for i in g["children"]]})
    used.update(g["children"])
rest = [conv_element(e) for i, e in enumerate(els) if i not in used]
if rest:
    bones.append({"name": "misc", "parent": "gun", "pivot": [0, 7, 8], "cubes": rest})

bones += [
    {"name": "righthand", "parent": "gun_and_righthand", "pivot": [6, 19, 0]},
    {"name": "righthand_pos", "parent": "righthand", "pivot": [0, 8, 0]},
    {"name": "positioning2", "parent": "gun_and_righthand", "pivot": [0, 0, 0]},
    {"name": "muzzle_pos", "parent": "positioning2", "pivot": [0, 9.125, -14]},
    # ===== カメラ・視点 (m870の実測値そのまま) =====
    {"name": "camera", "pivot": [2.8, 14.2, 15.5]},
    {"name": "views", "pivot": [2, 12, 12]},
    {"name": "idle_view", "parent": "views", "pivot": [2.8, 14.2, 15.5]},
    {"name": "iron_view", "parent": "views", "pivot": [0, 13.0, 13]},
    {"name": "refit_view", "parent": "views", "pivot": [20, 9, -2], "rotation": [0, 90, 0]},
    # ===== 表示位置 (m870の実測値そのまま) =====
    {"name": "positioning", "pivot": [0, 0, 0]},
    {"name": "fixed", "parent": "positioning", "pivot": [1.35, 8.275, -0.05], "rotation": [0, 90, -35]},  # 額縁: 剣モードと同じ斜め側面
    {"name": "ground", "parent": "positioning", "pivot": [0, -4, 2]},
    {"name": "thirdperson_hand", "parent": "positioning", "pivot": [0, 6.825, 5.425]},
]

geo = {
    "format_version": "1.12.0",
    "minecraft:geometry": [{
        "description": {
            "identifier": "geometry.gunblade",
            "texture_width": TW, "texture_height": TH,
            "visible_bounds_width": 4, "visible_bounds_height": 3,
            "visible_bounds_offset": [0, 0.5, 0],
        },
        "bones": bones,
    }],
}
# ---- シリンダー割当: body の 3x3x4 ドラムブロックを cylinder ボーンへ ----
_bones = {b["name"]: b for b in geo["minecraft:geometry"][0]["bones"]}
_body, _cyl = _bones["body"], _bones["cylinder"]
for _i, _c in enumerate(_body["cubes"]):
    if _c["size"] == [3, 3, 4]:
        _o, _s = _c["origin"], _c["size"]
        _cyl["pivot"] = [round(_o[0]+_s[0]/2, 5), round(_o[1]+_s[1]/2, 5), round(_o[2]+_s[2]/2, 5)]
        _cyl["cubes"] = [_c]
        del _body["cubes"][_i]
        break

json.dump(geo, open(f"{PACK}/geo_models/gun/gunblade_geo.json", "w"), indent=2)
print("geo written, bones:", len(bones))

# 剣のテクスチャを銃側へコピー (同一ソース)
shutil.copy(TEX, f"{PACK}/textures/gun/uv/gunblade.png")
print("texture restored to original 32x32")
