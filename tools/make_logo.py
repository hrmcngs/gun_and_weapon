#!/usr/bin/env python3
"""MODロゴ生成: 剣モデルのGUI構図レンダリング + 背景装飾
   - logo.png (128x128, jar内 mods.toml 用)
   - promo/curseforge_icon.png (400x400, CurseForgeプロジェクトアイコン用)"""
import json, math, os, sys
sys.path.insert(0, "tools")
from PIL import Image, ImageDraw

# tools/make_icons.py のレンダリング部を流用するため一時的に模倣
REPO = os.getcwd()
MODEL = f"{REPO}/src/main/resources/assets/gun_and_weapon/models/item/gunblade_sword.json"
TEX = f"{REPO}/src/main/resources/assets/gun_and_weapon/textures/item/gunblade.png"

model = json.load(open(MODEL))
els = model["elements"]
gui = model.get("display", {}).get("gui", {})
GUI_ROT = gui.get("rotation", [30, 225, 0])
tex = Image.open(TEX).convert("RGBA")

def face_corners(name, f, t):
    x0, y0, z0 = f; x1, y1, z1 = t
    m = {
        "north": ((x1,y1,z0), (x0,y1,z0), (x1,y0,z0)),
        "south": ((x0,y1,z1), (x1,y1,z1), (x0,y0,z1)),
        "west":  ((x0,y1,z0), (x0,y1,z1), (x0,y0,z0)),
        "east":  ((x1,y1,z1), (x1,y1,z0), (x1,y0,z1)),
        "up":    ((x0,y1,z0), (x1,y1,z0), (x0,y1,z1)),
        "down":  ((x0,y0,z1), (x1,y0,z1), (x0,y0,z0)),
    }
    tl, tr, bl = m[name]
    br = tuple(tr[i] + bl[i] - tl[i] for i in range(3))
    return tl, tr, bl, br

def rot_axis(p, axis, ang, o):
    x, y, z = p[0]-o[0], p[1]-o[1], p[2]-o[2]
    c, s = math.cos(ang), math.sin(ang)
    if axis == "x": y, z = y*c - z*s, y*s + z*c
    elif axis == "y": x, z = x*c + z*s, -x*s + z*c
    else: x, y = x*c - y*s, x*s + y*c
    return (x+o[0], y+o[1], z+o[2])

def gui_transform(p):
    x, y, z = p[0]-8, p[1]-8, p[2]-8
    rx, ry, rz = [math.radians(a) for a in GUI_ROT]
    c, s = math.cos(rz), math.sin(rz); x, y = x*c - y*s, x*s + y*c
    c, s = math.cos(ry), math.sin(ry); x, z = x*c + z*s, -x*s + z*c
    c, s = math.cos(rx), math.sin(rx); y, z = y*c - z*s, y*s + z*c
    return (x, y, z)

quads = []
for e in els:
    f, t = e["from"], e["to"]
    rot = e.get("rotation") or {}
    ang = math.radians(rot.get("angle", 0)); axis = rot.get("axis", "y")
    origin = rot.get("origin", [8, 8, 8])
    for name, face in e["faces"].items():
        uv = face["uv"]
        cs = []
        for p in face_corners(name, f, t):
            if ang: p = rot_axis(p, axis, ang, origin)
            cs.append(gui_transform(p))
        quads.append((cs, uv))

def render_blade(size, pad):
    xs = [c[0] for q in quads for c in q[0]]
    ys = [c[1] for q in quads for c in q[0]]
    scale = (size - 2*pad) / max(max(xs)-min(xs), max(ys)-min(ys))
    ox = (max(xs)+min(xs))/2; oy = (max(ys)+min(ys))/2
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    zbuf = [[-1e9]*size for _ in range(size)]
    for (tl, tr, bl, br), (u1, v1, u2, v2) in quads:
        w3 = math.dist(tl, tr); h3 = math.dist(tl, bl)
        nu = max(2, int(w3*scale*1.8)); nv = max(2, int(h3*scale*1.8))
        for i in range(nu):
            fu = (i+0.5)/nu
            for j in range(nv):
                fv = (j+0.5)/nv
                p = [ (tl[k]+(tr[k]-tl[k])*fu) + ((bl[k]+(br[k]-bl[k])*fu) - (tl[k]+(tr[k]-tl[k])*fu))*fv for k in range(3)]
                px = int((p[0]-ox)*scale + size/2); py = int((oy-p[1])*scale + size/2)
                if not (0 <= px < size and 0 <= py < size): continue
                if p[2] <= zbuf[py][px]: continue
                u = u1 + (u2-u1)*fu; v = v1 + (v2-v1)*fv
                txp = min(max(int(u/16*tex.width), 0), tex.width-1)
                typ = min(max(int(v/16*tex.height), 0), tex.height-1)
                col = tex.getpixel((txp, typ))
                if col[3] < 10: continue
                zbuf[py][px] = p[2]
                img.putpixel((px, py), col[:3]+(255,))
    return img

def make_icon(size):
    # 背景: 濃紺のラジアルグラデ + 金の縁
    bg = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    d = ImageDraw.Draw(bg)
    cx = size/2
    for r in range(size, 0, -1):
        t_ = r/size
        col = (int(24+14*(1-t_)), int(26+16*(1-t_)), int(36+22*(1-t_)), 255)
        d.ellipse([cx-r, cx-r, cx+r, cx+r], fill=col)
    # 金の縁 (角丸)
    bw = max(2, size//48)
    for i in range(bw):
        d.rounded_rectangle([i, i, size-1-i, size-1-i], radius=size//10,
                            outline=(212, 175, 90, 255) if i < bw//2+1 else (140, 110, 50, 255))
    # 刃を薄く光らせる背光
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([size*0.18, size*0.18, size*0.82, size*0.82], fill=(90, 120, 160, 60))
    bg = Image.alpha_composite(bg, glow)
    blade = render_blade(size, int(size*0.12))
    # ドロップシャドウ
    shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sh_mask = blade.split()[3].point(lambda a: min(a, 120))
    shadow.paste((0, 0, 0, 130), (int(size*0.02), int(size*0.03)), sh_mask)
    bg = Image.alpha_composite(bg, shadow)
    bg = Image.alpha_composite(bg, blade)
    return bg

os.makedirs("promo", exist_ok=True)
make_icon(400).save("promo/curseforge_icon.png")
make_icon(128).save("src/main/resources/logo.png")
print("icons written: promo/curseforge_icon.png (400x400), src/main/resources/logo.png (128x128)")
