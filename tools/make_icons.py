#!/usr/bin/env python3
"""ガンブレードの slot / hud アイコンを「剣モードのアイテム3D表示」と同じ見た目で生成する。

使い方 (リポジトリルートから):
    python3 tools/make_icons.py

剣モデル (models/item/gunblade_sword.json) の display.gui と同じ回転角で
Java モデルをレンダリングするので、インベントリの剣アイコンと同じ構図になる。
モデル/テクスチャ/display.gui を変更したら再実行すること。
"""
import json, math, os
from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL = f"{REPO}/src/main/resources/assets/gun_and_weapon/models/item/gunblade_sword.json"
TEX = f"{REPO}/src/main/resources/assets/gun_and_weapon/custom/gunblade_pack/assets/gun_and_weapon/textures/gun/uv/gunblade.png"
OUT = f"{REPO}/src/main/resources/assets/gun_and_weapon/custom/gunblade_pack/assets/gun_and_weapon/textures/gun"

model = json.load(open(MODEL))
els = model["elements"]
gui = model.get("display", {}).get("gui", {})
GUI_ROT = gui.get("rotation", [30, 225, 0])
tex = Image.open(TEX).convert("RGBA")

# vanilla の面ごとのUV隅マッピング (検証済み): (top-left, top-right, bottom-left)
def face_corners(name, f, t):
    x0, y0, z0 = f; x1, y1, z1 = t
    if name == "north": return (x1,y1,z0), (x0,y1,z0), (x1,y0,z0)
    if name == "south": return (x0,y1,z1), (x1,y1,z1), (x0,y0,z1)
    if name == "west":  return (x0,y1,z0), (x0,y1,z1), (x0,y0,z0)
    if name == "east":  return (x1,y1,z1), (x1,y1,z0), (x1,y0,z1)
    if name == "up":    return (x0,y1,z0), (x1,y1,z0), (x0,y1,z1)
    if name == "down":  return (x0,y0,z1), (x1,y0,z1), (x0,y0,z0)

def rot_axis(p, axis, ang, origin):
    x, y, z = (p[0]-origin[0], p[1]-origin[1], p[2]-origin[2])
    c, s = math.cos(ang), math.sin(ang)
    if axis == "x":   y, z = y*c - z*s, y*s + z*c
    elif axis == "y": x, z = x*c + z*s, -x*s + z*c
    elif axis == "z": x, y = x*c - y*s, x*s + y*c
    return (x+origin[0], y+origin[1], z+origin[2])

def gui_transform(p):
    # 中心(8,8,8)へ移動 → rotationXYZ (Zを最初にベクトルへ適用)
    x, y, z = p[0]-8, p[1]-8, p[2]-8
    rx, ry, rz = [math.radians(a) for a in GUI_ROT]
    # Rz
    c, s = math.cos(rz), math.sin(rz); x, y = x*c - y*s, x*s + y*c
    # Ry
    c, s = math.cos(ry), math.sin(ry); x, z = x*c + z*s, -x*s + z*c
    # Rx
    c, s = math.cos(rx), math.sin(rx); y, z = y*c - z*s, y*s + z*c
    return (x, y, z)

# 全面のクアッド (corners 4点 + uv 4点) を収集
quads = []
for e in els:
    f, t = e["from"], e["to"]
    rot = e.get("rotation") or {}
    ang = math.radians(rot.get("angle", 0))
    axis = rot.get("axis", "y"); origin = rot.get("origin", [8, 8, 8])
    for name, face in e["faces"].items():
        u1, v1, u2, v2 = face["uv"]
        tl, tr, bl = face_corners(name, f, t)
        br = tuple(tr[i] + bl[i] - tl[i] for i in range(3))
        cs = []
        for p in (tl, tr, bl, br):
            if ang: p = rot_axis(p, axis, ang, origin)
            cs.append(gui_transform(p))
        quads.append((cs, (u1, v1, u2, v2)))

def render(width, height, pad=2):
    xs = [c[0] for q in quads for c in q[0]]
    ys = [c[1] for q in quads for c in q[0]]
    scale = min((width - 2*pad) / (max(xs)-min(xs)+1e-9), (height - 2*pad) / (max(ys)-min(ys)+1e-9))
    ox = (max(xs)+min(xs))/2; oy = (max(ys)+min(ys))/2
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    zbuf = [[-1e9]*width for _ in range(height)]
    for (tl, tr, bl, br), (u1, v1, u2, v2) in quads:
        w3 = math.dist(tl, tr); h3 = math.dist(tl, bl)
        nu = max(2, int(w3*scale*1.8)); nv = max(2, int(h3*scale*1.8))
        for i in range(nu):
            fu = (i+0.5)/nu
            for j in range(nv):
                fv = (j+0.5)/nv
                top = [tl[k] + (tr[k]-tl[k])*fu for k in range(3)]
                bot = [bl[k] + (br[k]-bl[k])*fu for k in range(3)]
                p = [top[k] + (bot[k]-top[k])*fv for k in range(3)]
                px = int((p[0]-ox)*scale + width/2)
                py = int((oy-p[1])*scale + height/2)
                if not (0 <= px < width and 0 <= py < height): continue
                if p[2] <= zbuf[py][px]: continue
                u = u1 + (u2-u1)*fu; v = v1 + (v2-v1)*fv
                txp = min(max(int(u/16*tex.width), 0), tex.width-1)
                typ = min(max(int(v/16*tex.height), 0), tex.height-1)
                col = tex.getpixel((txp, typ))
                if col[3] < 10: continue
                zbuf[py][px] = p[2]
                img.putpixel((px, py), col[:3] + (255,))
    return img

render(64, 64).save(f"{OUT}/slot/gunblade.png")
render(180, 60).save(f"{OUT}/hud/gunblade.png")
print("icons written (GUI view, rot", GUI_ROT, ")")
