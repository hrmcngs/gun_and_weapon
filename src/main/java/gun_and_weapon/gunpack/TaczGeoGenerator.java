package gun_and_weapon.gunpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 剣モード (models/item/gunblade_sword.json — Java Block/Item モデル) を
 * TACZ の Bedrock geo に変換する。tools/convert_gunblade_geo.py の Java 移植。
 *
 * 剣モデルが唯一のソース: 起動時に毎回変換されるため、
 * Blockbench で剣モデルを編集して再起動すれば銃側も必ず追従する。
 *
 * 変換規約 (docs/GUNPACK_NOTES.md 参照):
 *  - x: ミラー+センタリング (origin_x = 8 - to_x) / y: -3.375 シフト / z: そのまま
 *  - 回転: X軸 -a / Y軸 -a / Z軸 +a、ピボットも同変換
 *  - UV: east↔west 入替、north/south/up/down は u 反転、負の uv_size は正方向へ正規化
 *  - 骨格: m870 実測値 (カメラ・視点・手・positioning)
 *  - 3x3x4 のキューブは cylinder ボーンへ (リロードで回転)
 */
public final class TaczGeoGenerator {

	private static final double DY = -3.375; // 銃身ライン 12.5 -> 9.125 (m870 と同じ高さ)

	private TaczGeoGenerator() {}

	/** 剣モデル JSON (リソースストリーム) から TACZ geo JSON 文字列を生成する */
	public static String generate(InputStreamReader swordModelReader) {
		JsonObject model = new Gson().fromJson(swordModelReader, JsonObject.class);
		JsonArray elements = model.getAsJsonArray("elements");

		int texW = 32, texH = 32;
		if (model.has("texture_size")) {
			JsonArray ts = model.getAsJsonArray("texture_size");
			texW = ts.get(0).getAsInt();
			texH = ts.get(1).getAsInt();
		}
		double su = texW / 16.0, sv = texH / 16.0;

		JsonArray bones = new JsonArray();
		// ===== m870 (動作実績) と同じ骨格構造 =====
		bones.add(bone("root", null, 0, 8, 3));
		bones.add(bone("bullet_and_lefthand", "root", 0, 7.625, -1.6));
		bones.add(bone("lefthand", "bullet_and_lefthand", -6, 19, 0));
		bones.add(bone("lefthand_pos", "lefthand", 0, 8, 0));
		bones.add(bone("gun_and_righthand", "root", 0, 7, 8));
		bones.add(bone("constraint", "gun_and_righthand", -0.225, 10.325, -8.825));
		bones.add(bone("muzzle_flash", "gun_and_righthand", 0, 9.125, -14.5));
		bones.add(bone("shell", "gun_and_righthand", 0, 9.6, 4));
		JsonObject gun = bone("gun", "gun_and_righthand", 0, 7, 8);
		bones.add(gun);
		JsonObject cylinder = bone("cylinder", "gun", 0, 10.125, 4);
		cylinder.add("cubes", new JsonArray());
		bones.add(cylinder);

		// ===== グループ (stock/body/blade) ごとにキューブを変換 =====
		JsonArray groups = model.has("groups") ? model.getAsJsonArray("groups") : null;
		boolean[] used = new boolean[elements.size()];
		if (groups != null) {
			for (JsonElement ge : groups) {
				if (!ge.isJsonObject()) continue;
				JsonObject g = ge.getAsJsonObject();
				JsonObject b = bone(g.get("name").getAsString(), "gun", 0, 7, 8);
				JsonArray cubes = new JsonArray();
				for (JsonElement ch : g.getAsJsonArray("children")) {
					int idx = ch.getAsInt();
					cubes.add(convertElement(elements.get(idx).getAsJsonObject(), su, sv));
					used[idx] = true;
				}
				b.add("cubes", cubes);
				bones.add(b);
			}
		}
		JsonArray rest = new JsonArray();
		for (int i = 0; i < elements.size(); i++) {
			if (!used[i]) rest.add(convertElement(elements.get(i).getAsJsonObject(), su, sv));
		}
		if (rest.size() > 0) {
			JsonObject misc = bone("misc", "gun", 0, 7, 8);
			misc.add("cubes", rest);
			bones.add(misc);
		}

		bones.add(bone("righthand", "gun_and_righthand", 6, 19, 0));
		bones.add(bone("righthand_pos", "righthand", 0, 8, 0));
		bones.add(bone("positioning2", "gun_and_righthand", 0, 0, 0));
		bones.add(bone("muzzle_pos", "positioning2", 0, 9.125, -14));
		// ===== カメラ・視点 =====
		bones.add(bone("camera", null, 2.8, 14.2, 15.5));
		bones.add(bone("views", null, 2, 12, 12));
		bones.add(bone("idle_view", "views", 2.8, 14.2, 15.5));
		bones.add(bone("iron_view", "views", 0, 13.0, 13));
		JsonObject refitView = bone("refit_view", "views", 20, 9, -2);
		refitView.add("rotation", arr(0, 90, 0));
		bones.add(refitView);
		// 改造画面で拡張マガジンスロットを選んだ時のカメラ。
		// TACZ は refit_<type>_view という名前のボーンを探す — 無いとカメラが
		// 原点に飛んで変な場所を映すため、シリンダーが中央に来る位置に置く。
		JsonObject refitMagView = bone("refit_extended_mag_view", "views", 12, 10.5, 4);
		refitMagView.add("rotation", arr(0, 90, 0));
		bones.add(refitMagView);
		// ===== 表示位置 =====
		bones.add(bone("positioning", null, 0, 0, 0));
		JsonObject fixed = bone("fixed", "positioning", 1.35, 8.275, -0.05);
		fixed.add("rotation", fixedRotation(model)); // 額縁: 剣モデルの display.fixed から導出
		bones.add(fixed);
		bones.add(bone("ground", "positioning", 0, -4, 2));
		bones.add(bone("thirdperson_hand", "positioning", 0, 6.825, 5.425));

		// ===== 3x3x4 キューブを cylinder へ移す =====
		assignCylinder(bones, cylinder);

		JsonObject description = new JsonObject();
		description.addProperty("identifier", "geometry.gunblade");
		description.addProperty("texture_width", texW);
		description.addProperty("texture_height", texH);
		description.addProperty("visible_bounds_width", 4);
		description.addProperty("visible_bounds_height", 3);
		description.add("visible_bounds_offset", arr(0, 0.5, 0));

		JsonObject geometry = new JsonObject();
		geometry.add("description", description);
		geometry.add("bones", bones);
		JsonArray geoList = new JsonArray();
		geoList.add(geometry);
		JsonObject rootObj = new JsonObject();
		rootObj.addProperty("format_version", "1.12.0");
		rootObj.add("minecraft:geometry", geoList);

		return new GsonBuilder().setPrettyPrinting().create().toJson(rootObj);
	}

	private static JsonObject convertElement(JsonObject e, double su, double sv) {
		JsonArray f = e.getAsJsonArray("from");
		JsonArray t = e.getAsJsonArray("to");
		double fx = f.get(0).getAsDouble(), fy = f.get(1).getAsDouble(), fz = f.get(2).getAsDouble();
		double tx = t.get(0).getAsDouble(), ty = t.get(1).getAsDouble(), tz = t.get(2).getAsDouble();

		JsonObject cube = new JsonObject();
		cube.add("origin", arr(round(8 - tx), round(fy + DY), round(fz)));
		cube.add("size", arr(round(tx - fx), round(ty - fy), round(tz - fz)));

		if (e.has("rotation")) {
			JsonObject rot = e.getAsJsonObject("rotation");
			double angle = rot.get("angle").getAsDouble();
			if (angle != 0) {
				String axis = rot.get("axis").getAsString();
				JsonArray o = rot.getAsJsonArray("origin");
				cube.add("pivot", arr(
						round(8 - o.get(0).getAsDouble()),
						round(o.get(1).getAsDouble() + DY),
						round(o.get(2).getAsDouble())));
				switch (axis) {
					case "x" -> cube.add("rotation", arr(-angle, 0, 0));
					case "y" -> cube.add("rotation", arr(0, -angle, 0));
					case "z" -> cube.add("rotation", arr(0, 0, angle));
				}
			}
		}

		JsonObject uv = new JsonObject();
		for (Map.Entry<String, JsonElement> entry : e.getAsJsonObject("faces").entrySet()) {
			String name = entry.getKey();
			JsonArray fuv = entry.getValue().getAsJsonObject().getAsJsonArray("uv");
			double u1 = fuv.get(0).getAsDouble(), v1 = fuv.get(1).getAsDouble();
			double u2 = fuv.get(2).getAsDouble(), v2 = fuv.get(3).getAsDouble();
			// north/south/up/down は u 反転
			if (name.equals("north") || name.equals("south") || name.equals("up") || name.equals("down")) {
				double tmp = u1; u1 = u2; u2 = tmp;
			}
			// 負の uv_size を出さない (TACZ 安全側): 正方向へ正規化
			double lu = Math.min(u1, u2), hu = Math.max(u1, u2);
			double lv = Math.min(v1, v2), hv = Math.max(v1, v2);
			JsonObject face = new JsonObject();
			face.add("uv", arr(round(lu * su), round(lv * sv)));
			face.add("uv_size", arr(round((hu - lu) * su), round((hv - lv) * sv)));
			// east↔west 入替
			String outName = name.equals("east") ? "west" : name.equals("west") ? "east" : name;
			uv.add(outName, face);
		}
		cube.add("uv", uv);
		return cube;
	}

	/** body 等から 3x3x4 のドラムキューブを探して cylinder ボーンへ移す */
	private static void assignCylinder(JsonArray bones, JsonObject cylinder) {
		for (JsonElement be : bones) {
			JsonObject b = be.getAsJsonObject();
			if (!b.has("cubes") || b == cylinder) continue;
			JsonArray cubes = b.getAsJsonArray("cubes");
			for (int i = 0; i < cubes.size(); i++) {
				JsonObject c = cubes.get(i).getAsJsonObject();
				JsonArray s = c.getAsJsonArray("size");
				if (s.get(0).getAsDouble() == 3 && s.get(1).getAsDouble() == 3 && s.get(2).getAsDouble() == 4) {
					JsonArray o = c.getAsJsonArray("origin");
					double cx = o.get(0).getAsDouble() + 1.5;
					double cy = o.get(1).getAsDouble() + 1.5;
					double oz = o.get(2).getAsDouble();
					cylinder.add("pivot", arr(round(cx), round(cy), round(oz + 2)));
					cylinder.getAsJsonArray("cubes").add(c);
					cubes.remove(i);
					addCylinderBullets(bones, cx, cy, oz);
					return;
				}
			}
		}
	}

	/**
	 * ドラム内部に 12ゲージシェル (TACZ の tacz:12g と同じ配色: 赤いハル+真鍮ベース) を
	 * 4チャンバー × 前後2発 = 8発 仕込む (装弾数 8 と一致)。
	 * チャンバーごとに独立したボーン (bullet_1〜4、cylinder の子) にして、
	 * リロードアニメで1室ずつ (2発1組で) 装填する動きを付けられるようにする。
	 * 通常時はドラムに完全に内包されて見えない (TACZ標準銃の bullet ボーンと同じ常設ジオ方式)。
	 * UV はテクスチャ右下に埋めた 12g パレット (geo空間座標、64px実画像の1/2):
	 *   赤ハル[30,30] / 暗赤[31,30] / 真鍮[30,31] / 明真鍮[31,31]
	 */
	private static void addCylinderBullets(JsonArray bones, double cx, double cy, double oz) {
		// ドラム軸 (z) の周りにひし形配置 (4室)。z- が銃口側、z+ が後方 (リム)
		double[][] centers = { { 0, 0.85 }, { 0.85, 0 }, { 0, -0.85 }, { -0.85, 0 } };
		// スピードローダー1回分 = 4発1組の「段」ボーン。
		//   bullet_1 = 前段 (奥。最初に装填される)
		//   bullet_2 = 後段 (手前。2回目に装填される)
		double[] rowZ = { oz + 0.25, oz + 1.95 };
		for (int row = 0; row < 2; row++) {
			JsonObject rowBone = bone("bullet_" + (row + 1), "cylinder", round(cx), round(cy), round(oz + 2));
			JsonArray cubes = new JsonArray();
			for (double[] cc : centers) {
				addShell(cubes, cx + cc[0], cy + cc[1], rowZ[row]);
			}
			rowBone.add("cubes", cubes);
			bones.add(rowBone);
		}
		addLoaderBones(bones, cx, cy, oz);
	}

	/**
	 * 装填道具の可視化ボーン。TACZ は mag_extended_N という名前のボーンを
	 * 「レベルNの拡張マガジン装着中のみ」描画する (BedrockGunModel の機能ボーン)。
	 * これを利用して:
	 *   mag_extended_1 = 帯状スピードストリップ (level 1)
	 *   mag_extended_2 = 円状スピードローダー (level 2)
	 * ジオメトリはドラム後端の内部 (z = oz+3.5〜3.98) に置いてあり、
	 * 通常時はドラムに隠れて見えない。リロードアニメがボーンを後方へ
	 * 動かした時だけ姿を現す = 「装着時かつリロード中のみ表示」。
	 */
	private static void addLoaderBones(JsonArray bones, double cx, double cy, double oz) {
		// --- mag_extended_1: 帯状ストリップ (革の帯) ---
		JsonObject strip = bone("mag_extended_1", "cylinder", round(cx), round(cy), round(oz + 2));
		JsonArray stripCubes = new JsonArray();
		// 革の帯 (横長・ドラム断面に収まる幅)
		stripCubes.add(loaderCube(cx - 1.3, cy - 0.35, oz + 3.6, 2.6, 0.7, 0.18, 29, 30));
		// 上下の縁 (濃い方向け…同色でシンプルに)
		stripCubes.add(loaderCube(cx - 1.3, cy + 0.35, oz + 3.58, 2.6, 0.14, 0.22, 29, 30));
		strip.add("cubes", stripCubes);
		bones.add(strip);

		// --- mag_extended_2: 円状スピードローダー (灰色の円盤 + ノブ) ---
		JsonObject loader = bone("mag_extended_2", "cylinder", round(cx), round(cy), round(oz + 2));
		JsonArray loaderCubes = new JsonArray();
		// 円盤2枚を45°クロスで八角形風に。
		// 45°回転した正方形の対角半径 (辺×√2/2) がドラム半径 1.5 を超えないよう
		// 2.0×2.0 に抑える (対角半径 1.41)。大きくするとドラム側面から突き出る。
		JsonObject discA = loaderCube(cx - 1.0, cy - 1.0, oz + 3.55, 2.0, 2.0, 0.2, 28, 30);
		JsonObject discB = loaderCube(cx - 1.0, cy - 1.0, oz + 3.55, 2.0, 2.0, 0.2, 28, 30);
		discB.add("rotation", arr(0, 0, 45));
		discB.add("pivot", arr(round(cx), round(cy), round(oz + 3.65)));
		loaderCubes.add(discA);
		loaderCubes.add(discB);
		// 背面ノブ (ドラム後端 oz+4 の内側に収める)
		loaderCubes.add(loaderCube(cx - 0.45, cy - 0.45, oz + 3.75, 0.9, 0.9, 0.22, 28, 30));
		loader.add("cubes", loaderCubes);
		bones.add(loader);

		// --- shell_hand: 装填ループで手に持って押し込む1発分のシェル ---
		// 通常時はドラム中心軸の内部に隠しておき、reload_push_* アニメで
		// 手元 → ドラム後方 → 押し込み (ドラム内=非表示) と動かす。
		JsonObject shellHand = bone("shell_hand", "cylinder", round(cx), round(cy), round(oz + 2));
		JsonArray shellCubes = new JsonArray();
		addShell(shellCubes, cx, cy, oz + 0.8);
		shellHand.add("cubes", shellCubes);
		bones.add(shellHand);
	}

	/** 単色UVのキューブ (origin指定版) */
	private static JsonObject loaderCube(double ox, double oy, double oz, double w, double h, double d,
			double u, double v) {
		JsonObject cube = new JsonObject();
		cube.add("origin", arr(round(ox), round(oy), round(oz)));
		cube.add("size", arr(w, h, d));
		JsonObject uv = new JsonObject();
		for (String face : new String[] { "north", "south", "east", "west", "up", "down" }) {
			uv.add(face, faceUv(u, v, 0.5, 0.5));
		}
		cube.add("uv", uv);
		return cube;
	}

	/** 1発分の 12g シェル (ハル+真鍮ベース+リム) を zStart から後方向きに積む */
	private static void addShell(JsonArray cubes, double bx, double by, double zStart) {
		// ハル (赤いプラスチック筒。先端 north 面は暗赤 = クリンプ)
		cubes.add(bulletCube(bx, by, 0.65, zStart, 0.95, 30, 30, 31, 30));
		// 真鍮ベース
		cubes.add(bulletCube(bx, by, 0.65, zStart + 0.95, 0.35, 30, 31, 30, 31));
		// リム (明るい真鍮、一回り太い)
		cubes.add(bulletCube(bx, by, 0.8, zStart + 1.3, 0.12, 31, 31, 31, 31));
	}

	/** 中心(bx,by)・断面 w×w・zStart から長さ len のキューブ。側面 UV と 先端(north) UV を指定 */
	private static JsonObject bulletCube(double bx, double by, double w, double zStart, double len,
			double u, double v, double frontU, double frontV) {
		JsonObject cube = new JsonObject();
		cube.add("origin", arr(round(bx - w / 2), round(by - w / 2), round(zStart)));
		cube.add("size", arr(w, w, len));
		JsonObject uv = new JsonObject();
		for (String face : new String[] { "south", "east", "west", "up", "down" }) {
			uv.add(face, faceUv(u, v, 0.5, 0.5));
		}
		uv.add("north", faceUv(frontU, frontV, 0.5, 0.5));
		cube.add("uv", uv);
		return cube;
	}

	private static JsonObject faceUv(double u, double v, double w, double h) {
		JsonObject f = new JsonObject();
		f.add("uv", arr(u, v));
		f.add("uv_size", arr(w, h));
		return f;
	}

	/**
	 * 額縁 (fixed) の向きを剣モデルの display.fixed.rotation から導出する。
	 * 座標系変換 (xミラー等) により vanilla の [rx,ry,rz] は TACZ では
	 * [-rx,-ry,rz] に対応する。display.fixed が無い場合は実測の既定値。
	 */
	private static JsonArray fixedRotation(JsonObject model) {
		if (model.has("display")) {
			JsonObject disp = model.getAsJsonObject("display");
			if (disp.has("fixed") && disp.getAsJsonObject("fixed").has("rotation")) {
				JsonArray r = disp.getAsJsonObject("fixed").getAsJsonArray("rotation");
				return arr(-r.get(0).getAsDouble(), -r.get(1).getAsDouble(), r.get(2).getAsDouble());
			}
		}
		return arr(0, 90, -35);
	}

	private static JsonObject bone(String name, String parent, double px, double py, double pz) {
		JsonObject b = new JsonObject();
		b.addProperty("name", name);
		if (parent != null) b.addProperty("parent", parent);
		b.add("pivot", arr(px, py, pz));
		return b;
	}

	private static JsonArray arr(double... vals) {
		JsonArray a = new JsonArray();
		for (double v : vals) a.add(round(v));
		return a;
	}

	private static double round(double v) {
		return Math.round(v * 100000.0) / 100000.0;
	}
}
