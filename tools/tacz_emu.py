#!/usr/bin/env python3
"""TACZ BedrockModel パーサ/レンダラの忠実なエミュレータ。
   BedrockModel.java / BedrockCubePerFace.java / BedrockPolygon.java (1.20.1) 準拠。"""
import json, math
from PIL import Image

def build_parts(geo_path):
    """TACZのconvertPivot/convertOrigin/回転をそのまま実装し、
       ワールド(ローカルモデル)空間の四角形リスト[(corners4, uvs4)]を返す"""
    d = json.load(open(geo_path))
    g = d["minecraft:geometry"][0]
    TW = g["description"]["texture_width"]; TH = g["description"]["texture_height"]
    bones = {b["name"]: b for b in g["bones"]}

    def bone_pos(b):
        # convertPivot(bones, idx)
        p = b.get("pivot", [0, 0, 0])
        par = b.get("parent")
        if par is not None:
            pp = bones[par].get("pivot", [0, 0, 0])
            return [p[0] - pp[0], pp[1] - p[1], p[2] - pp[2]]
        else:
            return [p[0], 24 - p[1], p[2]]

    def rot_matrix(rx, ry, rz):
        # poseStack: mulPose Z, Y, X の順 → v' = Rz(Ry(Rx v))
        def Rx(a):
            c, s = math.cos(a), math.sin(a)
            return [[1,0,0],[0,c,-s],[0,s,c]]
        def Ry(a):
            c, s = math.cos(a), math.sin(a)
            return [[c,0,s],[0,1,0],[-s,0,c]]
        def Rz(a):
            c, s = math.cos(a), math.sin(a)
            return [[c,-s,0],[s,c,0],[0,0,1]]
        def mul(A, B):
            return [[sum(A[i][k]*B[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
        return mul(Rz(rz), mul(Ry(ry), Rx(rx)))

    def apply(M, v):
        return [sum(M[i][k]*v[k] for k in range(3)) for i in range(3)]

    quads = []  # (4 corners worldspace, 4 uv(px), facename)

    def emit_cube(cube, transform):
        # transform: (origin_offset_fn) — キューブのローカル座標系→ワールド
        o = cube["origin"]; s = cube["size"]
        # ローカル起点 (convertOrigin 済みの値が渡ってくる)
        x, y, z = o
        xE, yE, zE = x + s[0], y + s[1], z + s[2]
        v1 = (x,  y,  z); v2 = (xE, y,  z); v3 = (xE, yE, z); v4 = (x,  yE, z)
        v5 = (x,  y,  zE); v6 = (xE, y,  zE); v7 = (xE, yE, zE); v8 = (x,  yE, zE)
        # BedrockCubePerFace: 面ごとの頂点順とUV割当 (idx0←(u2,v1) idx1←(u1,v1) idx2←(u1,v2) idx3←(u2,v2))
        FACES = {
            "down":  (v6, v5, v1, v2),
            "up":    (v3, v4, v8, v7),
            "west":  (v1, v5, v8, v4),
            "north": (v2, v1, v4, v3),
            "east":  (v6, v2, v3, v7),
            "south": (v5, v6, v7, v8),
        }
        for fname, vs in FACES.items():
            fuv = cube["uv"].get(fname)
            if fuv is None: continue
            u1, vv1 = fuv["uv"]; u2 = u1 + fuv["uv_size"][0]; v2_ = vv1 + fuv["uv_size"][1]
            uvs = [(u2, vv1), (u1, vv1), (u1, v2_), (u2, v2_)]
            corners = [transform(list(v)) for v in vs]
            quads.append((corners, uvs, fname))

    for b in g["bones"]:
        # ボーンのワールド変換を親からたどる
        chain = []
        cur = b
        while cur is not None:
            chain.append(cur)
            cur = bones.get(cur.get("parent"))
        chain.reverse()  # root -> leaf
        def make_tf(chain):
            steps = []
            for bb in chain:
                pos = bone_pos(bb)
                r = bb.get("rotation")
                M = rot_matrix(*[math.radians(a) for a in r]) if r else None
                steps.append((pos, M))
            def tf(v):
                # 子から順に: ローカル→…→ルート。poseStackは root から適用なので逆順に合成
                for pos, M in reversed(steps):
                    if M: v = apply(M, v)
                    v = [v[0] + pos[0], v[1] + pos[1], v[2] + pos[2]]
                return v
            return tf
        bone_tf = make_tf(chain)

        for cube in b.get("cubes", []):
            p = b.get("pivot", [0, 0, 0])
            crot = cube.get("rotation")
            if crot:
                cp = cube["pivot"]
                # convertPivot(bones, cube): [cp-p, p-cp(y), cp-p]
                cpos = [cp[0] - p[0], p[1] - cp[1], cp[2] - p[2]]
                M = rot_matrix(*[math.radians(a) for a in crot])
                o = cube["origin"]; s = cube["size"]
                # convertOrigin(cube): [o-cp, cp-o-sy, o-cp]
                lo = [o[0] - cp[0], cp[1] - o[1] - s[1], o[2] - cp[2]]
                def tf(v, M=M, cpos=cpos, bone_tf=bone_tf):
                    v = apply(M, v)
                    v = [v[0] + cpos[0], v[1] + cpos[1], v[2] + cpos[2]]
                    return bone_tf(v)
                emit_cube({"origin": lo, "size": s, "uv": cube["uv"]}, tf)
            else:
                o = cube["origin"]; s = cube["size"]
                # convertOrigin(bones, cube): [o-p, p-o-sy, o-p]
                lo = [o[0] - p[0], p[1] - o[1] - s[1], o[2] - p[2]]
                emit_cube({"origin": lo, "size": s, "uv": cube["uv"]}, bone_tf)

    return quads, TW, TH

def render(quads, TW, TH, tex_path, view, width=640, height=320, pad=8, flip=(1,1,1)):
    """view: world点→(sx, sy, depth)。flip: 描画前に各軸へ乗算 (最終視覚系の候補)"""
    tex = Image.open(tex_path).convert("RGBA")
    sxr, syr = tex.width / TW, tex.height / TH
    pts = []
    fq = []
    for corners, uvs, fname in quads:
        cs = [[c[0]*flip[0], c[1]*flip[1], c[2]*flip[2]] for c in corners]
        fq.append((cs, uvs, fname))
        pts += [view(c)[:2] for c in cs]
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    scale = min((width-2*pad)/(max(xs)-min(xs)+1e-9), (height-2*pad)/(max(ys)-min(ys)+1e-9))
    ox, oy = min(xs), max(ys)
    img = Image.new("RGBA", (width, height), (24,24,28,255))
    zbuf = [[-1e9]*width for _ in range(height)]
    for cs, uvs, fname in fq:
        c0, c1, c2, c3 = cs
        w3 = math.dist(c1, c0); h3 = math.dist(c1, c2)
        nu = max(2, int(w3*scale*1.7)); nv = max(2, int(h3*scale*1.7))
        for i in range(nu):
            fu = (i+0.5)/nu
            for j in range(nv):
                fv = (j+0.5)/nv
                # 頂点順: 0,1,2,3 = 四角形の周回。bilinear: p = (1-fv)*((1-fu)*v1 + fu*v0) + fv*((1-fu)*v2 + fu*v3)
                top = [c1[k] + (c0[k]-c1[k])*fu for k in range(3)]
                bot = [c2[k] + (c3[k]-c2[k])*fu for k in range(3)]
                p = [top[k] + (bot[k]-top[k])*fv for k in range(3)]
                u = uvs[1][0] + (uvs[0][0]-uvs[1][0])*fu
                vv = uvs[1][1] + (uvs[2][1]-uvs[1][1])*fv
                X, Y, D = view(p)
                px = int((X-ox)*scale)+pad; py = int((oy-Y)*scale)+pad
                if not (0 <= px < width and 0 <= py < height): continue
                if D <= zbuf[py][px]: continue
                tx = min(max(int(u*sxr), 0), tex.width-1)
                ty = min(max(int(vv*syr), 0), tex.height-1)
                col = tex.getpixel((tx, ty))
                if col[3] < 10: continue
                zbuf[py][px] = D
                img.putpixel((px, py), (col[0], col[1], col[2], 255))
    return img

VIEW_XSIDE_POS = lambda p: (-p[2], p[1], p[0])   # +x側から (右側面)
VIEW_XSIDE_NEG = lambda p: (p[2], p[1], -p[0])   # -x側から (左側面)
