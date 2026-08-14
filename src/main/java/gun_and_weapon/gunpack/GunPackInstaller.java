package gun_and_weapon.gunpack;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import net.minecraftforge.fml.loading.FMLPaths;

import gun_and_weapon.GunAndWeaponMod;

/**
 * TACZ ガンパックのインストール (Mod構築時に毎回実行)。
 *
 * TACZ の registerExportResource は jar 内の静的ファイルをコピーするだけなので使わず、
 * 自前で <gamedir>/tacz/gunblade_pack に書き出す:
 *   1. 静的ファイル (meta / display / animation / lang / index / data / recipe / アイコン)
 *      を jar からコピー
 *   2. gunblade_geo.json は剣モデル (models/item/gunblade_sword.json) から
 *      TaczGeoGenerator で毎回生成  ← 剣モデルが唯一のソース
 *   3. uv テクスチャは剣テクスチャ (textures/item/gunblade.png) をコピー
 *
 * これにより Blockbench で剣側を編集して再起動するだけで銃側も追従する。
 */
public final class GunPackInstaller {

	private static final String RES = "/assets/gun_and_weapon/custom/gunblade_pack/";
	private static final String SWORD_MODEL = "/assets/gun_and_weapon/models/item/gunblade_sword.json";
	private static final String SWORD_TEXTURE = "/assets/gun_and_weapon/textures/item/gunblade.png";
	private static final String[] STATIC_FILES = {
			"gunpack.meta.json",
			"assets/gun_and_weapon/animations/gunblade.animation.json",
			"assets/gun_and_weapon/lang/en_us.json",
			"assets/gun_and_weapon/lang/ja_jp.json",
			"data/gun_and_weapon/index/guns/gunblade.json",
			"data/gun_and_weapon/data/guns/gunblade_data.json",
			// スピードローダー (円状) / スピードストリップ (帯状) — extended_mag アタッチメント
			"assets/gun_and_weapon/display/attachments/speedloader_display.json",
			"assets/gun_and_weapon/textures/attachment/slot/speedloader.png",
			"data/gun_and_weapon/index/attachments/speedloader.json",
			"data/gun_and_weapon/data/attachments/speedloader_data.json",
			"data/gun_and_weapon/recipes/attachments/speedloader.json",
			"assets/gun_and_weapon/display/attachments/speed_strip_display.json",
			"assets/gun_and_weapon/textures/attachment/slot/speed_strip.png",
			"data/gun_and_weapon/index/attachments/speed_strip.json",
			"data/gun_and_weapon/data/attachments/speed_strip_data.json",
			"data/gun_and_weapon/recipes/attachments/speed_strip.json",
			// 筍ストック — stock アタッチメント。テクスチャは手描き用に別ファイル参照。
			"assets/gun_and_weapon/display/attachments/bamboo_shoot_stock_display.json",
			"assets/gun_and_weapon/geo_models/attachment/bamboo_shoot_stock.json",
			"data/gun_and_weapon/index/attachments/bamboo_shoot_stock.json",
			"data/gun_and_weapon/data/attachments/bamboo_shoot_stock_data.json",
			"data/gun_and_weapon/recipes/attachments/bamboo_shoot_stock.json",
			// アタッチメントの3Dモデル (アイテム表示用)
			"assets/gun_and_weapon/geo_models/attachment/speedloader.json",
			"assets/gun_and_weapon/geo_models/attachment/speed_strip.json",
			"assets/gun_and_weapon/textures/attachment/uv/loaders.png",
			// ガンブレードに装着できるアタッチメントの適合表 (これが無いと装着不可)
			"data/gun_and_weapon/tacz_tags/attachments/allow_attachments/gunblade.json",
			// リロードのLuaステートマシン (1発ずつ/2発ずつ/4発ずつ装填の切替)
			"assets/gun_and_weapon/scripts/gunblade_state_machine.lua",
	};

	private GunPackInstaller() {}

	/**
	 * display json を jar のテンプレートから読み、transform.scale の
	 * ground / fixed を剣モデルの display.ground / display.fixed の scale で
	 * 上書きして書き出す (ドロップ・額縁の大きさを剣モードと一致させる)。
	 */
	private static void writeDisplayJson(Path packDir) throws java.io.IOException {
		com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
		com.google.gson.JsonObject display;
		try (InputStream is = GunPackInstaller.class.getResourceAsStream(
				RES + "assets/gun_and_weapon/display/guns/gunblade_display.json")) {
			if (is == null) throw new java.io.IOException("display json template not found");
			display = new com.google.gson.Gson().fromJson(
					new InputStreamReader(is, StandardCharsets.UTF_8), com.google.gson.JsonObject.class);
		}
		com.google.gson.JsonObject swordDisplay = null;
		try (InputStream is = GunPackInstaller.class.getResourceAsStream(SWORD_MODEL)) {
			if (is != null) {
				com.google.gson.JsonObject model = new com.google.gson.Gson().fromJson(
						new InputStreamReader(is, StandardCharsets.UTF_8), com.google.gson.JsonObject.class);
				if (model.has("display")) swordDisplay = model.getAsJsonObject("display");
			}
		}
		com.google.gson.JsonObject scale = display.getAsJsonObject("transform").getAsJsonObject("scale");
		scale.add("ground", scaleOf(swordDisplay, "ground"));
		scale.add("fixed", scaleOf(swordDisplay, "fixed"));

		Path target = packDir.resolve("assets/gun_and_weapon/display/guns/gunblade_display.json");
		Files.createDirectories(target.getParent());
		Files.writeString(target, gson.toJson(display), StandardCharsets.UTF_8);
	}

	/** 剣モデル display.<context>.scale を取得 (無ければ [1,1,1] = vanilla 既定) */
	private static com.google.gson.JsonArray scaleOf(com.google.gson.JsonObject swordDisplay, String context) {
		com.google.gson.JsonArray out = new com.google.gson.JsonArray();
		double v = 1.0;
		if (swordDisplay != null && swordDisplay.has(context)
				&& swordDisplay.getAsJsonObject(context).has("scale")) {
			v = swordDisplay.getAsJsonObject(context).getAsJsonArray("scale").get(0).getAsDouble();
		}
		for (int i = 0; i < 3; i++) out.add(v);
		return out;
	}

	public static void install() {
		try {
			Path packDir = FMLPaths.GAMEDIR.get().resolve("tacz").resolve("gunblade_pack");

			// 古い内容を削除して常に作り直す
			if (Files.exists(packDir)) {
				try (var walk = Files.walk(packDir)) {
					walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
				}
			}

			// 1) 静的ファイル
			for (String file : STATIC_FILES) {
				Path target = packDir.resolve(file);
				Files.createDirectories(target.getParent());
				try (InputStream is = GunPackInstaller.class.getResourceAsStream(RES + file)) {
					if (is == null) {
						GunAndWeaponMod.LOGGER.warn("Gun pack resource not found: {}", RES + file);
						continue;
					}
					Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}

			// 2) 剣モデル → TACZ geo を生成
			Path geoTarget = packDir.resolve("assets/gun_and_weapon/geo_models/gun/gunblade_geo.json");
			Files.createDirectories(geoTarget.getParent());
			try (InputStream is = GunPackInstaller.class.getResourceAsStream(
					SWORD_MODEL)) {
				if (is == null) throw new IOException("sword model not found");
				String geoJson = TaczGeoGenerator.generate(new InputStreamReader(is, StandardCharsets.UTF_8));
				Files.writeString(geoTarget, geoJson, StandardCharsets.UTF_8);
			}

			// 2.5) display json: ground/fixed スケールを剣モデルの display から導出
			writeDisplayJson(packDir);

			// 2.6) slot / hud アイコン: 剣モデルの display.gui 構図でレンダリング
			Path slotTarget = packDir.resolve("assets/gun_and_weapon/textures/gun/slot/gunblade.png");
			Path hudTarget = packDir.resolve("assets/gun_and_weapon/textures/gun/hud/gunblade.png");
			Files.createDirectories(slotTarget.getParent());
			Files.createDirectories(hudTarget.getParent());
			try (InputStream modelIs = GunPackInstaller.class.getResourceAsStream(SWORD_MODEL);
			     InputStream texIs = GunPackInstaller.class.getResourceAsStream(SWORD_TEXTURE);
			     var slotOs = Files.newOutputStream(slotTarget);
			     var hudOs = Files.newOutputStream(hudTarget)) {
				if (modelIs != null && texIs != null) {
					IconRenderer.render(modelIs, texIs, slotOs, hudOs);
				}
			}

			// 3) 剣テクスチャ → 銃 uv テクスチャ
			Path texTarget = packDir.resolve("assets/gun_and_weapon/textures/gun/uv/gunblade.png");
			Files.createDirectories(texTarget.getParent());
			try (InputStream is = GunPackInstaller.class.getResourceAsStream(
					SWORD_TEXTURE)) {
				if (is == null) throw new IOException("sword texture not found");
				Files.copy(is, texTarget, StandardCopyOption.REPLACE_EXISTING);
			}

			GunAndWeaponMod.LOGGER.info("Gunblade gun pack installed (geo generated from sword model): {}", packDir);
		} catch (Exception e) {
			GunAndWeaponMod.LOGGER.error("Failed to install gunblade gun pack", e);
		}
	}
}
