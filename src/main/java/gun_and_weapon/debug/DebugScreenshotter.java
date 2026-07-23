package gun_and_weapon.debug;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import gun_and_weapon.GunAndWeaponMod;

/**
 * デバッグ用 (環境変数 GUNBLADE_AUTOSHOT がある時だけ有効):
 *   - 5秒ごとに自動スクリーンショット (run/screenshots/gunblade_debug_N.png)
 *   - run/debug_cmd.txt にコマンドを書くと毎tick読み取って実行し、ファイルを削除する:
 *       fp / tp / tpf   … 視点切替 (一人称/三人称背面/三人称前面)
 *       shot            … 即時スクリーンショット (gunblade_manual_N.png)
 *       /<command>      … サーバーコマンド実行 (例: /time set day)
 *   通常プレイでは何もしない。
 */
// ===== 一旦無効化 (デバッグ用) =====
// 開発中の自動スクショ/コマンド駆動テスト用クラス。
// 使いたい時は下の @Mod.EventBusSubscriber のコメントを外して、
// 環境変数 GUNBLADE_AUTOSHOT=1 を付けて起動する。
@Mod.EventBusSubscriber(modid = GunAndWeaponMod.MODID, value = Dist.CLIENT)
public class DebugScreenshotter {

	private static final boolean ENABLED = System.getenv("GUNBLADE_AUTOSHOT") != null;
	private static int tick = 0;
	private static int manualShot = 0;

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (!ENABLED || event.phase != TickEvent.Phase.END) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;

		tick++;
		if (tick % 100 == 0) {
			Screenshot.grab(mc.gameDirectory, "gunblade_debug_" + (tick / 100) + ".png",
					mc.getMainRenderTarget(), component -> {});
		}

		Path cmdFile = mc.gameDirectory.toPath().resolve("debug_cmd.txt");
		if (!Files.exists(cmdFile)) return;
		List<String> lines;
		try {
			lines = Files.readAllLines(cmdFile);
			Files.delete(cmdFile);
		} catch (Exception e) {
			return;
		}
		for (String line : lines) {
			line = line.trim();
			if (line.isEmpty()) continue;
			try {
				execute(mc, line);
			} catch (Exception e) {
				GunAndWeaponMod.LOGGER.error("debug_cmd failed: {}", line, e);
			}
		}
	}

	private static void execute(Minecraft mc, String line) {
		switch (line) {
			case "fp" -> mc.options.setCameraType(CameraType.FIRST_PERSON);
			case "reload" -> mc.reloadResourcePacks();
			case "closegui" -> mc.setScreen(null);
			case "toggle" -> {
				MinecraftServer server = mc.getSingleplayerServer();
				if (server != null && mc.player != null) {
					var uuid = mc.player.getUUID();
					server.execute(() -> {
						var sp = server.getPlayerList().getPlayer(uuid);
						if (sp != null) gun_and_weapon.item.GunbladeModeSwitch.toggle(sp);
					});
				}
			}
			case "slot1" -> mc.player.getInventory().selected = 0;
			case "slot2" -> mc.player.getInventory().selected = 1;
			case "slot3" -> mc.player.getInventory().selected = 2;
			case "tp" -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			case "tpf" -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			case "shot" -> {
				manualShot++;
				Screenshot.grab(mc.gameDirectory, "gunblade_manual_" + manualShot + ".png",
						mc.getMainRenderTarget(), component -> {});
			}
			default -> {
				if (line.startsWith("/")) {
					MinecraftServer server = mc.getSingleplayerServer();
					if (server != null) {
						String cmd = line;
						server.execute(() -> server.getCommands().performPrefixedCommand(
								server.createCommandSourceStack(), cmd));
					}
				}
			}
		}
	}
}
