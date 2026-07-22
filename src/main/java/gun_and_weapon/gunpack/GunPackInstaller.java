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
	private static final String[] STATIC_FILES = {
			"gunpack.meta.json",
			"assets/gun_and_weapon/animations/gunblade.animation.json",
			"assets/gun_and_weapon/display/guns/gunblade_display.json",
			"assets/gun_and_weapon/lang/en_us.json",
			"assets/gun_and_weapon/lang/ja_jp.json",
			"assets/gun_and_weapon/textures/gun/slot/gunblade.png",
			"assets/gun_and_weapon/textures/gun/hud/gunblade.png",
			"data/gun_and_weapon/index/guns/gunblade.json",
			"data/gun_and_weapon/data/guns/gunblade_data.json",
			"data/gun_and_weapon/recipes/gun/gunblade.json",
	};

	private GunPackInstaller() {}

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
					"/assets/gun_and_weapon/models/item/gunblade_sword.json")) {
				if (is == null) throw new IOException("sword model not found");
				String geoJson = TaczGeoGenerator.generate(new InputStreamReader(is, StandardCharsets.UTF_8));
				Files.writeString(geoTarget, geoJson, StandardCharsets.UTF_8);
			}

			// 3) 剣テクスチャ → 銃 uv テクスチャ
			Path texTarget = packDir.resolve("assets/gun_and_weapon/textures/gun/uv/gunblade.png");
			Files.createDirectories(texTarget.getParent());
			try (InputStream is = GunPackInstaller.class.getResourceAsStream(
					"/assets/gun_and_weapon/textures/item/gunblade.png")) {
				if (is == null) throw new IOException("sword texture not found");
				Files.copy(is, texTarget, StandardCopyOption.REPLACE_EXISTING);
			}

			GunAndWeaponMod.LOGGER.info("Gunblade gun pack installed (geo generated from sword model): {}", packDir);
		} catch (Exception e) {
			GunAndWeaponMod.LOGGER.error("Failed to install gunblade gun pack", e);
		}
	}
}
